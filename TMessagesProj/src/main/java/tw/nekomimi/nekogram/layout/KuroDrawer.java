package tw.nekomimi.nekogram.layout;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.CallLogActivity;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.Premium.boosts.UserSelectorBottomSheet;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.TON.TONIntroActivity;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BirthdayController;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ContactsActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.SettingsActivity;

import java.util.List;

/**
 * A modal start-side drawer opened by a layout pack. The pack only picks which of Telegram's own
 * destinations appear (drawer.items); every item opens an existing screen, nothing is created here.
 */
public final class KuroDrawer {

    private static final String[] DEFAULT_ITEMS = {"profile", "settings", "contacts", "saved", "archive"};

    private KuroDrawer() {
    }

    /** The items the active layout wants, or null when it does not ask for a drawer. */
    public static List<String> items() {
        return LayoutManager.get().spec().listValue("drawer.items", null);
    }

    public static boolean wantsMenuButton() {
        List<String> left = LayoutManager.get().spec().listValue("chatList.header.left", null);
        return left != null && left.contains("menu") && (items() != null || showsSettings());
    }

    /** drawer.content = "settings": the drawer is Telegram's own Settings page. */
    private static boolean showsSettings() {
        return "settings".equals(LayoutManager.get().spec().stringValue("drawer.content", null));
    }

    private static void edgeToEdge(Window w) {
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        w.setDimAmount(0.45f);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, false);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            w.setAttributes(lp);
        }
    }

    /** The whole Settings page, hosted in a start-side panel that runs from the top edge to the bottom edge. */
    private static void showSettingsPage(final BaseFragment from) {
        final Context context = from.getParentActivity();
        final Dialog[] ref = new Dialog[1];
        final SettingsActivity[] holder = new SettingsActivity[1];
        final SettingsActivity page = new SettingsActivity(new Bundle()) {
            @Override
            public boolean presentFragment(BaseFragment fragment) {
                dismissDrawer(ref, holder);
                return from.presentFragment(fragment);
            }

            @Override
            public boolean presentFragment(BaseFragment fragment, boolean removeLast) {
                dismissDrawer(ref, holder);
                return from.presentFragment(fragment, removeLast);
            }

            @Override
            public void finishFragment() {
                dismissDrawer(ref, holder);
            }

            @Override
            public boolean finishFragment(boolean animated) {
                dismissDrawer(ref, holder);
                return true;
            }
        };
        holder[0] = page;

        final Dialog dialog = new Dialog(context);
        ref[0] = dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout root = new FrameLayout(context);
        root.setOnClickListener(v -> dialog.dismiss());
        final int width = Math.min(AndroidUtilities.dp(360), (int) (AndroidUtilities.displaySize.x * 0.88f));
        final FrameLayout panel = new FrameLayout(context);
        panel.setClickable(true);
        panel.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        root.addView(panel, new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));

        page.onFragmentCreate();
        page.setParentLayout(from.getParentLayout());
        page.performCreateView(context);
        View content = page.getFragmentView();
        AndroidUtilities.removeFromParent(content);
        panel.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        if (page.getActionBar() != null && page.getActionBar().shouldAddToContainer()) {
            AndroidUtilities.removeFromParent(page.getActionBar());
            panel.addView(page.getActionBar());
        }

        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> {
            try {
                page.onPause();
                page.onFragmentDestroy();
            } catch (Exception ignored) {
            }
        });
        Window w = dialog.getWindow();
        if (w != null) {
            edgeToEdge(w);
        }
        panel.setTranslationX(-width);
        dialog.show();
        androidx.core.view.ViewCompat.requestApplyInsets(panel);
        page.onResume();
        panel.animate().translationX(0).setDuration(200).start();
    }

    private static void dismissDrawer(Dialog[] ref, SettingsActivity[] holder) {
        if (ref[0] != null && ref[0].isShowing()) {
            ref[0].dismiss();
        }
    }

    public static void show(final BaseFragment from) {
        if (from == null || from.getParentActivity() == null) return;
        if (showsSettings()) {
            showSettingsPage(from);
            return;
        }
        List<String> items = items();
        if (items == null || items.isEmpty()) return;
        final Context context = from.getParentActivity();
        final int account = from.getCurrentAccount();

        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout root = new FrameLayout(context);
        root.setOnClickListener(v -> dialog.dismiss());

        final int width = Math.min(AndroidUtilities.dp(320), (int) (AndroidUtilities.displaySize.x * 0.86f));
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        panel.setClickable(true);
        panel.setFitsSystemWindows(false);
        root.addView(panel, new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));

        // Profile header
        TLRPC.User me = MessagesController.getInstance(account).getUser(UserConfig.getInstance(account).getClientUserId());
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(48), AndroidUtilities.dp(20), AndroidUtilities.dp(16));
        BackupImageView avatar = new BackupImageView(context);
        avatar.setRoundRadius(AndroidUtilities.dp(32));
        if (me != null) {
            avatar.setForUserOrChat(me, new AvatarDrawable(me));
        }
        header.addView(avatar, LayoutHelper.createLinear(64, 64));
        TextView name = new TextView(context);
        name.setTextSize(18);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        name.setSingleLine(true);
        name.setText(me == null ? "" : UserObject.getUserName(me));
        header.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));
        String un = me == null ? null : UserObject.getPublicUsername(me);
        if (un != null && !un.isEmpty()) {
            TextView user = new TextView(context);
            user.setTextSize(14);
            user.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            user.setText("@" + un);
            header.addView(user, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }
        panel.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(context);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        avatar.setOnClickListener(v -> {
            dialog.dismiss();
            open(from, account, "profile");
        });

        for (String id : items) {
            final Entry e = entry(id);
            if (e == null || !available(id, account)) continue;
            SettingsActivity.SettingCell row = new SettingsActivity.SettingCell(context, null);
            row.set(e.top, e.bottom, e.icon, e.title(), e.subtitle(), null);
            row.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));
            row.setContentDescription(e.title());
            row.setOnClickListener(v -> {
                dialog.dismiss();
                open(from, account, id);
            });
            list.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            edgeToEdge(w);
        }
        panel.setTranslationX(-width);
        dialog.show();
        panel.animate().translationX(0).setDuration(200).start();
    }

    private static final class Entry {
        final int top;
        final int bottom;
        final int icon;
        final String text;
        final int titleRes;
        final int subtitleRes;

        Entry(IconBackgroundColors color, int icon, String text, int titleRes, int subtitleRes) {
            this(color.top, color.bottom, icon, text, titleRes, subtitleRes);
        }

        Entry(int top, int bottom, int icon, String text, int titleRes, int subtitleRes) {
            this.top = top;
            this.bottom = bottom;
            this.icon = icon;
            this.text = text;
            this.titleRes = titleRes;
            this.subtitleRes = subtitleRes;
        }

        String title() {
            return text != null ? text : LocaleController.getString(titleRes);
        }

        String subtitle() {
            return subtitleRes != 0 ? LocaleController.getString(subtitleRes) : null;
        }
    }

    /** The drawer shows the same entries, in the same look, as Telegram's Settings page; unknown ids are ignored. */
    private static Entry entry(String id) {
        switch (id) {
            case "kuroTheme": return new Entry(IconBackgroundColors.BLUE, R.drawable.kuro_theme, "Kuro Theme", 0, 0);
            case "kuroSettings": return new Entry(IconBackgroundColors.PURPLE, R.drawable.filled_profile_settings, "Kuro Settings", 0, 0);
            case "numberFinder": return new Entry(IconBackgroundColors.ORANGE, R.drawable.kuro_user_finder, "Number Finder", 0, 0);
            case "specialContacts": return new Entry(IconBackgroundColors.RED, R.drawable.kuro_special_contacts, "Special Contacts", 0, 0);
            case "onlineContacts": return new Entry(IconBackgroundColors.CYAN, R.drawable.kuro_online_contacts, "Online Contacts", 0, 0);
            case "contactChanges": return new Entry(IconBackgroundColors.BLUE_DEEP, R.drawable.kuro_contact_changes, "Contact Changes", 0, 0);
            case "account": return new Entry(IconBackgroundColors.BLUE, R.drawable.settings_account, null, R.string.SettingsAccount, R.string.SettingsAccountInfo);
            case "chatSettings": return new Entry(IconBackgroundColors.ORANGE, R.drawable.settings_chat, null, R.string.SettingsChat, R.string.SettingsChatInfo);
            case "privacy": return new Entry(IconBackgroundColors.GREEN, R.drawable.settings_privacy, null, R.string.SettingsPrivacySecurity, R.string.SettingsPrivacySecurityInfo);
            case "notifications": return new Entry(IconBackgroundColors.RED, R.drawable.settings_sounds, null, R.string.SettingsNotifications, R.string.SettingsNotificationsInfo);
            case "data": return new Entry(IconBackgroundColors.BLUE_DEEP, R.drawable.settings_data, null, R.string.SettingsData, R.string.SettingsDataInfo);
            case "folders": return new Entry(IconBackgroundColors.BLUE_ALT, R.drawable.settings_folders, null, R.string.SettingsFolders, R.string.SettingsFoldersInfo);
            case "devices": return new Entry(IconBackgroundColors.CYAN, R.drawable.settings_devices, null, R.string.SettingsDevices, R.string.SettingsDevicesInfo);
            case "power": return new Entry(IconBackgroundColors.ORANGE_DEEP, R.drawable.settings_power, null, R.string.SettingsPowerSaving, R.string.SettingsPowerSavingInfo);
            case "language": return new Entry(IconBackgroundColors.PURPLE, R.drawable.settings_language, null, R.string.SettingsLanguage, 0);
            case "premium": return new Entry(0xFFB659FF, 0xFF617CFF, R.drawable.settings_premium, null, R.string.TelegramPremium, 0);
            case "stars": return new Entry(0xFFEFA612, 0xFFE77512, R.drawable.settings_stars, null, R.string.TelegramStars, 0);
            case "ton": return new Entry(0xFF1BA4ED, 0xFF1488E1, R.drawable.settings_gram_24, null, R.string.MyTON, 0);
            case "business": return new Entry(0xFFF45255, 0xFFDF3955, R.drawable.settings_business, null, R.string.TelegramBusiness, 0);
            case "gift": return new Entry(0xFFF38B31, 0xFFE26314, R.drawable.settings_gift, null, R.string.SendAGift, 0);
            case "askQuestion": return new Entry(IconBackgroundColors.ORANGE, R.drawable.settings_ask, null, R.string.AskAQuestion, 0);
            case "faq": return new Entry(IconBackgroundColors.BLUE_LIGHT, R.drawable.settings_faq, null, R.string.TelegramFAQ, 0);
            case "features": return new Entry(IconBackgroundColors.PURPLE, R.drawable.settings_features, null, R.string.TelegramFeatures, 0);
            case "privacyPolicy": return new Entry(IconBackgroundColors.GREEN, R.drawable.settings_policy, null, R.string.PrivacyPolicy, 0);
            case "profile": return new Entry(IconBackgroundColors.GRAY, R.drawable.msg_openprofile, null, R.string.MainTabsProfile, 0);
            case "settings": return new Entry(IconBackgroundColors.GRAY, R.drawable.msg_settings, null, R.string.Settings, 0);
            case "contacts": return new Entry(IconBackgroundColors.GRAY, R.drawable.msg_contacts, null, R.string.Contacts, 0);
            case "calls": return new Entry(IconBackgroundColors.GRAY, R.drawable.msg_calls, null, R.string.MainTabsCalls, 0);
            case "saved": return new Entry(IconBackgroundColors.GRAY, R.drawable.msg_saved, null, R.string.SavedMessages, 0);
            case "archive": return new Entry(IconBackgroundColors.GRAY, R.drawable.msg_archive, null, R.string.ArchivedChats, 0);
            default: return null;
        }
    }

    /** Same conditions Telegram's Settings page uses before showing an entry. */
    private static boolean available(String id, int account) {
        MessagesController mc = MessagesController.getInstance(account);
        switch (id) {
            case "premium":
            case "business":
                return !mc.premiumFeaturesBlocked();
            case "gift":
                return !mc.premiumPurchaseBlocked();
            case "stars":
                return mc.starsPurchaseAvailable();
            case "ton": {
                StarsController c = StarsController.getInstance(account, true);
                return ApplicationLoader.isBetaBuild() || ApplicationLoader.isStandaloneBuild() || ApplicationLoader.isHuaweiStoreBuild()
                        || (c.balanceAvailable() && (c.hasTransactions() || c.getBalance().positive()));
            }
            default:
                return true;
        }
    }

    private static void open(BaseFragment from, int account, String id) {
        long self = UserConfig.getInstance(account).getClientUserId();
        Bundle args = new Bundle();
        switch (id) {
            case "profile":
                args.putLong("user_id", self);
                args.putBoolean("my_profile", true);
                from.presentFragment(new ProfileActivity(args));
                break;
            case "settings":
                from.presentFragment(new SettingsActivity(args));
                break;
            case "contacts":
                args.putBoolean("needPhonebook", true);
                from.presentFragment(new ContactsActivity(args));
                break;
            case "calls":
                from.presentFragment(new CallLogActivity(args));
                break;
            case "saved":
                args.putLong("user_id", self);
                from.presentFragment(new ChatActivity(args));
                break;
            case "archive":
                args.putInt("folderId", 1);
                from.presentFragment(new DialogsActivity(args));
                break;
            case "kuroTheme": from.presentFragment(new tw.nekomimi.nekogram.settings.KuroThemeActivity()); break;
            case "kuroSettings": from.presentFragment(new tw.nekomimi.nekogram.settings.KuroSettingsActivity()); break;
            case "numberFinder": from.presentFragment(new tw.nekomimi.nekogram.settings.NumberFinderActivity()); break;
            case "specialContacts": from.presentFragment(new tw.nekomimi.nekogram.settings.SpecialContactsActivity()); break;
            case "onlineContacts": from.presentFragment(new tw.nekomimi.nekogram.settings.OnlineContactsActivity()); break;
            case "contactChanges": from.presentFragment(new tw.nekomimi.nekogram.settings.ContactChangesActivity()); break;
            case "account": from.presentFragment(new org.telegram.ui.UserInfoActivity()); break;
            case "chatSettings": from.presentFragment(new org.telegram.ui.ThemeActivity(org.telegram.ui.ThemeActivity.THEME_TYPE_BASIC)); break;
            case "privacy": from.presentFragment(new org.telegram.ui.PrivacySettingsActivity()); break;
            case "notifications": from.presentFragment(new org.telegram.ui.NotificationsSettingsActivity()); break;
            case "data": from.presentFragment(new org.telegram.ui.DataSettingsActivity()); break;
            case "folders": from.presentFragment(new org.telegram.ui.FiltersSetupActivity()); break;
            case "devices": from.presentFragment(new org.telegram.ui.SessionsActivity(0)); break;
            case "power": from.presentFragment(new org.telegram.ui.LiteModeSettingsActivity()); break;
            case "language": from.presentFragment(new org.telegram.ui.LanguageSelectActivity()); break;
            case "premium": from.presentFragment(new org.telegram.ui.PremiumPreviewFragment("settings")); break;
            case "stars": from.presentFragment(new StarsIntroActivity()); break;
            case "ton": from.presentFragment(new TONIntroActivity()); break;
            case "business": from.presentFragment(new org.telegram.ui.PremiumPreviewFragment(org.telegram.ui.PremiumPreviewFragment.FEATURES_BUSINESS, "settings")); break;
            case "gift": UserSelectorBottomSheet.open(0, BirthdayController.getInstance(UserConfig.selectedAccount).getState()); break;
            case "askQuestion": from.showDialog(AlertsCreator.createSupportAlert(from, null)); break;
            case "faq": Browser.openUrl(from.getParentActivity(), LocaleController.getString(R.string.TelegramFaqUrl)); break;
            case "features":
                if (MessagesController.getInstance(account).isFrozen()) {
                    org.telegram.ui.AccountFrozenAlert.show(account);
                } else {
                    Browser.openUrl(from.getParentActivity(), LocaleController.getString(R.string.TelegramFeaturesUrl));
                }
                break;
            case "privacyPolicy": Browser.openUrl(from.getParentActivity(), LocaleController.getString(R.string.PrivacyPolicyUrl)); break;
            default:
                break;
        }
    }
}
