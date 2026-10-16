package tw.nekomimi.nekogram.stickerstore;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * One look for every floating bar in the Kuro screens (the bottom tab bar and the sticker
 * detail action bar): a rounded pill inset from the screen edges whose fill is
 * translucent, so whatever scrolls underneath shows through slightly, with a hairline
 * border instead of a drop shadow (a shadow would show through the translucent fill).
 */
public final class KuroBarStyle {

    public static final int BAR_HEIGHT_DP = 60;
    public static final int SIDE_MARGIN_DP = 16;
    public static final int BOTTOM_MARGIN_DP = 16;
    public static final int CORNER_RADIUS_DP = 26;
    /** How much room scrolling content must leave at its bottom so its last row can be
     *  scrolled fully above the floating bar. */
    public static final int CONTENT_INSET_DP = BAR_HEIGHT_DP + BOTTOM_MARGIN_DP + 12;

    private static final int FILL_ALPHA = 0xD0; // ~82% opaque

    private KuroBarStyle() {
    }

    public static Drawable background() {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(AndroidUtilities.dp(CORNER_RADIUS_DP));
        d.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhite), FILL_ALPHA));
        d.setStroke(Math.max(1, AndroidUtilities.dp(0.5f)), ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_divider), 0x90));
        return d;
    }
}
