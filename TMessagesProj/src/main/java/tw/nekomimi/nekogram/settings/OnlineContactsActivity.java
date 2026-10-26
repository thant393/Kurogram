package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.ContactsController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class OnlineContactsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private TextView emptyView;

    private final ArrayList<TLRPC.User> onlineUsers = new ArrayList<>();

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().addObserver(this, NotificationCenter.contactsDidLoad);
        loadData();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().removeObserver(this, NotificationCenter.contactsDidLoad);
        super.onFragmentDestroy();
    }

    public OnlineContactsActivity() {
        super();
    }

    public OnlineContactsActivity(Bundle args) {
        super(args);
    }

    @Override
    public View createView(Context context) {
        // As the People tab of a layout pack there is no back arrow and the list stops above the tab bar.
        final boolean asTab = arguments != null && arguments.getBoolean("hasMainTabs", false);
        if (!asTab) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        }
        actionBar.setTitle(asTab ? "People" : "Online Contacts");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;
        fragmentView.setBackgroundColor(Theme.getColor(asTab ? Theme.key_windowBackgroundWhite : Theme.key_windowBackgroundGray));

        listAdapter = new ListAdapter(context);
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(listAdapter);
        if (asTab) {
            listView.setClipToPadding(false);
            listView.setPadding(0, 0, 0, org.telegram.messenger.AndroidUtilities.dp(org.telegram.ui.DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 8));
        }
        listView.setOnItemClickListener((view, position) -> {
            int index = tabMode() ? position - 1 : position;
            if (index >= 0 && index < onlineUsers.size()) {
                TLRPC.User user = onlineUsers.get(index);
                Bundle args = new Bundle();
                args.putLong("user_id", user.id);
                presentFragment(new ChatActivity(args));
            }
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setText("No contacts are currently online.");
        emptyView.setTextSize(15);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyView.setGravity(android.view.Gravity.CENTER);
        emptyView.setPadding(60, 0, 60, 0);
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, android.view.Gravity.CENTER));

        updateEmptyView();

        return fragmentView;
    }

    private boolean isOnline(TLRPC.User user) {
        if (user == null || user.status == null) {
            return false;
        }
        if (user.status instanceof TLRPC.TL_userStatusOnline) {
            return user.status.expires > ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        }
        return false;
    }

    private void loadData() {
        onlineUsers.clear();
        for (TLRPC.TL_contact contact : ContactsController.getInstance(currentAccount).contacts) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(contact.user_id);
            if (user == null || UserObject.isDeleted(user) || UserObject.isUserSelf(user)) {
                continue;
            }
            if (isOnline(user)) {
                onlineUsers.add(user);
            }
        }
        Collections.sort(onlineUsers, Comparator.comparingLong(u -> u.id));
    }

    private void updateEmptyView() {
        if (emptyView == null || listView == null) {
            return;
        }
        emptyView.setVisibility(onlineUsers.isEmpty() ? View.VISIBLE : View.GONE);
        listView.setVisibility(onlineUsers.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.contactsDidLoad) {
            loadData();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
            updateEmptyView();
        } else if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer) args[0];
            if ((mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                loadData();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
                updateEmptyView();
            }
        }
    }

    private boolean tabMode() {
        return arguments != null && arguments.getBoolean("hasMainTabs", false);
    }

    /** Messenger-style "People" rows: round avatar with a green active dot, name, short status. */
    private static final class PersonRow extends android.widget.LinearLayout {
        private final org.telegram.ui.Components.BackupImageView avatar;
        private final TextView name;
        private final TextView status;
        private final View dot;

        PersonRow(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(android.view.Gravity.CENTER_VERTICAL);
            setPadding(org.telegram.messenger.AndroidUtilities.dp(16), 0, org.telegram.messenger.AndroidUtilities.dp(16), 0);
            FrameLayout avatarBox = new FrameLayout(context);
            avatar = new org.telegram.ui.Components.BackupImageView(context);
            avatar.setRoundRadius(org.telegram.messenger.AndroidUtilities.dp(28));
            avatarBox.addView(avatar, LayoutHelper.createFrame(56, 56));
            dot = new View(context);
            android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
            d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            d.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGreenText));
            d.setStroke(org.telegram.messenger.AndroidUtilities.dp(2.5f), Theme.getColor(Theme.key_windowBackgroundWhite));
            dot.setBackground(d);
            avatarBox.addView(dot, LayoutHelper.createFrame(16, 16, android.view.Gravity.BOTTOM | android.view.Gravity.RIGHT));
            addView(avatarBox, LayoutHelper.createLinear(56, 56));
            android.widget.LinearLayout col = new android.widget.LinearLayout(context);
            col.setOrientation(VERTICAL);
            name = new TextView(context);
            name.setTextSize(17);
            name.setSingleLine(true);
            name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            col.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            status = new TextView(context);
            status.setTextSize(14);
            status.setSingleLine(true);
            status.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            col.addView(status, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
            addView(col, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 14, 0, 0, 0));
            setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));
        }

        void bind(TLRPC.User user, String statusText) {
            avatar.setForUserOrChat(user, new org.telegram.ui.Components.AvatarDrawable(user));
            name.setText(UserObject.getUserName(user));
            status.setText(statusText);
        }

        @Override
        protected void onMeasure(int w, int h) {
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(org.telegram.messenger.AndroidUtilities.dp(76), MeasureSpec.EXACTLY));
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return !(tabMode() && holder.getAdapterPosition() == 0);
        }

        @Override
        public int getItemCount() {
            return onlineUsers.size() + (tabMode() ? 1 : 0);
        }

        @Override
        public int getItemViewType(int position) {
            return tabMode() ? (position == 0 ? 1 : 2) : 0;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == 1) {
                TextView chip = new TextView(mContext);
                chip.setTextSize(16);
                chip.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
                chip.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                chip.setGravity(android.view.Gravity.CENTER_VERTICAL);
                chip.setCompoundDrawablePadding(org.telegram.messenger.AndroidUtilities.dp(8));
                android.graphics.drawable.GradientDrawable dotShape = new android.graphics.drawable.GradientDrawable();
                dotShape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                dotShape.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGreenText));
                dotShape.setSize(org.telegram.messenger.AndroidUtilities.dp(9), org.telegram.messenger.AndroidUtilities.dp(9));
                chip.setCompoundDrawablesWithIntrinsicBounds(dotShape, null, null, null);
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setCornerRadius(org.telegram.messenger.AndroidUtilities.dp(20));
                bg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
                chip.setBackground(bg);
                chip.setPadding(org.telegram.messenger.AndroidUtilities.dp(14), 0, org.telegram.messenger.AndroidUtilities.dp(16), 0);
                android.widget.FrameLayout wrap = new android.widget.FrameLayout(mContext);
                wrap.setPadding(org.telegram.messenger.AndroidUtilities.dp(16), org.telegram.messenger.AndroidUtilities.dp(8), 0, org.telegram.messenger.AndroidUtilities.dp(8));
                wrap.addView(chip, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 40, android.view.Gravity.LEFT));
                wrap.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(wrap);
            }
            if (viewType == 2) {
                PersonRow row = new PersonRow(mContext);
                row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(row);
            }
            UserCell cell = new UserCell(mContext, 8, 0, false);
            cell.setLayoutParams(GroupCardHelper.freshLayoutParams());
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (tabMode()) {
                if (position == 0) {
                    ((TextView) ((android.widget.FrameLayout) holder.itemView).getChildAt(0)).setText(onlineUsers.size() + " active");
                } else {
                    ((PersonRow) holder.itemView).bind(onlineUsers.get(position - 1), org.telegram.messenger.LocaleController.getString("Online", R.string.Online));
                }
                return;
            }
            TLRPC.User user = onlineUsers.get(position);
            UserCell cell = (UserCell) holder.itemView;
            int pos = GroupCardHelper.positionInGroup(position, onlineUsers.size());
            GroupCardHelper.applyCard(cell, pos);
            cell.setData(user, null, org.telegram.messenger.LocaleController.getString("Online", R.string.Online), GroupCardHelper.needsDivider(pos) ? 1 : 0);
        }
    }
}
