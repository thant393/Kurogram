package tw.nekomimi.nekogram.stickerstore;

import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

/**
 * Reports the signed-in user's own Telegram profile (name, username, avatar id) to the sticker
 * backend, so packs they own show real owner details. Telegram's API can't look up an arbitrary
 * account by id, but the owner's own app always knows who they are. Runs when the Kuro store
 * opens and only talks to the server when something changed since the last successful report.
 */
public final class OwnerProfileSync {

    private OwnerProfileSync() {
    }

    /** The signed-in user's profile as the backend stores it. */
    public static final class Profile {
        public long id;
        public String name, username, avatarUrl, avatarFileId;
    }

    /** The account's own global profile name (first + last as set in Telegram) - not a
     *  contact-list nickname, which only exists for accounts that saved someone as a contact. */
    private static String globalName(TLRPC.User user) {
        String first = user.first_name != null ? user.first_name.trim() : "";
        String last = user.last_name != null ? user.last_name.trim() : "";
        return (first + " " + last).trim();
    }

    public static Profile currentProfile(int account) {
        TLRPC.User me = UserConfig.getInstance(account).getCurrentUser();
        if (me == null || me.id == 0) {
            return null;
        }
        Profile p = new Profile();
        p.id = me.id;
        p.name = globalName(me);
        p.username = UserObject.getPublicUsername(me);
        p.avatarFileId = me.photo != null && me.photo.photo_id != 0 ? String.valueOf(me.photo.photo_id) : null;
        p.avatarUrl = !TextUtils.isEmpty(p.username) && p.avatarFileId != null ? "https://t.me/i/userpic/320/" + p.username + ".jpg" : null;
        if (TextUtils.isEmpty(p.name)) p.name = null;
        if (TextUtils.isEmpty(p.username)) p.username = null;
        return p;
    }

    public static void sync(int account) {
        if (!StickerStoreApi.isConfigured()) {
            return;
        }
        final TLRPC.User me = UserConfig.getInstance(account).getCurrentUser();
        if (me == null || me.id == 0) {
            return;
        }
        String name = globalName(me);
        String username = UserObject.getPublicUsername(me);
        String photoId = me.photo != null && me.photo.photo_id != 0 ? String.valueOf(me.photo.photo_id) : null;
        // t.me serves public profile pictures at this URL for accounts with a public username.
        String avatarUrl = !TextUtils.isEmpty(username) && photoId != null ? "https://t.me/i/userpic/320/" + username + ".jpg" : null;
        if (TextUtils.isEmpty(name)) {
            name = null;
        }
        if (TextUtils.isEmpty(username)) {
            username = null;
        }

        final String fingerprint = name + "|" + username + "|" + photoId;
        if (fingerprint.equals(StickerUserState.getSyncedProfile(me.id))) {
            return;
        }
        StickerStoreApi.syncOwnerProfile(me.id, name, username, avatarUrl, photoId, new StickerStoreApi.ResultCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                StickerUserState.setSyncedProfile(me.id, fingerprint);
            }

            @Override
            public void onError(String message) {
                // Not remembered as synced, so the next time the store opens it tries again.
            }
        });
    }
}
