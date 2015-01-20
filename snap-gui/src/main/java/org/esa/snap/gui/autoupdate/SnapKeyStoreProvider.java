package org.esa.snap.gui.autoupdate;

import java.io.InputStream;
import java.security.KeyStore;
import java.util.logging.Level;

import org.esa.snap.gui.SnapApp;
import org.netbeans.spi.autoupdate.KeyStoreProvider;

@org.openide.util.lookup.ServiceProvider(service = org.netbeans.spi.autoupdate.KeyStoreProvider.class)
public final class SnapKeyStoreProvider implements KeyStoreProvider {

    private static final String KS_RESOURCE_PATH = "/keystore/snap.ks";

    @Override
    public KeyStore getKeyStore() {
        try (InputStream inputStream = getClass().getResourceAsStream(KS_RESOURCE_PATH)) {
            KeyStore keyStore;
            keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(inputStream, null);
            return keyStore;
        } catch (Exception ex) {
            SnapApp.getDefault().getLogger().log(Level.WARNING, ex.getMessage(), ex);
        }
        return null;
    }

}