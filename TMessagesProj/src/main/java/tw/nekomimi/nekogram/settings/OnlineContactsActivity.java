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

    @Override
    public boolean usesPageGradient() {
        return false;
    }


    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private TextView emptyView;

    private final ArrayList<TLRPC.User> onlineUsers = new ArrayList<>();
    // People tab (Messenger look): rows currently shown, the story rail entries and the selected chip.
    private final ArrayList<TLRPC.User> shown = new ArrayList<>();
    private final ArrayList<TLRPC.User> railUsers = new ArrayList<>();
    private final ArrayList<Long> railStoryPeers = new ArrayList<>();
    private final ArrayList<org.telegram.tgnet.tl.TL_stories.StoryItem> railStoryItems = new ArrayList<>();
    private boolean showAll;
    private boolean nobodyOnline;
    private boolean railOn = true;
    private boolean chipsOn = true;

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
            if (tabMode()) {
                int index = position - headerCount();
                if (index >= 0 && index < shown.size()) {
                    openChat(shown.get(index));
                }
                return;
            }
            if (position >= 0 && position < onlineUsers.size()) {
                openChat(onlineUsers.get(position));
            }
        });
        FrameLayout.LayoutParams listParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        if (asTab) {
            // The action bar is drawn over the page: start the list below it so the first row/rail is not hidden.
            listParams.topMargin = org.telegram.ui.ActionBar.ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? org.telegram.messenger.AndroidUtilities.statusBarHeight : 0);
        }
        frameLayout.addView(listView, listParams);

        emptyView = new TextView(context);
        emptyView.setText(tabMode() ? "No contacts yet." : "No contacts are currently online.");
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
        buildPeople();
    }

    private int lastSeen(TLRPC.User u) {
        if (isOnline(u)) {
            return Integer.MAX_VALUE;
        }
        if (u.status instanceof TLRPC.TL_userStatusOffline) {
            return u.status.expires;
        }
        if (u.status instanceof TLRPC.TL_userStatusRecently) {
            return 2;
        }
        return u.status instanceof TLRPC.TL_userStatusLastWeek ? 1 : 0;
    }

    /** Rows + story rail for the People tab. Never empty while the account has contacts. */
    private void buildPeople() {
        final tw.nekomimi.nekogram.layout.LayoutSpec spec = tw.nekomimi.nekogram.layout.LayoutManager.get().spec();
        railOn = !"off".equals(spec.stringValue("people.storyRail", "on"));
        chipsOn = !"off".equals(spec.stringValue("people.chips", "on"));
        ArrayList<TLRPC.User> all = new ArrayList<>();
        for (TLRPC.TL_contact contact : ContactsController.getInstance(currentAccount).contacts) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(contact.user_id);
            if (user == null || UserObject.isDeleted(user) || UserObject.isUserSelf(user) || user.bot) {
                continue;
            }
            all.add(user);
        }
        Collections.sort(all, (a, b) -> Integer.compare(lastSeen(b), lastSeen(a)));
        nobodyOnline = onlineUsers.isEmpty();
        shown.clear();
        if (showAll || nobodyOnline) {
            shown.addAll(all);
        } else {
            shown.addAll(onlineUsers);
        }
        // Rail: people with stories first. With no stories, fall back to the most recently active contacts.
        railUsers.clear();
        railStoryPeers.clear();
        railStoryItems.clear();
        try {
            ArrayList<org.telegram.tgnet.tl.TL_stories.PeerStories> list = MessagesController.getInstance(currentAccount).getStoriesController().getDialogListStories();
            if (list != null) {
                for (org.telegram.tgnet.tl.TL_stories.PeerStories ps : list) {
                    long id = org.telegram.messenger.DialogObject.getPeerDialogId(ps.peer);
                    TLRPC.User u = id > 0 ? MessagesController.getInstance(currentAccount).getUser(id) : null;
                    if (u != null && !UserObject.isUserSelf(u)) {
                        org.telegram.tgnet.tl.TL_stories.StoryItem pick = null;
                        for (org.telegram.tgnet.tl.TL_stories.StoryItem it : ps.stories) {
                            if (it != null && it.media != null && (pick == null || it.id > ps.max_read_id && pick.id <= ps.max_read_id)) {
                                pick = it;
                            }
                        }
                        railUsers.add(u);
                        railStoryPeers.add(id);
                        railStoryItems.add(pick);
                    }
                }
            }
        } catch (Throwable ignore) {
        }
        if (railUsers.isEmpty()) {
            for (int i = 0; i < all.size() && i < 12; i++) {
                railUsers.add(all.get(i));
            }
        }
    }

    private void updateEmptyView() {
        if (emptyView == null || listView == null) {
            return;
        }
        final boolean empty = tabMode() ? (shown.isEmpty() && railUsers.isEmpty()) : onlineUsers.isEmpty();
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

    private int headerCount() {
        return (railOn ? 1 : 0) + (chipsOn ? 1 : 0) + (nobodyOnline && !showAll ? 1 : 0);
    }

    private void openChat(TLRPC.User user) {
        Bundle args = new Bundle();
        args.putLong("user_id", user.id);
        presentFragment(new ChatActivity(args));
    }

    private void refreshList() {
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        updateEmptyView();
    }

    private void openStory(int railIndex) {
        if (railIndex < railStoryPeers.size() && getParentActivity() != null) {
            ArrayList<Long> peers = new ArrayList<>(railStoryPeers);
            getOrCreateStoryViewer().open(getParentActivity(), null, peers, railIndex, null, null,
                    org.telegram.ui.Stories.StoriesListPlaceProvider.of(listView), false);
        } else if (railIndex < railUsers.size()) {
            openChat(railUsers.get(railIndex));
        }
    }

    private void addStory() {
        if (getParentActivity() != null) {
            org.telegram.ui.Stories.recorder.StoryRecorder.getInstance(getParentActivity(), currentAccount).open((org.telegram.ui.Stories.recorder.StoryRecorder.SourceView) null);
        }
    }

    private static int dp(float v) {
        return org.telegram.messenger.AndroidUtilities.dp(v);
    }

    private static android.graphics.drawable.GradientDrawable round(int color, float radiusDp) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setCornerRadius(dp(radiusDp));
        d.setColor(color);
        return d;
    }

    /** One story-rail card: "Add to story" (first), a contact with a story, or a recently active contact. */
    /** Stories from the list often carry only a tiny stripped preview; use the picture only when a real size exists. */
    private static boolean hasRealThumb(org.telegram.tgnet.tl.TL_stories.StoryItem item) {
        java.util.List<TLRPC.PhotoSize> sizes = null;
        if (item.media != null && item.media.document != null) {
            sizes = item.media.document.thumbs;
        } else if (item.media != null && item.media.photo != null) {
            sizes = item.media.photo.sizes;
        }
        if (sizes == null) {
            return false;
        }
        for (TLRPC.PhotoSize sz : sizes) {
            if (sz != null && !(sz instanceof TLRPC.TL_photoStrippedSize) && !(sz instanceof TLRPC.TL_photoSizeEmpty) && sz.w >= 90) {
                return true;
            }
        }
        return false;
    }

    private final class StoryCard extends FrameLayout {
        StoryCard(Context context, TLRPC.User user, boolean add, boolean hasStory, org.telegram.tgnet.tl.TL_stories.StoryItem item) {
            super(context);
            setBackground(round(Theme.getColor(Theme.key_windowBackgroundGray), 12));
            setClipToOutline(true);
            org.telegram.ui.Components.BackupImageView img = new org.telegram.ui.Components.BackupImageView(context);
            org.telegram.ui.Components.AvatarDrawable ad = new org.telegram.ui.Components.AvatarDrawable(user);
            if (add) {
                img.setForUserOrChat(user, ad);
                addView(img, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, android.view.Gravity.TOP));
                FrameLayout plus = new FrameLayout(context);
                plus.setBackground(round(Theme.getColor(Theme.key_featuredStickers_addButton), 14));
                TextView t = new TextView(context);
                t.setText("+");
                t.setTextSize(20);
                t.setGravity(android.view.Gravity.CENTER);
                t.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
                plus.addView(t, LayoutHelper.createFrame(28, 28, android.view.Gravity.CENTER));
                addView(plus, LayoutHelper.createFrame(28, 28, android.view.Gravity.CENTER_HORIZONTAL | android.view.Gravity.BOTTOM, 0, 0, 0, 38));
                TextView label = new TextView(context);
                label.setText("Add to story");
                label.setTextSize(12);
                label.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
                label.setGravity(android.view.Gravity.CENTER);
                label.setSingleLine(true);
                label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                addView(label, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, android.view.Gravity.BOTTOM, 4, 0, 4, 10));
                return;
            }
            // Big photo fills the card (blurry-free: use the big location when there is one).
            org.telegram.messenger.ImageLocation big = org.telegram.messenger.ImageLocation.getForUserOrChat(user, org.telegram.messenger.ImageLocation.TYPE_BIG);
            if (item != null && hasRealThumb(item)) {
                // The story's own picture / video frame, like Messenger; the poster is the small icon.
                org.telegram.ui.Stories.StoriesUtilities.setThumbImage(ad, img.getImageReceiver(), item, 90, 150);
            } else if (big != null) {
                img.setImage(big, "300_300", ad, user);
            } else {
                img.setForUserOrChat(user, ad);
            }
            addView(img, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            View scrim = new View(context);
            scrim.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x99000000}));
            addView(scrim, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 70, android.view.Gravity.BOTTOM));
            // Poster icon: one plain avatar view plus a separate ring view on top (no nesting).
            org.telegram.ui.Components.BackupImageView small = new org.telegram.ui.Components.BackupImageView(context);
            small.setRoundRadius(dp(15));
            small.setForUserOrChat(user, new org.telegram.ui.Components.AvatarDrawable(user));
            addView(small, LayoutHelper.createFrame(30, 30, android.view.Gravity.TOP | android.view.Gravity.LEFT, 8, 8, 0, 0));
            View ring = new View(context);
            android.graphics.drawable.GradientDrawable rg = new android.graphics.drawable.GradientDrawable();
            rg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            rg.setColor(0);
            rg.setStroke(dp(hasStory ? 2.5f : 1.5f), hasStory ? Theme.getColor(Theme.key_featuredStickers_addButton) : 0xffffffff);
            ring.setBackground(rg);
            addView(ring, LayoutHelper.createFrame(36, 36, android.view.Gravity.TOP | android.view.Gravity.LEFT, 5, 5, 0, 0));
            if (isOnline(user)) {
                View dot = new View(context);
                android.graphics.drawable.GradientDrawable dg = new android.graphics.drawable.GradientDrawable();
                dg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                dg.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGreenText));
                dg.setStroke(dp(2), 0xffffffff);
                dot.setBackground(dg);
                addView(dot, LayoutHelper.createFrame(12, 12, android.view.Gravity.TOP | android.view.Gravity.LEFT, 33, 33, 0, 0));
            }
            TextView name = new TextView(context);
            name.setText(UserObject.getFirstName(user));
            name.setTextSize(13);
            name.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
            name.setSingleLine(true);
            name.setTextColor(0xffffffff);
            addView(name, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, android.view.Gravity.BOTTOM, 8, 0, 8, 8));
        }
    }

    private final class RailView extends android.widget.HorizontalScrollView {
        RailView(Context context) {
            super(context);
            setHorizontalScrollBarEnabled(false);
            setOverScrollMode(OVER_SCROLL_NEVER);
            android.widget.LinearLayout row = new android.widget.LinearLayout(context);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setPadding(dp(12), dp(8), dp(12), dp(8));
            TLRPC.User self = getUserConfig().getCurrentUser();
            StoryCard add = new StoryCard(context, self, true, false, null);
            add.setOnClickListener(v -> addStory());
            row.addView(add, LayoutHelper.createLinear(88, 150, 0, 0, 4, 0));
            for (int i = 0; i < railUsers.size(); i++) {
                final int index = i;
                final boolean story = i < railStoryPeers.size();
                StoryCard card = new StoryCard(context, railUsers.get(i), false, story, story && i < railStoryItems.size() ? railStoryItems.get(i) : null);
                card.setOnClickListener(v -> openStory(index));
                row.addView(card, LayoutHelper.createLinear(88, 150, 0, 0, 4, 0));
            }
            addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            setFillViewport(false);
        }
    }

    private TextView chip(Context context, String text, boolean selected, boolean dot, View.OnClickListener click) {
        TextView c = new TextView(context);
        c.setText(text);
        c.setTextSize(15);
        c.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
        c.setGravity(android.view.Gravity.CENTER_VERTICAL);
        c.setTextColor(selected ? Theme.getColor(Theme.key_featuredStickers_addButton) : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        c.setBackground(round(selected ? (Theme.getColor(Theme.key_featuredStickers_addButton) & 0x00ffffff) | 0x26000000 : Theme.getColor(Theme.key_windowBackgroundGray), 20));
        c.setPadding(dp(14), 0, dp(16), 0);
        if (dot) {
            android.graphics.drawable.GradientDrawable ds = new android.graphics.drawable.GradientDrawable();
            ds.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            ds.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGreenText));
            ds.setSize(dp(9), dp(9));
            c.setCompoundDrawablePadding(dp(8));
            c.setCompoundDrawablesWithIntrinsicBounds(ds, null, null, null);
        } else {
            c.setPadding(dp(16), 0, dp(16), 0);
        }
        c.setOnClickListener(click);
        return c;
    }

    private View buildChips(Context context) {
        android.widget.HorizontalScrollView sv = new android.widget.HorizontalScrollView(context);
        sv.setHorizontalScrollBarEnabled(false);
        fillChips(sv, context);
        return sv;
    }

    /** (Re)draws the chip row so the selected chip always matches the list below it. */
    private void fillChips(android.widget.HorizontalScrollView sv, Context context) {
        sv.removeAllViews();
        android.widget.LinearLayout row = new android.widget.LinearLayout(context);
        row.setPadding(dp(16), dp(4), dp(8), dp(8));
        row.addView(chip(context, onlineUsers.size() + " active", !showAll, true, v -> {
            showAll = false;
            buildPeople();
            refreshList();
        }), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, 0, 0, 8, 0));
        row.addView(chip(context, "All", showAll, false, v -> {
            showAll = true;
            buildPeople();
            refreshList();
        }), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, 0, 0, 8, 0));
        sv.addView(row);
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

        void bind(TLRPC.User user, String statusText, boolean online) {
            dot.setVisibility(online ? View.VISIBLE : View.GONE);
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
            return !tabMode() || holder.getItemViewType() == 2;
        }

        @Override
        public int getItemCount() {
            return tabMode() ? headerCount() + shown.size() : onlineUsers.size();
        }

        // 1 = story rail, 3 = chips, 4 = "nobody is online" note, 2 = person row, 0 = classic card row
        @Override
        public int getItemViewType(int position) {
            if (!tabMode()) {
                return 0;
            }
            int p = position;
            if (railOn) {
                if (p == 0) return 1;
                p--;
            }
            if (chipsOn) {
                if (p == 0) return 3;
                p--;
            }
            if (nobodyOnline && !showAll) {
                if (p == 0) return 4;
            }
            return 2;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v;
            if (viewType == 1) {
                v = new RailView(mContext);
            } else if (viewType == 3) {
                v = buildChips(mContext);
            } else if (viewType == 4) {
                TextView t = new TextView(mContext);
                t.setText("Nobody is active right now. Showing recently active contacts.");
                t.setTextSize(13);
                t.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                t.setPadding(dp(16), dp(2), dp(16), dp(8));
                v = t;
            } else if (viewType == 2) {
                v = new PersonRow(mContext);
            } else {
                UserCell cell = new UserCell(mContext, 8, 0, false);
                cell.setLayoutParams(GroupCardHelper.freshLayoutParams());
                return new RecyclerListView.Holder(cell);
            }
            // The story rail needs an explicit height; wrap_content inside a horizontal scroller clipped the cards.
            v.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, viewType == 1 ? dp(166) : ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(v);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (tabMode()) {
                if (holder.getItemViewType() == 3) {
                    fillChips((android.widget.HorizontalScrollView) holder.itemView, mContext);
                    return;
                }
                if (holder.getItemViewType() == 2) {
                    TLRPC.User u = shown.get(position - headerCount());
                    boolean on = isOnline(u);
                    String status = on ? org.telegram.messenger.LocaleController.getString("Online", R.string.Online)
                            : org.telegram.messenger.LocaleController.formatUserStatus(currentAccount, u);
                    ((PersonRow) holder.itemView).bind(u, status, on);
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
