package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import tw.nekomimi.nekogram.stickerstore.KuroBarStyle;
import tw.nekomimi.nekogram.stickerstore.ProfileTabView;
import tw.nekomimi.nekogram.stickerstore.StickersTabView;

public class KuroThemeActivity extends BaseFragment {

    private static final int TAB_THEMES = 0;
    private static final int TAB_STICKERS = 1;
    private static final int TAB_PROFILE = 2;

    // iOS-style floating tab bar: a rounded, translucent pill inset from the screen edges.
    // The look (fill, border, sizes) lives in KuroBarStyle so every Kuro floating bar
    // matches, and every tab's content runs underneath it.

    private FrameLayout contentFrame;
    private View[] tabContents = new View[3];
    private TabButton[] tabButtons = new TabButton[3];
    private int selectedTab = TAB_THEMES;

    private static class TabButton extends LinearLayout {
        ImageView icon;
        TextView label;

        TabButton(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);

            icon = new ImageView(context);
            addView(icon, LayoutHelper.createLinear(24, 24, Gravity.CENTER_HORIZONTAL, 0, 6, 0, 2));

            label = new TextView(context);
            label.setTextSize(11);
            label.setGravity(Gravity.CENTER);
            addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        }

        void applySelected(boolean selected) {
            int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
            int tint = selected ? accent : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
            icon.setColorFilter(new PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN));
            label.setTextColor(tint);
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Kuro Theme");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout rootLayout = new FrameLayout(context);
        fragmentView = rootLayout;
        rootLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        // Content fills the whole screen and runs UNDER the translucent bar - each tab
        // leaves its own bottom inset (see createTabContent) so its last row can still be
        // scrolled fully above the bar.
        contentFrame = new FrameLayout(context);
        rootLayout.addView(contentFrame, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout tabBar = new LinearLayout(context);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setBackground(KuroBarStyle.background());
        // Clip children (ripples/highlights) to the rounded outline; no elevation, since a
        // drop shadow would show through the translucent fill.
        tabBar.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        tabBar.setClipToOutline(true);

        rootLayout.addView(tabBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, KuroBarStyle.BAR_HEIGHT_DP, Gravity.BOTTOM,
                KuroBarStyle.SIDE_MARGIN_DP, 0, KuroBarStyle.SIDE_MARGIN_DP, KuroBarStyle.BOTTOM_MARGIN_DP));

        addTabButton(context, tabBar, TAB_THEMES, R.drawable.msg_theme, "Themes");
        addTabButton(context, tabBar, TAB_STICKERS, R.drawable.msg_sticker, "Stickers");
        addTabButton(context, tabBar, TAB_PROFILE, R.drawable.msg_contacts, "Profile");

        selectTab(TAB_THEMES);

        return fragmentView;
    }

    private void addTabButton(Context context, LinearLayout tabBar, int index, int iconRes, String text) {
        TabButton button = new TabButton(context);
        button.icon.setImageResource(iconRes);
        button.label.setText(text);
        button.setOnClickListener(v -> selectTab(index));
        tabBar.addView(button, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));
        tabButtons[index] = button;
    }

    private void selectTab(int index) {
        if (selectedTab != index || tabContents[index] == null) {
            selectedTab = index;
            showTabContent(index);
        }
        for (int i = 0; i < tabButtons.length; i++) {
            if (tabButtons[i] != null) {
                tabButtons[i].applySelected(i == index);
            }
        }
    }

    private void showTabContent(int index) {
        contentFrame.removeAllViews();
        if (tabContents[index] == null) {
            tabContents[index] = createTabContent(index);
        }
        if (tabContents[index].getParent() instanceof FrameLayout) {
            ((FrameLayout) tabContents[index].getParent()).removeView(tabContents[index]);
        }
        contentFrame.addView(tabContents[index], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    private View createTabContent(int index) {
        Context context = contentFrame.getContext();
        switch (index) {
            case TAB_STICKERS: {
                StickersTabView stickersTab = new StickersTabView(context, this);
                stickersTab.setBottomInset(AndroidUtilities.dp(KuroBarStyle.CONTENT_INSET_DP));
                return stickersTab;
            }
            case TAB_PROFILE: {
                ProfileTabView profileTab = new ProfileTabView(context, currentAccount);
                profileTab.setBottomInset(AndroidUtilities.dp(KuroBarStyle.CONTENT_INSET_DP));
                return profileTab;
            }
            case TAB_THEMES:
            default:
                return createComingSoonView(context);
        }
    }

    private View createComingSoonView(Context context) {
        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        TextView textView = new TextView(context);
        textView.setText("Coming soon");
        textView.setTextSize(16);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        frameLayout.addView(textView, params);

        return frameLayout;
    }
}
