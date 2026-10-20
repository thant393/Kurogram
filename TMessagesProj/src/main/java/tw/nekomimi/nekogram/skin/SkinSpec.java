package tw.nekomimi.nekogram.skin;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A skin: nothing but validated values that pick between layouts the app already contains.
 * A skin file never carries code or layout XML - it only says which built-in variant to use
 * and with which numbers - so every field is range-checked here.
 */
public final class SkinSpec {

    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_JSON_BYTES = 64 * 1024;

    public static final String LIST_CARD = "card";
    public static final String LIST_FLAT = "flat";

    public final String id;
    public final String name;
    /** Corner radius of the large surfaces (profile cards, grid cards), in dp. */
    public final int cardRadiusDp;
    /** Columns of the profile's sticker grid. */
    public final int gridColumns;
    /** "card" (bordered rounded cards) or "flat" (borderless cells on the surface). */
    public final String listStyle;
    /** Accent override, 0xAARRGGBB, or 0 to keep the theme's own accent. */
    public final int accentColor;

    private SkinSpec(String id, String name, int cardRadiusDp, int gridColumns, String listStyle, int accentColor) {
        this.id = id;
        this.name = name;
        this.cardRadiusDp = cardRadiusDp;
        this.gridColumns = gridColumns;
        this.listStyle = listStyle;
        this.accentColor = accentColor;
    }

    public static SkinSpec defaults() {
        return new SkinSpec("default", "Default", 22, 2, LIST_CARD, 0);
    }

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
            JSONObject layout = root.optJSONObject("layout");
            if (layout == null) {
                layout = new JSONObject();
            }
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
            int accent = 0;
            JSONObject colors = root.optJSONObject("colors");
            if (colors != null && colors.has("accent")) {
                String c = colors.optString("accent", "");
                if (!c.matches("#[0-9A-Fa-f]{6}")) {
                    throw new IllegalArgumentException("accent must look like #RRGGBB");
                }
                accent = 0xFF000000 | (int) Long.parseLong(c.substring(1), 16);
            }
            return new SkinSpec(id, name, radius, columns, listStyle, accent);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Skin file is not valid JSON");
        }
    }
}
