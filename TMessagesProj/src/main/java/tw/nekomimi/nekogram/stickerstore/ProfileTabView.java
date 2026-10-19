package tw.nekomimi.nekogram.stickerstore;

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
import tw.nekomimi.nekogram.settings.GroupCardHelper;

public class ProfileTabView extends FrameLayout {

    private static final int TAB_THEMES = 0;
    private static final int TAB_STICKERS = 1;

    /** Bumped when the user uploads a pack, so a profile screen coming back into view reloads. */
    private static int uploadVersion;

    public static void notifyPackUploaded() {
        uploadVersion++;
    }

    private final int currentAccount;
    private final BaseFragment fragment;
    private final LinearLayout[] tabButtons = new LinearLayout[2];
    private final TextView[] tabLabels = new TextView[2];
    private final View[] tabIndicators = new View[2];
    private final FrameLayout contentFrame;
    private final View themesEmpty;
    private MyStickersView stickersView;
    private final ImageView fab;
    private int selectedTab = TAB_STICKERS;
    private int seenUploadVersion;
    private int bottomInset;
    private final long ownerId;

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
        final boolean self = ownerId == UserConfig.getInstance(currentAccount).getClientUserId();
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        TLRPC.User user = self
                ? UserConfig.getInstance(currentAccount).getCurrentUser()
                : org.telegram.messenger.MessagesController.getInstance(currentAccount).getUser(ownerId);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(GroupCardHelper.cardBackground(context, GroupCardHelper.POS_SINGLE));
        card.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(24), AndroidUtilities.dp(24), AndroidUtilities.dp(24));
        column.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 16, 16, 8));

        BackupImageView avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(AndroidUtilities.dp(40));
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        if (user != null) {
            avatarDrawable.setInfo(currentAccount, user);
            avatarView.setForUserOrChat(user, avatarDrawable);
        } else {
            avatarDrawable.setInfo(ownerId, fallbackName, null);
            avatarView.setImageDrawable(avatarDrawable);
        }
        card.addView(avatarView, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 12));

        TextView nameView = new TextView(context);
        nameView.setTextSize(18);
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setGravity(Gravity.CENTER);
        nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameView.setText(user != null ? UserObject.getUserName(user)
                : fallbackName != null && !fallbackName.isEmpty() ? fallbackName
                : fallbackUsername != null && !fallbackUsername.isEmpty() ? "@" + fallbackUsername
                : self ? "Not signed in" : "ID " + ownerId);
        card.addView(nameView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 4));

        String username = user != null ? user.username : fallbackUsername;
        if (username != null && !username.isEmpty()) {
            TextView usernameView = new TextView(context);
            usernameView.setTextSize(14);
            usernameView.setGravity(Gravity.CENTER);
            usernameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            usernameView.setText("@" + username);
            card.addView(usernameView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 4));
        }

        if (self && user != null && user.phone != null && !user.phone.isEmpty()) {
            TextView phoneView = new TextView(context);
            phoneView.setTextSize(14);
            phoneView.setGravity(Gravity.CENTER);
            phoneView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            phoneView.setText(PhoneFormat.getInstance().format("+" + user.phone));
            card.addView(phoneView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        }

        // --- tabs: Themes | Stickers (tap to switch) ---
        LinearLayout tabStrip = new LinearLayout(context);
        tabStrip.setOrientation(LinearLayout.HORIZONTAL);
        column.addView(tabStrip, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 16, 0, 16, 0));
        addTab(context, tabStrip, TAB_THEMES, "Themes");
        addTab(context, tabStrip, TAB_STICKERS, "Stickers");

        contentFrame = new FrameLayout(context);
        column.addView(contentFrame, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        // Themes: not implemented yet - an empty state.
        FrameLayout themes = new FrameLayout(context);
        TextView themesText = new TextView(context);
        themesText.setText("No themes uploaded yet");
        themesText.setTextSize(15);
        themesText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        themes.addView(themesText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        themesEmpty = themes;

        // Stickers: the user's uploaded packs (created lazily the first time the tab shows).
        // Circular "+" button: opens the upload screen.
        fab = new ImageView(context);
        fab.setImageResource(R.drawable.kuro_plus);
        fab.setScaleType(ImageView.ScaleType.CENTER);
        fab.setColorFilter(0xFFFFFFFF);
        GradientDrawable fabBg = new GradientDrawable();
        fabBg.setShape(GradientDrawable.OVAL);
        fabBg.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        fab.setBackground(fabBg);
        fab.setElevation(AndroidUtilities.dp(6));
        fab.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        fab.setContentDescription("Upload");
        fab.setOnClickListener(v -> {
            if (fragment != null && fragment.getParentActivity() != null) {
                fragment.presentFragment(new UploadHubActivity());
            }
        });
        addView(fab, LayoutHelper.createFrame(56, 56, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 20, 20));
        fab.setVisibility(self ? VISIBLE : GONE);

        seenUploadVersion = uploadVersion;
        showTab(TAB_STICKERS);
    }

    private void addTab(Context context, LinearLayout strip, int index, String title) {
        LinearLayout tab = new LinearLayout(context);
        tab.setOrientation(LinearLayout.VERTICAL);
        tab.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView label = new TextView(context);
        label.setText(title);
        label.setTextSize(15);
        label.setTypeface(AndroidUtilities.bold());
        label.setGravity(Gravity.CENTER);
        tab.addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 0, 1f, Gravity.CENTER_HORIZONTAL));

        View indicator = new View(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(2));
        bg.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        indicator.setBackground(bg);
        tab.addView(indicator, LayoutHelper.createLinear(40, 3, Gravity.CENTER_HORIZONTAL));

        tab.setOnClickListener(v -> showTab(index));
        strip.addView(tab, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));
        tabButtons[index] = tab;
        tabLabels[index] = label;
        tabIndicators[index] = indicator;
    }

    private void showTab(int index) {
        selectedTab = index;
        for (int i = 0; i < tabButtons.length; i++) {
            boolean on = i == index;
            tabLabels[i].setTextColor(on
                    ? Theme.getColor(Theme.key_featuredStickers_addButton)
                    : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            tabIndicators[i].animate().alpha(on ? 1f : 0f).setDuration(150).start();
        }
        contentFrame.removeAllViews();
        if (index == TAB_THEMES) {
            contentFrame.addView(themesEmpty, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        } else {
            if (stickersView == null) {
                stickersView = new MyStickersView(getContext(), fragment, ownerId);
                stickersView.setBottomInset(AndroidUtilities.dp(92) + bottomInset);
            }
            contentFrame.addView(stickersView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
        contentFrame.setAlpha(0f);
        contentFrame.animate().alpha(1f).setDuration(150).start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Coming back from the upload screen after adding a pack: show it.
        if (seenUploadVersion != uploadVersion) {
            seenUploadVersion = uploadVersion;
            if (stickersView != null) {
                stickersView.reload();
            }
        }
    }

    /** Room to leave at the bottom so content (and the + button) never sit under the floating tab bar. */
    public void setBottomInset(int px) {
        bottomInset = px;
        MarginLayoutParams lp = (MarginLayoutParams) fab.getLayoutParams();
        lp.bottomMargin = px + AndroidUtilities.dp(16);
        fab.setLayoutParams(lp);
        if (stickersView != null) {
            stickersView.setBottomInset(AndroidUtilities.dp(92) + px);
        }
    }
}
