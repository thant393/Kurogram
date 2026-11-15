package tw.kurocore.kurogram.stickerstore;

import tw.kurocore.kurogram.layout.LayoutManager;
import tw.kurocore.kurogram.layout.LayoutSpec;
import tw.kurocore.kurogram.skin.KuroUi;
import tw.kurocore.kurogram.skin.SkinTokens;

/**
 * What the active skin/layout says about the Theme Store (screens.themeStore.* and layout themeStore.*).
 * Every method returns the value the store used before these keys existed when the key is absent, so a theme
 * without them renders exactly as it always did. Shared by the store grid and a creator's page (same views).
 */
public final class StoreLook {

    /** Height of a preview card as a multiple of its width; the original phone-shaped preview is 1.9. */
    static final float ORIGINAL_ASPECT = 1.9f;

    private StoreLook() {
    }

    /** Columns that fit {@code widthPx}: never fewer than {@code base} (the phone layout), more when each column can stay at least {@code minCellDp} wide. */
    public static int fitColumns(int widthPx, int base, int minCellDp) {
        int fit = widthPx / Math.max(1, org.telegram.messenger.AndroidUtilities.dp(minCellDp));
        return Math.max(base, Math.min(fit, base * 3));
    }

    /** Re-evaluates the span count whenever the list's width changes (rotation, split screen, foldables). */
    public static void autoSpan(androidx.recyclerview.widget.RecyclerView list,
                                androidx.recyclerview.widget.GridLayoutManager lm,
                                java.util.function.IntSupplier base, int minCellDp, Runnable after) {
        list.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int w = r - l - v.getPaddingLeft() - v.getPaddingRight();
            if (w <= 0) {
                return;
            }
            int cols = fitColumns(w, base.getAsInt(), minCellDp);
            if (lm.getSpanCount() != cols) {
                lm.setSpanCount(cols);
                if (after != null) {
                    after.run();
                }
            }
        });
    }

    private static LayoutSpec layout() {
        return LayoutManager.get().spec();
    }

    /** layout.themeStore.mode = "list" -> 1; "grid" -> screens.themeStore.gridColumns (2-3); absent -> 2 as before. */
    public static int columns() {
        String mode = layout().stringValue("themeStore.mode", null);
        if ("list".equals(mode)) {
            return 1;
        }
        if ("grid".equals(mode)) {
            return Math.max(2, Math.min(3, KuroUi.t().gridColumns));
        }
        return 2;
    }

    static float previewAspect() {
        switch (KuroUi.t().storePreviewAspect) {
            case 0:
                return 1f;
            case 1:
                return 1.4f;
            default:
                return ORIGINAL_ASPECT;
        }
    }

    /** Corner radius of a card; the screen's own value (card radius - 2) when the key is absent. */
    static int cardRadiusDp() {
        int r = KuroUi.t().storeCardRadiusDp;
        return r >= 0 ? r : StickerPackGridCell.cellRadiusDp();
    }

    /** Space between two cards (each card keeps half of it as margin); 12 as before. */
    static int cardGapDp() {
        int g = KuroUi.t().storeCardGapDp;
        return g >= 0 ? g : 12;
    }

    static float titleSizeSp(float def) {
        int s = KuroUi.t().storeTitleSizeSp;
        return s >= 0 ? KuroUi.title(s) : KuroUi.title(def);
    }

    /** false only when the layout explicitly says false. */
    static boolean showCreator() {
        return layout().boolValue("themeStore.meta.creator", true);
    }

    static boolean showDownloads() {
        return layout().boolValue("themeStore.meta.downloads", true);
    }

    static boolean overlayMeta() {
        return "overlay".equals(layout().stringValue("themeStore.meta.position", "below"));
    }

    /** The sort + category chip row of the store page (not shown on a creator's page either way). */
    static boolean chipsOn() {
        return !"off".equals(layout().stringValue("themeStore.chips", "on"));
    }

    /** The search action of the store's Themes tab. */
    public static boolean searchOn() {
        return !"off".equals(layout().stringValue("themeStore.search", "on"));
    }

    /** components.button.style for the store's buttons: -1 (not set) keeps today's filled button. */
    static int buttonStyle() {
        SkinTokens t = KuroUi.t();
        return t.buttonStyle;
    }
}
