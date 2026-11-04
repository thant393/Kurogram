package tw.kurocore.kurogram.skin;

import java.util.List;

import tw.kurocore.kurogram.layout.LayoutManager;

/**
 * Per-screen row visibility from the active layout ("layout.<screen>.hiddenRows"). A screen asks
 * {@link #hidden} for each row it may drop while it builds its row ints; absent list = every row stays.
 * Rows a screen marks as locked (ScreenCapabilities) are never hidden, whatever the document says, and the
 * rule lists only allow ids that are safe to drop (no security rows).
 */
public final class RowMap {

    private RowMap() {
    }

    /** True when the active layout hides at least one row of this screen. */
    public static boolean anyHidden(String screenId) {
        try {
            List<String> hidden = LayoutManager.get().spec().listValue(screenId + ".hiddenRows", null);
            return hidden != null && !hidden.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hidden(String screenId, String rowId) {
        try {
            if (ScreenCapabilities.isLockedRow(screenId, rowId)) {
                return false;
            }
            List<String> hidden = LayoutManager.get().spec().listValue(screenId + ".hiddenRows", null);
            return hidden != null && hidden.contains(rowId);
        } catch (Exception e) {
            return false;
        }
    }
}
