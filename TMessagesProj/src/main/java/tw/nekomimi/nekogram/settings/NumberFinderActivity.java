package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import android.os.Bundle;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.CountrySelectActivity;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Phone number lookup against the user's own synced Telegram contacts, styled after
 * Telegram's own phone-login screen (floating-label outline field + full width accent
 * button). Matching works against whatever country code a contact is actually stored
 * with - nothing here assumes or defaults to a particular country.
 */
public class NumberFinderActivity extends BaseFragment {

    private OutlineTextContainerView phoneOutlineView;
    private EditText phoneInput;
    private OutlineTextContainerView countryOutlineView;
    private TextView countryTextView;
    private final ArrayList<CountrySelectActivity.Country> countriesArray = new ArrayList<>();
    private final HashMap<String, List<CountrySelectActivity.Country>> codesMap = new HashMap<>();
    private CountrySelectActivity.Country currentCountry;
    private boolean ignoreChange;
    private LinearLayout resultContainer;
    private BackupImageView avatarImageView;
    private TextView nameTextView;
    private TextView usernameTextView;
    private TextView statusTextView;
    private FrameLayout openChatButton;
    private TLRPC.User foundUser;

    /** Below this many matching trailing digits, a suffix match is too weak to trust -
     *  this is what keeps results from crossing between contacts in different countries
     *  that happen to share a short local-number suffix, instead of quietly favoring
     *  whichever country the first matching contact happens to be in. */
    private static final int MIN_MATCH_DIGITS = 7;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Number Finder");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        LinearLayout rootLayout = new LinearLayout(context);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = rootLayout;

        TextView headerText = new TextView(context);
        headerText.setText("Find a contact by their phone number, with their country code.");
        headerText.setTextSize(14);
        headerText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        headerText.setGravity(Gravity.CENTER);
        headerText.setPadding(AndroidUtilities.dp(32), AndroidUtilities.dp(20), AndroidUtilities.dp(32), AndroidUtilities.dp(16));
        rootLayout.addView(headerText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        loadCountries();

        // Country field (top): tap opens Telegram's own country picker.
        countryOutlineView = new OutlineTextContainerView(context);
        countryOutlineView.setText("Country");
        countryOutlineView.setForceUseCenter(true);
        countryOutlineView.setFocusable(true);
        countryOutlineView.animateSelection(1f, false);

        FrameLayout countryRow = new FrameLayout(context);
        countryTextView = new TextView(context);
        countryTextView.setTextSize(16);
        countryTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        countryTextView.setSingleLine(true);
        countryTextView.setEllipsize(TextUtils.TruncateAt.END);
        countryTextView.setText("Choose a country");
        countryRow.addView(countryTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 0, 0, 28, 0));
        TextView arrow = new TextView(context);
        arrow.setText("\u25BE");
        arrow.setTextSize(18);
        arrow.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        countryRow.addView(arrow, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT));
        countryOutlineView.addView(countryRow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 16, 8, 16, 8));
        countryOutlineView.setOnClickListener(v -> {
            CountrySelectActivity fragment = new CountrySelectActivity(true, countriesArray);
            fragment.setCountrySelectActivityDelegate(country -> {
                selectCountry(country);
                phoneInput.requestFocus();
                phoneInput.setSelection(phoneInput.length());
                AndroidUtilities.showKeyboard(phoneInput);
            });
            presentFragment(fragment);
        });
        LinearLayout.LayoutParams countryParams = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 58);
        countryParams.setMargins(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        rootLayout.addView(countryOutlineView, countryParams);

        // Phone field (bottom): fixed "+" prefix, then country code + number.
        phoneOutlineView = new OutlineTextContainerView(context);
        phoneOutlineView.setText("Phone Number");

        LinearLayout phoneRow = new LinearLayout(context);
        phoneRow.setOrientation(LinearLayout.HORIZONTAL);
        phoneRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView plus = new TextView(context);
        plus.setText("+");
        plus.setTextSize(16);
        plus.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        phoneRow.addView(plus, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        phoneInput = new EditText(context);
        phoneInput.setInputType(InputType.TYPE_CLASS_PHONE);
        phoneInput.setHint("Code and number");
        phoneInput.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        phoneInput.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        phoneInput.setTextSize(16);
        phoneInput.setSingleLine(true);
        phoneInput.setBackground(null);
        phoneInput.setPadding(AndroidUtilities.dp(4), 0, 0, 0);
        phoneInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        phoneInput.setOnFocusChangeListener((v, hasFocus) -> phoneOutlineView.animateSelection(hasFocus));
        phoneInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch();
                return true;
            }
            return false;
        });
        phoneInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (ignoreChange) {
                    return;
                }
                String raw = s.toString();
                String digits = raw.replaceAll("[^0-9]", "");
                if (!digits.equals(raw)) {
                    ignoreChange = true;
                    s.replace(0, s.length(), digits);
                    ignoreChange = false;
                }
                detectCountry(digits);
            }
        });
        phoneOutlineView.attachEditText(phoneInput);
        phoneRow.addView(phoneInput, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        phoneOutlineView.addView(phoneRow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 16, 4, 16, 4));

        LinearLayout.LayoutParams outlineParams = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 58);
        outlineParams.setMargins(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        rootLayout.addView(phoneOutlineView, outlineParams);

        TextView searchButton = new TextView(context);
        searchButton.setText("Search");
        searchButton.setTextColor(0xffffffff);
        searchButton.setTextSize(15);
        searchButton.setTypeface(AndroidUtilities.bold());
        searchButton.setGravity(Gravity.CENTER);
        searchButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        searchButton.setOnClickListener(v -> doSearch());
        LinearLayout.LayoutParams searchButtonParams = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48);
        searchButtonParams.setMargins(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), 0);
        rootLayout.addView(searchButton, searchButtonParams);

        // Result container (hidden until search)
        resultContainer = new LinearLayout(context);
        resultContainer.setOrientation(LinearLayout.VERTICAL);
        resultContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        resultContainer.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(32), AndroidUtilities.dp(24), AndroidUtilities.dp(24));
        resultContainer.setVisibility(View.GONE);

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(40));
        resultContainer.addView(avatarImageView, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL));

        nameTextView = new TextView(context);
        nameTextView.setTextSize(18);
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameTextView.setGravity(Gravity.CENTER);
        nameTextView.setPadding(0, AndroidUtilities.dp(12), 0, 0);
        resultContainer.addView(nameTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        usernameTextView = new TextView(context);
        usernameTextView.setTextSize(14);
        usernameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        usernameTextView.setGravity(Gravity.CENTER);
        usernameTextView.setPadding(0, AndroidUtilities.dp(4), 0, 0);
        resultContainer.addView(usernameTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        openChatButton = new FrameLayout(context);
        openChatButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        TextView openChatText = new TextView(context);
        openChatText.setText("Open Chat");
        openChatText.setTextColor(0xffffffff);
        openChatText.setTextSize(14);
        openChatText.setGravity(Gravity.CENTER);
        openChatButton.addView(openChatText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        openChatButton.setOnClickListener(v -> {
            if (foundUser != null) {
                Bundle args = new Bundle();
                args.putLong("user_id", foundUser.id);
                presentFragment(new ChatActivity(args));
            }
        });
        LinearLayout.LayoutParams btnParams = LayoutHelper.createLinear(160, 40, Gravity.CENTER_HORIZONTAL);
        btnParams.topMargin = AndroidUtilities.dp(16);
        resultContainer.addView(openChatButton, btnParams);

        statusTextView = new TextView(context);
        statusTextView.setTextSize(15);
        statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        statusTextView.setGravity(Gravity.CENTER);
        statusTextView.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(32), AndroidUtilities.dp(24), AndroidUtilities.dp(24));
        statusTextView.setVisibility(View.GONE);
        rootLayout.addView(statusTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        rootLayout.addView(resultContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        return fragmentView;
    }


    private void loadCountries() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(ApplicationLoader.applicationContext.getResources().getAssets().open("countries.txt")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] args = line.split(";");
                if (args.length < 3) {
                    continue;
                }
                CountrySelectActivity.Country c = new CountrySelectActivity.Country();
                c.code = args[0];
                c.shortname = args[1];
                c.name = args[2];
                if (!TextUtils.equals(c.code, "FT")) {
                    String localized = LocaleController.getCountryName(c.shortname);
                    if (!TextUtils.isEmpty(localized) && !TextUtils.equals(c.shortname, localized)) {
                        c.defaultName = c.name;
                        c.name = localized;
                    }
                }
                countriesArray.add(c);
                List<CountrySelectActivity.Country> list = codesMap.get(c.code);
                if (list == null) {
                    codesMap.put(c.code, list = new ArrayList<>());
                }
                list.add(c);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        Collections.sort(countriesArray, Comparator.comparing(o -> o.name));
    }

    private void setCountryLabel(CountrySelectActivity.Country c) {
        currentCountry = c;
        countryTextView.setText(c != null ? c.name : "Choose a country");
    }

    /** Typing a code (+95, +1, +44 ...) picks the matching country; the longest code wins. */
    private void detectCountry(String digits) {
        CountrySelectActivity.Country match = null;
        for (int len = Math.min(4, digits.length()); len >= 1 && match == null; len--) {
            List<CountrySelectActivity.Country> list = codesMap.get(digits.substring(0, len));
            if (list != null && !list.isEmpty()) {
                // keep the current country when it shares this code (e.g. +1 US/Canada)
                match = list.contains(currentCountry) ? currentCountry : list.get(0);
            }
        }
        setCountryLabel(match);
    }

    /** Picking a country swaps the code at the start of the number for the country's own. */
    private void selectCountry(CountrySelectActivity.Country country) {
        String digits = phoneInput.getText().toString().replaceAll("[^0-9]", "");
        String rest = digits;
        if (currentCountry != null && digits.startsWith(currentCountry.code)) {
            rest = digits.substring(currentCountry.code.length());
        } else if (currentCountry != null) {
            rest = digits;
        }
        ignoreChange = true;
        phoneInput.setText(country.code + rest);
        ignoreChange = false;
        phoneInput.setSelection(phoneInput.length());
        setCountryLabel(country);
    }

    private void doSearch() {
        // "+" + country code + number, digits only.
        String normalized = phoneInput.getText().toString().replaceAll("[^0-9]", "");
        AndroidUtilities.hideKeyboard(phoneInput);

        resultContainer.setVisibility(View.GONE);
        statusTextView.setVisibility(View.GONE);
        foundUser = null;

        if (TextUtils.isEmpty(normalized)) {
            statusTextView.setText("Please enter a phone number.");
            statusTextView.setVisibility(View.VISIBLE);
            return;
        }

        // Match by longest common trailing-digit run rather than "first contact whose
        // number happens to share any suffix" - that first-match approach is what made
        // the search look biased toward whichever country code the earliest-indexed
        // contacts used. This way a contact stored under +44, +95, +1, or any other
        // country code is matched on equal footing, purely by how much of the number
        // the user actually typed in matches.
        TLRPC.User best = null;
        int bestScore = 0;
        for (TLRPC.TL_contact contact : ContactsController.getInstance(currentAccount).contacts) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(contact.user_id);
            if (user == null || user.phone == null) {
                continue;
            }
            String userPhone = user.phone.replaceAll("[^0-9]", "");
            if (userPhone.isEmpty()) {
                continue;
            }
            int score = commonSuffixLength(userPhone, normalized);
            boolean fullMatch = score == userPhone.length() || score == normalized.length();
            if (score > bestScore && (fullMatch || score >= MIN_MATCH_DIGITS)) {
                bestScore = score;
                best = user;
            }
        }

        if (best != null) {
            foundUser = best;
            String fullName = ContactsController.formatName(best.first_name, best.last_name);
            nameTextView.setText(fullName);
            if (best.username != null) {
                usernameTextView.setText("@" + best.username);
                usernameTextView.setVisibility(View.VISIBLE);
            } else {
                usernameTextView.setVisibility(View.GONE);
            }
            AvatarDrawable avatarDrawable = new AvatarDrawable(best);
            avatarImageView.setForUserOrChat(best, avatarDrawable);
            resultContainer.setVisibility(View.VISIBLE);
        } else {
            statusTextView.setText("No contact found with this number.\nMake sure the number is saved in your contacts.");
            statusTextView.setVisibility(View.VISIBLE);
        }
    }

    private static int commonSuffixLength(String a, String b) {
        int i = a.length() - 1;
        int j = b.length() - 1;
        int count = 0;
        while (i >= 0 && j >= 0 && a.charAt(i) == b.charAt(j)) {
            count++;
            i--;
            j--;
        }
        return count;
    }
}
