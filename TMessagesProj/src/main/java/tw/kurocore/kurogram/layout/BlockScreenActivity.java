package tw.kurocore.kurogram.layout;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import tw.kurocore.kurogram.skin.KuroUi;

/**
 * One generic screen that draws whatever blocks a layout file lists for a screen id (BlockSpec), so a theme can
 * add screens like People without an app update. It reads only the ready-made blocks and data sources; it never
 * runs anything from the file. Used for a tab when the layout defines {@code screens.<id>}.
 */
public class BlockScreenActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private final String screenId;
    private BlockSpec.Screen screen;
    private LinearLayout content;
    private BlockRenderer renderer;

    public BlockScreenActivity(Bundle args) {
        super(args);
        screenId = args == null ? "" : args.getString("screen_id", "");
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.contactsDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().addObserver(this, NotificationCenter.dialogsNeedReload);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.contactsDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().removeObserver(this, NotificationCenter.dialogsNeedReload);
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        screen = LayoutManager.get().spec().screen(screenId);
        final boolean asTab = arguments != null && arguments.getBoolean("hasMainTabs", false);
        if (!asTab) {
            actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        }
        actionBar.setTitle(screen != null ? screen.title : screenId);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        FrameLayout root = new FrameLayout(context);
        fragmentView = root;
        root.setBackgroundColor(KuroUi.screenBackground("people".equals(screenId) ? "onlineContacts" : screenId, Theme.getColor(Theme.key_windowBackgroundWhite)));
        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.topMargin = ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, asTab ? AndroidUtilities.dp(org.telegram.ui.DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 8) : 0);
        root.addView(scroll, lp);
        renderer = new BlockRenderer(this, currentAccount, content, null);
        rebuild(context);
        return fragmentView;
    }

    private void rebuild(Context context) {
        if (renderer != null) {
            renderer.render(context, screen);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (fragmentView == null) {
            return;
        }
        if (id == NotificationCenter.contactsDidLoad) {
            rebuild(fragmentView.getContext());
        } else if (id == NotificationCenter.dialogsNeedReload) {
            rebuild(fragmentView.getContext());
        } else if (id == NotificationCenter.updateInterfaces && args.length > 0 && args[0] instanceof Integer
                && ((Integer) args[0] & MessagesController.UPDATE_MASK_STATUS) != 0) {
            rebuild(fragmentView.getContext());
        }
    }
}
