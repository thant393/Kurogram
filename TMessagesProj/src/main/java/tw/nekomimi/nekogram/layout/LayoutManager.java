package tw.nekomimi.nekogram.layout;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;

/**
 * Holds the active layout pack. Screens ask {@link #get()}; when no pack is active (or a saved one
 * no longer validates) they get the classic layout, i.e. Telegram's own design. Applying validates
 * first and keeps the previous layout on any failure.
 */
public final class LayoutManager {

    private static final String PREFS = "kuro_layout";
    private static final String KEY_JSON = "active_json";
    private static final LayoutManager INSTANCE = new LayoutManager();

    private volatile LayoutSpec active = LayoutSpec.classic();
    private volatile boolean loaded;

    private LayoutManager() {
    }

    public static LayoutManager get() {
        return INSTANCE;
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public LayoutSpec spec() {
        if (loaded) {
            return active;
        }
        return load();
    }

    private synchronized LayoutSpec load() {
        if (!loaded) {
            if (ApplicationLoader.applicationContext == null) {
                return active;
            }
            try {
                String json = prefs().getString(KEY_JSON, null);
                if (json != null) {
                    active = LayoutSpec.parse(json);
                }
            } catch (Exception e) {
                FileLog.e(e);
                active = LayoutSpec.classic(); // a saved layout that no longer validates falls back to Telegram's own
            }
            loaded = true;
        }
        return active;
    }

    /** Validates then activates and saves. Throws IllegalArgumentException (reason for the user); nothing changes then. */
    public synchronized void apply(String json) {
        LayoutSpec spec = LayoutSpec.parse(json);
        prefs().edit().putString(KEY_JSON, json).apply();
        active = spec;
        loaded = true;
        notifyChanged();
    }

    /** Back to Telegram's own layout. */
    public synchronized void reset() {
        prefs().edit().remove(KEY_JSON).apply();
        active = LayoutSpec.classic();
        loaded = true;
        notifyChanged();
    }

    private void notifyChanged() {
        org.telegram.messenger.AndroidUtilities.runOnUIThread(() ->
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.kuroSkinChanged));
    }
}
