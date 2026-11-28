package tw.kurocore.kurogram.skin.editor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fills the standard values a draft leaves unset, so what is published is one complete document (the store requires
 * that). Only missing values are filled, and they follow the draft's own colours, so a theme keeps its look.
 */
public final class SkinCompleter {

    private SkinCompleter() {
    }

    /** The document with every missing standard value and the layout filled in; values already set are never touched. */
    public static Map<String, Object> complete(Map<String, Object> doc) {
        Map<String, Object> root = doc == null ? new LinkedHashMap<String, Object>() : doc;
        String bg = color(root, "tokens.colors.background", "#FFFFFF");
        boolean dark = luminance(bg) < 0.4;
        String text = dark ? "#FFFFFF" : "#000000";
        String text2 = dark ? "#A8A8AD" : "#707579";
        String surface = color(root, "tokens.colors.surface", bg);
        String accent = color(root, "tokens.colors.accent", "#3390EC");
        String elevated = color(root, "tokens.colors.surfaceElevated", surface);
        String onAccent = color(root, "tokens.colors.onAccent", "#FFFFFF");
        String divider = color(root, "tokens.colors.divider", dark ? "#2C2C2E" : "#E6E6E6");
        String icon = color(root, "tokens.colors.icon", text2);
        String textPrimary = color(root, "tokens.colors.textPrimary", text);

        fill(root, "tokens.colors.accent", accent);
        fill(root, "tokens.colors.onAccent", onAccent);
        fill(root, "tokens.colors.background", bg);
        fill(root, "tokens.colors.surface", surface);
        fill(root, "tokens.colors.surfaceElevated", elevated);
        fill(root, "tokens.colors.textPrimary", textPrimary);
        fill(root, "tokens.colors.textSecondary", text2);
        fill(root, "tokens.colors.divider", divider);
        fill(root, "tokens.colors.icon", icon);
        fill(root, "tokens.colors.error", "#E53935");
        fill(root, "tokens.colors.success", "#43A047");
        fill(root, "tokens.colors.overlay", "#66000000");
        fill(root, "tokens.gradient.top", bg);
        fill(root, "tokens.gradient.middle", bg);
        fill(root, "tokens.gradient.bottom", bg);
        fill(root, "tokens.gradient.direction", "vertical");
        fill(root, "tokens.typography.fontFamily", "default");
        fill(root, "tokens.typography.bodySizeSp", 16L);
        fill(root, "tokens.typography.titleSizeSp", 20L);
        fill(root, "tokens.typography.captionSizeSp", 12L);
        fill(root, "tokens.typography.fontWeight", "normal");
        fill(root, "tokens.typography.lineHeightMultiplier", 1.2);
        fill(root, "tokens.spacing.xs", 4L);
        fill(root, "tokens.spacing.sm", 8L);
        fill(root, "tokens.spacing.md", 12L);
        fill(root, "tokens.spacing.lg", 16L);
        fill(root, "tokens.spacing.xl", 24L);
        fill(root, "tokens.shapes.cardRadiusDp", 12L);
        fill(root, "tokens.shapes.buttonRadiusDp", 12L);
        fill(root, "tokens.shapes.inputRadiusDp", 10L);
        fill(root, "tokens.shapes.dialogRadiusDp", 16L);
        fill(root, "tokens.shapes.bubbleRadiusDp", 12L);
        fill(root, "tokens.elevation.cardDp", 0L);
        fill(root, "tokens.elevation.dialogDp", 8L);
        fill(root, "components.appBar.background", surface);
        fill(root, "components.appBar.foreground", textPrimary);
        fill(root, "components.appBar.heightDp", 56L);
        fill(root, "components.navigationBar.background", surface);
        fill(root, "components.navigationBar.selected", accent);
        fill(root, "components.navigationBar.unselected", icon);
        fill(root, "components.bottomNavigation.background", surface);
        fill(root, "components.bottomNavigation.selectedBackground", accent);
        fill(root, "components.bottomNavigation.iconSizeDp", 24L);
        fill(root, "components.card.background", surface);
        fill(root, "components.card.borderColor", divider);
        fill(root, "components.card.borderWidthDp", 0L);
        fill(root, "components.button.background", accent);
        fill(root, "components.button.foreground", onAccent);
        fill(root, "components.button.heightDp", 48L);
        fill(root, "components.input.background", elevated);
        fill(root, "components.input.foreground", textPrimary);
        fill(root, "components.input.hint", text2);
        fill(root, "components.dialog.background", elevated);
        fill(root, "components.dialog.scrim", "#80000000");
        fill(root, "screens.chats.layout", "classic");
        fill(root, "screens.chats.background", bg);
        fill(root, "screens.chats.listStyle", "flat");
        fill(root, "screens.chats.avatarSizeDp", 56L);
        fill(root, "screens.chats.showDividers", Boolean.TRUE);
        fill(root, "screens.chats.threeLines", Boolean.FALSE);
        fill(root, "screens.contacts.background", bg);
        fill(root, "screens.contacts.sectionStyle", "flat");
        fill(root, "screens.contacts.avatarSizeDp", 46L);
        fill(root, "screens.profile.background", bg);
        fill(root, "screens.profile.headerStyle", "centered");
        fill(root, "screens.profile.avatarSizeDp", 96L);
        fill(root, "screens.profile.cardRadiusDp", 12L);
        fill(root, "screens.settings.background", bg);
        fill(root, "screens.settings.groupStyle", "flat");
        fill(root, "screens.settings.iconStyle", "rounded");
        fill(root, "screens.settings.showDescriptions", Boolean.TRUE);
        fill(root, "screens.themeStore.background", bg);
        fill(root, "screens.themeStore.gridColumns", 2L);
        fill(root, "screens.themeStore.previewRadiusDp", 12L);
        fill(root, "chat.bubbleIn", surface);
        fill(root, "chat.bubbleOut", accent);
        fill(root, "chat.bubbleInText", textPrimary);
        fill(root, "chat.bubbleOutText", onAccent);
        fill(root, "chat.bubbleRadiusDp", 12L);
        fill(root, "chat.fontSizeSp", 16L);
        fill(root, "effects.blur", Boolean.FALSE);
        fill(root, "effects.glass", Boolean.FALSE);
        fill(root, "effects.animations", Boolean.TRUE);
        fill(root, "effects.reducedMotion", Boolean.FALSE);
        if (root.get("layout") == null) {
            root.put("layout", "classic");
        }
        return root;
    }

    @SuppressWarnings("unchecked")
    private static Object get(Map<String, Object> root, String path) {
        Object cur = root;
        for (String key : path.split("\\.")) {
            if (!(cur instanceof Map) || !((Map<String, Object>) cur).containsKey(key)) {
                return null;
            }
            cur = ((Map<String, Object>) cur).get(key);
        }
        return cur == Json.NULL ? null : cur;
    }

    @SuppressWarnings("unchecked")
    private static void fill(Map<String, Object> root, String path, Object value) {
        if (get(root, path) != null) {
            return;
        }
        String[] keys = path.split("\\.");
        Map<String, Object> cur = root;
        for (int i = 0; i < keys.length - 1; i++) {
            Object next = cur.get(keys[i]);
            if (!(next instanceof Map)) {
                next = new LinkedHashMap<String, Object>();
                cur.put(keys[i], next);
            }
            cur = (Map<String, Object>) next;
        }
        cur.put(keys[keys.length - 1], value);
    }

    private static String color(Map<String, Object> root, String path, String fallback) {
        Object v = get(root, path);
        return v instanceof String && ((String) v).matches("#[0-9A-Fa-f]{6}") ? (String) v : fallback;
    }

    private static double luminance(String hex) {
        int c = Integer.parseInt(hex.substring(1, 7), 16);
        return (0.299 * ((c >> 16) & 255) + 0.587 * ((c >> 8) & 255) + 0.114 * (c & 255)) / 255.0;
    }
}
