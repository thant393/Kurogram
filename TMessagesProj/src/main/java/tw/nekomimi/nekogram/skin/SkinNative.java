package tw.nekomimi.nekogram.skin;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

/**
 * Skin values that map onto Telegram's own settings (bubble corners, text size, chat list
 * layout, glass/blur). The user's original value is remembered the first time a skin takes a
 * setting over, and put back when a later skin doesn't set it (or the skin is reset), so
 * "no skin" is always the stock app.
 */
final class SkinNative {

    private static final String PREFS = "kuro_skin_native";

    private SkinNative() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void apply(SkinSpec spec) {
        SharedPreferences p = prefs();
        SharedPreferences.Editor e = p.edit();

        // Bubble corner radius
        if (spec.bubbleRadius != null) {
            remember(p, e, "bubble", SharedConfig.bubbleRadius);
            setBubbleRadius(spec.bubbleRadius);
        } else if (p.contains("bubble")) {
            setBubbleRadius(p.getInt("bubble", 17));
            e.remove("bubble");
        }

        // Message text size
        if (spec.fontSize != null) {
            remember(p, e, "font", SharedConfig.fontSize);
            setFontSize(spec.fontSize);
        } else if (p.contains("font")) {
            setFontSize(p.getInt("font", 16));
            e.remove("font");
        }

        // Chat list: three-line rows
        if (spec.threeLines != null) {
            remember(p, e, "three", SharedConfig.useThreeLinesLayout ? 1 : 0);
            SharedConfig.setUseThreeLinesLayout(spec.threeLines);
        } else if (p.contains("three")) {
            SharedConfig.setUseThreeLinesLayout(p.getInt("three", 0) == 1);
            e.remove("three");
        }

        // Liquid glass and chat blur
        if (spec.liquidGlass != null) {
            remember(p, e, "glass", LiteMode.isEnabledSetting(LiteMode.FLAG_LIQUID_GLASS) ? 1 : 0);
            LiteMode.toggleFlag(LiteMode.FLAG_LIQUID_GLASS, spec.liquidGlass);
        } else if (p.contains("glass")) {
            LiteMode.toggleFlag(LiteMode.FLAG_LIQUID_GLASS, p.getInt("glass", 0) == 1);
            e.remove("glass");
        }
        if (spec.blur != null) {
            remember(p, e, "blur", LiteMode.isEnabledSetting(LiteMode.FLAG_CHAT_BLUR) ? 1 : 0);
            LiteMode.toggleFlag(LiteMode.FLAG_CHAT_BLUR, spec.blur);
        } else if (p.contains("blur")) {
            LiteMode.toggleFlag(LiteMode.FLAG_CHAT_BLUR, p.getInt("blur", 0) == 1);
            e.remove("blur");
        }
        e.apply();
    }

    /** Stores the user's own value only the first time (a second skin must not overwrite it). */
    private static void remember(SharedPreferences p, SharedPreferences.Editor e, String key, int value) {
        if (!p.contains(key)) {
            e.putInt(key, value);
        }
    }

    private static void setBubbleRadius(int v) {
        SharedConfig.bubbleRadius = v;
        MessagesController.getGlobalMainSettings().edit().putInt("bubbleRadius", v).apply();
    }

    private static void setFontSize(int v) {
        SharedConfig.fontSize = v;
        SharedConfig.fontSizeIsDefault = false;
        ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE)
                .edit().putInt("fons_size", v).apply();
        Theme.createCommonMessageResources();
    }
}
