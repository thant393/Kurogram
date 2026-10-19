package tw.nekomimi.nekogram.stickerstore;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * What THIS user has already done to a pack (their own star rating, whether they liked it),
 * remembered on the device so the detail screen can show "you rated 4" / a filled heart
 * and so a re-rating is sent as a change, not a new rating. The backend is the source of
 * truth for the shared numbers (average, count, likes); this only remembers the user's own
 * choice.
 */
public class StickerUserState {

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("kuro_sticker_user_state", Context.MODE_PRIVATE);
    }

    public static int getMyRating(String shortName) {
        return prefs().getInt("rating_" + shortName, 0);
    }

    public static void setMyRating(String shortName, int stars) {
        prefs().edit().putInt("rating_" + shortName, stars).apply();
    }

    public static boolean isLiked(String shortName) {
        return prefs().getBoolean("liked_" + shortName, false);
    }

    public static void setLiked(String shortName, boolean liked) {
        prefs().edit().putBoolean("liked_" + shortName, liked).apply();
    }

    /** Fingerprint of the profile last reported to the backend (see OwnerProfileSync). */
    public static String getSyncedProfile(long userId) {
        return prefs().getString("synced_profile_" + userId, "");
    }

    public static void setSyncedProfile(long userId, String fingerprint) {
        prefs().edit().putString("synced_profile_" + userId, fingerprint).apply();
    }
}
