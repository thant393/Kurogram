package tw.kurocore.kurogram.feature;

import org.json.JSONObject;

import java.util.ArrayList;

import tw.kurocore.kurogram.stickerstore.OwnerProfileSync;
import tw.kurocore.kurogram.stickerstore.StickerStoreApi;
import tw.kurocore.kurogram.stickerstore.ThemeInfo;

/**
 * Kuro Themes: the theme store (browse, download, like, rate, report, submit and the locally remembered state).
 * The skin engine itself (KuroSkinManager, SkinSpec ...) is NOT part of this feature and stays in the core.
 * All callbacks are delivered on the UI thread.
 */
public interface ThemeStore extends KuroFeature {

    interface ListCallback {
        void onSuccess(ArrayList<ThemeInfo> themes);

        void onError(String message);
    }

    interface ContentCallback {
        /** The theme file as text, or null when it can't be read. */
        void onContent(String content);
    }

    interface CategoriesCallback {
        void onSuccess(ArrayList<String> names);
    }

    // ---- network ----

    void fetchThemes(String sort, int page, int limit, String ownerId, String query, String category, ListCallback callback);

    void fetchCategories(CategoriesCallback callback);

    /** The theme file of one version without counting a download. */
    void fetchVersionContent(String id, int version, ContentCallback callback);

    /** The theme file: {id, slug, type, version, sha256, content}. Counts as one download. */
    void download(String themeId, long accountId, StickerStoreApi.ResultCallback callback);

    void submitTheme(long accountId, String title, String description, String category, String type,
                     String content, OwnerProfileSync.Profile owner, StickerStoreApi.ResultCallback callback);

    void setLike(String themeId, long accountId, boolean liked, StickerStoreApi.ResultCallback callback);

    void rate(String themeId, long accountId, int stars, StickerStoreApi.ResultCallback callback);

    void report(String themeId, long accountId, String reasonKey, StickerStoreApi.ResultCallback callback);

    // ---- what this user already did to a theme (local, synchronous) ----

    boolean isLiked(String id);

    void setLikedLocal(String id, boolean liked);

    int myRating(String id);

    void setMyRating(String id, int stars);

    // ---- offline copy of downloaded themes ----

    void cacheContent(String id, int version, String type, String sha256, String content);

    /** The cached download for id (same shape as {@link #download}), or null if none newer than minVersion. */
    JSONObject cachedDownload(String id, int minVersion);

    // ---- which theme is applied ----

    String appliedId();

    int appliedVersion();

    void setApplied(String id, int version);

    void clearApplied();

    /** Once per app start: re-apply the applied theme if its published file changed. Silent on failure. */
    void refreshApplied();
}
