package tw.kurocore.kurogram.layout;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Draws the ready-made blocks of a layout screen (BlockSpec) into a container. Shared by the full-page block
 * screens (BlockScreenActivity) and the side drawer, so every screen a theme builds looks and behaves the same.
 */
public final class BlockRenderer {

    private final BaseFragment host;
    private final int account;
    private final LinearLayout content;
    private final Runnable beforeNavigate;
    private BlockSpec.Screen screen;
    private String chipFilter = null;

    /** beforeNavigate runs just before a tap opens another screen (the drawer uses it to close itself). */
    public BlockRenderer(BaseFragment host, int account, LinearLayout content, Runnable beforeNavigate) {
        this.host = host;
        this.account = account;
        this.content = content;
        this.beforeNavigate = beforeNavigate;
    }

    private void go(BaseFragment fragment) {
        if (beforeNavigate != null) {
            beforeNavigate.run();
        }
        host.presentFragment(fragment);
    }

    /** Draws (or redraws) the screen's blocks into the container. */
    public void render(Context context, BlockSpec.Screen screenToDraw) {
        this.screen = screenToDraw;
        rebuild(context);
    }

    private void rebuild(Context context) {
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
                case "chatList":
                    addChatList(context, b);
                    break;
                case "menu":
                    addMenu(context, b);
                    break;
                case "profileCard":
                    addProfileCard(context);
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
                && (user.status.expires == 0 || user.status.expires > ConnectionsManager.getInstance(account).getCurrentTime());
    }

    /** A sortable "how recently" for a user: online now is the newest, then the exact last-seen time, then the vague buckets. */
    private int lastSeen(TLRPC.User u) {
        if (u == null || u.status == null) {
            return 0;
        }
        if (u.status instanceof TLRPC.TL_userStatusOnline) {
            return Integer.MAX_VALUE;
        }
        if (u.status instanceof TLRPC.TL_userStatusOffline) {
            return u.status.expires;
        }
        int now = ConnectionsManager.getInstance(account).getCurrentTime();
        return u.status instanceof TLRPC.TL_userStatusRecently ? now - 60 : u.status instanceof TLRPC.TL_userStatusLastWeek ? now - 5 * 86400 : u.status instanceof TLRPC.TL_userStatusLastMonth ? now - 20 * 86400 : 0;
    }

    private List<TLRPC.User> contacts(boolean onlyOnline) {
        List<TLRPC.User> out = new ArrayList<>();
        for (TLRPC.TL_contact c : ContactsController.getInstance(account).contacts) {
            TLRPC.User u = MessagesController.getInstance(account).getUser(c.user_id);
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
        d.setInfo(account, user);
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
            status.setText(LocaleController.formatUserStatus(account, user));
            col.addView(status);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cp.leftMargin = AndroidUtilities.dp(14);
            cell.addView(col, cp);
        }
        cell.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putLong("user_id", user.id);
            go(new ChatActivity(args));
        });
        return cell;
    }

    private void addList(Context context, BlockSpec.Block b) {
        String source = b.str("source", "contacts");
        int limit = b.intValue("limit", 50);
        List<TLRPC.User> users = new ArrayList<>();
        if ("contacts".equals(source) || "online".equals(source)) {
            boolean wantsOnline = "online".equals(source) || "online".equals(chipFilter);
            users = contacts(wantsOnline);
            if (users.isEmpty() && wantsOnline) {
                // like the built-in People screen: nobody is online, so show who was active most recently
                users = contacts(false);
                Collections.sort(users, (a, c) -> Integer.compare(lastSeen(c), lastSeen(a)));
                addText(context, "Nobody is online right now. Showing recently active contacts.", 13, Theme.key_windowBackgroundWhiteGrayText, false);
            }
        } else {
            // chats / unread: the people you talk to, newest first
            for (org.telegram.tgnet.TLRPC.Dialog dlg : MessagesController.getInstance(account).getAllDialogs()) {
                if (dlg.id <= 0 || ("unread".equals(source) && dlg.unread_count == 0 && !dlg.unread_mark)) {
                    continue;
                }
                TLRPC.User u = MessagesController.getInstance(account).getUser(dlg.id);
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

    private LinearLayout textRow(Context context, CharSequence title, CharSequence sub, Runnable click) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        TextView t = new TextView(context);
        t.setText(title);
        t.setTextSize(16);
        t.setSingleLine();
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        row.addView(t);
        if (sub != null && sub.length() > 0) {
            TextView s = new TextView(context);
            s.setText(sub);
            s.setTextSize(13);
            s.setSingleLine();
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            s.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            row.addView(s);
        }
        if (click != null) {
            row.setOnClickListener(v -> click.run());
        }
        content.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    /** A list of your real chats (people, groups, channels), newest first, each opening its chat. */
    private void addChatList(Context context, BlockSpec.Block b) {
        String filter = b.str("filter", "all");
        int limit = b.intValue("limit", 30);
        boolean card = "card".equals(b.str("row", "compact"));
        MessagesController mc = MessagesController.getInstance(account);
        int shown = 0;
        for (TLRPC.Dialog dlg : new ArrayList<>(mc.getAllDialogs())) {
            if (shown >= limit) {
                break;
            }
            long id = dlg.id;
            if (id == 0 || org.telegram.messenger.DialogObject.isEncryptedDialog(id)) {
                continue;
            }
            boolean user = id > 0;
            TLRPC.Chat chat = user ? null : mc.getChat(-id);
            boolean channel = chat != null && org.telegram.messenger.ChatObject.isChannelAndNotMegaGroup(chat);
            boolean unread = dlg.unread_count > 0 || dlg.unread_mark;
            if (("unread".equals(filter) && !unread) || ("people".equals(filter) && !user)
                    || ("groups".equals(filter) && (user || channel)) || ("channels".equals(filter) && !channel)) {
                continue;
            }
            CharSequence title = org.telegram.messenger.DialogObject.getName(account, id);
            java.util.ArrayList<org.telegram.messenger.MessageObject> msgs = mc.dialogMessage.get(id);
            CharSequence last = msgs != null && !msgs.isEmpty() && msgs.get(0) != null && msgs.get(0).messageText != null ? msgs.get(0).messageText : "";
            if (unread && dlg.unread_count > 0) {
                last = "(" + dlg.unread_count + ") " + last;
            }
            final long did = id;
            LinearLayout row = textRow(context, title, last, () -> {
                Bundle args = new Bundle();
                if (did > 0) {
                    args.putLong("user_id", did);
                } else {
                    args.putLong("chat_id", -did);
                }
                go(new ChatActivity(args));
            });
            if (card) {
                row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
            }
            if (unread) {
                ((TextView) row.getChildAt(0)).setTypeface(AndroidUtilities.bold());
            }
            shown++;
        }
        if (shown == 0) {
            addText(context, "No chats to show.", 14, Theme.key_windowBackgroundWhiteGrayText, false);
        }
    }

    /** Shortcuts to a few built-in screens, in the order the layout lists them. */
    private void addMenu(Context context, BlockSpec.Block b) {
        for (String item : b.list("items", new ArrayList<String>())) {
            switch (item) {
                case "settings":
                    textRow(context, LocaleController.getString(org.telegram.messenger.R.string.Settings), null, () -> go(new org.telegram.ui.SettingsActivity()));
                    break;
                case "profile":
                    textRow(context, "My profile", null, this::openProfile);
                    break;
                case "contacts":
                    textRow(context, LocaleController.getString(org.telegram.messenger.R.string.Contacts), null, () -> {
                        Bundle args = new Bundle();
                        args.putBoolean("needFinishFragment", false);
                        go(new org.telegram.ui.ContactsActivity(args));
                    });
                    break;
                case "calls":
                    textRow(context, LocaleController.getString(org.telegram.messenger.R.string.Calls), null, () -> go(new org.telegram.ui.CallLogActivity()));
                    break;
                case "savedMessages":
                    textRow(context, LocaleController.getString(org.telegram.messenger.R.string.SavedMessages), null, () -> {
                        Bundle args = new Bundle();
                        args.putLong("user_id", org.telegram.messenger.UserConfig.getInstance(account).getClientUserId());
                        go(new ChatActivity(args));
                    });
                    break;
                default:
                    break;
            }
        }
    }

    private void openProfile() {
        Bundle args = new Bundle();
        args.putLong("user_id", org.telegram.messenger.UserConfig.getInstance(account).getClientUserId());
        go(new org.telegram.ui.ProfileActivity(args));
    }

    /** Your own avatar, name and username; tapping it opens your profile. */
    private void addProfileCard(Context context) {
        TLRPC.User me = org.telegram.messenger.UserConfig.getInstance(account).getCurrentUser();
        if (me == null) {
            return;
        }
        View card = person(context, me, false);
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        card.setOnClickListener(v -> openProfile());
        content.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}
