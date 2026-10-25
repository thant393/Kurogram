package tw.nekomimi.nekogram.layout;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.CallLogActivity;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ContactsActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.SettingsActivity;

import java.util.List;

/**
 * A modal start-side drawer opened by a layout pack. The pack only picks which of Telegram's own
 * destinations appear (drawer.items); every item opens an existing screen, nothing is created here.
 */
public final class KuroDrawer {

    private static final String[] DEFAULT_ITEMS = {"profile", "settings", "contacts", "saved", "archive"};

    private KuroDrawer() {
    }

    /** The items the active layout wants, or null when it does not ask for a drawer. */
    public static List<String> items() {
        return LayoutManager.get().spec().listValue("drawer.items", null);
    }

    public static boolean wantsMenuButton() {
        List<String> left = LayoutManager.get().spec().listValue("chatList.header.left", null);
        return left != null && left.contains("menu") && items() != null;
    }

    public static void show(final BaseFragment from) {
        if (from == null || from.getParentActivity() == null) return;
        List<String> items = items();
        if (items == null || items.isEmpty()) return;
        final Context context = from.getParentActivity();
        final int account = from.getCurrentAccount();

        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout root = new FrameLayout(context);
        root.setOnClickListener(v -> dialog.dismiss());

        final int width = Math.min(AndroidUtilities.dp(320), (int) (AndroidUtilities.displaySize.x * 0.86f));
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        panel.setClickable(true);
        panel.setFitsSystemWindows(false);
        root.addView(panel, new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));

        // Profile header
        TLRPC.User me = MessagesController.getInstance(account).getUser(UserConfig.getInstance(account).getClientUserId());
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(48), AndroidUtilities.dp(20), AndroidUtilities.dp(16));
        BackupImageView avatar = new BackupImageView(context);
        avatar.setRoundRadius(AndroidUtilities.dp(32));
        if (me != null) {
            avatar.setForUserOrChat(me, new AvatarDrawable(me));
        }
        header.addView(avatar, LayoutHelper.createLinear(64, 64));
        TextView name = new TextView(context);
        name.setTextSize(18);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        name.setSingleLine(true);
        name.setText(me == null ? "" : UserObject.getUserName(me));
        header.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));
        String un = me == null ? null : UserObject.getPublicUsername(me);
        if (un != null && !un.isEmpty()) {
            TextView user = new TextView(context);
            user.setTextSize(14);
            user.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            user.setText("@" + un);
            header.addView(user, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }
        panel.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(context);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        for (String id : items) {
            final int[] spec = describe(id);
            if (spec == null) continue;
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
            row.setMinimumHeight(AndroidUtilities.dp(52));
            row.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));
            ImageView icon = new ImageView(context);
            icon.setImageResource(spec[1]);
            icon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon));
            row.addView(icon, LayoutHelper.createLinear(24, 24));
            TextView label = new TextView(context);
            label.setTextSize(16);
            label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            label.setText(LocaleController.getString(spec[0]));
            row.addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 20, 0, 0, 0));
            row.setContentDescription(label.getText());
            row.setOnClickListener(v -> {
                dialog.dismiss();
                open(from, account, id);
            });
            list.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.45f);
        }
        panel.setTranslationX(-width);
        dialog.show();
        panel.animate().translationX(0).setDuration(200).start();
    }

    /** {label string id, icon id} for a known item, null for anything unsupported (ignored). */
    private static int[] describe(String id) {
        switch (id) {
            case "profile": return new int[]{R.string.MainTabsProfile, R.drawable.msg_openprofile};
            case "settings": return new int[]{R.string.Settings, R.drawable.msg_settings};
            case "contacts": return new int[]{R.string.Contacts, R.drawable.msg_contacts};
            case "calls": return new int[]{R.string.MainTabsCalls, R.drawable.msg_calls};
            case "saved": return new int[]{R.string.SavedMessages, R.drawable.msg_saved};
            case "archive": return new int[]{R.string.ArchivedChats, R.drawable.msg_archive};
            default: return null;
        }
    }

    private static void open(BaseFragment from, int account, String id) {
        long self = UserConfig.getInstance(account).getClientUserId();
        Bundle args = new Bundle();
        switch (id) {
            case "profile":
                args.putLong("user_id", self);
                args.putBoolean("my_profile", true);
                from.presentFragment(new ProfileActivity(args));
                break;
            case "settings":
                from.presentFragment(new SettingsActivity(args));
                break;
            case "contacts":
                args.putBoolean("needPhonebook", true);
                from.presentFragment(new ContactsActivity(args));
                break;
            case "calls":
                from.presentFragment(new CallLogActivity(args));
                break;
            case "saved":
                args.putLong("user_id", self);
                from.presentFragment(new ChatActivity(args));
                break;
            case "archive":
                args.putInt("folderId", 1);
                from.presentFragment(new DialogsActivity(args));
                break;
            default:
                break;
        }
    }
}
