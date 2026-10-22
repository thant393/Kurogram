package tw.nekomimi.nekogram.skin;

import android.util.SparseIntArray;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.ui.ActionBar.Theme;

import java.util.HashMap;
import java.util.Map;

/**
 * A skin: nothing but validated values that pick between looks the app already contains. A skin
 * file never carries code or layout XML. Anything a skin leaves out keeps the original UI, so an
 * empty skin is exactly the stock app.
 *
 * schemaVersion 2 (current), five layers:
 *   1 tokens      colors, typography, spacing, shapes, elevation   (app-wide values)
 *   2 components  appBar, navigationBar, bottomNavigation, card, button, input, dialog
 *   3 screens     chats, contacts, profile, settings, themeStore   (+ the chat screen: "chat")
 *   4 assets & effects   assets (wallpaper/font/iconPack, reserved: must be null), effects
 *   5 meta        id, name, author, license, tags (store/runtime bookkeeping)
 * schemaVersion 1 (flat: layout/colors/chat/list/surface) is still read and converted.
 *
 * Every known field is validated. Fields the app can validate but has no UI path for yet are
 * accepted and ignored (see SUPPORT in docs / reply); a JSON field is never proof of a feature.
 */
public final class SkinSpec {

    public static final int SCHEMA_VERSION = 2;
    /** What this build of the app understands; a theme whose min_app_version is higher is refused. */
    public static final int ENGINE_LEVEL = 1;
    public static final int MAX_JSON_BYTES = 64 * 1024;

    public static final String LIST_CARD = "card";
    public static final String LIST_FLAT = "flat";

    public final String id;
    public final String name;
    public final int schemaVersion;
    public final int cardRadiusDp;
    public final int dialogRadiusDp;
    public final int gridColumns;
    public final String listStyle;
    /** Accent override, 0xAARRGGBB, or 0 to keep the theme's own accent. */
    public final int accentColor;
    /** semantic colour name -> 0xFFRRGGBB */
    public final Map<String, Integer> colors;
    /** null = leave the app's own setting alone. */
    public final Integer bubbleRadius;
    public final Integer fontSize;
    public final Boolean threeLines;
    public final Boolean liquidGlass;
    public final Boolean blur;
    /** Every validated v2 field by its dotted path (Integer, Double, Boolean or String). */
    private final Map<String, Object> values;

    private SparseIntArray keyOverrides;

    private SkinSpec(String id, String name, int schemaVersion, int cardRadiusDp, int dialogRadiusDp, int gridColumns, String listStyle,
                     Map<String, Integer> colors, Integer bubbleRadius, Integer fontSize, Boolean threeLines, Boolean liquidGlass, Boolean blur, Map<String, Object> values) {
        this.id = id;
        this.name = name;
        this.schemaVersion = schemaVersion;
        this.cardRadiusDp = cardRadiusDp;
        this.dialogRadiusDp = dialogRadiusDp;
        this.gridColumns = gridColumns;
        this.listStyle = listStyle;
        this.colors = colors;
        Integer accent = colors.get("accent");
        this.accentColor = accent != null ? accent : 0;
        this.bubbleRadius = bubbleRadius;
        this.fontSize = fontSize;
        this.threeLines = threeLines;
        this.liquidGlass = liquidGlass;
        this.blur = blur;
        this.values = values;
    }

    /** Integer field by path (e.g. "components.button.heightDp"), or the default when the skin leaves it out. */
    public int intValue(String path, int def) {
        Object v = values.get(path);
        return v instanceof Integer ? (Integer) v : def;
    }

    public boolean boolValue(String path, boolean def) {
        Object v = values.get(path);
        return v instanceof Boolean ? (Boolean) v : def;
    }

    public String stringValue(String path, String def) {
        Object v = values.get(path);
        return v instanceof String ? (String) v : def;
    }

    /** Colour field as 0xAARRGGBB (#RRGGBB becomes opaque), or 0 when the skin leaves it out. */
    public int colorValue(String path) {
        Object v = values.get(path);
        if (!(v instanceof String)) {
            return 0;
        }
        String hex = ((String) v).substring(1);
        long c = Long.parseLong(hex, 16);
        return hex.length() == 6 ? (int) (0xFF000000L | c) : (int) c;
    }

    public boolean has(String path) {
        return values.containsKey(path);
    }


    public static SkinSpec defaults() {
        return new SkinSpec("default", "Default", SCHEMA_VERSION, 22, 22, 2, LIST_CARD, new HashMap<>(), null, null, null, null, null, new HashMap<>());
    }

    // ---- semantic colour -> Telegram colour keys (later entries win over earlier ones) ----

    /** Theme colour key -> colour, for every key this skin overrides (empty if none). Built once. */
    public synchronized SparseIntArray keyOverrides() {
        if (keyOverrides == null) {
            SparseIntArray o = new SparseIntArray();
            // tokens first, then components, so a component can refine a token.
            put(o, "accent", Theme.key_windowBackgroundWhiteBlueText, Theme.key_windowBackgroundWhiteBlueText2,
                    Theme.key_windowBackgroundWhiteBlueText3, Theme.key_windowBackgroundWhiteBlueText4,
                    Theme.key_windowBackgroundWhiteBlueText5, Theme.key_windowBackgroundWhiteBlueText6,
                    Theme.key_windowBackgroundWhiteBlueText7, Theme.key_windowBackgroundWhiteBlueIcon,
                    Theme.key_windowBackgroundWhiteBlueHeader, Theme.key_windowBackgroundWhiteLinkText,
                    Theme.key_dialogTextBlue, Theme.key_dialogTextBlue2, Theme.key_dialogButton,
                    Theme.key_switchTrackChecked, Theme.key_radioBackgroundChecked, Theme.key_checkbox,
                    Theme.key_chats_unreadCounter, Theme.key_chats_actionBackground, Theme.key_featuredStickers_addButton);
            put(o, "onAccent", Theme.key_featuredStickers_buttonText, Theme.key_chats_unreadCounterText, Theme.key_chats_actionIcon);
            put(o, "background", Theme.key_windowBackgroundGray, Theme.key_dialogBackgroundGray);
            put(o, "surface", Theme.key_windowBackgroundWhite);
            put(o, "surfaceElevated", Theme.key_dialogBackground, Theme.key_actionBarDefaultSubmenuBackground);
            put(o, "textPrimary", Theme.key_windowBackgroundWhiteBlackText, Theme.key_dialogTextBlack, Theme.key_chats_name,
                    Theme.key_actionBarDefaultSubmenuItem, Theme.key_chat_messagePanelText);
            put(o, "textSecondary", Theme.key_windowBackgroundWhiteGrayText, Theme.key_windowBackgroundWhiteGrayText2,
                    Theme.key_windowBackgroundWhiteGrayText3, Theme.key_windowBackgroundWhiteGrayText4,
                    Theme.key_windowBackgroundWhiteGrayText5, Theme.key_windowBackgroundWhiteGrayText6,
                    Theme.key_windowBackgroundWhiteGrayText7, Theme.key_windowBackgroundWhiteGrayText8,
                    Theme.key_chats_message, Theme.key_dialogTextGray);
            put(o, "divider", Theme.key_divider, Theme.key_dialogGrayLine);
            put(o, "icon", Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_chat_messagePanelIcons);
            put(o, "error", Theme.key_text_RedBold, Theme.key_text_RedRegular);
            put(o, "success", Theme.key_windowBackgroundWhiteGreenText, Theme.key_color_green);
            // components
            put(o, "card", Theme.key_windowBackgroundWhite);
            put(o, "appBar", Theme.key_actionBarDefault);
            put(o, "appBarText", Theme.key_actionBarDefaultTitle, Theme.key_actionBarDefaultIcon, Theme.key_actionBarDefaultSubtitle);
            put(o, "button", Theme.key_featuredStickers_addButton);
            put(o, "buttonText", Theme.key_featuredStickers_buttonText);
            put(o, "input", Theme.key_windowBackgroundWhiteInputField);
            put(o, "inputHint", Theme.key_windowBackgroundWhiteHintText);
            put(o, "dialog", Theme.key_dialogBackground);
            // chat screen
            put(o, "bubbleIn", Theme.key_chat_inBubble);
            put(o, "bubbleOut", Theme.key_chat_outBubble);
            put(o, "bubbleInText", Theme.key_chat_messageTextIn);
            put(o, "bubbleOutText", Theme.key_chat_messageTextOut);
            keyOverrides = o;
        }
        return keyOverrides;
    }

    private void put(SparseIntArray target, String name, int... keys) {
        Integer c = colors.get(name);
        if (c != null) {
            for (int k : keys) {
                target.put(k, c);
            }
        }
    }

    // ---- validation rules (the backend's _validate_skin mirrors this table exactly) ----

    private static final String C6 = "c6";          // #RRGGBB
    private static final String C68 = "c68";        // #RRGGBB or #AARRGGBB
    private static final String NULL = "null";      // reserved: must be absent or null
    private static final String BOOL = "bool";

    /** path | kind. kind is c6, c68, bool, null, int:lo:hi, float:lo:hi or enum:a,b,c */
    private static final String[] RULES_V2 = {
            "tokens.colors.accent|c6", "tokens.colors.onAccent|c6", "tokens.colors.background|c6", "tokens.colors.surface|c6",
            "tokens.colors.surfaceElevated|c6", "tokens.colors.textPrimary|c6", "tokens.colors.textSecondary|c6",
            "tokens.colors.divider|c6", "tokens.colors.icon|c6", "tokens.colors.error|c6", "tokens.colors.success|c6",
            "tokens.colors.overlay|c68",
            "tokens.typography.fontFamily|enum:default", "tokens.typography.bodySizeSp|int:12:30",
            "tokens.typography.titleSizeSp|int:14:40", "tokens.typography.captionSizeSp|int:8:20",
            "tokens.typography.fontWeight|enum:normal,medium,bold", "tokens.typography.lineHeightMultiplier|float:1:2",
            "tokens.spacing.xs|int:0:64", "tokens.spacing.sm|int:0:64", "tokens.spacing.md|int:0:64",
            "tokens.spacing.lg|int:0:64", "tokens.spacing.xl|int:0:64",
            "tokens.shapes.cardRadiusDp|int:0:32", "tokens.shapes.buttonRadiusDp|int:0:32",
            "tokens.shapes.inputRadiusDp|int:0:32", "tokens.shapes.dialogRadiusDp|int:0:32", "tokens.shapes.bubbleRadiusDp|int:0:17",
            "tokens.elevation.cardDp|int:0:24", "tokens.elevation.dialogDp|int:0:24",
            "components.appBar.background|c6", "components.appBar.foreground|c6", "components.appBar.heightDp|int:40:72",
            "components.navigationBar.background|c6", "components.navigationBar.selected|c6", "components.navigationBar.unselected|c6",
            "components.bottomNavigation.background|c6", "components.bottomNavigation.selectedBackground|c6",
            "components.bottomNavigation.iconSizeDp|int:16:40",
            "components.card.background|c6", "components.card.borderColor|c6", "components.card.borderWidthDp|int:0:4",
            "components.button.background|c6", "components.button.foreground|c6", "components.button.heightDp|int:36:64",
            "components.input.background|c6", "components.input.foreground|c6", "components.input.hint|c6",
            "components.dialog.background|c6", "components.dialog.scrim|c68",
            "screens.chats.background|c6", "screens.chats.listStyle|enum:card,flat", "screens.chats.avatarSizeDp|int:32:72",
            "screens.chats.showDividers|bool", "screens.chats.threeLines|bool",
            "screens.contacts.background|c6", "screens.contacts.sectionStyle|enum:card,flat", "screens.contacts.avatarSizeDp|int:32:72",
            "screens.profile.background|c6", "screens.profile.headerStyle|enum:centered,left",
            "screens.profile.avatarSizeDp|int:64:160", "screens.profile.cardRadiusDp|int:0:32",
            "screens.settings.background|c6", "screens.settings.groupStyle|enum:card,flat",
            "screens.settings.iconStyle|enum:rounded,square,plain", "screens.settings.showDescriptions|bool",
            "screens.themeStore.background|c6", "screens.themeStore.gridColumns|int:2:3", "screens.themeStore.previewRadiusDp|int:0:32",
            "chat.bubbleIn|c6", "chat.bubbleOut|c6", "chat.bubbleInText|c6", "chat.bubbleOutText|c6",
            "chat.bubbleRadiusDp|int:0:17", "chat.fontSizeSp|int:12:30",
            "assets.wallpaper|null", "assets.font|null", "assets.iconPack|null",
            "effects.blur|bool", "effects.glass|bool", "effects.animations|bool", "effects.reducedMotion|bool",
    };

    /** Parses and validates; throws IllegalArgumentException with a readable reason on any problem. */
    public static SkinSpec parse(String json) {
        if (json == null || json.getBytes().length > MAX_JSON_BYTES) {
            throw new IllegalArgumentException("Skin file is missing or too large");
        }
        try {
            JSONObject root = new JSONObject(json);
            int version = root.optInt("schemaVersion", 0);
            if (version == 1) {
                return parseV1(root);
            }
            if (version == 2) {
                return parseV2(root);
            }
            throw new IllegalArgumentException(version > SCHEMA_VERSION
                    ? "This theme needs a newer version of Kurogram" : "Unsupported skin schemaVersion");
        } catch (JSONException e) {
            throw new IllegalArgumentException("Skin file is not valid JSON");
        }
    }

    // ---- v2 ----

    private static SkinSpec parseV2(JSONObject root) throws JSONException {
        for (String rule : RULES_V2) {
            int bar = rule.indexOf('|');
            check(root, rule.substring(0, bar), rule.substring(bar + 1));
        }
        JSONObject meta = orEmpty(root.optJSONObject("meta"));
        String id = meta.optString("id", "");
        if (!id.matches("[A-Za-z0-9_.-]{1,48}")) {
            throw new IllegalArgumentException("meta.id is invalid");
        }
        String name = meta.optString("name", id);
        if (name.length() > 48 || meta.optString("author", "").length() > 48 || meta.optString("license", "").length() > 24) {
            throw new IllegalArgumentException("meta text is too long");
        }
        JSONArray tags = meta.optJSONArray("tags");
        if (tags != null) {
            if (tags.length() > 8) {
                throw new IllegalArgumentException("meta.tags: at most 8");
            }
            for (int i = 0; i < tags.length(); i++) {
                if (!tags.optString(i, "").matches("[A-Za-z0-9_\\- ]{1,24}")) {
                    throw new IllegalArgumentException("meta.tags has an invalid tag");
                }
            }
        }

        Map<String, Integer> colors = new HashMap<>();
        String[][] map = {
                {"accent", "tokens.colors.accent"}, {"onAccent", "tokens.colors.onAccent"}, {"background", "tokens.colors.background"},
                {"surface", "tokens.colors.surface"}, {"surfaceElevated", "tokens.colors.surfaceElevated"},
                {"textPrimary", "tokens.colors.textPrimary"}, {"textSecondary", "tokens.colors.textSecondary"},
                {"divider", "tokens.colors.divider"}, {"icon", "tokens.colors.icon"}, {"error", "tokens.colors.error"},
                {"success", "tokens.colors.success"},
                {"card", "components.card.background"}, {"appBar", "components.appBar.background"},
                {"appBarText", "components.appBar.foreground"}, {"button", "components.button.background"},
                {"buttonText", "components.button.foreground"}, {"input", "components.input.background"},
                {"inputHint", "components.input.hint"}, {"dialog", "components.dialog.background"},
                {"bubbleIn", "chat.bubbleIn"}, {"bubbleOut", "chat.bubbleOut"},
                {"bubbleInText", "chat.bubbleInText"}, {"bubbleOutText", "chat.bubbleOutText"},
        };
        for (String[] m : map) {
            Object v = get(root, m[1]);
            if (v instanceof String) {
                colors.put(m[0], 0xFF000000 | (int) Long.parseLong(((String) v).substring(1), 16));
            }
        }
        int card = intOr(root, "tokens.shapes.cardRadiusDp", 22);
        int dialog = intOr(root, "tokens.shapes.dialogRadiusDp", card);
        int columns = intOr(root, "screens.themeStore.gridColumns", 2);
        String listStyle = strOr(root, "screens.settings.groupStyle", strOr(root, "screens.chats.listStyle", LIST_CARD));
        Integer bubble = intOrNull(root, "chat.bubbleRadiusDp");
        if (bubble == null) bubble = intOrNull(root, "tokens.shapes.bubbleRadiusDp");
        Integer font = intOrNull(root, "chat.fontSizeSp");
        if (font == null) font = intOrNull(root, "tokens.typography.bodySizeSp");
        Map<String, Object> values = new HashMap<>();
        for (String rule : RULES_V2) {
            String path = rule.substring(0, rule.indexOf('|'));
            Object v = get(root, path);
            if (v != null) {
                values.put(path, v);
            }
        }
        return new SkinSpec(id, name, 2, card, dialog, columns, listStyle, colors, bubble, font,
                boolOrNull(root, "screens.chats.threeLines"), boolOrNull(root, "effects.glass"), boolOrNull(root, "effects.blur"), values);
    }

    // ---- v1 (flat, kept so existing themes keep working) ----

    private static SkinSpec parseV1(JSONObject root) {
        String id = root.optString("id", "");
        if (!id.matches("[A-Za-z0-9_.-]{1,48}")) {
            throw new IllegalArgumentException("Invalid skin id");
        }
        String name = root.optString("name", id);
        if (name.length() > 48) {
            throw new IllegalArgumentException("Skin name too long");
        }
        JSONObject layout = orEmpty(root.optJSONObject("layout"));
        int radius = layout.optInt("cardRadiusDp", 22);
        if (radius < 0 || radius > 32) {
            throw new IllegalArgumentException("cardRadiusDp must be 0-32");
        }
        int columns = layout.optInt("gridColumns", 2);
        if (columns < 2 || columns > 3) {
            throw new IllegalArgumentException("gridColumns must be 2 or 3");
        }
        String listStyle = layout.optString("listStyle", LIST_CARD);
        if (!LIST_CARD.equals(listStyle) && !LIST_FLAT.equals(listStyle)) {
            throw new IllegalArgumentException("listStyle must be card or flat");
        }
        Map<String, Integer> colors = new HashMap<>();
        JSONObject c = root.optJSONObject("colors");
        if (c != null) {
            String[][] names = {{"accent", "accent"}, {"background", "background"}, {"surface", "surface"}, {"text", "textPrimary"},
                    {"textSecondary", "textSecondary"}, {"divider", "divider"}, {"actionBar", "appBar"}, {"actionBarText", "appBarText"},
                    {"bubbleIn", "bubbleIn"}, {"bubbleOut", "bubbleOut"}, {"bubbleInText", "bubbleInText"}, {"bubbleOutText", "bubbleOutText"}};
            for (String[] n : names) {
                if (c.has(n[0])) {
                    String hex = c.optString(n[0], "");
                    if (!hex.matches("#[0-9A-Fa-f]{6}")) {
                        throw new IllegalArgumentException(n[0] + " must look like #RRGGBB");
                    }
                    colors.put(n[1], 0xFF000000 | (int) Long.parseLong(hex.substring(1), 16));
                }
            }
        }
        JSONObject chat = orEmpty(root.optJSONObject("chat"));
        Integer bubbleRadius = chat.has("bubbleRadius") ? range(chat, "bubbleRadius", 0, 17) : null;
        Integer fontSize = chat.has("fontSize") ? range(chat, "fontSize", 12, 30) : null;
        JSONObject list = orEmpty(root.optJSONObject("list"));
        Boolean threeLines = list.has("threeLines") ? list.optBoolean("threeLines") : null;
        JSONObject surface = orEmpty(root.optJSONObject("surface"));
        Boolean liquidGlass = surface.has("liquidGlass") ? surface.optBoolean("liquidGlass") : null;
        Boolean blur = surface.has("blur") ? surface.optBoolean("blur") : null;
        return new SkinSpec(id, name, 1, radius, radius, columns, listStyle, colors, bubbleRadius, fontSize, threeLines, liquidGlass, blur, new HashMap<>());
    }

    // ---- helpers ----

    private static void check(JSONObject root, String path, String kind) {
        Object v = get(root, path);
        if (v == null) {
            return; // optional
        }
        boolean ok;
        String[] k = kind.split(":");
        switch (k[0]) {
            case "c6":
                ok = v instanceof String && ((String) v).matches("#[0-9A-Fa-f]{6}");
                break;
            case "c68":
                ok = v instanceof String && ((String) v).matches("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?");
                break;
            case "bool":
                ok = v instanceof Boolean;
                break;
            case "null":
                ok = false; // present and non-null
                break;
            case "int":
                ok = v instanceof Integer && (Integer) v >= Integer.parseInt(k[1]) && (Integer) v <= Integer.parseInt(k[2]);
                break;
            case "float":
                ok = v instanceof Number && ((Number) v).doubleValue() >= Double.parseDouble(k[1]) && ((Number) v).doubleValue() <= Double.parseDouble(k[2]);
                break;
            case "enum":
                ok = v instanceof String && java.util.Arrays.asList(k[1].split(",")).contains(v);
                break;
            default:
                ok = false;
        }
        if (!ok) {
            throw new IllegalArgumentException(path + (k[0].equals("null") ? " is not supported yet (leave it null)" : " has an invalid value"));
        }
    }

    /** Walks a dotted path; returns null when any step is missing or JSON null. */
    private static Object get(JSONObject root, String path) {
        Object cur = root;
        for (String part : path.split("\\.")) {
            if (!(cur instanceof JSONObject)) {
                return null;
            }
            cur = ((JSONObject) cur).opt(part);
            if (cur == null || cur == JSONObject.NULL) {
                return null;
            }
        }
        return cur;
    }

    private static int intOr(JSONObject r, String path, int d) {
        Object v = get(r, path);
        return v instanceof Integer ? (Integer) v : d;
    }

    private static Integer intOrNull(JSONObject r, String path) {
        Object v = get(r, path);
        return v instanceof Integer ? (Integer) v : null;
    }

    private static String strOr(JSONObject r, String path, String d) {
        Object v = get(r, path);
        return v instanceof String ? (String) v : d;
    }

    private static Boolean boolOrNull(JSONObject r, String path) {
        Object v = get(r, path);
        return v instanceof Boolean ? (Boolean) v : null;
    }

    private static JSONObject orEmpty(JSONObject o) {
        return o != null ? o : new JSONObject();
    }

    private static int range(JSONObject o, String key, int min, int max) {
        int v = o.optInt(key, Integer.MIN_VALUE);
        if (v < min || v > max) {
            throw new IllegalArgumentException(key + " must be " + min + "-" + max);
        }
        return v;
    }
}
