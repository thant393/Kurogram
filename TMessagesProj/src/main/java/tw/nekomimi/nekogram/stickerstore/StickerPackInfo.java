package tw.nekomimi.nekogram.stickerstore;

import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;

/**
 * One sticker pack as listed by the backend catalog (short_name, category, rating,
 * download_count, owner_telegram_id). The backend does not store a human display name
 * or an icon - those come from Telegram itself once the pack's real sticker set is
 * resolved by short_name (see StickerSetResolver). Until resolved, {@link #getDisplayTitle()}
 * returns null rather than falling back to the technical short_name, so the UI can show
 * a neutral loading state instead of a technical string.
 */
public class StickerPackInfo {

    /** The backend's own document id (Mongo _id) - used in /{pack_id}/... endpoints. May be
     *  null if the list response doesn't include one, in which case shortName is used. */
    public String id;
    public String shortName;
    public String category;
    public double ratingAvg;
    /** How many users rated this pack. */
    public int ratingCount;
    /** Distinct from ratingCount - the backend's own like/favorite counter, when present. */
    public int totalLikes;
    public int downloads;
    public long ownerTelegramId;
    /** From the backend's nested owner object, when present (used until Telegram resolves the real user). */
    public String ownerDisplayName;
    public String ownerUsername;

    /** Filled in asynchronously by StickerSetResolver once the real Telegram sticker set is fetched. */
    public TLRPC.TL_messages_stickerSet resolvedSet;

    public static StickerPackInfo fromJson(JSONObject obj) {
        StickerPackInfo info = new StickerPackInfo();
        JSONObject oidObj = obj.optJSONObject("_id");
        if (oidObj != null && oidObj.has("$oid")) {
            info.id = oidObj.optString("$oid", null);
        } else {
            info.id = firstString(obj, "id", "_id", "pack_id");
        }
        info.shortName = firstString(obj, "short_name", "shortName", "name");
        info.category = firstString(obj, "category", "category_name");
        info.downloads = firstInt(obj, "download_count", "downloads", "downloads_count");
        // New shape: nested "owner" {telegram_id, display_name, username, avatar_url, ...};
        // the flat owner_telegram_id is still read for documents not migrated yet.
        JSONObject ownerObj = obj.optJSONObject("owner");
        if (ownerObj != null) {
            info.ownerTelegramId = firstLong(ownerObj, "telegram_id", "id");
            info.ownerDisplayName = firstString(ownerObj, "display_name", "name");
            info.ownerUsername = firstString(ownerObj, "username");
        }
        if (info.ownerTelegramId == 0) {
            info.ownerTelegramId = firstLong(obj, "owner_telegram_id", "ownerTelegramId", "owner_id");
        }

        JSONObject ratingObj = obj.optJSONObject("rating");
        if (ratingObj != null) {
            info.ratingAvg = ratingObj.optDouble("avg", 0);
            info.ratingCount = ratingObj.optInt("count", 0);
        } else {
            info.ratingAvg = firstDouble(obj, "avg_rating", "average_rating", "rating");
            info.ratingCount = firstInt(obj, "rating_count", "ratings_count");
        }

        // The backend's own like counter, if it exposes one as its own field. Falls back to
        // the rating count (how many people rated the pack) only when no dedicated likes
        // field is present at all, so a real total_likes value from the API is never
        // overwritten by the unrelated rating count.
        if (hasAny(obj, "total_likes", "likes_count", "likes")) {
            info.totalLikes = firstInt(obj, "total_likes", "likes_count", "likes");
        } else {
            info.totalLikes = info.ratingCount;
        }
        return info;
    }

    /** What to put in the {pack_id} slot of the backend's endpoints. */
    public String getApiId() {
        return id != null && !id.isEmpty() ? id : shortName;
    }

    /** Average = sum of stars / number of ratings. Applies one user's rating to the local
     *  numbers the same way the server's upsert does: a first-time rating adds one rating to
     *  the count; changing an earlier rating (previousStars > 0) keeps the count and only
     *  swaps that user's old stars for the new ones. */
    public void applyMyRating(int stars, int previousStars) {
        double sum = ratingAvg * ratingCount;
        if (previousStars > 0 && ratingCount > 0) {
            sum = sum - previousStars + stars;
        } else {
            ratingCount += 1;
            sum += stars;
        }
        ratingAvg = ratingCount > 0 ? sum / ratingCount : 0;
    }

    /** If the server's reply to a rating call carries the recalculated average/count, adopt
     *  those (they are authoritative). Returns true if it did. */
    public boolean mergeServerRating(JSONObject response) {
        if (response == null) {
            return false;
        }
        JSONObject src = response.optJSONObject("pack") != null ? response.optJSONObject("pack") : response;
        JSONObject r = src.optJSONObject("rating");
        if (r != null && r.has("avg")) {
            ratingAvg = r.optDouble("avg", ratingAvg);
            ratingCount = r.optInt("count", ratingCount);
            return true;
        }
        if (src.has("avg") && src.has("count")) {
            ratingAvg = src.optDouble("avg", ratingAvg);
            ratingCount = src.optInt("count", ratingCount);
            return true;
        }
        return false;
    }

    /** The real, human-readable pack name from Telegram - null until resolved, never the short_name. */
    public String getDisplayTitle() {
        if (resolvedSet != null && resolvedSet.set != null && resolvedSet.set.title != null && !resolvedSet.set.title.isEmpty()) {
            return resolvedSet.set.title;
        }
        return null;
    }

    public TLRPC.Document getFirstSticker() {
        if (resolvedSet != null && resolvedSet.documents != null && !resolvedSet.documents.isEmpty()) {
            return resolvedSet.documents.get(0);
        }
        return null;
    }

    private static boolean hasAny(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                return true;
            }
        }
        return false;
    }

    private static String firstString(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                String value = obj.optString(key, null);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }

    private static double firstDouble(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                return obj.optDouble(key, 0);
            }
        }
        return 0;
    }

    private static int firstInt(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                return obj.optInt(key, 0);
            }
        }
        return 0;
    }

    private static long firstLong(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                // owner_telegram_id may arrive as a JSON string or a number depending on
                // backend serialization - optString + parse handles both safely.
                String raw = obj.optString(key, null);
                if (raw != null && !raw.isEmpty()) {
                    try {
                        return Long.parseLong(raw);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return 0;
    }
}
