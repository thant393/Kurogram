package tw.kurocore.kurogram.settings;

import tw.kurocore.kurogram.feature.ContactChanges;
import tw.kurocore.kurogram.feature.KuroFeatures;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;

import static org.telegram.messenger.AndroidUtilities.dp;

public class ContactChangesActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int MENU_CLEAR = 1;

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private TextView emptyView;

    private final ArrayList<ContactChanges.Entry> logEntries = new ArrayList<>();

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().addObserver(this, NotificationCenter.contactsDidLoad);
        loadLog();
        checkForChanges();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().removeObserver(this, NotificationCenter.contactsDidLoad);
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Contacts Changes");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_CLEAR) {
                    confirmClearLog();
                }
            }
        });
        actionBar.createMenu().addItem(MENU_CLEAR, R.drawable.msg_delete);

        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listAdapter = new ListAdapter(context);
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(listAdapter);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setText("No changes recorded yet.\nKurogram will log name and photo changes of your contacts while the app is open.");
        emptyView.setTextSize(15);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(60, 0, 60, 0);
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        updateEmptyView();

        return fragmentView;
    }

    private void confirmClearLog() {
        if (getParentActivity() == null || logEntries.isEmpty()) {
            return;
        }
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getParentActivity());
        builder.setTitle("Clear Log");
        builder.setMessage("Clear all recorded contact changes?");
        builder.setPositiveButton("Clear", (dialog, which) -> {
            KuroFeatures.contactChanges().clearLog();
            logEntries.clear();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
            updateEmptyView();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void updateEmptyView() {
        if (emptyView == null || listView == null) {
            return;
        }
        emptyView.setVisibility(logEntries.isEmpty() ? View.VISIBLE : View.GONE);
        listView.setVisibility(logEntries.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void loadLog() {
        logEntries.clear();
        logEntries.addAll(KuroFeatures.contactChanges().log());
    }

    private void checkForChanges() {
        if (KuroFeatures.contactChanges().checkForChanges(currentAccount)) {
            loadLog();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
            updateEmptyView();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.updateInterfaces || id == NotificationCenter.contactsDidLoad) {
            checkForChanges();
        }
    }

    private String formatEntryText(ContactChanges.Entry entry) {
        if ("photo".equals(entry.type)) {
            return "changed his/her Profile Photo.";
        } else {
            return "changed his/her name from \"" + entry.oldValue + "\" to \"" + entry.newValue + "\".";
        }
    }

    private static String formatDate(long time) {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(time);

        boolean sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR);
        java.util.Locale locale = java.util.Locale.getDefault();
        if (sameDay) {
            return new java.text.SimpleDateFormat("HH:mm", locale).format(new java.util.Date(time));
        }

        long diffDays = (now.getTimeInMillis() - then.getTimeInMillis()) / 86400000L;
        if (diffDays < 7) {
            return new java.text.SimpleDateFormat("EEE", locale).format(new java.util.Date(time));
        }

        boolean sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR);
        if (sameYear) {
            return new java.text.SimpleDateFormat("MMM d", locale).format(new java.util.Date(time));
        }
        return new java.text.SimpleDateFormat("MMM d, yyyy", locale).format(new java.util.Date(time));
    }

    private static class ChangeCell extends FrameLayout {

        private final BackupImageView avatarImageView;
        private final TextView nameTextView;
        private final TextView descriptionTextView;
        private final TextView dateTextView;
        private final AvatarDrawable avatarDrawable = new AvatarDrawable();

        ChangeCell(Context context) {
            super(context);

            avatarImageView = new BackupImageView(context);
            avatarImageView.setRoundRadius(dp(10));
            addView(avatarImageView, LayoutHelper.createFrame(46, 46, Gravity.LEFT | Gravity.TOP, 13, 10, 0, 0));

            dateTextView = new TextView(context);
            dateTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
            dateTextView.setTextSize(13);
            dateTextView.setSingleLine(true);
            addView(dateTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP, 0, 13, 13, 0));

            // MATCH_PARENT width + matching left/right margins so setSingleLine/setEllipsize
            // actually take effect for long names, instead of growing under the date text.
            nameTextView = new TextView(context);
            nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            nameTextView.setTypeface(Typeface.DEFAULT_BOLD);
            nameTextView.setTextSize(16);
            nameTextView.setSingleLine(true);
            nameTextView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 75, 11, 70, 0));

            // MATCH_PARENT width + bottom margin so the cell can grow to fit up to 2 lines
            // instead of being clipped by a fixed-height onMeasure.
            descriptionTextView = new TextView(context);
            descriptionTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            descriptionTextView.setTextSize(14);
            descriptionTextView.setMaxLines(2);
            descriptionTextView.setLineSpacing(dp(2), 1f);
            descriptionTextView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            addView(descriptionTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 75, 34, 13, 10));
        }

        void bind(ContactChanges.Entry entry, int groupPos) {
            TLRPC.User user = MessagesController.getInstance(UserConfigHolder.currentAccount).getUser(entry.userId);
            avatarDrawable.setInfo(UserConfigHolder.currentAccount, user);
            avatarImageView.setForUserOrChat(user, avatarDrawable);
            nameTextView.setText(entry.name);
            descriptionTextView.setText(formatEntryTextStatic(entry));
            dateTextView.setText(formatDate(entry.time));
            GroupCardHelper.applyCard(this, groupPos);
            setWillNotDraw(!GroupCardHelper.needsDivider(groupPos));
        }

        private static String formatEntryTextStatic(ContactChanges.Entry entry) {
            if ("photo".equals(entry.type)) {
                return "changed his/her Profile Photo.";
            } else {
                return "changed his/her name from \"" + entry.oldValue + "\" to \"" + entry.newValue + "\".";
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            // Let the cell grow to fit its real content (avatar + up to 2 lines of
            // description) instead of forcing a fixed height that clipped longer entries.
            // A minimum height still keeps short single-line entries from feeling cramped
            // and matches the avatar's own footprint (10dp top + 46dp tall + 10dp bottom).
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int contentHeight = getMeasuredHeight();
            int minHeight = dp(66);
            int height = Math.max(contentHeight, minHeight);
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            if (!willNotDraw()) {
                canvas.drawLine(dp(75), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }
    }

    private static class UserConfigHolder {
        static int currentAccount;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        ListAdapter(Context context) {
            this.context = context;
            UserConfigHolder.currentAccount = currentAccount;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public int getItemCount() {
            return logEntries.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ChangeCell cell = new ChangeCell(context);
            cell.setLayoutParams(GroupCardHelper.freshLayoutParams());
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ContactChanges.Entry entry = logEntries.get(position);
            int pos = GroupCardHelper.positionInGroup(position, logEntries.size());
            ((ChangeCell) holder.itemView).bind(entry, pos);
        }
    }
}
