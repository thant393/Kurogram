package tw.nekomimi.nekogram.skin;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

import java.util.HashMap;
import java.util.Map;

/**
 * Skin values that map onto Telegram's own settings (bubble corners, text size, chat list
 * layout, glass/blur). Telegram keeps persisting those itself; this class only remembers, per
 * setting, the user's original value ("_orig") and what the skin last put there ("_applied").
 *
 * Restore rule: when a skin stops controlling a setting (or is reset), the original is put back
 * only if the current value is still what the skin set. If the user changed it meanwhile, their
 * value wins and is left alone. A newer skin that sets the setting takes it over again, and the
 * user's own value becomes the new original.
 */
final class SkinNative {

    private static final String PREFS = "kuro_skin_native";
    private static final String[] KEYS = {"bubble", "font", "three", "glass", "blur"};
    private static final String SUFFIX_ORIG = "_orig";
    private static final String SUFFIX_APPLIED = "_applied";
    private static final String FONT_WAS_DEFAULT = "font_wasdefault";

    private SkinNative() {
    }

    /** Everything needed to put the native settings and our bookkeeping back exactly as they were. */
    static final class Snapshot {
        final int[] values = new int[KEYS.length];
        final boolean fontIsDefault;
        final Map<String, Object> prefs = new HashMap<>();

        Snapshot(boolean fontIsDefault) {
            this.fontIsDefault = fontIsDefault;
        }
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static Snapshot snapshot() {
        Snapshot s = new Snapshot(SharedConfig.fontSizeIsDefault);
        for (int i = 0; i < KEYS.length; i++) {
            s.values[i] = current(KEYS[i]);
        }
        Map<String, ?> all = prefs().getAll();
        for (Map.Entry<String, ?> e : all.entrySet()) {
            s.prefs.put(e.getKey(), e.getValue());
        }
        return s;
    }

    /** Puts every native value and our own bookkeeping back. Throws if something can't be restored. */
    static void restore(Snapshot s) {
        for (int i = 0; i < KEYS.length; i++) {
            String key = KEYS[i];
            if (current(key) != s.values[i] || ("font".equals(key) && SharedConfig.fontSizeIsDefault != s.fontIsDefault)) {
                if ("font".equals(key) && s.fontIsDefault) {
                    restoreDefaultFont();
                } else {
                    set(key, s.values[i]);
                }
            }
        }
        SharedPreferences.Editor e = prefs().edit().clear();
        for (Map.Entry<String, Object> en : s.prefs.entrySet()) {
            Object v = en.getValue();
            if (v instanceof Integer) {
                e.putInt(en.getKey(), (Integer) v);
            } else if (v instanceof Boolean) {
                e.putBoolean(en.getKey(), (Boolean) v);
            }
        }
        e.commit();
    }

    static void apply(SkinSpec spec) {
        SharedPreferences p = prefs();
        SharedPreferences.Editor e = p.edit();
        // Older builds stored the original under the bare key and nothing else; we can't tell
        // whether the user changed it since, so leave the current value alone.
        for (String key : KEYS) {
            if (p.contains(key)) {
                e.remove(key);
            }
        }
        e.commit();

        sync("bubble", spec.bubbleRadius, p);
        sync("font", spec.fontSize, p);
        sync("three", spec.threeLines == null ? null : (spec.threeLines ? 1 : 0), p);
        sync("glass", spec.liquidGlass == null ? null : (spec.liquidGlass ? 1 : 0), p);
        sync("blur", spec.blur == null ? null : (spec.blur ? 1 : 0), p);
    }

    private static void sync(String key, Integer desired, SharedPreferences p) {
        int cur = current(key);
        boolean hasApplied = p.contains(key + SUFFIX_APPLIED);
        boolean userChanged = hasApplied && cur != p.getInt(key + SUFFIX_APPLIED, cur);
        SharedPreferences.Editor e = p.edit();

        if (hasApplied && !userChanged) {
            if (desired != null) {
                if (cur != desired) {
                    set(key, desired);
                }
                e.putInt(key + SUFFIX_APPLIED, desired).commit();
            } else {
                if ("font".equals(key) && p.getBoolean(FONT_WAS_DEFAULT, false)) {
                    restoreDefaultFont();
                } else {
                    set(key, p.getInt(key + SUFFIX_ORIG, cur));
                }
                forget(e, key);
            }
            return;
        }
        if (userChanged) {
            // The user's own value becomes the original; the skin no longer owns this setting.
            forget(e, key);
            e.commit();
            e = p.edit();
        }
        if (desired != null) {
            e.putInt(key + SUFFIX_ORIG, cur);
            if ("font".equals(key)) {
                e.putBoolean(FONT_WAS_DEFAULT, SharedConfig.fontSizeIsDefault && !userChanged);
            }
            e.putInt(key + SUFFIX_APPLIED, desired);
            e.commit();
            if (cur != desired) {
                set(key, desired);
            }
        }
    }

    private static void forget(SharedPreferences.Editor e, String key) {
        e.remove(key + SUFFIX_ORIG).remove(key + SUFFIX_APPLIED);
        if ("font".equals(key)) {
            e.remove(FONT_WAS_DEFAULT);
        }
        e.commit();
    }

    private static int current(String key) {
        switch (key) {
            case "bubble":
                return SharedConfig.bubbleRadius;
            case "font":
                return SharedConfig.fontSize;
            case "three":
                return SharedConfig.useThreeLinesLayout ? 1 : 0;
            case "glass":
                return LiteMode.isEnabledSetting(LiteMode.FLAG_LIQUID_GLASS) ? 1 : 0;
            default:
                return LiteMode.isEnabledSetting(LiteMode.FLAG_CHAT_BLUR) ? 1 : 0;
        }
    }

    private static void set(String key, int v) {
        switch (key) {
            case "bubble":
                SharedConfig.bubbleRadius = v;
                MessagesController.getGlobalMainSettings().edit().putInt("bubbleRadius", v).apply();
                break;
            case "font":
                SharedConfig.fontSize = v;
                SharedConfig.fontSizeIsDefault = false;
                mainConfig().edit().putInt("fons_size", v).apply();
                Theme.createCommonMessageResources();
                break;
            case "three":
                SharedConfig.setUseThreeLinesLayout(v == 1);
                break;
            case "glass":
                LiteMode.toggleFlag(LiteMode.FLAG_LIQUID_GLASS, v == 1);
                break;
            default:
                LiteMode.toggleFlag(LiteMode.FLAG_CHAT_BLUR, v == 1);
                break;
        }
    }

    /** The user never chose a size: remove the stored one so Telegram's own default applies again. */
    private static void restoreDefaultFont() {
        mainConfig().edit().remove("fons_size").apply();
        SharedConfig.fontSizeIsDefault = true;
        SharedConfig.updateTabletConfig();
        Theme.createCommonMessageResources();
    }

    private static SharedPreferences mainConfig() {
        return ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
    }
}
