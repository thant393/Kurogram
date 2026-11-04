package tw.kurocore.kurogram.feature;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import tw.kurocore.kurogram.skin.KuroUi;

/** Empty-state card shown instead of a feature's content when the feature is not part of this build. */
public class FeatureUnavailableView extends FrameLayout {

    public FeatureUnavailableView(Context context) {
        super(context);
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(28), AndroidUtilities.dp(24), AndroidUtilities.dp(28));
        card.setBackground(KuroUi.card(true, 16));

        TextView text = new TextView(context);
        text.setText(KuroFeatures.unavailableMessage());
        text.setTextSize(15);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setGravity(Gravity.CENTER);
        text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        card.addView(text, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 0, 24, 0));
    }
}
