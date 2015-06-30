/*
 * Copyright (C) 2015 CS SI
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

package org.esa.snap.smart.configurator.ui;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.SubProgressMonitor;
import com.bc.ceres.core.VirtualDir;
import com.bc.ceres.swing.progress.ProgressMonitorSwingWorker;
import org.esa.snap.configurator.ConfigurationOptimizer;
import org.esa.snap.configurator.PerformanceParameters;
import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.framework.gpf.Operator;
import org.esa.snap.framework.gpf.experimental.Output;
import org.esa.snap.framework.gpf.internal.OperatorExecutor;
import org.esa.snap.framework.gpf.internal.OperatorProductReader;
import org.esa.snap.framework.gpf.ui.DefaultSingleTargetProductDialog;
import org.esa.snap.framework.gpf.ui.TargetProductSelectorModel;
import org.esa.snap.framework.ui.AppContext;
import org.esa.snap.gpf.operators.standard.WriteOp;
import org.esa.snap.rcp.SnapDialogs;
import org.esa.snap.rcp.actions.file.SaveProductAsAction;
import org.esa.snap.util.SystemUtils;

import java.io.File;
import java.io.IOException;

/**
 * Dialog to launch performance parameters benchmark.
 *
 * @author Manuel Campomanes
 */
public class BenchmarkDialog extends DefaultSingleTargetProductDialog {

    /**
     * Benchmark calculs model
     */
    private Benchmark benchmarkModel;
    /**
     * Parent panel
     */
    private PerformancePanel perfPanel;

    /**
     * Constructor
     *
     * @param perfPanel Parent JPanel
     * @param operatorName Operator name
     * @param benchmarkModel Benchmark model
     * @param appContext Application context
     */
    public BenchmarkDialog(PerformancePanel perfPanel, String operatorName, Benchmark benchmarkModel, AppContext appContext){
        super(operatorName, appContext, "Benchmark "+operatorName, null, false);
        this.benchmarkModel = benchmarkModel;
        this.getJDialog().setModal(true);
        this.perfPanel = perfPanel;
    }

    private class ProductWriterSwingWorker extends ProgressMonitorSwingWorker<Product, Object> {

        private final Product targetProduct;
        private BenchmarkSingleCalcul currentBenchmarkCalcul;
        private String benchmarkCounter;

        private ProductWriterSwingWorker(Product targetProduct, BenchmarkSingleCalcul currentBenchmarkCalcul, String benchmarkCounter) {
            super(getJDialog(), "Benchmark Tests");
            this.targetProduct = targetProduct;
            this.currentBenchmarkCalcul = currentBenchmarkCalcul;
            this.benchmarkCounter = benchmarkCounter;
        }

        @Override
        protected Product doInBackground(ProgressMonitor pm) throws Exception {
            final TargetProductSelectorModel model = getTargetProductSelector().getModel();
            pm.beginTask("Benchmark running... ("+this.benchmarkCounter+")", model.isOpenInAppSelected() ? 100 : 95);

            Product product = null;
            try {
                long startTime = System.currentTimeMillis();
                Operator execOp = null;
                if (targetProduct.getProductReader() instanceof OperatorProductReader) {
                    final OperatorProductReader opReader = (OperatorProductReader) targetProduct.getProductReader();
                    Operator operator = opReader.getOperatorContext().getOperator();
                    boolean autoWriteDisabled = operator instanceof Output
                            || operator.getSpi().getOperatorDescriptor().isAutoWriteDisabled();
                    if (autoWriteDisabled) {
                        execOp = operator;
                    }
                }
                if (execOp == null) {
                    WriteOp writeOp = new WriteOp(targetProduct, model.getProductFile(), model.getFormatName());
                    writeOp.setDeleteOutputOnFailure(true);
                    writeOp.setWriteEntireTileRows(true);
                    writeOp.setClearCacheAfterRowWrite(false);
                    execOp = writeOp;
                }
                final OperatorExecutor executor = OperatorExecutor.create(execOp);
                executor.execute(SubProgressMonitor.create(pm, 95));
                //save execution time
                this.currentBenchmarkCalcul.setExecutionTime(System.currentTimeMillis() - startTime);
            } finally {
                if (product != targetProduct) {
                    targetProduct.dispose();
                }
            }
            return product;
        }
    }

    @Override
    protected void onApply() {
        //temporary directory for benchmark
        String tmpdirPath = System.getProperty("java.io.tmpdir") + "\\snap-benchmark-tmp";
        appContext.getPreferences().setPropertyString(SaveProductAsAction.PREFERENCES_KEY_LAST_PRODUCT_DIR, tmpdirPath);
        //save current performance parameters
        PerformanceParameters defaultPerformanceParameters = ConfigurationOptimizer.getInstance().getActualPerformanceParameters();
        PerformanceParameters benchmarkPerformanceParameters = new PerformanceParameters(defaultPerformanceParameters);
        int benchmarkCounterIndex = 1;
        for(BenchmarkSingleCalcul benchmarkSingleCalcul : this.benchmarkModel.getBenchmarkCalculs()){
            Product targetProduct = null;
            //load performance parameters for current benchmark
            benchmarkPerformanceParameters.setDefaultTileSize(benchmarkSingleCalcul.getTileSize());
            benchmarkPerformanceParameters.setCacheSize(benchmarkSingleCalcul.getCacheSize());
            benchmarkPerformanceParameters.setNbThreads(benchmarkSingleCalcul.getNbThreads());
            loadBenchmarkPerfParams(benchmarkPerformanceParameters);
            try {
                targetProduct = createTargetProduct();
                if (targetProduct == null) {
                    throw new NullPointerException("Target product is null.");
                }
            } catch (Throwable t) {
                handleInitialisationError(t);
            }
            String benchmarkCounter = benchmarkCounterIndex++ + "/"+this.benchmarkModel.getBenchmarkCalculs().size();
            final ProgressMonitorSwingWorker worker = new ProductWriterSwingWorker(targetProduct, benchmarkSingleCalcul, benchmarkCounter);
            worker.executeWithBlocking();
        }
        //sort benchmark results and return the faster
        BenchmarkSingleCalcul bestBenchmarkSingleCalcul = this.benchmarkModel.getFasterBenchmarkSingleCalcul();
        SnapDialogs.showInformation("Benchmark results", this.benchmarkModel.toString(), null);
        //load old params (before benchmark)
        loadBenchmarkPerfParams(defaultPerformanceParameters);
        //update parent panel with best values
        this.perfPanel.updatePerformanceParameters(bestBenchmarkSingleCalcul);
        //delete benchmark TMP directory
        VirtualDir.deleteFileTree(new File(tmpdirPath));
    }

    /**
     * Load performance parameters.
     *
     * @param performanceParameters
     */
    private void loadBenchmarkPerfParams(PerformanceParameters performanceParameters){
        ConfigurationOptimizer confOptimizer = ConfigurationOptimizer.getInstance();
        confOptimizer.updateCustomisedParameters(performanceParameters);
        try {
            confOptimizer.saveCustomisedParameters();
        } catch (IOException e) {
            SystemUtils.LOG.severe("Could not save performance parameters: " + e.getMessage());
        }
    }
}
