package tw.nekomimi.nekogram.stickerstore;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * One look for every floating bar in the Kuro screens (the bottom tab bar and the sticker
 * detail action bar), in two layers like Telegram's own bottom bar:
 *
 *  - the PILL (the rounded button container) is fully solid, so nothing scrolling beneath
 *    it ever shows through the buttons;
 *  - the SCRIM is the full-width layer behind the pill: translucent, fading out upward so
 *    it has no visible top edge, letting content ghost through around the pill.
 *
 * The header behind the Popular/Newest chips uses the same fade, downward.
 */
public final class KuroBarStyle {

    public static final int BAR_HEIGHT_DP = 60;
    public static final int SIDE_MARGIN_DP = 16;
    public static final int BOTTOM_MARGIN_DP = 16;
    public static final int CORNER_RADIUS_DP = 26;
    /** Height of the full-width scrim behind the bottom pill (pill + margin + fade-out room). */
    public static final int SCRIM_HEIGHT_DP = BAR_HEIGHT_DP + BOTTOM_MARGIN_DP + 24;
    /** How much room scrolling content must leave at its bottom so its last row can be
     *  scrolled fully above the floating bar. */
    public static final int CONTENT_INSET_DP = BAR_HEIGHT_DP + BOTTOM_MARGIN_DP + 12;

    private KuroBarStyle() {
    }

    /** The pill: 100% solid fill (no alpha), hairline border. */
    public static Drawable background() {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(AndroidUtilities.dp(CORNER_RADIUS_DP));
        d.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        d.setStroke(Math.max(1, AndroidUtilities.dp(0.5f)), ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_divider), 0x90));
        return d;
    }

    /** Full-width translucent layer for behind the bottom pill; fades to nothing at its top
     *  edge so no rectangular strip is visible. Add it BEFORE the pill so the pill is above. */
    public static View createBottomScrim(Context context) {
        View scrim = new View(context);
        int base = Theme.getColor(Theme.key_windowBackgroundGray);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{
                ColorUtils.setAlphaComponent(base, 0xE6),
                ColorUtils.setAlphaComponent(base, 0x99),
                ColorUtils.setAlphaComponent(base, 0x00)}));
        return scrim;
    }

    /** Background for the header behind the Popular/Newest chips: translucent at the top,
     *  fading out downward, so it blends into the page instead of ending in a hard strip. */
    public static Drawable headerFade() {
        int base = Theme.getColor(Theme.key_windowBackgroundGray);
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{
                ColorUtils.setAlphaComponent(base, 0xE6),
                ColorUtils.setAlphaComponent(base, 0x99),
                ColorUtils.setAlphaComponent(base, 0x00)});
    }
}
