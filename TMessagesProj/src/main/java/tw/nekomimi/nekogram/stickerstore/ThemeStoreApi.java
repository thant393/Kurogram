package tw.nekomimi.nekogram.stickerstore;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;

import java.net.URLEncoder;
import java.util.ArrayList;

/** Client for the backend's /api/v1/themes endpoints. */
public final class ThemeStoreApi {

    public interface ListCallback {
        void onSuccess(ArrayList<ThemeInfo> themes);

        void onError(String message);
    }

    private ThemeStoreApi() {
    }

    public static void fetchThemes(String sort, int page, int limit, String ownerId, String query, ListCallback callback) {
        int pageSize = limit <= 0 ? 20 : Math.min(limit, 50);
        StringBuilder path = new StringBuilder("api/v1/themes/?sort_by=").append(sort == null ? "newest" : sort)
                .append("&skip=").append((Math.max(1, page) - 1) * pageSize)
                .append("&limit=").append(pageSize);
        try {
            if (ownerId != null && !ownerId.isEmpty()) {
                path.append("&owner_id=").append(URLEncoder.encode(ownerId, "UTF-8"));
            }
            if (query != null && !query.trim().isEmpty()) {
                path.append("&q=").append(URLEncoder.encode(query.trim(), "UTF-8"));
            }
        } catch (Exception ignored) {
        }
        StickerStoreApi.getJson(path.toString(), new StickerStoreApi.JsonCallback() {
            @Override
            public void onSuccess(Object root) {
                ArrayList<ThemeInfo> out = new ArrayList<>();
                if (root instanceof JSONArray) {
                    JSONArray array = (JSONArray) root;
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject o = array.optJSONObject(i);
                        if (o != null) {
                            out.add(ThemeInfo.fromJson(o));
                        }
                    }
                }
                callback.onSuccess(out);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /** The theme file: {id, slug, type, version, sha256, content}. Counts as one download. */
    public static void download(String themeId, long accountId, StickerStoreApi.ResultCallback callback) {
        StickerStoreApi.getJson("api/v1/themes/" + themeId + "/download?account_id=" + accountId, new StickerStoreApi.JsonCallback() {
            @Override
            public void onSuccess(Object root) {
                callback.onSuccess(root instanceof JSONObject ? (JSONObject) root : null);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public static void submitTheme(long accountId, String title, String description, String category, String type,
                                   String content, OwnerProfileSync.Profile owner, StickerStoreApi.ResultCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("title", title);
            if (description != null && !description.isEmpty()) {
                body.put("description", description);
            }
            body.put("category", category);
            body.put("type", type);
            body.put("content", content);
            JSONObject palette = paletteFor(type, content);
            if (palette != null) {
                body.put("palette", palette);
            }
            JSONObject o = new JSONObject();
            o.put("display_name", owner.name == null ? JSONObject.NULL : owner.name);
            o.put("username", owner.username == null ? JSONObject.NULL : owner.username);
            o.put("avatar_url", owner.avatarUrl == null ? JSONObject.NULL : owner.avatarUrl);
            o.put("avatar_file_id", owner.avatarFileId == null ? JSONObject.NULL : owner.avatarFileId);
            body.put("owner", o);
            StickerStoreApi.send("POST", "api/v1/themes/?account_id=" + accountId, body, callback);
        } catch (Exception e) {
            org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> callback.onError("Couldn't build the upload request"));
        }
    }

    /** accent / background / is_dark so the store can draw a preview without downloading the file. */
    static JSONObject paletteFor(String type, String content) {
        try {
            int accent = 0, background = 0;
            if ("skin".equals(type)) {
                JSONObject root = new JSONObject(content);
                // v2 keeps colours under tokens.colors, v1 under colors
                JSONObject tokens = root.optJSONObject("tokens");
                JSONObject colors = tokens != null ? tokens.optJSONObject("colors") : root.optJSONObject("colors");
                String a = colors != null ? colors.optString("accent", "") : "";
                if (a.matches("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?")) {
                    accent = 0xFF000000 | (int) Long.parseLong(a.substring(1, 7), 16); // preview ignores alpha
                }
                String b = colors != null ? colors.optString("background", "") : "";
                if (b.matches("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?")) {
                    background = 0xFF000000 | (int) Long.parseLong(b.substring(1, 7), 16);
                }
            } else {
                for (String raw : content.split("\n")) {
                    String line = raw.trim();
                    int eq = line.indexOf('=');
                    if (eq <= 0) continue;
                    String key = line.substring(0, eq).trim();
                    int value = parseColorValue(line.substring(eq + 1).trim());
                    if (value == Integer.MIN_VALUE) continue;
                    if (key.equals("windowBackgroundWhite")) background = 0xFF000000 | (value & 0xFFFFFF);
                    if (key.equals("chats_actionBackground") || (accent == 0 && key.equals("actionBarDefault"))) {
                        accent = 0xFF000000 | (value & 0xFFFFFF);
                    }
                }
            }
            JSONObject palette = new JSONObject();
            if (accent != 0) palette.put("accent", String.format("#%06X", accent & 0xFFFFFF));
            if (background != 0) {
                palette.put("background", String.format("#%06X", background & 0xFFFFFF));
                double lum = (0.299 * ((background >> 16) & 0xFF) + 0.587 * ((background >> 8) & 0xFF) + 0.114 * (background & 0xFF)) / 255.0;
                palette.put("is_dark", lum < 0.5);
            }
            return palette.length() == 0 ? null : palette;
        } catch (Exception e) {
            return null;
        }
    }

    private static int parseColorValue(String v) {
        try {
            if (v.startsWith("#")) {
                return (int) Long.parseLong(v.substring(1), 16);
            }
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    public static void setLike(String themeId, long accountId, boolean liked, StickerStoreApi.ResultCallback callback) {
        String path = "api/v1/themes/" + themeId + "/like?account_id=" + accountId;
        StickerStoreApi.send(liked ? "POST" : "DELETE", path, null, new StickerStoreApi.ResultCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                callback.onSuccess(response);
            }

            @Override
            public void onError(String message) {
                // "already liked" / "have not liked" mean the server is already in the wanted state.
                if (message != null && (message.contains("Already liked") || message.contains("not liked"))) {
                    callback.onSuccess(null);
                } else {
                    callback.onError(message);
                }
            }
        });
    }

    public static void rate(String themeId, long accountId, int stars, StickerStoreApi.ResultCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("rating", stars);
            StickerStoreApi.send("PUT", "api/v1/themes/" + themeId + "/rating?account_id=" + accountId, body, callback);
        } catch (Exception e) {
            callback.onError("Couldn't build the rating request");
        }
    }

    public static void report(String themeId, long accountId, String reasonKey, StickerStoreApi.ResultCallback callback) {
        String reason = reasonKey;
        try {
            JSONObject body = new JSONObject();
            body.put("reason", reason);
            StickerStoreApi.send("POST", "api/v1/themes/" + themeId + "/report?account_id=" + accountId, body, callback);
        } catch (Exception e) {
            callback.onError("Couldn't build the report");
        }
    }

    // ---- what this user already did to a theme (remembered locally; the server enforces one each) ----

    public static boolean isLiked(String id) {
        return prefs().getBoolean("liked_" + id, false);
    }

    public static void setLikedLocal(String id, boolean liked) {
        prefs().edit().putBoolean("liked_" + id, liked).apply();
    }

    public static int myRating(String id) {
        return prefs().getInt("rating_" + id, 0);
    }

    public static void setMyRating(String id, int stars) {
        prefs().edit().putInt("rating_" + id, stars).apply();
    }

    // ---- offline copy of downloaded themes (so a theme can be re-applied without the network) ----

    private static final int MAX_CACHED = 10;

    /** Keeps the theme file (already hash-checked) for offline use; the 10 most recent are kept. */
    public static void cacheContent(String id, int version, String type, String sha256, String content) {
        try {
            SharedPreferences p = cachePrefs();
            JSONObject entry = new JSONObject();
            entry.put("v", version);
            entry.put("t", type);
            entry.put("sha", sha256);
            entry.put("c", content);
            org.json.JSONArray order = new org.json.JSONArray(p.getString("order", "[]"));
            org.json.JSONArray next = new org.json.JSONArray();
            next.put(id);
            for (int i = 0; i < order.length(); i++) {
                if (!id.equals(order.optString(i)) && next.length() < MAX_CACHED) {
                    next.put(order.optString(i));
                }
            }
            SharedPreferences.Editor e = p.edit().putString("e_" + id, entry.toString()).putString("order", next.toString());
            for (int i = 0; i < order.length(); i++) {
                String old = order.optString(i);
                boolean kept = false;
                for (int j = 0; j < next.length(); j++) {
                    kept |= old.equals(next.optString(j));
                }
                if (!kept) {
                    e.remove("e_" + old);
                }
            }
            e.apply();
        } catch (Exception ignore) {
        }
    }

    /** The saved copy as the same shape /download returns, or null when none (or older than minVersion). */
    public static JSONObject cachedDownload(String id, int minVersion) {
        try {
            String raw = cachePrefs().getString("e_" + id, null);
            if (raw == null) {
                return null;
            }
            JSONObject entry = new JSONObject(raw);
            if (entry.optInt("v", 0) < minVersion) {
                return null;
            }
            JSONObject out = new JSONObject();
            out.put("id", id);
            out.put("slug", "cached");
            out.put("type", entry.optString("t"));
            out.put("version", entry.optInt("v", 1));
            out.put("sha256", entry.optString("sha"));
            out.put("content", entry.optString("c"));
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static SharedPreferences cachePrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("kuro_theme_cache", Context.MODE_PRIVATE);
    }

    // ---- which store theme is currently applied (for the Apply / Reset button) ----

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("kuro_themes", Context.MODE_PRIVATE);
    }

    public static String appliedId() {
        return prefs().getString("applied_id", null);
    }

    public static void setApplied(String id) {
        setApplied(id, 1);
    }

    public static void setApplied(String id, int version) {
        prefs().edit().putString("applied_id", id).putInt("applied_version", version).apply();
    }

    public static int appliedVersion() {
        return prefs().getInt("applied_version", 1);
    }

    public static void clearApplied() {
        prefs().edit().remove("applied_id").remove("applied_version").apply();
    }
}
