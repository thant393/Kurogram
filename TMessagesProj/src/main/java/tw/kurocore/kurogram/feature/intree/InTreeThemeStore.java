package tw.kurocore.kurogram.feature.intree;

import org.json.JSONObject;

import tw.kurocore.kurogram.feature.ThemeStore;
import tw.kurocore.kurogram.stickerstore.OwnerProfileSync;
import tw.kurocore.kurogram.stickerstore.StickerStoreApi;
import tw.kurocore.kurogram.stickerstore.ThemeStoreApi;

/**
 * Temporary: the current in-tree code behind the {@link ThemeStore} interface. It exists so the screens can be
 * switched to the interface with no behaviour change (modularisation step 1). It is deleted in step 5, when the
 * real implementation lives in the private module.
 */
public final class InTreeThemeStore implements ThemeStore {

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void fetchThemes(String sort, int page, int limit, String ownerId, String query, String category, ListCallback callback) {
        ThemeStoreApi.fetchThemes(sort, page, limit, ownerId, query, category, callback);
    }

    @Override
    public void fetchCategories(CategoriesCallback callback) {
        ThemeStoreApi.fetchCategories(callback);
    }

    @Override
    public void fetchVersionContent(String id, int version, ContentCallback callback) {
        ThemeStoreApi.fetchVersionContent(id, version, callback);
    }

    @Override
    public void download(String themeId, long accountId, StickerStoreApi.ResultCallback callback) {
        ThemeStoreApi.download(themeId, accountId, callback);
    }

    @Override
    public void submitTheme(long accountId, String title, String description, String category, String type,
                            String content, OwnerProfileSync.Profile owner, StickerStoreApi.ResultCallback callback) {
        ThemeStoreApi.submitTheme(accountId, title, description, category, type, content, owner, callback);
    }

    @Override
    public void setLike(String themeId, long accountId, boolean liked, StickerStoreApi.ResultCallback callback) {
        ThemeStoreApi.setLike(themeId, accountId, liked, callback);
    }

    @Override
    public void rate(String themeId, long accountId, int stars, StickerStoreApi.ResultCallback callback) {
        ThemeStoreApi.rate(themeId, accountId, stars, callback);
    }

    @Override
    public void report(String themeId, long accountId, String reasonKey, StickerStoreApi.ResultCallback callback) {
        ThemeStoreApi.report(themeId, accountId, reasonKey, callback);
    }

    @Override
    public boolean isLiked(String id) {
        return ThemeStoreApi.isLiked(id);
    }

    @Override
    public void setLikedLocal(String id, boolean liked) {
        ThemeStoreApi.setLikedLocal(id, liked);
    }

    @Override
    public int myRating(String id) {
        return ThemeStoreApi.myRating(id);
    }

    @Override
    public void setMyRating(String id, int stars) {
        ThemeStoreApi.setMyRating(id, stars);
    }

    @Override
    public void cacheContent(String id, int version, String type, String sha256, String content) {
        ThemeStoreApi.cacheContent(id, version, type, sha256, content);
    }

    @Override
    public JSONObject cachedDownload(String id, int minVersion) {
        return ThemeStoreApi.cachedDownload(id, minVersion);
    }

    @Override
    public String appliedId() {
        return ThemeStoreApi.appliedId();
    }

    @Override
    public int appliedVersion() {
        return ThemeStoreApi.appliedVersion();
    }

    @Override
    public void setApplied(String id, int version) {
        ThemeStoreApi.setApplied(id, version);
    }

    @Override
    public void clearApplied() {
        ThemeStoreApi.clearApplied();
    }

    @Override
    public void refreshApplied() {
        ThemeStoreApi.refreshApplied();
    }
}
