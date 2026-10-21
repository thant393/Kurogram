package tw.nekomimi.nekogram.stickerstore;

import android.graphics.Color;

import org.json.JSONObject;

/** One theme as listed by the store (the theme file itself is fetched on demand). */
public class ThemeInfo {
    public String id;
    public String slug;
    public String title;
    public String description;
    public String category;
    public String type; // "skin" | "colors"
    public int accent;      // 0 = unknown
    public int background;  // 0 = unknown
    public boolean dark;
    public int downloads;
    public int likes;
    public double ratingAvg;
    public int ratingCount;
    public long ownerTelegramId;
    public String ownerName;
    public String ownerUsername;
    /** Set only for themes bundled in the app (BuiltInThemes); they apply without the network. */
    public String builtinContent;

    public static ThemeInfo fromJson(JSONObject o) {
        ThemeInfo t = new ThemeInfo();
        t.id = o.optString("_id", o.optString("id", ""));
        t.slug = o.optString("slug", "");
        t.title = o.optString("title", t.slug);
        t.description = o.isNull("description") ? null : o.optString("description", null);
        t.category = o.optString("category", "Custom");
        t.type = o.optString("type", "skin");
        JSONObject palette = o.optJSONObject("palette");
        if (palette != null) {
            t.accent = parseColor(palette.optString("accent", null));
            t.background = parseColor(palette.optString("background", null));
            t.dark = palette.optBoolean("is_dark", false);
        }
        t.downloads = o.optInt("download_count", 0);
        t.likes = o.optInt("total_likes", 0);
        JSONObject rating = o.optJSONObject("rating");
        if (rating != null) {
            t.ratingAvg = rating.optDouble("avg", 0);
            t.ratingCount = rating.optInt("count", 0);
        }
        JSONObject owner = o.optJSONObject("owner");
        if (owner != null) {
            try {
                t.ownerTelegramId = Long.parseLong(owner.optString("telegram_id", "0"));
            } catch (NumberFormatException ignored) {
            }
            t.ownerName = owner.isNull("display_name") ? null : owner.optString("display_name", null);
            t.ownerUsername = owner.isNull("username") ? null : owner.optString("username", null);
        }
        return t;
    }

    private static int parseColor(String hex) {
        if (hex == null || !hex.matches("#[0-9A-Fa-f]{6}")) {
            return 0;
        }
        return Color.parseColor(hex);
    }
}
