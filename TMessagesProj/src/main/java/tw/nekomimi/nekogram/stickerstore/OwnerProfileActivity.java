package tw.nekomimi.nekogram.stickerstore;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

/**
 * Profile screen of a sticker pack's owner, opened from the pack detail. The signed-in user's
 * own id gives the full own-profile view (Themes / Stickers tabs plus the upload + button);
 * any other id gives that owner's profile with their uploaded content.
 */
public class OwnerProfileActivity extends BaseFragment {

    private final long ownerId;
    private final String ownerName;
    private final String ownerUsername;

    public OwnerProfileActivity(long ownerId, String ownerName, String ownerUsername) {
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.ownerUsername = ownerUsername;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Profile");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        FrameLayout root = new FrameLayout(context);
        tw.nekomimi.nekogram.skin.KuroUi.applyPageBackground(root);
        root.addView(new ProfileTabView(context, this, currentAccount, ownerId, ownerName, ownerUsername),
                org.telegram.ui.Components.LayoutHelper.createFrame(
                        org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                        org.telegram.ui.Components.LayoutHelper.MATCH_PARENT));
        fragmentView = root;
        return root;
    }
}
