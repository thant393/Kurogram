package tw.kurocore.kurogram.feature;

/**
 * Implemented by {@code tw.kurocore.kurogram.features.impl.FeaturesBootstrap} in the private kuro-features
 * module (needs a public no-argument constructor). Core calls it once at app start.
 */
public interface FeaturesProvider {

    /** Must equal {@link KuroFeatures#API_VERSION}, otherwise the module is ignored. */
    int apiVersion();

    void register(Registrar registrar);

    interface Registrar {
        void themes(ThemeStore store);
    }
}
