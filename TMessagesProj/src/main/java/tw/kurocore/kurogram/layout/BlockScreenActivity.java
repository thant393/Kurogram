package tw.kurocore.kurogram.layout;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import tw.kurocore.kurogram.skin.KuroUi;

/**
 * One generic screen that draws whatever blocks a layout file lists for a screen id (BlockSpec), so a theme can
 * add screens like People without an app update. It reads only the ready-made blocks and data sources; it never
 * runs anything from the file. Used for a tab when the layout defines {@code screens.<id>}.
 */
public class BlockScreenActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private final String screenId;
    private BlockSpec.Screen screen;
    private LinearLayout content;
    private String chipFilter = null;

    public BlockScreenActivity(Bundle args) {
        super(args);
        screenId = args == null ? "" : args.getString("screen_id", "");
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.contactsDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.contactsDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        screen = LayoutManager.get().spec().screen(screenId);
        final boolean asTab = arguments != null && arguments.getBoolean("hasMainTabs", false);
        if (!asTab) {
            actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        }
        actionBar.setTitle(screen != null ? screen.title : screenId);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        FrameLayout root = new FrameLayout(context);
        fragmentView = root;
        root.setBackgroundColor(KuroUi.screenBackground("people".equals(screenId) ? "onlineContacts" : screenId, Theme.getColor(Theme.key_windowBackgroundWhite)));
        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.topMargin = ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, asTab ? AndroidUtilities.dp(org.telegram.ui.DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 8) : 0);
        root.addView(scroll, lp);
        rebuild(context);
        return fragmentView;
    }

    private void rebuild(Context context) {
        if (content == null) {
            return;
        }
        content.removeAllViews();
        if (screen == null) {
            return;
        }
        for (BlockSpec.Block b : screen.blocks) {
            switch (b.type) {
                case "storyRail":
                    addRail(context, b);
                    break;
                case "chips":
                    addChips(context, b);
                    break;
                case "note":
                    addText(context, b.str("text", ""), 13, Theme.key_windowBackgroundWhiteGrayText, false);
                    break;
                case "section":
                    addText(context, b.str("title", ""), 14, Theme.key_windowBackgroundWhiteBlueHeader, true);
                    break;
                case "spacer":
                    content.addView(new View(context), new LinearLayout.LayoutParams(1, AndroidUtilities.dp(b.intValue("heightDp", 8))));
                    break;
                case "list":
                    addList(context, b);
                    break;
                default:
                    break; // header sets the title only
            }
        }
    }

    private void addText(Context context, String text, int sp, int colorKey, boolean bold) {
        TextView t = new TextView(context);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(Theme.getColor(colorKey));
        t.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(4));
        if (bold) {
            t.setTypeface(AndroidUtilities.bold());
        }
        content.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addChips(Context context, BlockSpec.Block b) {
        HorizontalScrollView hs = new HorizontalScrollView(context);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(context);
        row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
        List<String> items = b.list("items", java.util.Arrays.asList("all"));
        if (chipFilter == null || !items.contains(chipFilter)) {
            chipFilter = items.get(0);
        }
        for (final String id : items) {
            TextView chip = new TextView(context);
            boolean on = id.equals(chipFilter);
            chip.setText("online".equals(id) ? "Online" : "contacts".equals(id) ? "Contacts" : "All");
            chip.setTextSize(14);
            chip.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(7), AndroidUtilities.dp(14), AndroidUtilities.dp(7));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(AndroidUtilities.dp(16));
            int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
            bg.setColor(on ? (accent & 0x00FFFFFF) | 0x26000000 : Theme.getColor(Theme.key_windowBackgroundGray));
            chip.setBackground(bg);
            chip.setTextColor(on ? accent : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            chip.setOnClickListener(v -> {
                chipFilter = id;
                rebuild(context);
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.rightMargin = AndroidUtilities.dp(8);
            row.addView(chip, cp);
        }
        hs.addView(row);
        content.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private boolean isOnline(TLRPC.User user) {
        return user != null && user.status instanceof TLRPC.TL_userStatusOnline
                && (user.status.expires == 0 || user.status.expires > ConnectionsManager.getInstance(currentAccount).getCurrentTime());
    }

    private List<TLRPC.User> contacts(boolean onlyOnline) {
        List<TLRPC.User> out = new ArrayList<>();
        for (TLRPC.TL_contact c : ContactsController.getInstance(currentAccount).contacts) {
            TLRPC.User u = MessagesController.getInstance(currentAccount).getUser(c.user_id);
            if (u == null || UserObject.isDeleted(u) || UserObject.isUserSelf(u) || (onlyOnline && !isOnline(u))) {
                continue;
            }
            out.add(u);
        }
        Collections.sort(out, (a, b) -> UserObject.getUserName(a).compareToIgnoreCase(UserObject.getUserName(b)));
        return out;
    }

    private void addRail(Context context, BlockSpec.Block b) {
        List<TLRPC.User> users = contacts("online".equals(b.str("source", "contacts")));
        if (users.isEmpty()) {
            return;
        }
        HorizontalScrollView hs = new HorizontalScrollView(context);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(context);
        row.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(8), AndroidUtilities.dp(10), AndroidUtilities.dp(8));
        boolean cards = !"circles".equals(b.str("style", "cards"));
        for (int i = 0; i < users.size() && i < 20; i++) {
            View cell = person(context, users.get(i), true);
            if (cards) {
                // a rounded card holding the avatar and the name, like the story cards of the real People screen
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(AndroidUtilities.dp(12));
                bg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
                cell.setBackground(bg);
                cell.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(10), AndroidUtilities.dp(4), AndroidUtilities.dp(8));
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(AndroidUtilities.dp(76), AndroidUtilities.dp(100));
                cp.rightMargin = AndroidUtilities.dp(6);
                row.addView(cell, cp);
            } else {
                row.addView(cell, new LinearLayout.LayoutParams(AndroidUtilities.dp(68), ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }
        hs.addView(row);
        content.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private View person(Context context, TLRPC.User user, boolean vertical) {
        LinearLayout cell = new LinearLayout(context);
        cell.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        cell.setGravity(vertical ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
        int size = AndroidUtilities.dp(vertical ? 52 : 44);
        BackupImageView avatar = new BackupImageView(context);
        avatar.setRoundRadius(size / 2);
        AvatarDrawable d = new AvatarDrawable();
        d.setInfo(currentAccount, user);
        avatar.setForUserOrChat(user, d);
        cell.addView(avatar, new LinearLayout.LayoutParams(size, size));
        TextView name = new TextView(context);
        name.setText(UserObject.getUserName(user));
        name.setSingleLine();
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        if (vertical) {
            name.setTextSize(12);
            name.setGravity(Gravity.CENTER);
            cell.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);
            name.setTextSize(16);
            col.addView(name);
            TextView status = new TextView(context);
            status.setTextSize(13);
            status.setSingleLine();
            status.setTextColor(Theme.getColor(isOnline(user) ? Theme.key_windowBackgroundWhiteBlueText : Theme.key_windowBackgroundWhiteGrayText));
            status.setText(LocaleController.formatUserStatus(currentAccount, user));
            col.addView(status);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cp.leftMargin = AndroidUtilities.dp(14);
            cell.addView(col, cp);
        }
        cell.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putLong("user_id", user.id);
            presentFragment(new ChatActivity(args));
        });
        return cell;
    }

    private void addList(Context context, BlockSpec.Block b) {
        String source = b.str("source", "contacts");
        int limit = b.intValue("limit", 50);
        List<TLRPC.User> users = new ArrayList<>();
        if ("contacts".equals(source) || "online".equals(source)) {
            users = contacts("online".equals(source) || "online".equals(chipFilter));
        } else {
            // chats / unread: the people you talk to, newest first
            for (org.telegram.tgnet.TLRPC.Dialog dlg : MessagesController.getInstance(currentAccount).getAllDialogs()) {
                if (dlg.id <= 0 || ("unread".equals(source) && dlg.unread_count == 0 && !dlg.unread_mark)) {
                    continue;
                }
                TLRPC.User u = MessagesController.getInstance(currentAccount).getUser(dlg.id);
                if (u != null && !UserObject.isUserSelf(u) && (!"online".equals(chipFilter) || isOnline(u))) {
                    users.add(u);
                }
            }
        }
        boolean card = "card".equals(b.str("row", "compact"));
        for (int i = 0; i < users.size() && i < limit; i++) {
            View row = person(context, users.get(i), false);
            row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(card ? 12 : 8), AndroidUtilities.dp(16), AndroidUtilities.dp(card ? 12 : 8));
            content.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (users.isEmpty()) {
            addText(context, "Nobody to show yet.", 14, Theme.key_windowBackgroundWhiteGrayText, false);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (fragmentView == null) {
            return;
        }
        if (id == NotificationCenter.contactsDidLoad) {
            rebuild(fragmentView.getContext());
        } else if (id == NotificationCenter.updateInterfaces && args.length > 0 && args[0] instanceof Integer
                && ((Integer) args[0] & MessagesController.UPDATE_MASK_STATUS) != 0) {
            rebuild(fragmentView.getContext());
        }
    }
}
