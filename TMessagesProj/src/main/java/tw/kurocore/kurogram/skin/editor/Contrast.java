package tw.kurocore.kurogram.skin.editor;

/** WCAG 2.x contrast ratio between two 0xAARRGGBB colours (the foreground is blended over the background first). */
public final class Contrast {
    private Contrast() {
    }

    private static double channel(int v) {
        double c = v / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double luminance(int rgb) {
        return 0.2126 * channel((rgb >> 16) & 0xFF) + 0.7152 * channel((rgb >> 8) & 0xFF) + 0.0722 * channel(rgb & 0xFF);
    }

    /** Foreground over background: translucent text is mixed with what is behind it, as it would be on screen. */
    public static int blend(int fg, int bg) {
        double a = (fg >>> 24) / 255.0;
        int r = (int) Math.round(((fg >> 16) & 0xFF) * a + ((bg >> 16) & 0xFF) * (1 - a));
        int g = (int) Math.round(((fg >> 8) & 0xFF) * a + ((bg >> 8) & 0xFF) * (1 - a));
        int b = (int) Math.round((fg & 0xFF) * a + (bg & 0xFF) * (1 - a));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static double ratio(int fg, int bg) {
        double l1 = luminance(blend(fg, bg)), l2 = luminance(bg & 0xFFFFFF | 0xFF000000);
        double hi = Math.max(l1, l2), lo = Math.min(l1, l2);
        return (hi + 0.05) / (lo + 0.05);
    }
}
