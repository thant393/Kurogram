package tw.kurocore.kurogram.skin.editor.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ContactsActivity;
import org.telegram.ui.DataSettingsActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.NotificationsSettingsActivity;
import org.telegram.ui.PrivacySettingsActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.SettingsActivity;
import org.telegram.ui.FiltersSetupActivity;
import org.telegram.ui.LanguageSelectActivity;
import org.telegram.ui.LiteModeSettingsActivity;
import org.telegram.ui.SessionsActivity;
import org.telegram.ui.ThemeActivity;
import tw.kurocore.kurogram.settings.ContactChangesActivity;
import tw.kurocore.kurogram.settings.KuroSettingsActivity;
import tw.kurocore.kurogram.settings.KuroThemeActivity;
import tw.kurocore.kurogram.settings.NumberFinderActivity;
import tw.kurocore.kurogram.settings.SpecialContactsActivity;
import tw.kurocore.kurogram.settings.OnlineContactsActivity;

import java.util.ArrayList;

/**
 * Shows one of the app's own screens inside the Studio's phone frame, in its own navigation layout, so it is drawn with
 * the real views and the theme being edited. Only scrolling gets through: a plain tap is turned into a cancel, so the
 * preview can never open a chat, change a setting or sign out.
 */
final class RealScreenHost extends FrameLayout {
    private final ActionBarLayout layout;
    private final int slop;
    private float downX, downY;
    private boolean moved;

    /** The scenes that have a real screen to show (the rest, e.g. Components, stay drawn). */
    static boolean supports(String scene) {
        switch (scene) {
            case "chats":
            case "chat":
            case "settings":
            case "drawer":
            case "people":
            case "contacts":
            case "profile":
            case "themeStore":
            case "rows.privacy":
            case "rows.notifications":
            case "rows.dataStorage":
            case "kuroSettings":
            case "numberFinder":
            case "specialContacts":
            case "onlineContacts":
            case "contactChanges":
            case "chatSettings":
            case "folders":
            case "devices":
            case "powerSaving":
            case "language":
                return true;
            default:
                return false;
        }
    }

    private static BaseFragment create(String scene) {
        // The account's own "Saved Messages" / profile: nothing here sends or changes anything (taps are blocked).
        long self = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        Bundle b = new Bundle();
        switch (scene) {
            case "settings":
                return new SettingsActivity();
            case "drawer":
                b.putBoolean("kuroDrawer", true);
                return new SettingsActivity(b);
            case "contacts":
                return new ContactsActivity(null);
            case "people":
                return new OnlineContactsActivity(b);
            case "chat":
                b.putLong("user_id", self);
                return new ChatActivity(b);
            case "profile":
                b.putLong("user_id", self);
                return new ProfileActivity(b);
            case "themeStore":
                return new KuroThemeActivity();
            case "rows.privacy":
                return new PrivacySettingsActivity();
            case "rows.notifications":
                return new NotificationsSettingsActivity();
            case "rows.dataStorage":
                return new DataSettingsActivity();
            case "kuroSettings":
                return new KuroSettingsActivity();
            case "numberFinder":
                return new NumberFinderActivity();
            case "specialContacts":
                return new SpecialContactsActivity();
            case "onlineContacts":
                return new OnlineContactsActivity(b);
            case "contactChanges":
                return new ContactChangesActivity();
            case "chatSettings":
                return new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC);
            case "folders":
                return new FiltersSetupActivity();
            case "devices":
                return new SessionsActivity(0);
            case "powerSaving":
                return new LiteModeSettingsActivity();
            case "language":
                return new LanguageSelectActivity();
            default:
                return new DialogsActivity(null);
        }
    }

    RealScreenHost(Context context, String scene) {
        super(context);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        layout = new ActionBarLayout((Activity) context, false);
        layout.setFragmentStack(new ArrayList<>());
        addView(layout, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        layout.presentFragment(create(scene));
        layout.onResume();
    }

    void release() {
        try {
            layout.onPause();
            layout.removeAllFragments();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                moved = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(ev.getX() - downX) > slop || Math.abs(ev.getY() - downY) > slop) {
                    moved = true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (!moved) {
                    ev.setAction(MotionEvent.ACTION_CANCEL);
                }
                break;
            default:
        }
        return super.dispatchTouchEvent(ev);
    }
}
