package tw.nekomimi.nekogram.skin;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.Theme;

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

    /** Like radiusPx, but scaled by the skin's dialog radius (dialogs and sheets). */
    public int dialogRadiusPx(float baseDp) {
        return AndroidUtilities.dp(baseDp * spec().dialogRadiusDp / (float) SkinSpec.defaults().dialogRadiusDp);
    }

    /** The skin's accent colour, or the theme's own accent when the skin doesn't set one. */
    public int accent() {
        int override = spec().accentColor;
        return override != 0 ? override : org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_featuredStickers_addButton);
    }

    /**
     * Validates, persists and applies a skin given as JSON as one transaction. Throws
     * IllegalArgumentException when the skin is invalid (nothing changes), or IllegalStateException
     * when applying failed and the previous look was restored (or, if even that failed, reset).
     */
    public void apply(String json) {
        final SkinSpec parsed = SkinSpec.parse(json);
        runOnMain(() -> transact(parsed, json));
    }

    public void reset() {
        runOnMain(() -> transact(SkinSpec.defaults(), null));
    }

    /** Callers are on the UI thread, so the transaction finishes before apply() returns. */
    private void runOnMain(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    r.run();
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            });
        }
    }

    private void transact(SkinSpec next, String json) {
        final SkinSpec prevSpec = spec();
        final String prevJson = prefs().getString(KEY_JSON, null);
        final SkinNative.Snapshot snapshot = SkinNative.snapshot();
        try {
            synchronized (this) {
                loaded = true;
                active = next;
                write(json);
            }
            SkinNative.apply(next);
            Theme.refreshThemeColors(false, true);
        } catch (Throwable t) {
            FileLog.e(t);
            try {
                synchronized (this) {
                    active = prevSpec;
                    write(prevJson);
                }
                SkinNative.restore(snapshot);
                Theme.refreshThemeColors(false, true);
            } catch (Throwable undoFailed) {
                FileLog.e(undoFailed);
                synchronized (this) {
                    active = SkinSpec.defaults();
                    write(null);
                }
                notifyChanged();
                throw new IllegalStateException("Couldn't apply the theme and couldn't fully undo it. Tap \"Reset to default\".");
            }
            notifyChanged();
            throw new IllegalStateException("Couldn't apply the theme; your previous look was kept.");
        }
        notifyChanged();
    }

    private void write(String json) {
        SharedPreferences.Editor e = prefs().edit();
        if (json == null) {
            e.remove(KEY_JSON);
        } else {
            e.putString(KEY_JSON, json);
        }
        e.commit();
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

    /** Colour for a Theme key if the active skin overrides it, else 0. */
    public int colorOverride(int themeKey) {
        return spec().keyOverrides().get(themeKey, 0);
    }

    private boolean notifyPending;

    /** One notification + one interface rebuild, however many changes arrive in the same tick. */
    private void notifyChanged() {
        if (notifyPending) {
            return;
        }
        notifyPending = true;
        AndroidUtilities.runOnUIThread(() -> {
            notifyPending = false;
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.kuroSkinChanged);
            // Rebuild every open Telegram screen so the new colours / radii show everywhere now.
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
        });
    }
}
