package tw.kurocore.kurogram.skin.editor;

/**
 * Conversion between the skin's colour text and Android-style 0xAARRGGBB ints, kept free of Android classes so it is
 * unit-tested on a plain JVM. "col" rules are CSS-style #RRGGBB or #RRGGBBAA; "argb" rules (overlay, scrim) are #RRGGBB or #AARRGGBB.
 */
public final class ColorCodec {
    private ColorCodec() {
    }

    /** @return 0xAARRGGBB, or 0 when the text is not a valid colour for this rule kind. */
    public static int parse(String text, Rule.Kind kind) {
        if (text == null || !text.matches("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?")) {
            return 0;
        }
        long v = Long.parseLong(text.substring(1), 16);
        if (text.length() == 7) {
            return (int) (0xFF000000L | v);
        }
        if (kind == Rule.Kind.ARGB) {
            return (int) v;
        }
        long rgb = v >> 8, a = v & 0xFF; // #RRGGBBAA
        return (int) ((a << 24) | rgb);
    }

    /** Opaque colours are written as #RRGGBB; translucent ones in the form the rule kind expects. */
    public static String format(int argb, Rule.Kind kind) {
        int a = argb >>> 24, rgb = argb & 0xFFFFFF;
        if (a == 0xFF) {
            return String.format("#%06X", rgb);
        }
        return kind == Rule.Kind.ARGB ? String.format("#%02X%06X", a, rgb) : String.format("#%06X%02X", rgb, a);
    }
}
