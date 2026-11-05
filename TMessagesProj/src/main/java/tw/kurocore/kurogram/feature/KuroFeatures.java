package tw.kurocore.kurogram.feature;

import android.content.Context;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

import tw.kurocore.kurogram.feature.stub.ContactChangesStub;
import tw.kurocore.kurogram.feature.stub.SpecialContactsStub;
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

    // Safe stubs until a module registers the real implementations (see FeaturesProvider).
    private static volatile ThemeStore themes = new ThemeStoreStub();
    private static volatile SpecialContacts specialContacts = new SpecialContactsStub();
    private static volatile ContactChanges contactChanges = new ContactChangesStub();
    private static boolean initialised;

    private KuroFeatures() {
    }

    public static ThemeStore themes() {
        return themes;
    }

    public static SpecialContacts specialContacts() {
        return specialContacts;
    }

    public static ContactChanges contactChanges() {
        return contactChanges;
    }

    /**
     * For features that have no screen of their own to show an empty state in: shows a Toast and returns true
     * when the feature is not part of this build. Use before opening such a feature.
     */
    public static boolean toastIfUnavailable(KuroFeature feature) {
        if (feature.isAvailable()) {
            return false;
        }
        AndroidUtilities.runOnUIThread(() -> Toast.makeText(org.telegram.messenger.ApplicationLoader.applicationContext,
                unavailableMessage(), Toast.LENGTH_SHORT).show());
        return true;
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

                    @Override
                    public void specialContacts(SpecialContacts feature) {
                        specialContacts = feature != null ? feature : new SpecialContactsStub();
                    }

                    @Override
                    public void contactChanges(ContactChanges feature) {
                        contactChanges = feature != null ? feature : new ContactChangesStub();
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
        for (KuroFeature f : new KuroFeature[]{themes, specialContacts, contactChanges}) {
            try {
                f.onAppStart(app);
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    }

    /** The one sentence a screen shows when a feature is not part of this build. */
    public static String unavailableMessage() {
        return LocaleController.getString(R.string.KuroFeatureUnavailable);
    }
}
