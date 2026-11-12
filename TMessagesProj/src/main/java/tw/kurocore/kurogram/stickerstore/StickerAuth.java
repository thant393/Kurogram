package tw.kurocore.kurogram.stickerstore;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * One-time sign-in to the store: the person presses Start in the project's Telegram bot, the server learns their
 * real Telegram id from Telegram, and this app keeps the signed token it hands back. Every store request then carries
 * that token (see the interceptor in StickerStoreApi), so nobody can act as somebody else.
 */
public final class StickerAuth {
    private static final String PREFS = "kuro_store_auth";
    private static boolean signingIn;

    private StickerAuth() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String token() {
        return prefs().getString("token", null);
    }

    public static void clear() {
        prefs().edit().remove("token").apply();
    }

    private static String url(String path) {
        String base = StickerStoreApi.getBaseUrl();
        return (base.endsWith("/") ? base : base + "/") + path;
    }

    /** Asks the person to sign in (once); safe to call from any thread, ignores repeats while one is running. */
    public static void promptSignIn() {
        AndroidUtilities.runOnUIThread(() -> {
            if (signingIn) return;
            BaseFragment f = LaunchActivity.getSafeLastFragment();
            Activity act = f != null ? f.getParentActivity() : null;
            if (act == null) return;
            AlertDialog.Builder b = new AlertDialog.Builder(act);
            b.setTitle("Sign in");
            b.setMessage("To like, rate, report or publish, confirm your Telegram account once. A bot chat will open - press Start, then come back.");
            b.setPositiveButton("Open bot", (d, w) -> start(act));
            b.setNegativeButton("Cancel", null);
            f.showDialog(b.create());
        });
    }

    private static void start(Activity act) {
        signingIn = true;
        new Thread(() -> {
            try {
                OkHttpClient plain = new OkHttpClient();
                RequestBody empty = RequestBody.create("", MediaType.get("application/json"));
                String code, link;
                try (Response r = plain.newCall(new Request.Builder().url(url("api/v1/auth/start")).post(empty).build()).execute()) {
                    if (!r.isSuccessful()) throw new java.io.IOException("start " + r.code());
                    JSONObject o = new JSONObject(r.body().string());
                    code = o.getString("code");
                    link = o.getString("link");
                }
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                });
                long end = System.currentTimeMillis() + 10 * 60 * 1000L;
                while (System.currentTimeMillis() < end) {
                    Thread.sleep(2000);
                    try (Response r = plain.newCall(new Request.Builder().url(url("api/v1/auth/poll?code=" + code)).get().build()).execute()) {
                        if (r.code() == 404) break;
                        if (!r.isSuccessful()) continue;
                        JSONObject o = new JSONObject(r.body().string());
                        if ("ok".equals(o.optString("status"))) {
                            prefs().edit().putString("token", o.getString("token")).apply();
                            AndroidUtilities.runOnUIThread(() -> android.widget.Toast.makeText(ApplicationLoader.applicationContext, "Signed in. Try again.", android.widget.Toast.LENGTH_LONG).show());
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e("StickerAuth: sign-in failed", e);
                AndroidUtilities.runOnUIThread(() -> android.widget.Toast.makeText(ApplicationLoader.applicationContext, "Sign-in failed. Try again later.", android.widget.Toast.LENGTH_LONG).show());
            } finally {
                signingIn = false;
            }
        }, "StickerAuth").start();
    }
}
