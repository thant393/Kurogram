package tw.nekomimi.nekogram.stickerstore;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import tw.nekomimi.nekogram.settings.GroupCardHelper;

public class StickerPackGridCell extends FrameLayout {

    private final BackupImageView iconView;
    private final TextView titleView;
    private final TextView categoryView;
    private final TextView likesView;
    private final TextView ratingView;

    private StickerPackInfo currentPack;
    private int currentAccount;

    public StickerPackGridCell(Context context) {
        super(context);
        // Clean press feedback: a native ripple masked to the card's own rounded shape (the
        // list's square full-cell selector is turned off in StickersTabView), so nothing tints
        // or flashes outside the card.
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.RECTANGLE);
        mask.setCornerRadius(AndroidUtilities.dp(20));
        mask.setColor(0xFFFFFFFF);
        setBackground(new RippleDrawable(
                ColorStateList.valueOf(Theme.getColor(Theme.key_listSelector)),
                cardBackground(),
                mask));
        setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        iconView = new BackupImageView(context);
        iconView.setRoundRadius(AndroidUtilities.dp(16));
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.RECTANGLE);
        iconBg.setCornerRadius(AndroidUtilities.dp(16));
        iconBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        iconView.setBackground(iconBg);
        column.addView(iconView, LayoutHelper.createLinear(64, 64, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 12));

        // Display Name only - the technical short_name is never shown anywhere in this cell.
        titleView = new TextView(context);
        titleView.setTextSize(14);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setGravity(Gravity.CENTER);
        titleView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        titleView.setSingleLine(true);
        titleView.setMaxLines(1);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        column.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        categoryView = new TextView(context);
        categoryView.setTextSize(12);
        categoryView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        categoryView.setGravity(Gravity.CENTER);
        categoryView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        categoryView.setMaxLines(1);
        categoryView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        column.addView(categoryView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 3, 0, 10));

        // Bottom stats row: total likes on the bottom-left, average rating on the bottom-right.
        LinearLayout statsRow = new LinearLayout(context);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        column.addView(statsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        likesView = new TextView(context);
        likesView.setTextSize(12);
        likesView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        likesView.setGravity(Gravity.LEFT);
        statsRow.addView(likesView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        ratingView = new TextView(context);
        ratingView.setTextSize(12);
        ratingView.setTypeface(AndroidUtilities.bold());
        ratingView.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        ratingView.setGravity(Gravity.RIGHT);
        statsRow.addView(ratingView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.RIGHT | Gravity.CENTER_VERTICAL));

        setWillNotDraw(false);
    }

    /** iOS-style card: large radius, white fill and a hairline border. */
    private static GradientDrawable cardBackground() {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(AndroidUtilities.dp(20));
        d.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        d.setStroke(Math.max(1, AndroidUtilities.dp(0.5f)), 0x1A000000);
        return d;
    }

    /** Roomier variant for 2-column grids: a bigger thumbnail and more padding. */
    public StickerPackGridCell setLarge(boolean large) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) iconView.getLayoutParams();
        lp.width = lp.height = AndroidUtilities.dp(large ? 96 : 64);
        lp.topMargin = AndroidUtilities.dp(large ? 10 : 4);
        iconView.setLayoutParams(lp);
        int pad = AndroidUtilities.dp(large ? 12 : 8);
        setPadding(pad, pad, pad, pad);
        titleView.setTextSize(large ? 15 : 14);
        return this;
    }

    public void setPack(StickerPackInfo pack, int currentAccount) {
        this.currentPack = pack;
        this.currentAccount = currentAccount;

        categoryView.setText(pack.category != null ? pack.category : "");
        categoryView.setVisibility(pack.category != null && !pack.category.isEmpty() ? View.VISIBLE : View.GONE);

        likesView.setText(pack.totalLikes > 0 ? CountFormat.format(pack.totalLikes) + " ♥" : "");
        ratingView.setText(pack.ratingAvg > 0
                ? String.format(LocaleController.getInstance().getCurrentLocale(), "★ %.1f", pack.ratingAvg)
                : "");

        applyResolvedTitleAndIcon();

        if (pack.resolvedSet == null) {
            TLRPC.TL_messages_stickerSet cached = StickerSetResolver.getCached(pack.shortName);
            if (cached != null) {
                pack.resolvedSet = cached;
                applyResolvedTitleAndIcon();
            } else {
                StickerSetResolver.resolve(currentAccount, pack.shortName, new StickerSetResolver.Callback() {
                    @Override
                    public void onResolved(TLRPC.TL_messages_stickerSet stickerSet) {
                        if (currentPack == pack) {
                            pack.resolvedSet = stickerSet;
                            applyResolvedTitleAndIcon();
                        }
                    }

                    @Override
                    public void onError() {
                        if (currentPack == pack) {
                            titleView.setText(R.string.AddStickersNotFound);
                        }
                    }
                });
            }
        }
    }

    private void applyResolvedTitleAndIcon() {
        String title = currentPack.getDisplayTitle();
        titleView.setText(title != null ? title : "…");

        TLRPC.Document thumbDocument = currentPack.getFirstSticker();
        if (currentPack.resolvedSet != null && currentPack.resolvedSet.set != null && !currentPack.resolvedSet.set.thumbs.isEmpty()) {
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(currentPack.resolvedSet.set.thumbs, 90);
            iconView.setImage(org.telegram.messenger.ImageLocation.getForSticker(thumb, thumbDocument, currentPack.resolvedSet.set.thumb_version), "80_80", (android.graphics.drawable.Drawable) null, currentPack.resolvedSet);
        } else if (thumbDocument != null) {
            var svgThumb = DocumentObject.getSvgThumb(thumbDocument, Theme.key_windowBackgroundGray, 1.0f);
            iconView.setImage(org.telegram.messenger.ImageLocation.getForDocument(thumbDocument), "80_80", svgThumb, currentPack.resolvedSet);
        } else {
            iconView.setImageDrawable(null);
        }
    }
}
