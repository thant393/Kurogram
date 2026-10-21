package tw.nekomimi.nekogram.skin;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Holds the active skin. Screens read values from get() when they build views and listen to
 * NotificationCenter.kuroSkinChanged to re-apply them live. A bad skin is rejected whole and
 * the previous one stays active.
 */
public final class KuroSkinManager {

    private static final String PREFS = "kuro_skin";
    private static final String KEY_JSON = "active_json";
    private static final KuroSkinManager INSTANCE = new KuroSkinManager();

    private volatile SkinSpec active = SkinSpec.defaults();
    private volatile boolean loaded;

    private KuroSkinManager() {
    }

    public static KuroSkinManager get() {
        return INSTANCE;
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public SkinSpec spec() {
        if (loaded) {
            return active; // hot path (Theme.getColor): no locking once loaded
        }
        return load();
    }

    private synchronized SkinSpec load() {
        if (!loaded) {
            if (ApplicationLoader.applicationContext == null) {
                return active; // too early to read preferences: defaults for now, try again later
            }
            try {
                String json = prefs().getString(KEY_JSON, null);
                if (json != null) {
                    active = SkinSpec.parse(json);
                }
            } catch (Exception ignore) {
                active = SkinSpec.defaults();
            }
            loaded = true;
        }
        return active;
    }

    /** The skin's accent colour, or the theme's own accent when the skin doesn't set one. */
    public int accent() {
        int override = spec().accentColor;
        return override != 0 ? override : org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_featuredStickers_addButton);
    }

    /** Validates, persists and applies a skin given as JSON; throws IllegalArgumentException if invalid. */
    public void apply(String json) {
        SkinSpec parsed = SkinSpec.parse(json);
        synchronized (this) {
            loaded = true;
            active = parsed;
            prefs().edit().putString(KEY_JSON, json).apply();
        }
        notifyChanged();
    }

    public void reset() {
        synchronized (this) {
            loaded = true;
            active = SkinSpec.defaults();
            prefs().edit().remove(KEY_JSON).apply();
        }
        notifyChanged();
    }

    /** Reads manifest.json out of a skin zip (nothing is extracted to disk) and applies it. */
    public void applyZip(File zip) throws Exception {
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (!"manifest.json".equals(entry.getName())) {
                    continue;
                }
                apply(readLimited(in));
                return;
            }
        }
        throw new IllegalArgumentException("manifest.json not found in the skin");
    }

    private static String readLimited(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > SkinSpec.MAX_JSON_BYTES) {
                throw new IllegalArgumentException("Skin file is too large");
            }
        }
        return out.toString("UTF-8");
    }

    /** A corner radius designed as baseDp for the default skin, scaled to the active skin, in px. */
    public int radiusPx(float baseDp) {
        return AndroidUtilities.dp(baseDp * spec().cardRadiusDp / (float) SkinSpec.defaults().cardRadiusDp);
    }

    /** The skin's accent when it sets one, otherwise 0 (keep the theme's own colour). */
    public int accentOverride() {
        return spec().accentColor;
    }

    private void notifyChanged() {
        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.kuroSkinChanged);
            // Rebuild every open Telegram screen so the new colours / radii show everywhere now.
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
        });
    }
}
