package tw.kurocore.kurogram.stickerstore;

import org.json.JSONObject;

import tw.kurocore.kurogram.feature.KuroFeatures;

/**
 * Publishes (or updates) a theme made in the Theme Builder. There is no form: the name and description come from the theme
 * itself (meta.name / meta.description), the category stays what it was ("Custom" for a new theme).
 */
public final class ThemePublisher {
    public interface Callback {
        /** {@code warning} is null on full success; otherwise the theme is saved but its styles were not. */
        void done(String warning);

        void failed(String message);
    }

    private ThemePublisher() {
    }

    public static String nameOf(String themeJson) {
        return metaText(themeJson, "name");
    }

    private static String metaText(String themeJson, String key) {
        try {
            JSONObject meta = new JSONObject(themeJson).optJSONObject("meta");
            return meta == null ? "" : meta.optString(key, "").trim();
        } catch (Exception e) {
            return "";
        }
    }

    public static void publish(int account, ThemeInfo editing, String themeJson, String stylesJson, Callback cb) {
        final OwnerProfileSync.Profile me = OwnerProfileSync.currentProfile(account);
        if (me == null) {
            cb.failed("You need to be signed in.");
            return;
        }
        final String title = nameOf(themeJson);
        if (title.isEmpty()) {
            cb.failed("Give the theme a name.");
            return;
        }
        final String description = metaText(themeJson, "description");
        StickerStoreApi.ResultCallback done = new StickerStoreApi.ResultCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (stylesJson == null) {
                    ProfileTabView.notifyPackUploaded();
                    cb.done(null);
                    return;
                }
                String id = editing != null ? editing.id : response == null ? "" : response.optString("id", "");
                KuroFeatures.themes().updateThemeStyles(id, me.id, stylesJson, new StickerStoreApi.ResultCallback() {
                    @Override
                    public void onSuccess(JSONObject r) {
                        ProfileTabView.notifyPackUploaded();
                        cb.done(null);
                    }

                    @Override
                    public void onError(String message) {
                        // the theme itself is already in the store: say so, so nobody uploads it twice
                        ProfileTabView.notifyPackUploaded();
                        cb.done(message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                cb.failed(message);
            }
        };
        if (editing == null) {
            KuroFeatures.themes().submitTheme(me.id, title, description, "Custom", "skin", themeJson, me, done);
        } else {
            KuroFeatures.themes().updateTheme(editing.id, me.id, title, description.isEmpty() ? null : description, null, themeJson, done);
        }
    }

    public static void remove(int account, ThemeInfo editing, Callback cb) {
        final OwnerProfileSync.Profile me = OwnerProfileSync.currentProfile(account);
        if (me == null) {
            cb.failed("You need to be signed in.");
            return;
        }
        KuroFeatures.themes().unpublishTheme(editing.id, me.id, new StickerStoreApi.ResultCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                ProfileTabView.notifyPackUploaded();
                cb.done(null);
            }

            @Override
            public void onError(String message) {
                cb.failed(message);
            }
        });
    }
}
