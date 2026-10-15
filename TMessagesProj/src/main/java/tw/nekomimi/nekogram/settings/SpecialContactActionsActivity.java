package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public class SpecialContactActionsActivity extends BaseFragment {

    private static final int MENU_SETTINGS = 1;

    private final long userId;
    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private TextView emptyView;
    private ArrayList<SpecialContactsMonitor.Action> actions = new ArrayList<>();

    public SpecialContactActionsActivity(long userId) {
        super();
        this.userId = userId;
    }

    @Override
    public boolean onFragmentCreate() {
        actions = SpecialContactsMonitor.loadActions(userId);
        return super.onFragmentCreate();
    }

    @Override
    public void onResume() {
        super.onResume();
        actions = SpecialContactsMonitor.loadActions(userId);
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        updateEmptyView();
    }

    @Override
    public View createView(Context context) {
        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(userId);
        String name = user != null ? ContactsController.formatName(user.first_name, user.last_name) : "Special Contact";
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(name);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_SETTINGS) {
                    presentFragment(new SpecialContactSettingsActivity(userId));
                }
            }
        });
        actionBar.createMenu().addItem(MENU_SETTINGS, R.drawable.msg_settings);

        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listAdapter = new ListAdapter(context);
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(listAdapter);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setText("No action\nThis user has no saved action");
        emptyView.setTextSize(15);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(60, 0, 60, 0);
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        updateEmptyView();
        return fragmentView;
    }

    private void updateEmptyView() {
        if (emptyView == null || listView == null) {
            return;
        }
        emptyView.setVisibility(actions.isEmpty() ? View.VISIBLE : View.GONE);
        listView.setVisibility(actions.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public int getItemCount() {
            return actions.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            TextInfoPrivacyCell cell = new TextInfoPrivacyCell(context);
            cell.setLayoutParams(GroupCardHelper.freshLayoutParams());
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            SpecialContactsMonitor.Action a = actions.get(position);
            TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
            cell.setText(a.text + " \u2022 " + SpecialContactsMonitor.formatAgo(a.time));
            int pos = GroupCardHelper.positionInGroup(position, actions.size());
            GroupCardHelper.applyCard(cell, pos);
        }
    }
}
