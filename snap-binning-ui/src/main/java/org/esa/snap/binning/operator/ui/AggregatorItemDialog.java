/*
 * Copyright (C) 2011 Brockmann Consult GmbH (info@brockmann-consult.de)
 * 
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.snap.binning.operator.ui;

import com.bc.ceres.binding.Property;
import com.bc.ceres.binding.PropertyContainer;
import com.bc.ceres.binding.PropertySet;
import com.bc.ceres.binding.ValidationException;
import com.bc.ceres.binding.ValueSet;
import com.bc.ceres.swing.binding.PropertyPane;
import org.esa.snap.binning.AggregatorConfig;
import org.esa.snap.binning.AggregatorDescriptor;
import org.esa.snap.binning.TypedDescriptor;
import org.esa.snap.binning.TypedDescriptorsRegistry;
import org.esa.snap.core.gpf.annotations.ParameterDescriptorFactory;
import org.esa.snap.ui.ModalDialog;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

class AggregatorItemDialog extends ModalDialog {

    private final AggregatorItem aggregatorItem;
    private final String[] sourceVarNames;
    private AggregatorConfig aggregatorConfig;
    private AggregatorDescriptor aggregatorDescriptor;
    private PropertySet aggregatorPropertySet;
    private JComboBox<String> aggregatorComboBox;

    public AggregatorItemDialog(Window parent, String[] sourceVarNames, AggregatorItem aggregatorItem, boolean initWithDefaults) {
        super(parent, "Edit Aggregator", ID_OK | ID_CANCEL, null);
        this.sourceVarNames = sourceVarNames;
        this.aggregatorItem = aggregatorItem;
        aggregatorConfig = aggregatorItem.aggregatorConfig;
        aggregatorDescriptor = aggregatorItem.aggregatorDescriptor;
        aggregatorPropertySet = createPropertySet(aggregatorConfig);
        if (initWithDefaults) {
            aggregatorPropertySet.setDefaultValues();
        } else {
            PropertySet objectPropertySet = PropertyContainer.createObjectBacked(aggregatorConfig);
            Property[] objectProperties = objectPropertySet.getProperties();
            for (Property objectProperty : objectProperties) {
                aggregatorPropertySet.setValue(objectProperty.getName(), objectProperty.getValue());
            }
        }
    }

    @Override
    public int show() {
        setContent(createUI());
        this.getJDialog().getRootPane().registerKeyboardAction(e -> close(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        getJDialog().setResizable(false);
        return super.show();
    }


    @Override
    protected boolean verifyUserInput() {
        try {
            for (Property property : aggregatorPropertySet.getProperties()) {
                property.validate(property.getValue());
            }
        } catch (ValidationException e) {
            this.showErrorDialog(e.getMessage(), "Invalid Aggregator Properties");
            return false;
        }
        return true;
    }

    @Override
    protected void onOK() {
        AggregatorConfig config = aggregatorDescriptor.createConfig();
        PropertySet objectPropertySet = config.asPropertySet();
        Property[] mapProperties = aggregatorPropertySet.getProperties();
        for (Property mapProperty : mapProperties) {
            objectPropertySet.setValue(mapProperty.getName(), mapProperty.getValue());
        }
        objectPropertySet.setValue("type", aggregatorDescriptor.getName());
        aggregatorItem.aggregatorConfig = config;
        aggregatorItem.aggregatorDescriptor = aggregatorDescriptor;
        super.onOK();
    }

    private Component createUI() {
        final JPanel mainPanel = new JPanel(new BorderLayout(5, 5));

        final TypedDescriptorsRegistry registry = TypedDescriptorsRegistry.getInstance();
        List<AggregatorDescriptor> aggregatorDescriptors = registry.getDescriptors(AggregatorDescriptor.class);
        List<String> aggregatorNames = aggregatorDescriptors.stream().map(TypedDescriptor::getName).collect(Collectors.toList());
        Collections.sort(aggregatorNames);

        aggregatorComboBox = new JComboBox<>(aggregatorNames.toArray(new String[aggregatorNames.size()]));
        aggregatorComboBox.setSelectedItem(aggregatorConfig.getName());
        aggregatorComboBox.addActionListener(e -> {
            aggregatorDescriptor = getDescriptorFromComboBox();
            aggregatorConfig = aggregatorDescriptor.createConfig();
            aggregatorPropertySet = createPropertySet(aggregatorConfig);
            JPanel aggrPropertyPanel = createPropertyPanel(aggregatorPropertySet);
            mainPanel.remove(1);
            mainPanel.add(aggrPropertyPanel, BorderLayout.CENTER);
            getJDialog().getContentPane().revalidate();
            getJDialog().pack();
        });

        JPanel aggrPropertyPanel = createPropertyPanel(aggregatorPropertySet);

        mainPanel.add(aggregatorComboBox, BorderLayout.NORTH);
        mainPanel.add(aggrPropertyPanel, BorderLayout.CENTER);
        return mainPanel;
    }

    private PropertySet createPropertySet(AggregatorConfig config) {
        return PropertyContainer.createMapBacked(new HashMap<>(), config.getClass(),
                                                 new ParameterDescriptorFactory());
    }

    private AggregatorDescriptor getDescriptorFromComboBox() {
        final TypedDescriptorsRegistry registry = TypedDescriptorsRegistry.getInstance();
        String aggrType = (String) aggregatorComboBox.getSelectedItem();
        return registry.getDescriptor(AggregatorDescriptor.class, aggrType);
    }

    private JPanel createPropertyPanel(PropertySet propertySet) {
        Property[] properties = propertySet.getProperties();
        for (Property property : properties) {
            String propertyName = property.getName();
            if ("type".equals(propertyName)) {
                property.getDescriptor().setAttribute("visible", false);
            }
            if (AggregatorTableController.isSourcePropertyName(propertyName)) {
                property.getDescriptor().setValueSet(new ValueSet(sourceVarNames));
            }
        }
        return new PropertyPane(propertySet).createPanel();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            final JFrame jFrame = new JFrame();
            jFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            final JButton aggregatorComboBox1 = new JButton("Show Dialog...");
            aggregatorComboBox1.addActionListener(e -> {
                AggregatorItemDialog dialog1 = new AggregatorItemDialog(jFrame, new String[]{
                        "stein",
                        "papier",
                        "schere",
                        "echse",
                        "spock"
                }, new AggregatorItem(), true);
                dialog1.getJDialog().setLocation(550, 300);
                dialog1.show();

            });
            jFrame.setContentPane(aggregatorComboBox1);
            jFrame.setBounds(300, 300, 200, 80);
            jFrame.setVisible(true);
        });
    }
}
