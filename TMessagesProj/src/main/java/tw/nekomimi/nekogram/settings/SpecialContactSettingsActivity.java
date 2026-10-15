package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONException;
import org.json.JSONObject;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

import static org.telegram.messenger.AndroidUtilities.dp;

public class SpecialContactSettingsActivity extends BaseFragment {

    private static final int MENU_CONFIRM = 1;
    private static final int SOUND_PICKER_REQUEST = 7201;

    private long userId;
    private boolean isNewContact;
    private RecyclerListView listView;
    private ListAdapter listAdapter;

    private final int TYPE_USER_HEADER = 0;
    private final int TYPE_HEADER = 1;
    private final int TYPE_CHECK = 2;
    private final int TYPE_TEXT = 3;
    private final int TYPE_INFO = 4;
    private final int TYPE_LIGHT = 5;

    private final ArrayList<Integer> items = new ArrayList<>();

    private static final String[] CHECK_KEYS = {
            "goingOnline", "goingOffline", "readingMessage", "sendingMessage",
            "changingProfilePicture", "changingUsername"
    };

    private static final String[] CHECK_LABELS = {
            "Going Online", "Going Offline", "Reading Message", "Sending Message",
            "Changing Profile Picture", "Changing Username"
    };

    private static final boolean[] CHECK_DEFAULTS = {
            true, false, true, false, true, true
    };

    private static final String[] VIBRATE_LABELS = {"Default", "Short", "Disabled", "Long"};
    private static final String[] PRIORITY_LABELS = {"Default", "High", "Max", "Min"};

    private static final int[] LIGHT_COLORS = {
            0xFF4D9DE0, 0xFFE15554, 0xFF3BB273, 0xFFE1BC29,
            0xFF7768AE, 0xFFE07A5F, 0xFFFFFFFF, 0xFF9E9E9E
    };

    public SpecialContactSettingsActivity(long userId) {
        this(userId, false);
    }

    public SpecialContactSettingsActivity(long userId, boolean isNewContact) {
        super();
        this.userId = userId;
        this.isNewContact = isNewContact;
    }

    private SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("kuro_special_contacts", Context.MODE_PRIVATE);
    }

    private JSONObject loadSettings() {
        String saved = prefs().getString("settings_" + userId, null);
        if (saved != null) {
            try {
                return new JSONObject(saved);
            } catch (JSONException ignored) {
            }
        }
        JSONObject obj = new JSONObject();
        for (int i = 0; i < CHECK_KEYS.length; i++) {
            try {
                obj.put(CHECK_KEYS[i], CHECK_DEFAULTS[i]);
            } catch (JSONException ignored) {
            }
        }
        try {
            obj.put("actionsNotification", true);
            obj.put("sound", "Default");
            obj.put("vibrate", 0);
            obj.put("priority", 0);
            obj.put("lightColor", LIGHT_COLORS[0]);
        } catch (JSONException ignored) {
        }
        return obj;
    }

    private void saveSettings(JSONObject obj) {
        prefs().edit().putString("settings_" + userId, obj.toString()).apply();
    }

    private void commitAddContact() {
        SharedPreferences p = prefs();
        String saved = p.getString("contacts", "[]");
        try {
            org.json.JSONArray arr = new org.json.JSONArray(saved);
            boolean exists = false;
            for (int i = 0; i < arr.length(); i++) {
                if (arr.getLong(i) == userId) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                arr.put(userId);
                p.edit().putString("contacts", arr.toString()).apply();
            }
        } catch (JSONException ignored) {
        }
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        handleBackPressed();
        return false;
    }

    private void handleBackPressed() {
        if (!isNewContact) {
            finishFragment();
            return;
        }
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getParentActivity());
        builder.setTitle("Add Special Contact?");
        builder.setMessage("Do you want to add this contact to your special contact list?");
        builder.setPositiveButton("Add", (dialog, which) -> {
            commitAddContact();
            finishFragment();
        });
        builder.setNegativeButton("Discard", (dialog, which) -> finishFragment());
        builder.show();
    }

    private void updateRows() {
        items.clear();
        items.add(TYPE_USER_HEADER);
        items.add(TYPE_HEADER); // Actions
        for (int i = 0; i < CHECK_KEYS.length; i++) {
            items.add(TYPE_CHECK);
        }
        items.add(TYPE_HEADER); // General
        items.add(TYPE_CHECK); // Actions Notification
        items.add(TYPE_TEXT); // Sound
        items.add(TYPE_TEXT); // Vibrate
        items.add(TYPE_TEXT); // Priority
        items.add(TYPE_INFO); // priority info
        items.add(TYPE_HEADER); // Light
        items.add(TYPE_LIGHT); // Color
        items.add(TYPE_INFO); // light info
    }

    @Override
    public boolean onFragmentCreate() {
        updateRows();
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Special Contact Settings");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    handleBackPressed();
                } else if (id == MENU_CONFIRM) {
                    commitAddContact();
                    finishFragment();
                }
            }
        });
        if (isNewContact) {
            actionBar.createMenu().addItem(MENU_CONFIRM, R.drawable.ic_ab_done);
        }

        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listAdapter = new ListAdapter(context);
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            int type = items.get(position);
            if (type == TYPE_CHECK) {
                JSONObject settings = loadSettings();
                int checkIndex = getCheckIndexForPosition(position);
                boolean newVal;
                if (checkIndex >= 0) {
                    newVal = !settings.optBoolean(CHECK_KEYS[checkIndex], CHECK_DEFAULTS[checkIndex]);
                    try {
                        settings.put(CHECK_KEYS[checkIndex], newVal);
                    } catch (JSONException ignored) {
                    }
                } else {
                    newVal = !settings.optBoolean("actionsNotification", true);
                    try {
                        settings.put("actionsNotification", newVal);
                    } catch (JSONException ignored) {
                    }
                }
                saveSettings(settings);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(newVal);
                }
            } else if (type == TYPE_TEXT) {
                int textPos = getTextRowIndex(position);
                if (textPos == 0) {
                    openSoundPicker();
                } else if (textPos == 1) {
                    openVibratePicker();
                } else if (textPos == 2) {
                    openPriorityPicker();
                }
            } else if (type == TYPE_LIGHT) {
                openColorPicker();
            }
        });

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return fragmentView;
    }

    private void openSoundPicker() {
        if (getParentActivity() == null) {
            return;
        }
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
        getParentActivity().startActivityForResult(intent, SOUND_PICKER_REQUEST);
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == SOUND_PICKER_REQUEST && data != null) {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            JSONObject settings = loadSettings();
            try {
                settings.put("sound", uri != null ? uri.toString() : "NoSound");
            } catch (JSONException ignored) {
            }
            saveSettings(settings);
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        }
    }

    private void openVibratePicker() {
        if (getParentActivity() == null) {
            return;
        }
        JSONObject settings = loadSettings();
        int current = settings.optInt("vibrate", 0);
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getParentActivity());
        builder.setTitle("Vibrate");
        builder.setSingleChoiceItems(VIBRATE_LABELS, current, (dialog, which) -> {
            JSONObject s = loadSettings();
            try {
                s.put("vibrate", which);
            } catch (JSONException ignored) {
            }
            saveSettings(s);
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
            dialog.dismiss();
        });
        builder.show();
    }

    private void openPriorityPicker() {
        if (getParentActivity() == null) {
            return;
        }
        JSONObject settings = loadSettings();
        int current = settings.optInt("priority", 0);
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getParentActivity());
        builder.setTitle("Priority");
        builder.setSingleChoiceItems(PRIORITY_LABELS, current, (dialog, which) -> {
            JSONObject s = loadSettings();
            try {
                s.put("priority", which);
            } catch (JSONException ignored) {
            }
            saveSettings(s);
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
            dialog.dismiss();
        });
        builder.show();
    }

    private void openColorPicker() {
        if (getParentActivity() == null) {
            return;
        }
        Context context = getParentActivity();
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(dp(16), dp(16), dp(16), dp(8));

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
        builder.setTitle("Light Color");
        android.app.AlertDialog dialog = builder.setView(layout).create();

        for (int color : LIGHT_COLORS) {
            FrameLayout swatch = new FrameLayout(context);
            android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
            circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            circle.setColor(color);
            circle.setStroke(dp(1), 0x33000000);
            swatch.setBackground(circle);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(36));
            lp.setMargins(dp(6), 0, dp(6), 0);
            swatch.setOnClickListener(v -> {
                JSONObject s = loadSettings();
                try {
                    s.put("lightColor", color);
                } catch (JSONException ignored) {
                }
                saveSettings(s);
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
                dialog.dismiss();
            });
            layout.addView(swatch, lp);
        }

        dialog.show();
    }

    private int getCheckIndexForPosition(int position) {
        int checkStart = 2;
        int idx = position - checkStart;
        if (idx >= 0 && idx < CHECK_KEYS.length) {
            return idx;
        }
        return -1;
    }

    private int getTextRowIndex(int position) {
        int count = -1;
        for (int i = 0; i <= position; i++) {
            if (items.get(i) == TYPE_TEXT) {
                count++;
            }
        }
        return count;
    }

    private int getHeaderIndex(int position) {
        int count = -1;
        for (int i = 0; i <= position; i++) {
            if (items.get(i) == TYPE_HEADER) {
                count++;
            }
        }
        return count;
    }

    private String formatSound(JSONObject settings) {
        String sound = settings.optString("sound", "Default");
        if (sound.equals("Default") || sound.isEmpty()) {
            return "Default";
        }
        if (sound.equals("NoSound")) {
            return "None";
        }
        try {
            android.media.Ringtone ringtone = RingtoneManager.getRingtone(ApplicationLoader.applicationContext, Uri.parse(sound));
            if (ringtone != null) {
                return ringtone.getTitle(ApplicationLoader.applicationContext);
            }
        } catch (Exception ignored) {
        }
        return "Custom";
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = items.get(holder.getAdapterPosition());
            return type == TYPE_CHECK || type == TYPE_TEXT || type == TYPE_LIGHT;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case TYPE_USER_HEADER:
                    view = new UserCell(context, 16, 0, false);
                    break;
                case TYPE_HEADER:
                    view = new HeaderCell(context);
                    break;
                case TYPE_CHECK:
                    view = new TextCheckCell(context);
                    break;
                case TYPE_INFO:
                    view = new TextInfoPrivacyCell(context);
                    break;
                case TYPE_LIGHT:
                    view = new TextSettingsCell(context);
                    break;
                default:
                    view = new TextSettingsCell(context);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            int type = items.get(position);
            JSONObject settings = loadSettings();
            switch (type) {
                case TYPE_USER_HEADER: {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(userId);
                    String name = user != null ? ContactsController.formatName(user.first_name, user.last_name) : "Unknown";
                    String status = user != null ? LocaleController.formatUserStatus(currentAccount, user) : "";
                    ((UserCell) holder.itemView).setData(user, name, status, 0, false);
                    break;
                }
                case TYPE_HEADER: {
                    String[] headerLabels = {"Actions", "General", "Light"};
                    int idx = getHeaderIndex(position);
                    ((HeaderCell) holder.itemView).setText(idx >= 0 && idx < headerLabels.length ? headerLabels[idx] : "");
                    break;
                }
                case TYPE_CHECK: {
                    int checkIndex = getCheckIndexForPosition(position);
                    int pos = checkIndex >= 0 ? GroupCardHelper.positionInGroup(checkIndex, CHECK_KEYS.length) : GroupCardHelper.POS_TOP;
                    GroupCardHelper.applyCard(holder.itemView, pos);
                    boolean divider = GroupCardHelper.needsDivider(pos);
                    if (checkIndex >= 0) {
                        boolean val = settings.optBoolean(CHECK_KEYS[checkIndex], CHECK_DEFAULTS[checkIndex]);
                        ((TextCheckCell) holder.itemView).setTextAndCheck(CHECK_LABELS[checkIndex], val, divider);
                    } else {
                        boolean val = settings.optBoolean("actionsNotification", true);
                        ((TextCheckCell) holder.itemView).setTextAndCheck("Actions Notification", val, divider);
                    }
                    break;
                }
                case TYPE_TEXT: {
                    int textPos = getTextRowIndex(position);
                    // General card = [Actions Notification, Sound, Vibrate, Priority] (size 4); text rows are indices 1-3
                    int pos = GroupCardHelper.positionInGroup(textPos + 1, 4);
                    GroupCardHelper.applyCard(holder.itemView, pos);
                    boolean divider = GroupCardHelper.needsDivider(pos);
                    if (textPos == 0) {
                        ((TextSettingsCell) holder.itemView).setTextAndValue("Sound", formatSound(settings), divider);
                    } else if (textPos == 1) {
                        int v = settings.optInt("vibrate", 0);
                        ((TextSettingsCell) holder.itemView).setTextAndValue("Vibrate", VIBRATE_LABELS[v], divider);
                    } else if (textPos == 2) {
                        int p = settings.optInt("priority", 0);
                        ((TextSettingsCell) holder.itemView).setTextAndValue("Priority", PRIORITY_LABELS[p], divider);
                    }
                    break;
                }
                case TYPE_LIGHT: {
                    GroupCardHelper.applyCard(holder.itemView, GroupCardHelper.POS_SINGLE);
                    ((TextSettingsCell) holder.itemView).setTextAndValue("Color", "", false);
                    break;
                }
                case TYPE_INFO: {
                    int infoIdx = 0;
                    for (int i = 0; i <= position; i++) {
                        if (items.get(i) == TYPE_INFO) {
                            infoIdx++;
                        }
                    }
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (infoIdx == 1) {
                        cell.setText("Higher priority notifications will work even in Do Not Disturb mode.");
                    } else {
                        cell.setText("Blinking light used to indicate new messages on some devices.");
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position);
        }
    }
}
