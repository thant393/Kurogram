package tw.kurocore.kurogram.feature.stub;

import org.json.JSONObject;

import tw.kurocore.kurogram.feature.KuroFeatures;
import tw.kurocore.kurogram.feature.ThemeStore;
import tw.kurocore.kurogram.stickerstore.OwnerProfileSync;
import tw.kurocore.kurogram.stickerstore.StickerStoreApi;

/** Safe no-op Theme Store for builds without the private module: nothing is fetched, nothing is stored, nothing crashes. */
public final class ThemeStoreStub implements ThemeStore {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void fetchThemes(String sort, int page, int limit, String ownerId, String query, String category, ListCallback callback) {
        callback.onError(KuroFeatures.unavailableMessage());
    }

    @Override
    public void fetchCategories(CategoriesCallback callback) {
        callback.onSuccess(new java.util.ArrayList<>());
    }

    @Override
    public void fetchVersionContent(String id, int version, ContentCallback callback) {
        callback.onContent(null);
    }

    @Override
    public void download(String themeId, long accountId, StickerStoreApi.ResultCallback callback) {
        callback.onError(KuroFeatures.unavailableMessage());
    }

    @Override
    public void submitTheme(long accountId, String title, String description, String category, String type,
                            String content, OwnerProfileSync.Profile owner, StickerStoreApi.ResultCallback callback) {
        callback.onError(KuroFeatures.unavailableMessage());
    }

    @Override
    public void setLike(String themeId, long accountId, boolean liked, StickerStoreApi.ResultCallback callback) {
        callback.onError(KuroFeatures.unavailableMessage());
    }

    @Override
    public void rate(String themeId, long accountId, int stars, StickerStoreApi.ResultCallback callback) {
        callback.onError(KuroFeatures.unavailableMessage());
    }

    @Override
    public void report(String themeId, long accountId, String reasonKey, StickerStoreApi.ResultCallback callback) {
        callback.onError(KuroFeatures.unavailableMessage());
    }

    @Override
    public boolean isLiked(String id) {
        return false;
    }

    @Override
    public void setLikedLocal(String id, boolean liked) {
    }

    @Override
    public int myRating(String id) {
        return 0;
    }

    @Override
    public void setMyRating(String id, int stars) {
    }

    @Override
    public void cacheContent(String id, int version, String type, String sha256, String content) {
    }

    @Override
    public JSONObject cachedDownload(String id, int minVersion) {
        return null;
    }

    @Override
    public String appliedId() {
        return null;
    }

    @Override
    public int appliedVersion() {
        return 0;
    }

    @Override
    public void setApplied(String id, int version) {
    }

    @Override
    public void clearApplied() {
    }

    @Override
    public void refreshApplied() {
    }
}
