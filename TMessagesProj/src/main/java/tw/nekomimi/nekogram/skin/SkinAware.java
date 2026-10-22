package tw.nekomimi.nekogram.skin;

/**
 * Implemented by a screen or view that restyles itself when the skin changes.
 *
 * Register with KuroSkinManager.register(this) when it becomes visible (fragment onResume / view
 * attached) and unregister when it goes away. The manager holds only weak references, so a
 * forgotten unregister can't leak a screen, but a live screen still needs to register.
 *
 * onSkinChanged runs on the UI thread, once per skin change (changes in the same tick are merged).
 */
public interface SkinAware {

    void onSkinChanged(SkinTokens tokens);

    /**
     * true: this screen fully restyles itself in onSkinChanged, so the app does NOT rebuild it
     * (scroll position and data are kept). false (default): the app rebuilds it, as before.
     */
    default boolean restylesInPlace() {
        return false;
    }
}
