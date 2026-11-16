package tw.kurocore.kurogram.stickerstore;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OutlineTextContainerView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

import tw.kurocore.kurogram.feature.KuroFeatures;
import tw.kurocore.kurogram.settings.GroupCardHelper;

/**
 * Uploads a theme to the store, or edits one the user already uploaded. No code to paste: the theme
 * file is picked from the phone (a "drop zone" you tap), checked on the spot, and the name,
 * description and category are ordinary fields. When editing, the file is optional - leave it out to
 * change only the details, pick one to publish a new version. Stickers have their own screen
 * ({@link StickerUploadActivity}).
 */
public class ThemeUploadActivity extends BaseFragment {

    private static final int PICK_FILE = 4201;
    private static final int MAX_BYTES = 64 * 1024;

    private final ThemeInfo editing; // null = a new theme
    private EditText titleInput, descInput;
    private TextView categoryView, fileZone, fileStatus, saveButton, statusView;
    private final ArrayList<String> categories = new ArrayList<>(Arrays.asList("Custom"));
    private static final String NEW_CATEGORY = "＋ New category…";
    private int categoryIndex;
    private String content; // the validated file text, or null
    private boolean busy;
    private String prefill; // a theme made in the Builder, shown as if it had been picked as a file

    public ThemeUploadActivity() {
        this(null);
    }

    /** Hands over a theme made in the Theme Builder; it is checked like a picked file. */
    public void setPrefill(String json) {
        this.prefill = json;
    }

    /** Opens in edit mode for one of the user's own themes. */
    public ThemeUploadActivity(ThemeInfo editing) {
        this.editing = editing;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(editing == null ? "Upload theme" : "Edit theme");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        tw.kurocore.kurogram.skin.KuroUi.applyPageBackground(root);
        fragmentView = root;
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(column, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        int accent = tw.kurocore.kurogram.skin.KuroSkinManager.get().accent();

        // ---- design visually ----
        TextView design = label(context, 15, true, Theme.key_windowBackgroundWhiteBlueText, editing == null ? "🎨  Design a theme visually" : "🎨  Edit visually in the Theme Builder");
        design.setTextColor(accent);
        design.setGravity(Gravity.CENTER);
        design.setOnClickListener(v -> openBuilder());
        column.addView(design, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52, 16, 12, 16, 0));

        // ---- the file ----
        LinearLayout fileCard = card(context);
        column.addView(fileCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 16, 16, 8));
        fileCard.addView(label(context, 17, true, Theme.key_windowBackgroundWhiteBlackText, "Theme file"));
        fileCard.addView(label(context, 14, false, Theme.key_windowBackgroundWhiteGrayText, editing == null
                        ? "Choose the theme file (.json) from your phone. It is checked before anything is uploaded."
                        : "Optional. Choose a new file to publish a new version; leave it empty to change only the details below."),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 12));

        fileZone = new TextView(context);
        fileZone.setGravity(Gravity.CENTER);
        fileZone.setTextSize(15);
        fileZone.setTypeface(AndroidUtilities.bold());
        fileZone.setTextColor(accent);
        fileZone.setText("＋  Tap to choose a theme file");
        android.graphics.drawable.GradientDrawable zone = new android.graphics.drawable.GradientDrawable();
        zone.setCornerRadius(AndroidUtilities.dp(tw.kurocore.kurogram.skin.KuroUi.floatingRadiusDp(12)));
        zone.setStroke(AndroidUtilities.dp(2), accent, AndroidUtilities.dp(6), AndroidUtilities.dp(4));
        zone.setColor((accent & 0x00FFFFFF) | 0x14000000);
        fileZone.setBackground(zone);
        fileZone.setMinHeight(AndroidUtilities.dp(96));
        fileZone.setOnClickListener(v -> pickFile());
        fileCard.addView(fileZone, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        fileStatus = label(context, 13, false, Theme.key_windowBackgroundWhiteGrayText, "");
        fileStatus.setVisibility(View.GONE);
        fileCard.addView(fileStatus, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        TextView paste = label(context, 14, true, Theme.key_windowBackgroundWhiteBlueText, "Or paste from clipboard");
        paste.setTextColor(accent);
        paste.setGravity(Gravity.CENTER);
        paste.setOnClickListener(v -> pasteFromClipboard(context));
        fileCard.addView(paste, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, 0, 6, 0, 0));

        // ---- details ----
        LinearLayout detailCard = card(context);
        column.addView(detailCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 8, 16, 8));
        detailCard.addView(label(context, 17, true, Theme.key_windowBackgroundWhiteBlackText, "Details"),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        titleInput = outlined(context, detailCard, "Theme name", false, 58);
        descInput = outlined(context, detailCard, "Description (optional)", true, 110);

        categoryView = new TextView(context);
        categoryView.setTextSize(15);
        categoryView.setGravity(Gravity.CENTER_VERTICAL);
        categoryView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        categoryView.setOnClickListener(v -> {
            categoryIndex = (categoryIndex + 1) % (categories.size() + 1);
            if (categoryIndex == categories.size()) {
                askNewCategory(context);
            }
            updateCategoryLabel();
        });
        detailCard.addView(categoryView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 4, 0, 4, 0));

        if (editing != null) {
            titleInput.setText(editing.title);
            descInput.setText(editing.description);
            if (!TextUtils.isEmpty(editing.category)) {
                categories.add(0, editing.category);
                categories.remove("Custom");
                categories.add("Custom");
            }
        }
        updateCategoryLabel();
        KuroFeatures.themes().fetchCategories(names -> {
            for (String n : names) {
                if (!categories.contains(n)) {
                    categories.add(n);
                }
            }
            updateCategoryLabel();
        });

        // ---- save ----
        saveButton = new TextView(context);
        saveButton.setText(editing == null ? "Upload theme" : "Save changes");
        saveButton.setTextColor(0xFFFFFFFF);
        saveButton.setTextSize(15);
        saveButton.setTypeface(AndroidUtilities.bold());
        saveButton.setGravity(Gravity.CENTER);
        saveButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(tw.kurocore.kurogram.skin.KuroUi.floatingRadiusDp(8)),
                accent, Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        saveButton.setOnClickListener(v -> save());
        column.addView(saveButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 8, 16, 0));

        statusView = label(context, 13, false, Theme.key_windowBackgroundWhiteGrayText, "");
        statusView.setVisibility(View.GONE);
        column.addView(statusView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 10, 20, 0));

        if (editing != null) {
            TextView remove = label(context, 15, true, Theme.key_text_RedBold, "Remove from store");
            remove.setGravity(Gravity.CENTER);
            remove.setOnClickListener(v -> confirmRemove(context));
            column.addView(remove, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52, 16, 8, 16, 24));
        }
        if (prefill != null) {
            checkContent("Theme Builder", prefill);
            prefill = null;
        }
        return fragmentView;
    }

    // ------------------------------------------------------------------ file

    /** New theme: open the Builder on a blank draft. Editing: fetch the published file first so nothing in it is lost. */
    private void openBuilder() {
        if (editing == null) {
            presentFragment(new tw.kurocore.kurogram.skin.editor.ui.ThemeBuilderActivity(null, null));
            return;
        }
        setStatus("Loading the theme…", false);
        KuroFeatures.themes().fetchVersionContent(editing.id, editing.version, null, null, text -> {
            if (fragmentView == null) {
                return;
            }
            if (text == null) {
                setStatus("Could not load the theme. Check the connection and try again.", true);
                return;
            }
            setStatus(null, false);
            presentFragment(new tw.kurocore.kurogram.skin.editor.ui.ThemeBuilderActivity(text, editing));
        });
    }

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, PICK_FILE);
        } catch (Exception e) {
            setStatus("No file picker found on this phone. Use \"paste from clipboard\" instead.", true);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_FILE || resultCode != Activity.RESULT_OK || data == null || data.getData() == null || fragmentView == null) {
            return;
        }
        Uri uri = data.getData();
        String name = displayName(uri);
        String text;
        try (InputStream in = getParentActivity().getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new java.io.IOException("no stream");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) {
                    acceptFile(name, null, "That file is too big for a theme (limit 64 KB).");
                    return;
                }
            }
            text = new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            acceptFile(name, null, "Couldn't read that file.");
            return;
        }
        checkContent(name, text);
    }

    private String displayName(Uri uri) {
        try (Cursor c = getParentActivity().getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) {
                    return c.getString(i);
                }
            }
        } catch (Exception ignored) {
        }
        return "theme file";
    }

    private void pasteFromClipboard(Context context) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        CharSequence text = cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0
                ? cm.getPrimaryClip().getItemAt(0).coerceToText(context) : null;
        if (TextUtils.isEmpty(text)) {
            acceptFile(null, null, "The clipboard is empty.");
            return;
        }
        checkContent("clipboard", text.toString());
    }

    private void checkContent(String name, String text) {
        String trimmed = text.trim();
        if (trimmed.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            acceptFile(name, null, "That file is too big for a theme (limit 64 KB).");
            return;
        }
        if (!trimmed.startsWith("{")) {
            acceptFile(name, null, "This isn't a Kurogram theme file (a theme is a complete .json document).");
            return;
        }
        try {
            tw.kurocore.kurogram.skin.SkinSpec.parse(trimmed);
        } catch (IllegalArgumentException e) {
            acceptFile(name, null, "Invalid theme: " + e.getMessage());
            return;
        }
        if (titleInput.getText().length() == 0) {
            try {
                JSONObject root = new JSONObject(trimmed);
                JSONObject meta = root.optJSONObject("meta");
                String n = meta != null ? meta.optString("name", "") : root.optString("name", "");
                if (!n.isEmpty() && n.length() <= 48) {
                    titleInput.setText(n);
                }
            } catch (Exception ignored) {
            }
        }
        acceptFile(name, trimmed, null);
    }

    /** Shows what happened to the picked file; {@code error == null} means it is valid and kept. */
    private void acceptFile(String name, String valid, String error) {
        content = valid;
        if (valid != null) {
            fileZone.setText("✓  " + name);
            fileStatus.setText("Valid theme · " + Math.max(1, valid.length() / 1024) + " KB. Tap to choose a different file.");
            fileStatus.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        } else {
            fileZone.setText("＋  Tap to choose a theme file");
            fileStatus.setText(error);
            fileStatus.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
        fileStatus.setVisibility(TextUtils.isEmpty(fileStatus.getText()) ? View.GONE : View.VISIBLE);
        setStatus(null, false);
    }

    // ------------------------------------------------------------------ save

    private void save() {
        if (busy) {
            return;
        }
        final String title = titleInput.getText().toString().trim();
        final String description = descInput.getText().toString().trim();
        if (title.isEmpty()) {
            setStatus("Give the theme a name.", true);
            return;
        }
        if (editing == null && content == null) {
            setStatus("Choose the theme file first.", true);
            return;
        }
        final OwnerProfileSync.Profile me = OwnerProfileSync.currentProfile(currentAccount);
        if (me == null) {
            setStatus("You need to be signed in.", true);
            return;
        }
        AndroidUtilities.hideKeyboard(titleInput);
        setBusy(true);
        StickerStoreApi.ResultCallback done = new StickerStoreApi.ResultCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (fragmentView == null) return;
                setBusy(false);
                ProfileTabView.notifyPackUploaded();
                Toast.makeText(getParentActivity(), editing == null ? "Theme uploaded" : "Saved", Toast.LENGTH_SHORT).show();
                finishFragment();
            }

            @Override
            public void onError(String message) {
                if (fragmentView == null) return;
                setBusy(false);
                setStatus(message, true);
            }
        };
        if (editing == null) {
            KuroFeatures.themes().submitTheme(me.id, title, description, currentCategory(), "skin", content, me, done);
        } else {
            KuroFeatures.themes().updateTheme(editing.id, me.id, title, description, currentCategory(), content, done);
        }
    }

    private void confirmRemove(Context context) {
        new android.app.AlertDialog.Builder(context)
                .setTitle("Remove from store?")
                .setMessage("\"" + editing.title + "\" will disappear from the store. People who already applied it keep it.")
                .setPositiveButton("Remove", (d, w) -> {
                    final OwnerProfileSync.Profile me = OwnerProfileSync.currentProfile(currentAccount);
                    if (me == null || busy) {
                        return;
                    }
                    setBusy(true);
                    KuroFeatures.themes().unpublishTheme(editing.id, me.id, new StickerStoreApi.ResultCallback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            if (fragmentView == null) return;
                            setBusy(false);
                            ProfileTabView.notifyPackUploaded();
                            Toast.makeText(getParentActivity(), "Removed from the store", Toast.LENGTH_SHORT).show();
                            finishFragment();
                        }

                        @Override
                        public void onError(String message) {
                            if (fragmentView == null) return;
                            setBusy(false);
                            setStatus(message, true);
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ------------------------------------------------------------------ small helpers

    private void setBusy(boolean value) {
        busy = value;
        saveButton.setAlpha(value ? 0.6f : 1f);
        saveButton.setText(value ? "Working…" : editing == null ? "Upload theme" : "Save changes");
    }

    private void setStatus(String text, boolean error) {
        statusView.setText(text);
        statusView.setTextColor(error ? Theme.getColor(Theme.key_text_RedBold) : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        statusView.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
    }

    private String currentCategory() {
        return categoryIndex >= 0 && categoryIndex < categories.size() ? categories.get(categoryIndex) : "Custom";
    }

    private void updateCategoryLabel() {
        String label = categoryIndex >= categories.size() ? NEW_CATEGORY : currentCategory();
        categoryView.setText("Category: " + label + "  ›  (tap to change)");
    }

    private void askNewCategory(Context context) {
        final EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(24)});
        new android.app.AlertDialog.Builder(context)
                .setTitle("New category")
                .setView(input)
                .setPositiveButton("OK", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        if (!categories.contains(name)) {
                            categories.add(name);
                        }
                        categoryIndex = categories.indexOf(name);
                    } else {
                        categoryIndex = 0;
                    }
                    updateCategoryLabel();
                })
                .setNegativeButton("Cancel", (d, w) -> {
                    categoryIndex = 0;
                    updateCategoryLabel();
                })
                .show();
    }

    private LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(GroupCardHelper.cardBackground(context, GroupCardHelper.POS_SINGLE));
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        return card;
    }

    private TextView label(Context context, int sp, boolean bold, int colorKey, String text) {
        TextView t = new TextView(context);
        t.setText(text);
        t.setTextSize(sp);
        if (bold) {
            t.setTypeface(AndroidUtilities.bold());
        }
        t.setTextColor(Theme.getColor(colorKey));
        return t;
    }

    private EditText outlined(Context context, LinearLayout parent, String label, boolean multiline, int heightDp) {
        OutlineTextContainerView outline = new OutlineTextContainerView(context);
        outline.setText(label);
        EditText input = new EditText(context);
        input.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        input.setTextSize(16);
        input.setBackground(null);
        if (multiline) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            input.setGravity(Gravity.TOP | Gravity.START);
            input.setMinLines(3);
            input.setMaxLines(5);
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            input.setSingleLine(true);
        }
        input.setOnFocusChangeListener((v, hasFocus) -> outline.animateSelection(hasFocus));
        outline.attachEditText(input);
        outline.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, multiline ? LayoutHelper.MATCH_PARENT : LayoutHelper.WRAP_CONTENT,
                multiline ? Gravity.TOP : Gravity.CENTER_VERTICAL, 16, multiline ? 14 : 4, 16, 4));
        // wrap_content for the multi-line box would clip the label, so the box has a fixed minimum
        parent.addView(outline, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, heightDp, 0, 0, 0, 10));
        return input;
    }
}
