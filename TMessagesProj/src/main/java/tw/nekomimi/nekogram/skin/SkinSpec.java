package tw.nekomimi.nekogram.skin;

import android.util.SparseIntArray;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.ui.ActionBar.Theme;

import java.util.HashMap;
import java.util.Map;

/**
 * A skin: nothing but validated values that pick between looks the app already contains. A skin
 * file never carries code or layout XML, so every field is range-checked here. Anything a skin
 * leaves out keeps the original UI, so an empty skin is exactly the stock app.
 *
 * Groups (all optional):
 *   colors  accent, background, surface, text, textSecondary, divider, actionBar, actionBarText,
 *           bubbleIn, bubbleOut, bubbleInText, bubbleOutText          ("#RRGGBB")
 *   layout  cardRadiusDp 0-32, gridColumns 2|3, listStyle card|flat   (Kuro screens + dialogs/cards)
 *   chat    bubbleRadius 0-17, fontSize 12-30                          (Telegram's own chat settings)
 *   list    threeLines true|false                                      (chat list row layout)
 *   surface liquidGlass true|false, blur true|false                    (translucent/glass bars)
 */
public final class SkinSpec {

    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_JSON_BYTES = 64 * 1024;

    public static final String LIST_CARD = "card";
    public static final String LIST_FLAT = "flat";

    public final String id;
    public final String name;
    public final int cardRadiusDp;
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

    private SparseIntArray keyOverrides;

    private SkinSpec(String id, String name, int cardRadiusDp, int gridColumns, String listStyle, Map<String, Integer> colors,
                     Integer bubbleRadius, Integer fontSize, Boolean threeLines, Boolean liquidGlass, Boolean blur) {
        this.id = id;
        this.name = name;
        this.cardRadiusDp = cardRadiusDp;
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
    }

    public static SkinSpec defaults() {
        return new SkinSpec("default", "Default", 22, 2, LIST_CARD, new HashMap<>(), null, null, null, null, null);
    }

    /** Theme colour key -> colour, for every key this skin overrides (empty if none). Built once. */
    public synchronized SparseIntArray keyOverrides() {
        if (keyOverrides == null) {
            SparseIntArray o = new SparseIntArray();
            put(o, "accent", Theme.key_windowBackgroundWhiteBlueText, Theme.key_windowBackgroundWhiteBlueText2,
                    Theme.key_windowBackgroundWhiteBlueText3, Theme.key_windowBackgroundWhiteBlueText4,
                    Theme.key_windowBackgroundWhiteBlueText5, Theme.key_windowBackgroundWhiteBlueText6,
                    Theme.key_windowBackgroundWhiteBlueText7, Theme.key_windowBackgroundWhiteBlueIcon,
                    Theme.key_windowBackgroundWhiteBlueHeader, Theme.key_windowBackgroundWhiteLinkText,
                    Theme.key_dialogTextBlue, Theme.key_dialogTextBlue2, Theme.key_dialogButton,
                    Theme.key_switchTrackChecked, Theme.key_radioBackgroundChecked, Theme.key_checkbox,
                    Theme.key_chats_unreadCounter, Theme.key_chats_actionBackground, Theme.key_featuredStickers_addButton);
            put(o, "background", Theme.key_windowBackgroundGray, Theme.key_dialogBackgroundGray);
            put(o, "surface", Theme.key_windowBackgroundWhite, Theme.key_dialogBackground);
            put(o, "text", Theme.key_windowBackgroundWhiteBlackText, Theme.key_dialogTextBlack, Theme.key_chats_name);
            put(o, "textSecondary", Theme.key_windowBackgroundWhiteGrayText, Theme.key_windowBackgroundWhiteGrayText2,
                    Theme.key_windowBackgroundWhiteGrayText3, Theme.key_windowBackgroundWhiteGrayText4,
                    Theme.key_windowBackgroundWhiteGrayText5, Theme.key_windowBackgroundWhiteGrayText6,
                    Theme.key_windowBackgroundWhiteGrayText7, Theme.key_windowBackgroundWhiteGrayText8, Theme.key_chats_message);
            put(o, "divider", Theme.key_divider, Theme.key_dialogGrayLine);
            put(o, "actionBar", Theme.key_actionBarDefault);
            put(o, "actionBarText", Theme.key_actionBarDefaultTitle, Theme.key_actionBarDefaultIcon, Theme.key_actionBarDefaultSubtitle);
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

    private static final String[] COLOR_NAMES = {"accent", "background", "surface", "text", "textSecondary", "divider",
            "actionBar", "actionBarText", "bubbleIn", "bubbleOut", "bubbleInText", "bubbleOutText"};

    /** Parses and validates; throws IllegalArgumentException with a readable reason on any problem. */
    public static SkinSpec parse(String json) {
        if (json == null || json.getBytes().length > MAX_JSON_BYTES) {
            throw new IllegalArgumentException("Skin file is missing or too large");
        }
        try {
            JSONObject root = new JSONObject(json);
            if (root.optInt("schemaVersion", 0) != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported skin schemaVersion");
            }
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
                for (String key : COLOR_NAMES) {
                    if (c.has(key)) {
                        String hex = c.optString(key, "");
                        if (!hex.matches("#[0-9A-Fa-f]{6}")) {
                            throw new IllegalArgumentException(key + " must look like #RRGGBB");
                        }
                        colors.put(key, 0xFF000000 | (int) Long.parseLong(hex.substring(1), 16));
                    }
                }
            }

            JSONObject chat = orEmpty(root.optJSONObject("chat"));
            Integer bubbleRadius = chat.has("bubbleRadius") ? range(chat, "bubbleRadius", 0, 17) : null;
            Integer fontSize = chat.has("fontSize") ? range(chat, "fontSize", 12, 30) : null;
            Boolean threeLines = root.optJSONObject("list") != null && root.optJSONObject("list").has("threeLines")
                    ? root.optJSONObject("list").optBoolean("threeLines") : null;
            JSONObject surface = orEmpty(root.optJSONObject("surface"));
            Boolean liquidGlass = surface.has("liquidGlass") ? surface.optBoolean("liquidGlass") : null;
            Boolean blur = surface.has("blur") ? surface.optBoolean("blur") : null;
            return new SkinSpec(id, name, radius, columns, listStyle, colors, bubbleRadius, fontSize, threeLines, liquidGlass, blur);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Skin file is not valid JSON");
        }
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
