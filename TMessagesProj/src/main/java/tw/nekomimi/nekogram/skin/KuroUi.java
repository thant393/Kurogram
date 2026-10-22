package tw.nekomimi.nekogram.skin;

import android.graphics.drawable.GradientDrawable;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * The one place Kuro screens get skin-aware colours and shapes from. A screen asks for a ROLE
 * ("card", "inset card", "primary button") and gets the skin's value, or Telegram's own colour
 * when the skin sets none, so a partial skin never breaks anything.
 *
 * Rule for Kuro screens: no Theme.getColor / GradientDrawable styling directly; use this.
 * Not thread-safe by design: call on the UI thread, like every view operation.
 */
public final class KuroUi {

    private KuroUi() {
    }

    public static SkinTokens t() {
        return KuroSkinManager.get().tokens();
    }

    // ---------- colours ----------

    /** The skin's accent, else Telegram's own accent colour. */
    public static int accent() {
        int c = t().accent;
        return c != 0 ? c : Theme.getColor(Theme.key_featuredStickers_addButton);
    }

    /** Text/icon colour on top of the accent (button text, check marks). */
    public static int onAccent() {
        int c = t().onAccent;
        return c != 0 ? c : 0xFFFFFFFF;
    }

    public static int textPrimary() {
        int c = t().textPrimary;
        return c != 0 ? c : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
    }

    public static int textSecondary() {
        int c = t().textSecondary;
        return c != 0 ? c : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
    }

    public static int error() {
        int c = t().error;
        return c != 0 ? c : Theme.getColor(Theme.key_text_RedBold);
    }

    /** Surface colour (what a card sits on): the skin's surface, else Telegram's white. */
    public static int surface() {
        int c = t().surface;
        return c != 0 ? c : Theme.getColor(Theme.key_windowBackgroundWhite);
    }

    /** Page background for a Kuro screen; pass the skin's per-screen colour. */
    public static int screenBackground(int skinColor) {
        if (skinColor != 0) {
            return skinColor;
        }
        int c = t().background;
        return c != 0 ? c : Theme.getColor(Theme.key_windowBackgroundGray);
    }

    /** A hairline that follows the text colour, so it shows on light and dark alike. */
    public static int hairline() {
        return ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), 0x26);
    }

    /** A shade of the surface that is visibly different from it (for cells inside a card). */
    public static int insetFill() {
        // Theme's white already carries the skin's surface/card override, so this matches what a card shows.
        int surface = Theme.getColor(Theme.key_windowBackgroundWhite) | 0xFF000000;
        int gray = Theme.getColor(Theme.key_windowBackgroundGray) | 0xFF000000;
        if (ColorUtils.calculateContrast(gray, surface) >= 1.1) {
            return gray;
        }
        return ColorUtils.blendARGB(surface, textPrimary() | 0xFF000000, 0.08f);
    }

    /** Colour of a Kuro page: the skin's themeStore background, else the skin background, else Telegram's grey. */
    public static int pageBackground() {
        return screenBackground(t().themeStoreBackground);
    }

    // ---------- type, motion, size ----------

    /** Stock sizes the skin's typography values are compared against. */
    private static final int STOCK_TITLE_SP = 20, STOCK_BODY_SP = 16, STOCK_CAPTION_SP = 12;

    /** A heading designed as {@code sp} for the stock skin, scaled by the skin's titleSizeSp. */
    public static float title(float sp) {
        int v = t().titleSizeSp;
        return v > 0 ? sp * v / STOCK_TITLE_SP : sp;
    }

    public static float body(float sp) {
        int v = t().bodySizeSp;
        return v > 0 ? sp * v / STOCK_BODY_SP : sp;
    }

    public static float caption(float sp) {
        int v = t().captionSizeSp;
        return v > 0 ? sp * v / STOCK_CAPTION_SP : sp;
    }

    /** An animation length in ms; 0 (instant) when the skin turns animations off or asks for reduced motion. */
    public static int anim(int ms) {
        SkinTokens t = t();
        return (!t.animations || t.reducedMotion) ? 0 : ms;
    }

    /** Primary button height in dp: the skin's, kept within what the floating bar can hold. */
    public static int buttonHeightDp(int def) {
        int h = t().buttonHeightDp;
        return h > 0 ? Math.min(h, 56) : def;
    }

    // ---------- shapes ----------

    /** Fill + corner radius + border for a card. {@code inset} = a cell sitting inside another card. */
    public static GradientDrawable card(boolean inset, int radiusDp) {
        SkinTokens t = t();
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(AndroidUtilities.dp(radiusDp));
        // Without a card colour of its own the cell uses Theme's white (which already carries the skin's override).
        d.setColor(inset ? insetFill() : Theme.getColor(Theme.key_windowBackgroundWhite));
        if (t.cardBorderWidthDp > 0) {
            d.setStroke(AndroidUtilities.dp(t.cardBorderWidthDp), t.cardBorderColor != 0 ? t.cardBorderColor : hairline());
        } else if (t.cardBorderWidthDp < 0 && (!t.flatLists || inset)) {
            d.setStroke(Math.max(1, AndroidUtilities.dp(0.5f)), hairline());
        }
        return d;
    }

    /** The primary (accent) button background; {@code disabled} greys it out. */
    public static GradientDrawable primaryButton(boolean disabled) {
        SkinTokens t = t();
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(AndroidUtilities.dp(t.buttonRadiusDp));
        d.setColor(disabled ? 0xFF8E8E93 : (t.buttonBackground != 0 ? t.buttonBackground : accent()));
        return d;
    }

    /** Text colour for {@link #primaryButton}. */
    public static int primaryButtonText(boolean disabled) {
        SkinTokens t = t();
        return disabled ? 0xFFFFFFFF : (t.buttonForeground != 0 ? t.buttonForeground : onAccent());
    }

    /** Fill of a floating-bar piece (round buttons next to the main pill). */
    public static int barFill() {
        int c = t().bottomBarBackground;
        return c != 0 ? c : Theme.getColor(Theme.key_windowBackgroundWhite);
    }

    /** A fully round or pill-shaped solid fill with a hairline border (floating bar pieces). */
    public static GradientDrawable pill(int radiusDp) {
        SkinTokens t = t();
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(AndroidUtilities.dp(radiusDp));
        // floating bars follow the skin's bottom-bar colour, else its surface
        d.setColor(t.bottomBarBackground != 0 ? t.bottomBarBackground : Theme.getColor(Theme.key_windowBackgroundWhite));
        d.setStroke(Math.max(1, AndroidUtilities.dp(0.5f)), hairline());
        return d;
    }
}
