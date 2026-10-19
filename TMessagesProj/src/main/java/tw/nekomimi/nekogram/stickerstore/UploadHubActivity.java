package tw.nekomimi.nekogram.stickerstore;

import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OutlineTextContainerView;

import tw.nekomimi.nekogram.settings.GroupCardHelper;

/**
 * Everything the user can upload to the Kuro store, in one place - opened by the + button on
 * the Profile tab. Today that is sticker packs (by link or short name; the pack must exist on
 * Telegram and have been created by the signed-in account) and, once themes exist, themes (shown as "coming soon").
 */
public class UploadHubActivity extends BaseFragment {

    private OutlineTextContainerView packOutline;
    private EditText packInput;
    private TextView uploadButton;
    private TextView statusView;
    private boolean busy;
    private final String prefillShortName;

    public UploadHubActivity() {
        this(null);
    }

    /** Opens with a pack already filled in (used from "your own packs" on the Profile tab). */
    public UploadHubActivity(String prefillShortName) {
        this.prefillShortName = prefillShortName;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Upload");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = root;

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(column, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        // ---- Sticker pack ----
        LinearLayout stickerCard = card(context);
        column.addView(stickerCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 16, 16, 8));

        stickerCard.addView(title(context, "Sticker pack"));
        stickerCard.addView(subtitle(context, "Add a sticker pack you created on Telegram to the store. Paste its link (t.me/addstickers/...) or its short name."),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 14));

        packOutline = new OutlineTextContainerView(context);
        packOutline.setText("Pack link or short name");
        packInput = new EditText(context);
        packInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        packInput.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        packInput.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        packInput.setTextSize(16);
        packInput.setSingleLine(true);
        packInput.setBackground(null);
        packInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        packInput.setOnFocusChangeListener((v, hasFocus) -> packOutline.animateSelection(hasFocus));
        packInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                upload();
                return true;
            }
            return false;
        });
        if (!TextUtils.isEmpty(prefillShortName)) {
            packInput.setText(prefillShortName);
        }
        packOutline.attachEditText(packInput);
        packOutline.addView(packInput, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 16, 4, 16, 4));
        stickerCard.addView(packOutline, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 58));

        uploadButton = new TextView(context);
        uploadButton.setText("Upload to store");
        uploadButton.setTextColor(0xFFFFFFFF);
        uploadButton.setTextSize(15);
        uploadButton.setTypeface(AndroidUtilities.bold());
        uploadButton.setGravity(Gravity.CENTER);
        uploadButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        uploadButton.setOnClickListener(v -> upload());
        stickerCard.addView(uploadButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 14, 0, 0));

        statusView = new TextView(context);
        statusView.setTextSize(13);
        statusView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        statusView.setVisibility(View.GONE);
        stickerCard.addView(statusView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));

        // ---- Theme ----
        LinearLayout themeCard = card(context);
        column.addView(themeCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 8, 16, 16));
        themeCard.addView(title(context, "Theme"));
        TextView soon = subtitle(context, "Uploading themes isn't available yet - coming soon.");
        themeCard.addView(soon, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
        themeCard.setAlpha(0.6f);

        return fragmentView;
    }

    private LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(GroupCardHelper.cardBackground(context, GroupCardHelper.POS_SINGLE));
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        return card;
    }

    private TextView title(Context context, String text) {
        TextView t = new TextView(context);
        t.setText(text);
        t.setTextSize(17);
        t.setTypeface(AndroidUtilities.bold());
        t.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        return t;
    }

    private TextView subtitle(Context context, String text) {
        TextView t = new TextView(context);
        t.setText(text);
        t.setTextSize(14);
        t.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        return t;
    }

    /** "https://t.me/addstickers/Name?x=1", "t.me/addstickers/Name", "@Name" or "Name" -> "Name". */
    private static String parseShortName(String raw) {
        String s = raw == null ? "" : raw.trim();
        int marker = s.indexOf("addstickers/");
        if (marker >= 0) {
            s = s.substring(marker + "addstickers/".length());
        }
        int cut = s.indexOf('?');
        if (cut >= 0) s = s.substring(0, cut);
        cut = s.indexOf('/');
        if (cut >= 0) s = s.substring(0, cut);
        if (s.startsWith("@")) s = s.substring(1);
        return s.matches("[A-Za-z0-9_]{1,64}") ? s : "";
    }

    private void setStatus(String text, boolean error) {
        statusView.setText(text);
        statusView.setTextColor(error ? Theme.getColor(Theme.key_text_RedBold) : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        statusView.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
    }

    private void setBusy(boolean value) {
        busy = value;
        uploadButton.setAlpha(value ? 0.6f : 1f);
        uploadButton.setText(value ? "Uploading…" : "Upload to store");
    }

    private void upload() {
        if (busy) {
            return;
        }
        final String shortName = parseShortName(packInput.getText().toString());
        if (shortName.isEmpty()) {
            setStatus("Enter a valid sticker pack link or short name.", true);
            return;
        }
        final OwnerProfileSync.Profile me = OwnerProfileSync.currentProfile(currentAccount);
        if (me == null) {
            setStatus("You need to be signed in to upload.", true);
            return;
        }
        AndroidUtilities.hideKeyboard(packInput);
        setBusy(true);
        setStatus("Checking the pack on Telegram…", false);

        // The pack has to be a real, public Telegram sticker pack before it goes in the store.
        StickerSetResolver.resolve(currentAccount, shortName, new StickerSetResolver.Callback() {
            @Override
            public void onResolved(TLRPC.TL_messages_stickerSet stickerSet) {
                if (fragmentView == null) return;
                setStatus("Verifying you created this pack…", false);
                // Telegram itself lists the sets this account created (works for every 64-bit
                // user id); if it can't be confirmed the upload is refused.
                OwnerCheck.fetchMine(currentAccount, new OwnerCheck.Callback() {
                    @Override
                    public void onResult(java.util.HashSet<String> names, java.util.HashSet<Long> ids) {
                        if (fragmentView == null) return;
                        if (!OwnerCheck.contains(stickerSet.set, names, ids)) {
                            setBusy(false);
                            setStatus("This pack wasn't created by your account. You can only upload packs you created.", true);
                            return;
                        }
                        startSubmit(shortName, me);
                    }

                    @Override
                    public void onError() {
                        if (fragmentView == null) return;
                        setBusy(false);
                        setStatus("Couldn't verify pack ownership with Telegram. Try again.", true);
                    }
                });
            }

            @Override
            public void onError() {
                if (fragmentView == null) return;
                setBusy(false);
                setStatus("Telegram couldn't find that sticker pack.", true);
            }
        });
    }

    private void startSubmit(final String shortName, final OwnerProfileSync.Profile me) {
        setStatus("Uploading…", false);
        StickerStoreApi.submitPack(shortName, me.id, me.name, me.username, me.avatarUrl, me.avatarFileId, new StickerStoreApi.ResultCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (fragmentView == null) return;
                setBusy(false);
                ProfileTabView.notifyPackUploaded();
                Toast.makeText(getParentActivity(), "Uploaded to the store", Toast.LENGTH_SHORT).show();
                finishFragment();
            }

            @Override
            public void onError(String message) {
                if (fragmentView == null) return;
                setBusy(false);
                setStatus(message != null && message.contains("409") ? "This pack is already in the store." : message, true);
            }
        });
    }
}
