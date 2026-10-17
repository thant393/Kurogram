package tw.nekomimi.nekogram.stickerstore;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.StickerEmojiCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.json.JSONObject;

import tw.nekomimi.nekogram.settings.GroupCardHelper;

/**
 * Our own full-screen sticker pack detail/preview screen - opened by tapping a grid card
 * in the Sticker Store, replacing Telegram's built-in "add sticker" popup entirely (not a
 * dialog/bottom sheet, a real navigated-to screen with our own layout).
 */
public class StickerPackDetailActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int GRID_COLUMNS = 4;
    private static final int GRID_CELL_DP = 80;
    private static final int GRID_PADDING_DP = 8; // top+bottom padding applied to gridView, each side
    private static final int GRID_MIN_HEIGHT_DP = 160; // used while loading/on error, before the real count is known

    private final StickerPackInfo pack;

    private BackupImageView iconView;
    private TextView titleView;
    private TextView categoryView;
    private TextView installedBadge;
    private LinearLayout starsRow;
    private TextView ratingNumberView;
    private TextView ownerView;
    private TextView ratingsCountView;
    private TextView likesCountView;
    private TextView downloadsCountView;
    private FrameLayout gridFrame;
    private RecyclerListView gridView;
    private RadialProgressView progressView;
    private TextView errorView;
    private TextView actionButton;

    // Floating bottom bar: [rating star] [Add to Sticker / Remove] [like heart], which swaps
    // for a 5-star picker bar ([x] stars [check]) while the user is rating.
    private LinearLayout normalBar;
    private LinearLayout ratingBar;
    private TextView starButton;
    private TextView likeButton;
    private TextView cancelRatingButton;
    private TextView confirmRatingButton;
    private final TextView[] pickStars = new TextView[5];
    private boolean ratingMode;
    private int pendingStars;
    private boolean submittingRating;
    private boolean submittingLike;
    /** Set right after our own install/remove so the button flips immediately, until
     *  Telegram's reloaded sticker list confirms the same thing. */
    private Boolean installedOverride;

    private TLRPC.TL_messages_stickerSet stickerSet;

    public StickerPackDetailActivity(StickerPackInfo pack) {
        super();
        this.pack = pack;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Sticker Pack");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout rootFrame = new FrameLayout(context);
        fragmentView = rootFrame;
        rootFrame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        // The content scrolls; the action bar below is a sibling, NOT part of the scrolled
        // content, so it stays fixed at the bottom whether the sticker list is short or long.
        // The bottom padding (with clipToPadding off) lets the last row scroll fully above it.
        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setPadding(0, 0, 0, AndroidUtilities.dp(KuroBarStyle.CONTENT_INSET_DP));
        rootFrame.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(root, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        // Header: icon + display name + category + installed badge.
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        header.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(24), AndroidUtilities.dp(24), AndroidUtilities.dp(16));
        root.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        iconView = new BackupImageView(context);
        iconView.setRoundRadius(AndroidUtilities.dp(20));
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.RECTANGLE);
        iconBg.setCornerRadius(AndroidUtilities.dp(20));
        iconBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        iconView.setBackground(iconBg);
        header.addView(iconView, LayoutHelper.createLinear(96, 96, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 12));

        titleView = new TextView(context);
        titleView.setTextSize(20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setGravity(Gravity.CENTER);
        titleView.setMaxLines(2);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        categoryView = new TextView(context);
        categoryView.setTextSize(14);
        categoryView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        categoryView.setGravity(Gravity.CENTER);
        header.addView(categoryView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 2, 0, 0));

        installedBadge = new TextView(context);
        installedBadge.setText("✓ Already Installed");
        installedBadge.setTextSize(12);
        installedBadge.setTypeface(AndroidUtilities.bold());
        installedBadge.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGreenText));
        installedBadge.setGravity(Gravity.CENTER);
        installedBadge.setVisibility(View.GONE);
        header.addView(installedBadge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));

        // Stats card: owner, average rating (stars), rating count, likes, downloads.
        LinearLayout statsCard = new LinearLayout(context);
        statsCard.setOrientation(LinearLayout.VERTICAL);
        statsCard.setBackground(GroupCardHelper.cardBackground(context, GroupCardHelper.POS_SINGLE));
        statsCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        LinearLayout.LayoutParams statsCardParams = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        statsCardParams.setMargins(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        root.addView(statsCard, statsCardParams);

        LinearLayout ratingRow = new LinearLayout(context);
        ratingRow.setOrientation(LinearLayout.HORIZONTAL);
        // Centered as a group (not just vertically) so the stars + number sit in the
        // middle of the card instead of hugging the left edge.
        ratingRow.setGravity(Gravity.CENTER);
        statsCard.addView(ratingRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        starsRow = new LinearLayout(context);
        starsRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 5; i++) {
            TextView star = new TextView(context);
            star.setTextSize(18);
            star.setText("★");
            starsRow.addView(star, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 2, 0));
        }
        ratingRow.addView(starsRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

        ratingNumberView = new TextView(context);
        ratingNumberView.setTextSize(15);
        ratingNumberView.setTypeface(AndroidUtilities.bold());
        ratingNumberView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        ratingRow.addView(ratingNumberView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        ownerView = createStatRow(context, statsCard, "Owner");
        ratingsCountView = createStatRow(context, statsCard, "Ratings");
        likesCountView = createStatRow(context, statsCard, "Likes");
        downloadsCountView = createStatRow(context, statsCard, "Downloads");

        // Real sticker grid. This sits inside the screen's outer ScrollView, so instead of
        // boxing it to a fixed height (which either cut the last row off or left dead
        // space, and fought the outer scroll with its own internal one), its height is
        // computed from the actual sticker count once the set loads (see updateGridHeight())
        // so the card always extends exactly to fit all the stickers - never clipped, never
        // short.
        gridFrame = new FrameLayout(context);
        LinearLayout.LayoutParams gridFrameParams = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, GRID_MIN_HEIGHT_DP);
        gridFrameParams.setMargins(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        root.addView(gridFrame, gridFrameParams);
        gridFrame.setBackground(GroupCardHelper.cardBackground(context, GroupCardHelper.POS_SINGLE));

        gridView = new RecyclerListView(context);
        GridLayoutManager layoutManager = new GridLayoutManager(context, GRID_COLUMNS);
        gridView.setLayoutManager(layoutManager);
        gridView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        gridView.setClipToPadding(false);
        // The outer ScrollView already owns scrolling for the whole screen - the grid's
        // height always matches its content (see updateGridHeight()), so it never needs to
        // scroll on its own.
        gridView.setNestedScrollingEnabled(false);
        gridView.setAdapter(new StickerAdapter());
        gridFrame.addView(gridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        progressView = new RadialProgressView(context);
        gridFrame.addView(progressView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        errorView = new TextView(context);
        errorView.setTextSize(14);
        errorView.setGravity(Gravity.CENTER);
        errorView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        errorView.setText(LocaleController.getString(R.string.AddStickersNotFound));
        errorView.setVisibility(View.GONE);
        gridFrame.addView(errorView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        rootFrame.addView(KuroBarStyle.createBottomScrim(context),
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, KuroBarStyle.SCRIM_HEIGHT_DP, Gravity.BOTTOM));
        rootFrame.addView(buildBottomBar(context), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, KuroBarStyle.BAR_HEIGHT_DP, Gravity.BOTTOM,
                KuroBarStyle.SIDE_MARGIN_DP, 0, KuroBarStyle.SIDE_MARGIN_DP, KuroBarStyle.BOTTOM_MARGIN_DP));

        applyPackHeader();
        refreshStarButton();
        refreshLikeButton();
        loadStickerSet();

        return fragmentView;
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.stickersDidLoad);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.stickersDidLoad);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.stickersDidLoad && stickerSet != null && fragmentView != null) {
            // Telegram's installed-sticker list just (re)loaded - this is the moment the real
            // installed state becomes known, so refresh the button from it.
            if (installedOverride != null && installedOverride == isInstalledInList()) {
                installedOverride = null;
            }
            updateActionButton();
        }
    }

    private View buildBottomBar(Context context) {
        FrameLayout bar = new FrameLayout(context);
        bar.setBackground(KuroBarStyle.background());
        bar.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        bar.setClipToOutline(true);

        // --- normal state: rating star (left), main button (center), like heart (right) ---
        normalBar = new LinearLayout(context);
        normalBar.setOrientation(LinearLayout.HORIZONTAL);
        normalBar.setGravity(Gravity.CENTER_VERTICAL);
        normalBar.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        bar.addView(normalBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        starButton = createBarIcon(context, "☆", 26);
        starButton.setOnClickListener(v -> setRatingMode(true));
        normalBar.addView(starButton, LayoutHelper.createLinear(48, 48));

        actionButton = new TextView(context);
        actionButton.setGravity(Gravity.CENTER);
        actionButton.setTextSize(15);
        actionButton.setTypeface(AndroidUtilities.bold());
        actionButton.setText("Loading…");
        actionButton.setEnabled(false);
        styleActionButton(false, false);
        normalBar.addView(actionButton, LayoutHelper.createLinear(0, 44, 1f, 6, 0, 6, 0));

        likeButton = createBarIcon(context, "♡", 26);
        likeButton.setOnClickListener(v -> toggleLike());
        normalBar.addView(likeButton, LayoutHelper.createLinear(48, 48));

        // --- rating state: cancel (x), five tappable stars, confirm (check) ---
        ratingBar = new LinearLayout(context);
        ratingBar.setOrientation(LinearLayout.HORIZONTAL);
        ratingBar.setGravity(Gravity.CENTER_VERTICAL);
        ratingBar.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        ratingBar.setVisibility(View.GONE);
        ratingBar.setAlpha(0f);
        bar.addView(ratingBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        cancelRatingButton = createBarIcon(context, "✕", 22);
        cancelRatingButton.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        cancelRatingButton.setOnClickListener(v -> setRatingMode(false));
        ratingBar.addView(cancelRatingButton, LayoutHelper.createLinear(48, 48));

        LinearLayout starsHolder = new LinearLayout(context);
        starsHolder.setOrientation(LinearLayout.HORIZONTAL);
        starsHolder.setGravity(Gravity.CENTER);
        ratingBar.addView(starsHolder, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));
        for (int i = 0; i < 5; i++) {
            final int value = i + 1;
            TextView star = createBarIcon(context, "☆", 30);
            star.setOnClickListener(v -> {
                pendingStars = value;
                refreshPickStars();
            });
            pickStars[i] = star;
            starsHolder.addView(star, LayoutHelper.createLinear(44, 48));
        }

        confirmRatingButton = createBarIcon(context, "✓", 26);
        confirmRatingButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        confirmRatingButton.setOnClickListener(v -> confirmRating());
        ratingBar.addView(confirmRatingButton, LayoutHelper.createLinear(48, 48));

        return bar;
    }

    private TextView createBarIcon(Context context, String glyph, int sizeSp) {
        TextView icon = new TextView(context);
        icon.setText(glyph);
        icon.setTextSize(sizeSp);
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        icon.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
        return icon;
    }

    /** Cross-fades between the normal bar and the star-picker bar (same container, same size). */
    private void setRatingMode(boolean show) {
        if (ratingMode == show || normalBar == null) {
            return;
        }
        ratingMode = show;
        if (show) {
            // Start from the user's earlier rating (if any) so they can see/change it.
            pendingStars = StickerUserState.getMyRating(pack.shortName);
            refreshPickStars();
        }
        final View in = show ? ratingBar : normalBar;
        final View out = show ? normalBar : ratingBar;
        in.setVisibility(View.VISIBLE);
        in.animate().cancel();
        in.setScaleX(0.96f);
        in.animate().alpha(1f).scaleX(1f).setDuration(180).start();
        out.animate().cancel();
        out.animate().alpha(0f).setDuration(120).withEndAction(() -> {
            // Only hide it if it hasn't been asked to come back in the meantime.
            boolean shouldBeHidden = (out == normalBar) == ratingMode;
            if (shouldBeHidden) {
                out.setVisibility(View.GONE);
            }
        }).start();
    }

    private void refreshPickStars() {
        for (int i = 0; i < pickStars.length; i++) {
            boolean on = i < pendingStars;
            pickStars[i].setText(on ? "★" : "☆");
            pickStars[i].setTextColor(on
                    ? Theme.getColor(Theme.key_featuredStickers_addButton)
                    : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        }
        confirmRatingButton.setAlpha(pendingStars > 0 ? 1f : 0.35f);
    }

    private void refreshStarButton() {
        boolean rated = StickerUserState.getMyRating(pack.shortName) > 0;
        starButton.setText(rated ? "★" : "☆");
        starButton.setTextColor(rated
                ? Theme.getColor(Theme.key_featuredStickers_addButton)
                : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
    }

    private void refreshLikeButton() {
        boolean liked = StickerUserState.isLiked(pack.shortName);
        likeButton.setText(liked ? "♥" : "♡");
        likeButton.setTextColor(liked
                ? Theme.getColor(Theme.key_text_RedBold)
                : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
    }

    private long myTelegramId() {
        return UserConfig.getInstance(currentAccount).getClientUserId();
    }

    /** Submits the chosen stars. The average/count shown on screen update IMMEDIATELY (same
     *  maths as the server's upsert: average = sum of stars / number of ratings), then the
     *  server's own recalculated numbers replace ours if its reply carries them; if the
     *  request fails, the screen goes back to what it showed before. */
    private void confirmRating() {
        if (pendingStars <= 0 || submittingRating) {
            return;
        }
        final int stars = pendingStars;
        final int previous = StickerUserState.getMyRating(pack.shortName);
        final double oldAvg = pack.ratingAvg;
        final int oldCount = pack.ratingCount;

        pack.applyMyRating(stars, previous);
        StickerUserState.setMyRating(pack.shortName, stars);
        applyPackHeader();
        refreshStarButton();
        setRatingMode(false);

        submittingRating = true;
        StickerStoreApi.submitRating(pack.getApiId(), myTelegramId(), stars, new StickerStoreApi.ResultCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                submittingRating = false;
                if (pack.mergeServerRating(response) && fragmentView != null) {
                    applyPackHeader();
                }
            }

            @Override
            public void onError(String message) {
                submittingRating = false;
                pack.ratingAvg = oldAvg;
                pack.ratingCount = oldCount;
                StickerUserState.setMyRating(pack.shortName, previous);
                if (fragmentView != null) {
                    applyPackHeader();
                    refreshStarButton();
                    Toast.makeText(getContext(), "Couldn't save your rating (" + message + ")", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void toggleLike() {
        if (submittingLike) {
            return;
        }
        final boolean liked = !StickerUserState.isLiked(pack.shortName);
        StickerUserState.setLiked(pack.shortName, liked);
        pack.totalLikes = Math.max(0, pack.totalLikes + (liked ? 1 : -1));
        applyPackHeader();
        refreshLikeButton();

        submittingLike = true;
        StickerStoreApi.setLike(pack.getApiId(), myTelegramId(), liked, new StickerStoreApi.ResultCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                submittingLike = false;
                // The server replies with the pack's real total_likes - prefer that.
                if (response != null && response.has("total_likes") && fragmentView != null) {
                    pack.totalLikes = response.optInt("total_likes", pack.totalLikes);
                    applyPackHeader();
                }
            }

            @Override
            public void onError(String message) {
                submittingLike = false;
                StickerUserState.setLiked(pack.shortName, !liked);
                pack.totalLikes = Math.max(0, pack.totalLikes + (liked ? -1 : 1));
                if (fragmentView != null) {
                    applyPackHeader();
                    refreshLikeButton();
                    Toast.makeText(getContext(), "Couldn't save your like (" + message + ")", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private TextView createStatRow(Context context, LinearLayout parent, String label) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextSize(14);
        labelView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        row.addView(labelView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(context);
        valueView.setTextSize(14);
        valueView.setTypeface(AndroidUtilities.bold());
        valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        row.addView(valueView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        return valueView;
    }

    private void applyPackHeader() {
        String title = pack.getDisplayTitle();
        titleView.setText(title != null ? title : "…");
        categoryView.setText(pack.category != null ? pack.category : "");
        categoryView.setVisibility(pack.category != null && !pack.category.isEmpty() ? View.VISIBLE : View.GONE);

        setStars(pack.ratingAvg);
        ratingNumberView.setText(pack.ratingAvg > 0
                ? String.format(LocaleController.getInstance().getCurrentLocale(), "%.1f", pack.ratingAvg)
                : "—");

        ownerView.setText(resolveOwnerLabel());
        ratingsCountView.setText(String.valueOf(pack.ratingCount));
        likesCountView.setText(String.valueOf(pack.totalLikes));
        downloadsCountView.setText(String.valueOf(pack.downloads));
    }

    private void setStars(double avg) {
        int filled = (int) Math.round(avg);
        for (int i = 0; i < starsRow.getChildCount(); i++) {
            TextView star = (TextView) starsRow.getChildAt(i);
            star.setTextColor(i < filled
                    ? Theme.getColor(Theme.key_featuredStickers_addButton)
                    : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        }
    }

    /** Only resolves from locally cached users - the backend only gives us a numeric
     *  Telegram id (owner_telegram_id), and fetching an arbitrary user by id needs an
     *  access_hash we don't have unless Telegram has already told us about that user. */
    private String resolveOwnerLabel() {
        if (pack.ownerTelegramId == 0) {
            return "Unknown";
        }
        TLRPC.User owner = MessagesController.getInstance(currentAccount).getUser(pack.ownerTelegramId);
        if (owner != null) {
            if (owner.username != null && !owner.username.isEmpty()) {
                return "@" + owner.username;
            }
            String name = UserObject.getUserName(owner);
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        return "Unknown";
    }

    private void loadStickerSet() {
        TLRPC.TL_messages_stickerSet cached = StickerSetResolver.getCached(pack.shortName);
        if (cached != null) {
            onStickerSetResolved(cached);
            return;
        }
        progressView.setVisibility(View.VISIBLE);
        StickerSetResolver.resolve(currentAccount, pack.shortName, new StickerSetResolver.Callback() {
            @Override
            public void onResolved(TLRPC.TL_messages_stickerSet result) {
                onStickerSetResolved(result);
            }

            @Override
            public void onError() {
                progressView.setVisibility(View.GONE);
                errorView.setVisibility(View.VISIBLE);
            }
        });
    }

    private void onStickerSetResolved(TLRPC.TL_messages_stickerSet result) {
        if (fragmentView == null) {
            return;
        }
        stickerSet = result;
        pack.resolvedSet = result;
        progressView.setVisibility(View.GONE);
        applyPackHeader();
        applyPackIcon();
        if (gridView.getAdapter() != null) {
            gridView.getAdapter().notifyDataSetChanged();
        }
        updateGridHeight();
        MediaDataController.getInstance(currentAccount).checkStickers(packType());
        updateActionButton();
    }

    /** Sizes the grid card to exactly fit every sticker (full rows of GRID_COLUMNS), so it
     *  always extends properly to the bottom instead of being clipped by - or leaving dead
     *  space below - a fixed-height box. */
    private void updateGridHeight() {
        int count = stickerSet != null && stickerSet.documents != null ? stickerSet.documents.size() : 0;
        int heightDp;
        if (count <= 0) {
            heightDp = GRID_MIN_HEIGHT_DP;
        } else {
            int rows = (count + GRID_COLUMNS - 1) / GRID_COLUMNS;
            heightDp = rows * GRID_CELL_DP + GRID_PADDING_DP * 2;
        }
        ViewGroup.LayoutParams params = gridFrame.getLayoutParams();
        params.height = AndroidUtilities.dp(heightDp);
        gridFrame.setLayoutParams(params);
    }

    private void applyPackIcon() {
        TLRPC.Document first = pack.getFirstSticker();
        if (stickerSet != null && stickerSet.set != null && !stickerSet.set.thumbs.isEmpty()) {
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(stickerSet.set.thumbs, 160);
            iconView.setImage(ImageLocation.getForSticker(thumb, first, stickerSet.set.thumb_version), "160_160", (android.graphics.drawable.Drawable) null, stickerSet);
        } else if (first != null) {
            var svgThumb = DocumentObject.getSvgThumb(first, Theme.key_windowBackgroundGray, 1.0f);
            iconView.setImage(ImageLocation.getForDocument(first), "160_160", svgThumb, stickerSet);
        }
    }

    private int packType() {
        if (stickerSet != null && stickerSet.set != null) {
            if (stickerSet.set.masks) return MediaDataController.TYPE_MASK;
            if (stickerSet.set.emojis) return MediaDataController.TYPE_EMOJIPACKS;
        }
        return MediaDataController.TYPE_IMAGE;
    }

    /** Is it in the user's installed list right now, per Telegram's own sticker data. */
    private boolean isInstalledInList() {
        MediaDataController mdc = MediaDataController.getInstance(currentAccount);
        return mdc.isStickerPackInstalled(stickerSet.set.id) || mdc.isStickerPackInstalled(pack.shortName);
    }

    /**
     * Why the old button could say "Add" for a pack the user already had: the installed
     * list lives in MediaDataController and is empty until it has been loaded, so on a cold
     * start isStickerPackInstalled() was simply false for everything. (Nothing to do with
     * our backend being slow - the installed state comes from Telegram, never from our API.)
     * Now: ask it to load, and listen for stickersDidLoad (see didReceivedNotification) to
     * refresh. Until the list has loaded, fall back to the flag Telegram attached to the
     * set itself (installed_date).
     */
    private boolean isInstalledNow() {
        if (installedOverride != null) {
            return installedOverride;
        }
        if (isInstalledInList()) {
            return true;
        }
        if (!MediaDataController.getInstance(currentAccount).areStickersLoaded(packType())) {
            return stickerSet.set.installed_date != 0;
        }
        return false;
    }

    private void styleActionButton(boolean installed, boolean ready) {
        int radius = AndroidUtilities.dp(22);
        if (!ready) {
            actionButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            actionButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(radius, Theme.getColor(Theme.key_windowBackgroundGray), Theme.getColor(Theme.key_windowBackgroundGray)));
        } else if (installed) {
            actionButton.setTextColor(Theme.getColor(Theme.key_text_RedBold));
            actionButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(radius, Theme.getColor(Theme.key_windowBackgroundGray), Theme.getColor(Theme.key_listSelector)));
        } else {
            actionButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            actionButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(radius, Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        }
    }

    private void updateActionButton() {
        if (stickerSet == null || stickerSet.set == null) {
            actionButton.setText("Loading…");
            actionButton.setEnabled(false);
            styleActionButton(false, false);
            installedBadge.setVisibility(View.GONE);
            return;
        }
        boolean installed = isInstalledNow();
        actionButton.setEnabled(true);
        installedBadge.setVisibility(installed ? View.VISIBLE : View.GONE);
        styleActionButton(installed, true);
        if (installed) {
            actionButton.setText("Remove");
            actionButton.setOnClickListener(v -> removeStickerSet());
        } else {
            actionButton.setText("Add to Sticker");
            actionButton.setOnClickListener(v -> installStickerSet());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // The install state can change elsewhere (another screen, another device) while
        // this one is backgrounded - re-check it rather than trusting whatever it showed
        // when the screen first opened.
        if (stickerSet != null) {
            updateActionButton();
        }
    }

    private void installStickerSet() {
        if (stickerSet == null || stickerSet.set == null) return;
        actionButton.setEnabled(false);
        TLRPC.TL_messages_installStickerSet req = new TLRPC.TL_messages_installStickerSet();
        TLRPC.TL_inputStickerSetShortName inputStickerSet = new TLRPC.TL_inputStickerSetShortName();
        inputStickerSet.short_name = pack.shortName;
        req.stickerset = inputStickerSet;
        final int type = stickerSet.set.masks ? MediaDataController.TYPE_MASK
                : stickerSet.set.emojis ? MediaDataController.TYPE_EMOJIPACKS
                : MediaDataController.TYPE_IMAGE;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            actionButton.setEnabled(true);
            if (error == null) {
                installedOverride = Boolean.TRUE;
                MediaDataController.getInstance(currentAccount).loadStickers(type, false, true);
                updateActionButton();
            } else {
                FileLog.e("StickerPackDetailActivity: install failed " + error.text);
            }
        }));
    }

    private void removeStickerSet() {
        if (stickerSet == null) return;
        actionButton.setEnabled(false);
        MediaDataController.getInstance(currentAccount).toggleStickerSet(getContext(), stickerSet, 0, this, true, true);
        installedOverride = Boolean.FALSE;
        AndroidUtilities.runOnUIThread(() -> {
            actionButton.setEnabled(true);
            updateActionButton();
        }, 300);
    }

    private class StickerAdapter extends RecyclerListView.SelectionAdapter {
        @Override
        public int getItemCount() {
            return stickerSet != null && stickerSet.documents != null ? stickerSet.documents.size() : 0;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            StickerEmojiCell cell = new StickerEmojiCell(parent.getContext(), false, null);
            cell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(80)));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            TLRPC.Document document = stickerSet.documents.get(position);
            ((StickerEmojiCell) holder.itemView).setSticker(document, stickerSet, false);
        }
    }
}
