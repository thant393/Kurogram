package tw.kurocore.kurogram.helpers;

import android.os.Process;
import android.os.SystemClock;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;

/**
 * Temporary cold-start measurement: records when each startup stage happens and shows one summary toast when the chat
 * list has been drawn, so slow stages can be found on a real phone without a debugger. Switch ENABLED off once done.
 */
public final class StartupTrace {
    public static final boolean ENABLED = true;

    private static final long[] at = new long[8];
    private static final String[] names = {
            "process start -> Application.onCreate", "Application.onCreate", "-> LaunchActivity.onCreate",
            "postInitApplication", "rest of LaunchActivity.onCreate", "-> chat list resumed", "-> chat list drawn"};
    private static boolean shown;

    private StartupTrace() {}

    /** stage 0 = Application.onCreate start ... 6 = chat list drawn; only the first call per stage counts. */
    public static void mark(int stage) {
        if (!ENABLED || stage < 0 || stage >= at.length || at[stage] != 0) {
            return;
        }
        at[stage] = SystemClock.elapsedRealtime();
    }

    public static void chatListResumed() {
        if (!ENABLED || shown || at[5] != 0) {
            return;
        }
        mark(5);
        AndroidUtilities.runOnUIThread(() -> {
            mark(6);
            show();
        }, 0);
    }

    private static int loggedInAccounts() {
        int n = 0;
        for (int a = 0; a < org.telegram.messenger.UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (org.telegram.messenger.UserConfig.getInstance(a).isClientActivated()) {
                n++;
            }
        }
        return n;
    }

    private static void show() {
        if (shown) {
            return;
        }
        shown = true;
        long processStart = Process.getStartElapsedRealtime();
        // at[0]=Application.onCreate start, at[1]=its end, at[2]=LaunchActivity.onCreate start, at[3]=postInit start,
        // at[4]=postInit end, at[7]=LaunchActivity.onCreate end
        long[] d = {
                at[0] - processStart, at[1] - at[0], at[2] - at[1], at[4] - at[3], at[7] - at[4], at[5] - at[7], at[6] - at[5]};
        StringBuilder sb = new StringBuilder("Startup ").append(at[6] - processStart).append(" ms\n");
        for (int i = 0; i < d.length; i++) {
            sb.append(names[i]).append(": ").append(d[i]).append('\n');
        }
        String text = sb.toString().trim();
        // A toast cuts the text after two lines, so the breakdown is shown in a dialog the person can screenshot at leisure
        org.telegram.ui.LaunchActivity act = org.telegram.ui.LaunchActivity.instance;
        if (act != null && !act.isFinishing()) {
            try {
                org.telegram.ui.ActionBar.AlertDialog.Builder b = new org.telegram.ui.ActionBar.AlertDialog.Builder(act);
                b.setTitle("Startup timing");
                b.setMessage(text + "\naccounts logged in: " + loggedInAccounts());
                b.setPositiveButton("OK", null);
                b.show();
                return;
            } catch (Throwable ignore) {
                // fall back to the toast below
            }
        }
        Toast.makeText(ApplicationLoader.applicationContext, text, Toast.LENGTH_LONG).show();
    }
}
