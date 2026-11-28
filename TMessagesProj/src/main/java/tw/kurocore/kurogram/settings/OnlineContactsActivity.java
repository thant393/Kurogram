package tw.kurocore.kurogram.settings;

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

    @Override
    public boolean usesPageGradient() {
        return false;
    }


    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private TextView emptyView;

    private final ArrayList<TLRPC.User> onlineUsers = new ArrayList<>();

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().addObserver(this, NotificationCenter.contactsDidLoad);
        loadData();
        scheduleRefresh();
        return super.onFragmentCreate();
    }

    /** Statuses expire silently (no update arrives when someone's "online" runs out), so re-check periodically. */
    private final Runnable refreshRunnable = () -> {
        reloadOnline();
        scheduleRefresh();
    };

    private void scheduleRefresh() {
        org.telegram.messenger.AndroidUtilities.cancelRunOnUIThread(refreshRunnable);
        org.telegram.messenger.AndroidUtilities.runOnUIThread(refreshRunnable, 20000);
    }

    private void reloadOnline() {
        loadData();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        updateEmptyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadOnline();
        scheduleRefresh();
    }

    @Override
    public void onFragmentDestroy() {
        org.telegram.messenger.AndroidUtilities.cancelRunOnUIThread(refreshRunnable);
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
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Online Contacts");
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
        fragmentView.setBackgroundColor(tw.kurocore.kurogram.skin.KuroUi.screenBackground("onlineContacts", Theme.getColor(Theme.key_windowBackgroundGray)));

        listAdapter = new ListAdapter(context);
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position >= 0 && position < onlineUsers.size()) {
                openChat(onlineUsers.get(position));
            }
        });
        frameLayout.addView(listView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

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
            // expires == 0 can occur on freshly parsed/partial status objects; treat as online like Telegram's own lists do
            return user.status.expires == 0 || user.status.expires > ConnectionsManager.getInstance(currentAccount).getCurrentTime();
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
        final boolean empty = onlineUsers.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
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
            int mask = args.length > 0 && args[0] instanceof Integer ? (Integer) args[0] : 0;
            if ((mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                loadData();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
                updateEmptyView();
            }
        }
    }

    private void openChat(TLRPC.User user) {
        Bundle args = new Bundle();
        args.putLong("user_id", user.id);
        presentFragment(new ChatActivity(args));
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            return onlineUsers.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            UserCell cell = new UserCell(mContext, 8, 0, false);
            cell.setLayoutParams(GroupCardHelper.freshLayoutParams());
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            TLRPC.User user = onlineUsers.get(position);
            UserCell cell = (UserCell) holder.itemView;
            int pos = GroupCardHelper.positionInGroup(position, onlineUsers.size());
            GroupCardHelper.applyCard(cell, pos);
            cell.setData(user, null, org.telegram.messenger.LocaleController.getString("Online", R.string.Online), GroupCardHelper.needsDivider(pos) ? 1 : 0);
        }
    }
}
