package tw.kurocore.kurogram.skin;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

/**
 * Crash-loop guard for skins. A skin that makes the app crash while it starts would lock the person out, with no
 * screen to switch it off. Each start is marked "unfinished" and cleared once the app has run for a while; if the
 * previous three starts never finished, the saved skin is dropped before anything reads it, and the app opens with
 * the default look.
 */
public final class SkinSafeMode {
    private static final String PREFS = "kuro_skin_guard";
    private static final long HEALTHY_AFTER_MS = 20_000;
    private static final int MAX_FAILED_STARTS = 3;

    private SkinSafeMode() {}

    public static void onAppStart(Context context) {
        try {
            final SharedPreferences guard = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            int failed = guard.getInt("unfinished_starts", 0);
            if (failed >= MAX_FAILED_STARTS) {
                // the skin is the only thing this guard can blame, so drop it and start clean
                context.getSharedPreferences("kuro_skin", Context.MODE_PRIVATE).edit().remove("active_json").apply();
                failed = 0;
            }
            guard.edit().putInt("unfinished_starts", failed + 1).apply();
            new Handler(Looper.getMainLooper()).postDelayed(
                    () -> guard.edit().putInt("unfinished_starts", 0).apply(), HEALTHY_AFTER_MS);
        } catch (Throwable ignore) {
            // the guard must never be the reason the app fails to start
        }
    }
}
