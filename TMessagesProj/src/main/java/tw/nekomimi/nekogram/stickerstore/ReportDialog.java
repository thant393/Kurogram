package tw.nekomimi.nekogram.stickerstore;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/** Shared "Report" flow for packs and themes: pick a reason, then send it. Remembers what was reported. */
public final class ReportDialog {

    public interface OnReason {
        void onReason(String reasonKey);
    }

    private static final String[] KEYS = {"spam", "inappropriate", "copyright", "broken", "other"};
    private static final String[] LABELS = {"Spam", "Inappropriate content", "Copyright", "Broken or not working", "Something else"};

    private ReportDialog() {
    }

    public static void show(Context context, String title, OnReason onReason) {
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setItems(LABELS, (d, which) -> onReason.onReason(KEYS[which]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("kuro_reports", Context.MODE_PRIVATE);
    }

    public static boolean isReported(String kind, String id) {
        return prefs().getBoolean(kind + "_" + id, false);
    }

    public static void markReported(String kind, String id) {
        prefs().edit().putBoolean(kind + "_" + id, true).apply();
    }
}
