package tw.nekomimi.nekogram.helpers;

import android.net.Uri;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.util.Locale;
import java.util.function.Consumer;

import tw.nekomimi.nekogram.settings.BaseKuroSettingsActivity;
import tw.nekomimi.nekogram.settings.KuroAppearanceSettingsActivity;
import tw.nekomimi.nekogram.settings.KuroChatSettingsActivity;
import tw.nekomimi.nekogram.settings.KuroDonateActivity;
import tw.nekomimi.nekogram.settings.KuroEmojiSettingsActivity;
import tw.nekomimi.nekogram.settings.KuroExperimentalSettingsActivity;
import tw.nekomimi.nekogram.settings.KuroGeneralSettingsActivity;
import tw.nekomimi.nekogram.settings.KuroPasscodeSettingsActivity;
import tw.nekomimi.nekogram.settings.KuroSettingsActivity;

public class SettingsHelper {

    public static void processDeepLink(Uri uri, Consumer<BaseFragment> callback, Runnable unknown, Browser.Progress progress) {
        if (uri == null) {
            unknown.run();
            return;
        }
        var segments = uri.getPathSegments();
        if (segments.isEmpty() || segments.size() > 2) {
            unknown.run();
            return;
        }
        BaseKuroSettingsActivity fragment;
        if (segments.size() == 1) {
            fragment = new KuroSettingsActivity();
        } else {
            var segment = segments.get(1);
            if (PasscodeHelper.getSettingsKey().equals(segment)) {
                fragment = new KuroPasscodeSettingsActivity();
            } else {
                switch (segment.toLowerCase(Locale.US)) {
                    case "appearance":
                    case "a":
                        fragment = new KuroAppearanceSettingsActivity();
                        break;
                    case "chat":
                    case "chats":
                    case "c":
                        fragment = new KuroChatSettingsActivity();
                        break;
                    case "donate":
                    case "d":
                        fragment = new KuroDonateActivity();
                        break;
                    case "experimental":
                    case "e":
                        fragment = new KuroExperimentalSettingsActivity();
                        break;
                    case "emoji":
                        fragment = new KuroEmojiSettingsActivity();
                        break;
                    case "general":
                    case "g":
                        fragment = new KuroGeneralSettingsActivity();
                        break;
                    case "reportid":
                        SettingsHelper.copyReportId();
                        return;
                    case "update":
                        LaunchActivity.instance.checkAppUpdate(true, progress);
                        return;
                    default:
                        unknown.run();
                        return;
                }
            }
        }
        callback.accept(fragment);
        var row = uri.getQueryParameter("r");
        if (TextUtils.isEmpty(row)) {
            row = uri.getQueryParameter("row");
        }
        if (!TextUtils.isEmpty(row)) {
            fragment.scrollToRow(row, unknown);
        }
    }

    public static void copyReportId() {
        AndroidUtilities.addToClipboard(AnalyticsHelper.userId);
        BulletinFactory.global().createSimpleBulletin(R.raw.copy, LocaleController.getString(R.string.TextCopied), LocaleController.getString(R.string.CopyReportIdDescription)).show();
    }
}
