package tw.nekomimi.nekogram.stickerstore;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import tw.nekomimi.nekogram.settings.GroupCardHelper;

public class ProfileTabView extends FrameLayout {

    public ProfileTabView(Context context, int currentAccount) {
        super(context);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(GroupCardHelper.cardBackground(context, GroupCardHelper.POS_SINGLE));
        card.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(24), AndroidUtilities.dp(24), AndroidUtilities.dp(24));
        addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 16, 16, 16, 16));

        BackupImageView avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(AndroidUtilities.dp(40));
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        if (user != null) {
            avatarDrawable.setInfo(currentAccount, user);
            avatarView.setForUserOrChat(user, avatarDrawable);
        }
        card.addView(avatarView, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 12));

        TextView nameView = new TextView(context);
        nameView.setTextSize(18);
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setGravity(Gravity.CENTER);
        nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameView.setText(user != null ? UserObject.getUserName(user) : "Not signed in");
        card.addView(nameView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 4));

        if (user != null && user.username != null) {
            TextView usernameView = new TextView(context);
            usernameView.setTextSize(14);
            usernameView.setGravity(Gravity.CENTER);
            usernameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            usernameView.setText("@" + user.username);
            card.addView(usernameView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 4));
        }

        if (user != null && user.phone != null && !user.phone.isEmpty()) {
            TextView phoneView = new TextView(context);
            phoneView.setTextSize(14);
            phoneView.setGravity(Gravity.CENTER);
            phoneView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            phoneView.setText(PhoneFormat.getInstance().format("+" + user.phone));
            card.addView(phoneView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        }
    }
}
