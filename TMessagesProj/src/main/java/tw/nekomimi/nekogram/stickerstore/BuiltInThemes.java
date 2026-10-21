package tw.nekomimi.nekogram.stickerstore;

import java.util.ArrayList;

/** Themes shipped inside the app, so they work offline and without the store. Each is a normal skin. */
public final class BuiltInThemes {

    public static final String ID_PREFIX = "builtin:";

    private BuiltInThemes() {
    }

    public static boolean isBuiltIn(String id) {
        return id != null && id.startsWith(ID_PREFIX);
    }

    public static ArrayList<ThemeInfo> all() {
        ArrayList<ThemeInfo> list = new ArrayList<>();
        // Classic: Telegram's older, squarer look, solid bars, no glass.
        list.add(make("classic", "Classic", "Telegram's older look: solid bars, smaller corners, no glass.", 0xFF2481CC, 0xFFF0F0F0, false,
                "{\"schemaVersion\":1,\"id\":\"classic\",\"name\":\"Classic\","
                        + "\"layout\":{\"cardRadiusDp\":8,\"gridColumns\":2,\"listStyle\":\"flat\"},"
                        + "\"colors\":{\"accent\":\"#2481CC\",\"background\":\"#F0F0F0\",\"surface\":\"#FFFFFF\",\"actionBar\":\"#527DA3\",\"actionBarText\":\"#FFFFFF\",\"bubbleOut\":\"#EFFDDE\",\"bubbleIn\":\"#FFFFFF\"},"
                        + "\"chat\":{\"bubbleRadius\":6},\"list\":{\"threeLines\":false},\"surface\":{\"liquidGlass\":false,\"blur\":false}}"));
        // Liquid Glass: round, translucent, airy.
        list.add(make("liquid-glass", "Liquid Glass", "Large round corners with translucent glass bars and blur.", 0xFF0A84FF, 0xFFF2F2F7, false,
                "{\"schemaVersion\":1,\"id\":\"liquid-glass\",\"name\":\"Liquid Glass\","
                        + "\"layout\":{\"cardRadiusDp\":28,\"gridColumns\":2,\"listStyle\":\"card\"},"
                        + "\"colors\":{\"accent\":\"#0A84FF\",\"background\":\"#F2F2F7\",\"surface\":\"#FFFFFF\"},"
                        + "\"chat\":{\"bubbleRadius\":17},\"list\":{\"threeLines\":false},\"surface\":{\"liquidGlass\":true,\"blur\":true}}"));
        // Minimal: flat, monochrome, compact.
        list.add(make("minimal", "Minimal", "Flat and quiet: thin dividers, tight corners, black accent.", 0xFF111111, 0xFFFFFFFF, false,
                "{\"schemaVersion\":1,\"id\":\"minimal\",\"name\":\"Minimal\","
                        + "\"layout\":{\"cardRadiusDp\":4,\"gridColumns\":3,\"listStyle\":\"flat\"},"
                        + "\"colors\":{\"accent\":\"#111111\",\"background\":\"#FFFFFF\",\"surface\":\"#FFFFFF\",\"divider\":\"#E5E5E5\",\"actionBar\":\"#FFFFFF\",\"actionBarText\":\"#111111\",\"text\":\"#111111\",\"bubbleOut\":\"#EDEDED\",\"bubbleIn\":\"#F7F7F7\",\"bubbleOutText\":\"#111111\",\"bubbleInText\":\"#111111\"},"
                        + "\"chat\":{\"bubbleRadius\":4},\"list\":{\"threeLines\":true},\"surface\":{\"liquidGlass\":false,\"blur\":false}}"));
        return list;
    }

    private static ThemeInfo make(String slug, String title, String description, int accent, int background, boolean dark, String content) {
        ThemeInfo t = new ThemeInfo();
        t.id = ID_PREFIX + slug;
        t.slug = slug;
        t.title = title;
        t.description = description;
        t.category = "Built-in";
        t.type = "skin";
        t.accent = accent;
        t.background = background;
        t.dark = dark;
        t.ownerName = "Kurogram";
        t.builtinContent = content;
        return t;
    }
}
