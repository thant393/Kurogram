package tw.kurocore.kurogram.feature;

import android.content.Context;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

import tw.kurocore.kurogram.feature.intree.InTreeThemeStore;
import tw.kurocore.kurogram.feature.stub.ThemeStoreStub;

/**
 * Entry point of the optional features. Screens ask this class for a feature and never reference an
 * implementation. A private module may provide the real implementations by shipping the class
 * {@code tw.kurocore.kurogram.features.impl.FeaturesBootstrap} (see {@link FeaturesProvider}); when that class
 * is absent the safe stubs stay in place.
 */
public final class KuroFeatures {

    /** Bumped whenever an interface in this package changes incompatibly; the private module checks it. */
    public static final int API_VERSION = 1;

    private static final String BOOTSTRAP_CLASS = "tw.kurocore.kurogram.features.impl.FeaturesBootstrap";

    // Step 1 of the modularisation: the in-tree implementation (the current code) is still the default.
    // Step 3 replaces it with the stub and lets the private module register the real one.
    private static volatile ThemeStore themes = new InTreeThemeStore();
    private static boolean initialised;

    private KuroFeatures() {
    }

    public static ThemeStore themes() {
        return themes;
    }

    /** Used by the registrar handed to the private module. */
    static void setThemes(ThemeStore store) {
        themes = store != null ? store : new ThemeStoreStub();
    }

    /** Call once from ApplicationLoader. Never throws. */
    public static synchronized void init(Context app) {
        if (initialised) {
            return;
        }
        initialised = true;
        try {
            Class<?> c = Class.forName(BOOTSTRAP_CLASS);
            FeaturesProvider provider = (FeaturesProvider) c.getDeclaredConstructor().newInstance();
            if (provider.apiVersion() == API_VERSION) {
                provider.register(new FeaturesProvider.Registrar() {
                    @Override
                    public void themes(ThemeStore store) {
                        setThemes(store);
                    }
                });
            } else {
                FileLog.e("kuro-features API " + provider.apiVersion() + " does not match core API " + API_VERSION);
            }
        } catch (ClassNotFoundException ignored) {
            // community build: the defaults stay
        } catch (Throwable t) {
            FileLog.e(t);
        }
        try {
            themes.onAppStart(app);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    /** The one sentence a screen shows when a feature is not part of this build. */
    public static String unavailableMessage() {
        return LocaleController.getString(R.string.KuroFeatureUnavailable);
    }
}
