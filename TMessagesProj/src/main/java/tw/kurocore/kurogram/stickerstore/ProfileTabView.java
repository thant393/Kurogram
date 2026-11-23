package tw.kurocore.kurogram.stickerstore;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Outline;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.messenger.R;
import tw.kurocore.kurogram.settings.GroupCardHelper;

public class ProfileTabView extends FrameLayout implements org.telegram.messenger.NotificationCenter.NotificationCenterDelegate {

    private static final int TAB_THEMES = 0;
    private static final int TAB_STICKERS = 1;

    /** Bumped when the user uploads a pack, so a profile screen coming back into view reloads. */
    private static int uploadVersion;

    public static void notifyPackUploaded() {
        uploadVersion++;
    }

    private final int currentAccount;
    private final BaseFragment fragment;
    private final SegmentedControl segmented;
    private final FrameLayout contentFrame;
    private ThemesTabView themesView;
    private MyStickersView stickersView;
    private TextView statCount, statCountLabel, statDownloads, statLikes;
    private int selectedTab = TAB_THEMES;
    private boolean segmentedReady;
    private int seenUploadVersion;
    private int bottomInset;
    private final long ownerId;
    private GradientDrawable cardBg;
    private GradientDrawable contentBg;
    private View headerCard;
    private GradientDrawable avatarRingDrawable;
    private TextView usernameViewRef;
    private View contentCard;

    /** The signed-in user's own profile (with the upload + button). */
    public ProfileTabView(Context context, BaseFragment fragment, int currentAccount) {
        this(context, fragment, currentAccount, UserConfig.getInstance(currentAccount).getClientUserId(), null, null);
    }

    /**
     * Profile of any sticker-pack owner. When ownerId is the signed-in user this is the full
     * own-profile view (phone number, + button, own un-uploaded packs); for anyone else it is
     * read-only: their uploaded content only. fallbackName/fallbackUsername are the backend's
     * owner info, used while the user isn't known to Telegram locally.
     */
    public ProfileTabView(Context context, BaseFragment fragment, int currentAccount, long ownerId, String fallbackName, String fallbackUsername) {
        super(context);
        this.fragment = fragment;
        this.currentAccount = currentAccount;
        this.ownerId = ownerId;
        this.self = ownerId == UserConfig.getInstance(currentAccount).getClientUserId();
        // A touch darker than the theme's grey so the white surfaces read clearly against it.
        tw.kurocore.kurogram.skin.KuroUi.applyBackground(this, tw.kurocore.kurogram.skin.KuroUi.t().profileBackground);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        TLRPC.User user = self
                ? UserConfig.getInstance(currentAccount).getCurrentUser()
                : org.telegram.messenger.MessagesController.getInstance(currentAccount).getUser(ownerId);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        cardBg.setCornerRadius(AndroidUtilities.dp(skinRadius()));
        cardBg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        card.setBackground(cardBg);
        card.setElevation(AndroidUtilities.dp(cardElevationDp()));
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), AndroidUtilities.dp(skinRadius()));
            }
        });
        headerCard = card;
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        column.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 8, 12, 8));

        BackupImageView avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(AndroidUtilities.dp(44));
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        if (user != null) {
            avatarDrawable.setInfo(currentAccount, user);
            avatarView.setForUserOrChat(user, avatarDrawable);
        } else {
            avatarDrawable.setInfo(ownerId, fallbackName, null);
            avatarView.setImageDrawable(avatarDrawable);
        }
        FrameLayout avatarRing = new FrameLayout(context);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        avatarRingDrawable = ring;
        ring.setStroke(AndroidUtilities.dp(2.5f), tw.kurocore.kurogram.skin.KuroSkinManager.get().accent());
        avatarRing.setBackground(ring);
        int ringDp = Math.min(76, tw.kurocore.kurogram.skin.KuroSkinManager.get().spec().intValue("screens.profile.avatarSizeDp", 100));
        avatarView.setRoundRadius(AndroidUtilities.dp((ringDp - 12) / 2f));
        avatarRing.addView(avatarView, LayoutHelper.createFrame(ringDp - 12, ringDp - 12, Gravity.CENTER));
        card.addView(avatarRing, LayoutHelper.createLinear(ringDp, ringDp, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 8));

        TextView nameView = new TextView(context);
        nameView.setTextSize(20);
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setGravity(Gravity.CENTER);
        nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        // Someone else's profile: the stored global name first (a cached user may carry the
        // viewer's own contact nickname for them).
        nameView.setText(!self && fallbackName != null && !fallbackName.isEmpty() ? fallbackName
                : user != null ? UserObject.getUserName(user)
                : fallbackName != null && !fallbackName.isEmpty() ? fallbackName
                : fallbackUsername != null && !fallbackUsername.isEmpty() ? "@" + fallbackUsername
                : self ? "Not signed in" : "ID " + ownerId);
        card.addView(nameView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 3));

        String username = user != null ? user.username : fallbackUsername;
        if (username != null && !username.isEmpty()) {
            TextView usernameView = new TextView(context);
            usernameView.setTextSize(15);
            usernameView.setTypeface(AndroidUtilities.bold());
            usernameView.setGravity(Gravity.CENTER);
            usernameViewRef = usernameView;
            usernameView.setTextColor(tw.kurocore.kurogram.skin.KuroSkinManager.get().accent());
            usernameView.setText("@" + username);
            card.addView(usernameView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 4));
        }

        if (self && user != null && user.phone != null && !user.phone.isEmpty()) {
            TextView phoneView = new TextView(context);
            phoneView.setTextSize(13);
            phoneView.setGravity(Gravity.CENTER);
            phoneView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            phoneView.setAlpha(0.85f);
            phoneView.setText(PhoneFormat.getInstance().format("+" + user.phone));
            card.addView(phoneView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        }

        // Creator dashboard: totals of what this person has published (own profile only; visitors see the plain profile)
        if (self) {
            LinearLayout stats = new LinearLayout(context);
            stats.setOrientation(LinearLayout.HORIZONTAL);
            stats.setGravity(Gravity.CENTER);
            statCount = new TextView(context);
            statCountLabel = new TextView(context);
            statDownloads = new TextView(context);
            statLikes = new TextView(context);
            stats.addView(statBox(context, statCount, statCountLabel, "Themes"), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            stats.addView(statBox(context, statDownloads, null, "Downloads"), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            stats.addView(statBox(context, statLikes, null, "Likes"), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            card.addView(stats, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 12, 4, 0));
            showStats(0, 0, 0);
        }

        // Lower half: one rounded white surface (same shape as the header card) holding the
        // segmented control and the content, so the screen reads as two matching cards.
        LinearLayout contentCard = new LinearLayout(context);
        contentCard.setOrientation(LinearLayout.VERTICAL);
        contentBg = new GradientDrawable();
        contentBg.setShape(GradientDrawable.RECTANGLE);
        contentBg.setCornerRadius(AndroidUtilities.dp(skinRadius()));
        contentBg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        contentCard.setBackground(contentBg);
        contentCard.setElevation(AndroidUtilities.dp(cardElevationDp()));
        contentCard.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), AndroidUtilities.dp(skinRadius()));
            }
        });
        contentCard.setClipToOutline(true);
        this.contentCard = contentCard;
        column.addView(contentCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f, 12, 0, 12, 12));

        // --- iOS-style segmented control: a capsule track with a white capsule that slides ---
        segmented = new SegmentedControl(context, new String[]{"Themes", "Stickers"}, this::showTab);
        contentCard.addView(segmented, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 14, 14, 14, 10));

        contentFrame = new FrameLayout(context);
        contentCard.addView(contentFrame, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        // Themes: this owner's store themes (created lazily the first time the tab shows).
        // Stickers: the user's uploaded packs (created lazily the first time the tab shows).
        seenUploadVersion = uploadVersion;
        showTab(TAB_THEMES);
    }

    private LinearLayout statBox(Context context, TextView number, TextView labelHolder, String label) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        number.setTextSize(18);
        number.setTypeface(AndroidUtilities.bold());
        number.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        number.setGravity(Gravity.CENTER);
        TextView l = labelHolder != null ? labelHolder : new TextView(context);
        l.setText(label);
        l.setTextSize(12);
        l.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        l.setGravity(Gravity.CENTER);
        box.addView(number);
        box.addView(l);
        return box;
    }

    private void showStats(int count, long downloads, long likes) {
        if (statCount == null) {
            return;
        }
        statCount.setText(CountFormat.format(count));
        statCountLabel.setText(selectedTab == TAB_THEMES ? "Themes" : "Stickers");
        statDownloads.setText(CountFormat.format((int) Math.min(Integer.MAX_VALUE, downloads)));
        statLikes.setText(CountFormat.format((int) Math.min(Integer.MAX_VALUE, likes)));
    }

    private final boolean self;
    private long[] themeStats = {0, 0, 0}, stickerStats = {0, 0, 0};

    private void newItem() {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        if (selectedTab == TAB_THEMES) {
            QuickStartSheet.show(fragment);
        } else {
            fragment.presentFragment(new StickerUploadActivity());
        }
    }

    private void showTab(int index) {
        selectedTab = index;
        segmented.select(index, segmentedReady);
        segmentedReady = true;
        contentFrame.removeAllViews();
        if (index == TAB_THEMES) {
            if (themesView == null) {
                themesView = new ThemesTabView(getContext(), fragment, ownerId, ownerId == UserConfig.getInstance(currentAccount).getClientUserId()
                        ? "No themes yet." : "No themes uploaded yet.");
                themesView.setInset(true);
                if (self) {
                    themesView.setOwnerMode(this::newItem, (n, d, l) -> {
                        themeStats = new long[]{n, d, l};
                        if (selectedTab == TAB_THEMES) {
                            showStats(n, d, l);
                        }
                    });
                }
                themesView.setBottomInset(AndroidUtilities.dp(92) + bottomInset);
            }
            contentFrame.addView(themesView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        } else {
            if (stickersView == null) {
                stickersView = new MyStickersView(getContext(), fragment, ownerId);
                if (self) {
                    stickersView.setOwnerMode(this::newItem, (n, d, l) -> {
                        stickerStats = new long[]{n, d, l};
                        if (selectedTab == TAB_STICKERS) {
                            showStats(n, d, l);
                        }
                    });
                }
                stickersView.setBottomInset(AndroidUtilities.dp(92) + bottomInset);
            }
            contentFrame.addView(stickersView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
        long[] st = index == TAB_THEMES ? themeStats : stickerStats;
        showStats((int) st[0], st[1], st[2]);
        contentFrame.setAlpha(0f);
        contentFrame.animate().alpha(1f).setDuration(tw.kurocore.kurogram.skin.KuroUi.anim(150)).start();
    }

    private static int skinRadius() {
        return tw.kurocore.kurogram.skin.KuroUi.t().profileCardRadiusDp;
    }

    private static int cardElevationDp() {
        return tw.kurocore.kurogram.skin.KuroUi.t().cardElevationDp;
    }

    /** Page colour behind the two cards: the skin's profile background, else a touch darker than the theme grey. */
    private static int screenBackground() {
        int c = tw.kurocore.kurogram.skin.KuroUi.t().profileBackground;
        return c != 0 ? c : androidx.core.graphics.ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundGray), 0xFF000000, 0.04f);
    }

    /** Re-applies the active skin to this screen's surfaces without rebuilding it. */
    private void applySkin() {
        int r = AndroidUtilities.dp(skinRadius());
        tw.kurocore.kurogram.skin.KuroUi.applyBackground(this, tw.kurocore.kurogram.skin.KuroUi.t().profileBackground);
        cardBg.setCornerRadius(r);
        contentBg.setCornerRadius(r);
        cardBg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        contentBg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        headerCard.setElevation(AndroidUtilities.dp(cardElevationDp()));
        contentCard.setElevation(AndroidUtilities.dp(cardElevationDp()));
        int accent = tw.kurocore.kurogram.skin.KuroSkinManager.get().accent();
        if (avatarRingDrawable != null) avatarRingDrawable.setStroke(AndroidUtilities.dp(2.5f), accent);
        if (usernameViewRef != null) usernameViewRef.setTextColor(accent);
        segmented.select(selectedTab, false);
        headerCard.invalidateOutline();
        contentCard.invalidateOutline();
        if (stickersView != null) {
            stickersView.applySkin();
        }
        if (themesView != null) {
            themesView.applySkin();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == org.telegram.messenger.NotificationCenter.kuroSkinChanged) {
            applySkin();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        org.telegram.messenger.NotificationCenter.getGlobalInstance().removeObserver(this, org.telegram.messenger.NotificationCenter.kuroSkinChanged);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        org.telegram.messenger.NotificationCenter.getGlobalInstance().addObserver(this, org.telegram.messenger.NotificationCenter.kuroSkinChanged);
        applySkin();
        // Coming back from the upload screen after adding a pack: show it.
        if (seenUploadVersion != uploadVersion) {
            seenUploadVersion = uploadVersion;
            if (stickersView != null) {
                stickersView.reload();
            }
            if (themesView != null) {
                themesView.reload();
            }
        }
    }

    /** Room to leave at the bottom so content (and the + button) never sit under the floating tab bar. */
    public void setBottomInset(int px) {
        bottomInset = px;
        if (stickersView != null) {
            stickersView.setBottomInset(AndroidUtilities.dp(92) + px);
        }
        if (themesView != null) {
            themesView.setBottomInset(AndroidUtilities.dp(92) + px);
        }
    }

    /** iOS-style segmented control: a grey capsule track and a white capsule with a soft shadow that glides under the chosen label. */
    private static class SegmentedControl extends FrameLayout {

        interface OnSelect {
            void onSelect(int index);
        }

        private final TextView[] labels;
        private final android.graphics.Paint trackPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint thumbPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF box = new android.graphics.RectF();
        private float pos; // 0..labels-1, animated
        private int selected = -1;
        private android.animation.ValueAnimator anim;

        SegmentedControl(Context context, String[] titles, OnSelect listener) {
            super(context);
            setWillNotDraw(false);
            setLayerType(LAYER_TYPE_SOFTWARE, null); // soft shadow under the thumb
            labels = new TextView[titles.length];
            int pad = AndroidUtilities.dp(3);
            setPadding(pad, pad, pad, pad);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            addView(row, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            for (int i = 0; i < titles.length; i++) {
                final int index = i;
                TextView label = new TextView(context);
                label.setText(titles[i]);
                label.setTextSize(14);
                label.setTypeface(AndroidUtilities.bold());
                label.setGravity(Gravity.CENTER);
                label.setOnClickListener(v -> listener.onSelect(index));
                row.addView(label, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));
                labels[i] = label;
            }
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            float w = getWidth(), h = getHeight();
            if (w <= 0) {
                return;
            }
            int text = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
            trackPaint.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(text, 0x16));
            box.set(0, 0, w, h);
            canvas.drawRoundRect(box, h / 2f, h / 2f, trackPaint);
            float pad = getPaddingLeft();
            float each = (w - 2 * pad) / labels.length;
            float left = pad + pos * each;
            box.set(left, pad, left + each, h - pad);
            thumbPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            thumbPaint.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(1), 0x33000000);
            canvas.drawRoundRect(box, (h - 2 * pad) / 2f, (h - 2 * pad) / 2f, thumbPaint);
        }

        void select(int index, boolean animate) {
            selected = index;
            if (anim != null) {
                anim.cancel();
            }
            if (animate && getWidth() > 0) {
                anim = android.animation.ValueAnimator.ofFloat(pos, index);
                anim.setDuration(tw.kurocore.kurogram.skin.KuroUi.anim(240));
                anim.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
                anim.addUpdateListener(a -> {
                    pos = (float) a.getAnimatedValue();
                    invalidate();
                });
                anim.start();
            } else {
                pos = index;
                invalidate();
            }
            for (int i = 0; i < labels.length; i++) {
                labels[i].setTextColor(i == index ? Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)
                        : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            }
        }
    }
}
