package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * Helper for rendering rows inside iOS / Telegram-style "grouped card" sections:
 * a light grey page background with white rounded-corner cards per section,
 * subtle dividers between rows inside a card, and spacing between cards.
 */
public class GroupCardHelper {

    public static final int POS_SINGLE = 0;
    public static final int POS_TOP = 1;
    public static final int POS_MIDDLE = 2;
    public static final int POS_BOTTOM = 3;

    private static final float RADIUS_DP = 16f;
    private static final int SIDE_MARGIN_DP = 16;
    private static final int SECTION_SPACING_DP = 16;

    public static int positionInGroup(int indexInGroup, int groupSize) {
        if (groupSize <= 1) {
            return POS_SINGLE;
        }
        if (indexInGroup == 0) {
            return POS_TOP;
        }
        if (indexInGroup == groupSize - 1) {
            return POS_BOTTOM;
        }
        return POS_MIDDLE;
    }

    public static boolean needsDivider(int pos) {
        return pos == POS_TOP || pos == POS_MIDDLE;
    }

    public static Drawable cardBackground(Context context, int pos) {
        float r = AndroidUtilities.dp(RADIUS_DP);
        float[] radii;
        switch (pos) {
            case POS_TOP:
                radii = new float[]{r, r, r, r, 0, 0, 0, 0};
                break;
            case POS_BOTTOM:
                radii = new float[]{0, 0, 0, 0, r, r, r, r};
                break;
            case POS_MIDDLE:
                radii = new float[]{0, 0, 0, 0, 0, 0, 0, 0};
                break;
            default:
                radii = new float[]{r, r, r, r, r, r, r, r};
                break;
        }
        // No stroke/border here: each row in a multi-row card is a separate drawable, and a
        // border on every row would draw a visible line along edges shared with the next row
        // (on top of the row's own divider), breaking the "one seamless card" look. Separation
        // from the page comes from the white-on-grey contrast alone, like iOS grouped tables.
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadii(radii);
        drawable.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        return drawable;
    }

    /**
     * Applies the rounded card background and left/right/top margins for a row at
     * the given position within its card group. Call from onBindViewHolder, after
     * the view already has a RecyclerView.LayoutParams (set in onCreateViewHolder).
     */
    public static void applyCard(View view, int pos) {
        view.setBackground(cardBackground(view.getContext(), pos));
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) lp;
            int side = AndroidUtilities.dp(SIDE_MARGIN_DP);
            params.leftMargin = side;
            params.rightMargin = side;
            params.topMargin = (pos == POS_TOP || pos == POS_SINGLE) ? AndroidUtilities.dp(SECTION_SPACING_DP) : 0;
            view.setLayoutParams(params);
        }
    }

    /** Resets margins to flat/full-bleed for rows that are not part of a card (headers, footers). */
    public static void clearCard(View view) {
        view.setBackground(null);
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) lp;
            params.leftMargin = 0;
            params.rightMargin = 0;
            params.topMargin = 0;
            view.setLayoutParams(params);
        }
    }

    public static RecyclerView.LayoutParams freshLayoutParams() {
        return new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
    }
}
