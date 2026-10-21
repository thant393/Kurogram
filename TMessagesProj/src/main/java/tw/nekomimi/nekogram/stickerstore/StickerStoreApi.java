package tw.nekomimi.nekogram.stickerstore;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class StickerStoreApi {

    public interface PacksCallback {
        void onSuccess(ArrayList<StickerPackInfo> packs);
        void onError(String message);
    }

    public interface ResultCallback {
        /** response is the parsed JSON object the server replied with, or null if the reply had no JSON object body. */
        void onSuccess(JSONObject response);
        void onError(String message);
    }

    public static final String SORT_POPULAR = "popular";
    public static final String SORT_NEWEST = "newest";

    private static OkHttpClient okHttpClient;

    private static OkHttpClient getClient() {
        if (okHttpClient == null) {
            okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .writeTimeout(20, TimeUnit.SECONDS)
                    .build();
        }
        return okHttpClient;
    }

    public static String getBaseUrl() {
        String url = BuildConfig.STICKER_API_BASE_URL;
        return url == null ? "" : url.trim();
    }

    public static boolean isConfigured() {
        return !getBaseUrl().isEmpty();
    }

    public static void fetchPacks(String category, String sort, int page, int limit, PacksCallback callback) {
        fetchPacks(category, sort, page, limit, null, callback);
    }

    /** Same list, restricted to the packs one Telegram account owns (ownerId == null: all). */
    public static void fetchPacks(String category, String sort, int page, int limit, String ownerId, PacksCallback callback) {
        if (!isConfigured()) {
            Utilities.stageQueue.postRunnable(() -> postError(callback, "Sticker store is not configured"));
            return;
        }
        StringBuilder url = new StringBuilder(getBaseUrl());
        if (!url.toString().endsWith("/")) {
            url.append("/");
        }
        int pageSize = limit <= 0 ? 20 : Math.min(limit, 50);
        int safePage = Math.max(1, page);
        String sortValue = sort == null ? SORT_NEWEST : sort;
        // The backend (GET /api/v1/stickers/) reads sort_by + skip + limit. The older
        // sort/page names are sent too and are simply ignored by a server that doesn't use
        // them - without sort_by/skip the sort chips did nothing and every "page" came back
        // identical (always the first rows).
        url.append("api/v1/stickers/?sort_by=").append(sortValue)
                .append("&skip=").append((safePage - 1) * pageSize)
                .append("&limit=").append(pageSize)
                .append("&sort=").append(sortValue)
                .append("&page=").append(safePage);
        if (category != null && !category.isEmpty()) {
            try {
                url.append("&category=").append(URLEncoder.encode(category, "UTF-8"));
            } catch (UnsupportedEncodingException ignored) {
            }
        }

        if (ownerId != null && !ownerId.isEmpty()) {
            url.append("&owner_id=").append(ownerId);
        }

        Request request = new Request.Builder().url(url.toString()).get().build();
        getClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                FileLog.e("StickerStoreApi: network error", e);
                postError(callback, "Couldn't reach the sticker store");
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response r = response) {
                    if (!r.isSuccessful() || r.body() == null) {
                        postError(callback, "Sticker store returned an error (" + r.code() + ")");
                        return;
                    }
                    String body = r.body().string();
                    ArrayList<StickerPackInfo> packs = parsePacks(body);
                    postSuccess(callback, packs);
                } catch (Exception e) {
                    FileLog.e("StickerStoreApi: parse error", e);
                    postError(callback, "Couldn't read the sticker store response");
                }
            }
        });
    }

    /** Upserts this user's rating: PUT /api/v1/stickers/{pack_id}/rating?account_id=<id>
     *  with {"rating": 1..5}. The server keeps one rating per (account, pack), recalculates
     *  the pack's average + count and replies with {"rating": {"avg", "count"}, ...}. */
    public static void submitRating(String packId, long accountId, int stars, ResultCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("rating", stars);
            send("PUT", "api/v1/stickers/" + URLEncoder.encode(packId, "UTF-8") + "/rating?account_id=" + accountId, body, callback);
        } catch (Exception e) {
            postResultError(callback, "Couldn't build the rating request");
        }
    }

    /** Like: POST /api/v1/stickers/{pack_id}/like?account_id=<id>; un-like: DELETE on the
     *  same URL. "Already liked" / "have not liked" (HTTP 400) just mean the server is
     *  already in the state we asked for, so they count as success. */
    public static void setLike(String packId, long accountId, boolean liked, ResultCallback callback) {
        try {
            String path = "api/v1/stickers/" + URLEncoder.encode(packId, "UTF-8") + "/like?account_id=" + accountId;
            send(liked ? "POST" : "DELETE", path, null, new ResultCallback() {
                @Override
                public void onSuccess(JSONObject response) {
                    if (callback != null) callback.onSuccess(response);
                }

                @Override
                public void onError(String message) {
                    if (message != null && (message.contains("Already liked") || message.contains("not liked"))) {
                        if (callback != null) callback.onSuccess(null);
                    } else if (callback != null) {
                        callback.onError(message);
                    }
                }
            });
        } catch (Exception e) {
            postResultError(callback, "Couldn't build the like request");
        }
    }

    /** Tells the backend this user's own Telegram profile, so the packs they own show their real
     *  name / username / avatar even when Telegram's API can't look the account up by id.
     *  PUT /api/v1/stickers/owners/{telegram_id}. Null fields clear the stored value. */
    public static void syncOwnerProfile(long telegramId, String displayName, String username, String avatarUrl, String avatarFileId, ResultCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("display_name", displayName == null ? JSONObject.NULL : displayName);
            body.put("username", username == null ? JSONObject.NULL : username);
            body.put("avatar_url", avatarUrl == null ? JSONObject.NULL : avatarUrl);
            body.put("avatar_file_id", avatarFileId == null ? JSONObject.NULL : avatarFileId);
            send("PUT", "api/v1/stickers/owners/" + telegramId, body, callback);
        } catch (Exception e) {
            postResultError(callback, "Couldn't build the profile request");
        }
    }

    /** Adds a Telegram sticker pack to the store as owned by this account:
     *  POST /api/v1/stickers/?account_id=<id> with the pack's short_name and the owner's
     *  profile (so their name/avatar show right away). 409 means it is already in the store. */
    public static void submitPack(String shortName, long accountId, String displayName, String username, String avatarUrl, String avatarFileId, ResultCallback callback) {
        try {
            JSONObject owner = new JSONObject();
            owner.put("display_name", displayName == null ? JSONObject.NULL : displayName);
            owner.put("username", username == null ? JSONObject.NULL : username);
            owner.put("avatar_url", avatarUrl == null ? JSONObject.NULL : avatarUrl);
            owner.put("avatar_file_id", avatarFileId == null ? JSONObject.NULL : avatarFileId);
            JSONObject body = new JSONObject();
            body.put("short_name", shortName);
            body.put("category", "Custom");
            body.put("owner", owner);
            send("POST", "api/v1/stickers/?account_id=" + accountId, body, callback);
        } catch (Exception e) {
            postResultError(callback, "Couldn't build the upload request");
        }
    }

    static void send(String method, String path, JSONObject body, ResultCallback callback) {
        if (!isConfigured()) {
            postResultError(callback, "Sticker store is not configured");
            return;
        }
        String base = getBaseUrl();
        String url = base.endsWith("/") ? base + path : base + "/" + path;
        RequestBody requestBody = RequestBody.create(body != null ? body.toString() : "", MediaType.get("application/json"));
        Request.Builder builder = new Request.Builder().url(url);
        if ("PUT".equals(method)) {
            builder.put(requestBody);
        } else if ("DELETE".equals(method)) {
            builder.delete();
        } else {
            builder.post(requestBody);
        }
        getClient().newCall(builder.build()).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                FileLog.e("StickerStoreApi: " + method + " " + path + " network error", e);
                postResultError(callback, "Couldn't reach the sticker store");
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response r = response) {
                    String text = r.body() != null ? r.body().string() : "";
                    if (!r.isSuccessful()) {
                        String snippet = text.length() > 160 ? text.substring(0, 160) : text;
                        postResultError(callback, "Server said " + r.code() + (snippet.isEmpty() ? "" : ": " + snippet));
                        return;
                    }
                    JSONObject parsed = null;
                    try {
                        Object root = new JSONTokener(text).nextValue();
                        if (root instanceof JSONObject) {
                            parsed = (JSONObject) root;
                        }
                    } catch (Exception ignored) {
                    }
                    final JSONObject result = parsed;
                    org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                        if (callback != null) {
                            callback.onSuccess(result);
                        }
                    });
                } catch (Exception e) {
                    FileLog.e("StickerStoreApi: " + method + " " + path + " read error", e);
                    postResultError(callback, "Couldn't read the server's reply");
                }
            }
        });
    }

    interface JsonCallback {
        /** root is a JSONObject or JSONArray. */
        void onSuccess(Object root);

        void onError(String message);
    }

    /** GET path (relative to the API base) and hands back the parsed JSON on the UI thread. */
    static void getJson(String path, JsonCallback callback) {
        if (!isConfigured()) {
            org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> callback.onError("Store is not configured"));
            return;
        }
        String base = getBaseUrl();
        String url = base.endsWith("/") ? base + path : base + "/" + path;
        getClient().newCall(new Request.Builder().url(url).get().build()).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> callback.onError("Couldn't reach the store"));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response r = response) {
                    String text = r.body() != null ? r.body().string() : "";
                    if (!r.isSuccessful()) {
                        String snippet = text.length() > 160 ? text.substring(0, 160) : text;
                        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> callback.onError("Server said " + r.code() + (snippet.isEmpty() ? "" : ": " + snippet)));
                        return;
                    }
                    final Object root = new JSONTokener(text).nextValue();
                    org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> callback.onSuccess(root));
                } catch (Exception e) {
                    FileLog.e("StickerStoreApi: getJson read error", e);
                    org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> callback.onError("Couldn't read the server's reply"));
                }
            }
        });
    }

    private static void postResultError(ResultCallback callback, String message) {
        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
            if (callback != null) {
                callback.onError(message);
            }
        });
    }

    private static ArrayList<StickerPackInfo> parsePacks(String body) throws Exception {
        ArrayList<StickerPackInfo> result = new ArrayList<>();
        Object root = new JSONTokener(body).nextValue();
        JSONArray array = null;
        if (root instanceof JSONArray) {
            array = (JSONArray) root;
        } else if (root instanceof JSONObject) {
            JSONObject obj = (JSONObject) root;
            for (String key : new String[]{"items", "results", "packs", "data", "stickers"}) {
                if (obj.has(key) && obj.get(key) instanceof JSONArray) {
                    array = obj.getJSONArray(key);
                    break;
                }
            }
        }
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                Object item = array.get(i);
                if (item instanceof JSONObject) {
                    result.add(StickerPackInfo.fromJson((JSONObject) item));
                } else if (item instanceof String) {
                    // Bare short_name string with no metadata - the display name is
                    // still resolved from Telegram itself via StickerSetResolver, same
                    // as the object case, so there's nothing else to set here.
                    StickerPackInfo info = new StickerPackInfo();
                    info.shortName = (String) item;
                    result.add(info);
                }
            }
        }
        return result;
    }

    private static void postSuccess(PacksCallback callback, ArrayList<StickerPackInfo> packs) {
        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
            if (callback != null) {
                callback.onSuccess(packs);
            }
        });
    }

    private static void postError(PacksCallback callback, String message) {
        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
            if (callback != null) {
                callback.onError(message);
            }
        });
    }
}
