package tw.nekomimi.nekogram.layout;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;

/**
 * The "Unread" tab of a layout pack: Telegram's own chat rows, filtered to the chats that still have
 * unread messages. It reads the real dialog list, so nothing here is a copy of any data.
 */
public class UnreadChatsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private final ArrayList<TLRPC.Dialog> unread = new ArrayList<>();
    private RecyclerListView listView;
    private TextView emptyView;
    private Adapter adapter;
    private boolean reloadQueued;

    public UnreadChatsActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.dialogsNeedReload);
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().addObserver(this, NotificationCenter.messagesRead);
        reload();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.dialogsNeedReload);
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().removeObserver(this, NotificationCenter.messagesRead);
    }

    @Override
    public View createView(Context context) {
        final boolean asTab = arguments != null && arguments.getBoolean("hasMainTabs", false);
        if (!asTab) {
            actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        }
        actionBar.setTitle("Unread");
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        fragmentView = root;
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        adapter = new Adapter(context);
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(adapter);
        if (asTab) {
            listView.setClipToPadding(false);
            listView.setPadding(0, 0, 0, AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 8));
        }
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= unread.size()) return;
            long id = unread.get(position).id;
            Bundle args = new Bundle();
            if (DialogObject.isUserDialog(id)) {
                args.putLong("user_id", id);
            } else if (DialogObject.isChatDialog(id)) {
                args.putLong("chat_id", -id);
            } else {
                return; // secret chats and other kinds open from the main list
            }
            presentFragment(new ChatActivity(args));
        });
        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setText("You're all caught up");
        emptyView.setTextSize(16);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        root.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        updateEmpty();
        return root;
    }

    private void reload() {
        unread.clear();
        for (TLRPC.Dialog d : MessagesController.getInstance(currentAccount).getDialogs(0)) {
            if (d != null && (d.unread_count > 0 || d.unread_mark)) {
                unread.add(d);
            }
        }
    }

    private void updateEmpty() {
        if (emptyView != null) {
            emptyView.setVisibility(unread.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (reloadQueued) return;
        reloadQueued = true;
        AndroidUtilities.runOnUIThread(() -> {
            reloadQueued = false;
            reload();
            if (adapter != null) adapter.notifyDataSetChanged();
            updateEmpty();
        }, 150);
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        private final Context context;

        Adapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            return unread.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            DialogCell cell = new DialogCell(null, context, true, false, currentAccount, null);
            cell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((DialogCell) holder.itemView).setDialog(unread.get(position), 0, 0);
        }
    }
}
