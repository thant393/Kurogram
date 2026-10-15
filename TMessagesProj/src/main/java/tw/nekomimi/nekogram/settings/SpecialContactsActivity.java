package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Cells.ManageChatUserCell;
import org.telegram.ui.ContactsActivity;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public class SpecialContactsActivity extends BaseFragment implements ManageChatUserCell.ManageChatUserCellDelegate {

    private RecyclerListView listView;
    private ListAdapter listAdapter;

    private final int TYPE_ENABLE = 0;
    private final int TYPE_ENABLE_INFO = 1;
    private final int TYPE_BACKGROUND = 2;
    private final int TYPE_BACKGROUND_INFO = 3;
    private final int TYPE_ADD_BUTTON = 4;
    private final int TYPE_CONTACT = 5;

    private final ArrayList<Integer> items = new ArrayList<>();
    private final ArrayList<Long> contactIds = new ArrayList<>();

    private SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("kuro_special_contacts", Context.MODE_PRIVATE);
    }

    private boolean isFeatureEnabled() {
        return prefs().getBoolean("enabled", true);
    }

    private boolean isBackgroundEnabled() {
        return prefs().getBoolean("background", false);
    }

    private void loadContactIds() {
        contactIds.clear();
        String saved = prefs().getString("contacts", "[]");
        try {
            JSONArray arr = new JSONArray(saved);
            for (int i = 0; i < arr.length(); i++) {
                contactIds.add(arr.getLong(i));
            }
        } catch (JSONException ignored) {
        }
    }

    private void saveContactIds() {
        JSONArray arr = new JSONArray();
        for (Long id : contactIds) {
            arr.put((long) id);
        }
        prefs().edit().putString("contacts", arr.toString()).apply();
    }

    private void addContact(long userId) {
        if (!contactIds.contains(userId)) {
            contactIds.add(userId);
            saveContactIds();
        }
    }

    private void removeContact(long userId) {
        contactIds.remove(userId);
        saveContactIds();
        prefs().edit().remove("settings_" + userId).apply();
    }

    private void updateRows() {
        items.clear();
        items.add(TYPE_ENABLE);
        items.add(TYPE_ENABLE_INFO);
        items.add(TYPE_BACKGROUND);
        items.add(TYPE_BACKGROUND_INFO);
        items.add(TYPE_ADD_BUTTON);
        for (int i = 0; i < contactIds.size(); i++) {
            items.add(TYPE_CONTACT);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        loadContactIds();
        updateRows();
        return super.onFragmentCreate();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadContactIds();
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Special Contacts");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listAdapter = new ListAdapter(context);
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(listAdapter);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            int type = items.get(position);
            if (type == TYPE_ENABLE) {
                boolean newVal = !isFeatureEnabled();
                prefs().edit().putBoolean("enabled", newVal).apply();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(newVal);
                }
            } else if (type == TYPE_BACKGROUND) {
                boolean newVal = !isBackgroundEnabled();
                prefs().edit().putBoolean("background", newVal).apply();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(newVal);
                }
            } else if (type == TYPE_ADD_BUTTON) {
                openContactPicker();
            } else if (type == TYPE_CONTACT) {
                int contactIndex = getContactIndexForPosition(position);
                if (contactIndex >= 0) {
                    presentFragment(new SpecialContactActionsActivity(contactIds.get(contactIndex)));
                }
            }
        });

        listView.setOnItemLongClickListener((view, position) -> {
            int type = items.get(position);
            if (type == TYPE_CONTACT) {
                int contactIndex = getContactIndexForPosition(position);
                if (contactIndex >= 0) {
                    long userId = contactIds.get(contactIndex);
                    ItemOptions.makeOptions(this, view)
                            .add(R.drawable.msg_settings, "Settings", () -> {
                                presentFragment(new SpecialContactSettingsActivity(userId));
                            })
                            .add(R.drawable.msg_delete, "Delete", () -> {
                                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getParentActivity());
                                builder.setTitle("Remove Contact");
                                builder.setMessage("Remove this contact from Special Contacts?");
                                builder.setPositiveButton("Remove", (dialog, which) -> {
                                    removeContact(userId);
                                    updateRows();
                                    listAdapter.notifyDataSetChanged();
                                });
                                builder.setNegativeButton("Cancel", null);
                                builder.show();
                            })
                            .show();
                    return true;
                }
            }
            return false;
        });

        return fragmentView;
    }

    private int getContactIndexForPosition(int position) {
        int start = 5;
        int idx = position - start;
        if (idx >= 0 && idx < contactIds.size()) {
            return idx;
        }
        return -1;
    }

    private void openContactPicker() {
        android.os.Bundle args = new android.os.Bundle();
        args.putBoolean("returnAsResult", true);
        args.putBoolean("destroyAfterSelect", true);
        ContactsActivity contactsActivity = new ContactsActivity(args);
        contactsActivity.setDelegate((user, param, activity) -> {
            presentFragment(new SpecialContactSettingsActivity(user.id, true));
        });
        presentFragment(contactsActivity);
    }

        @Override
    public boolean onOptionsButtonCheck(ManageChatUserCell cell, boolean click) {
        if (!click) return true;
        Object obj = cell.getCurrentObject();
        if (!(obj instanceof TLRPC.User)) return true;

        long userId = ((TLRPC.User) obj).id;
        ItemOptions.makeOptions(this, cell)
                .add(R.drawable.msg_settings, "Settings", () -> {
                    presentFragment(new SpecialContactSettingsActivity(userId));
                })
                .add(R.drawable.msg_delete, "Delete", () -> {
                    android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getParentActivity());
                    builder.setTitle("Remove Contact");
                    builder.setMessage("Remove this contact from Special Contacts?");
                    builder.setPositiveButton("Remove", (dialog, which) -> {
                        removeContact(userId);
                        updateRows();
                        listAdapter.notifyDataSetChanged();
                    });
                    builder.setNegativeButton("Cancel", null);
                    builder.show();
                })
                .show();
        return true;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context context;

        ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = items.get(holder.getAdapterPosition());
            return type == TYPE_ENABLE || type == TYPE_BACKGROUND || type == TYPE_ADD_BUTTON || type == TYPE_CONTACT;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case TYPE_ENABLE:
                case TYPE_BACKGROUND:
                    view = new TextCheckCell(context);
                    break;
                case TYPE_ENABLE_INFO:
                case TYPE_BACKGROUND_INFO:
                    view = new TextInfoPrivacyCell(context);
                    break;
                case TYPE_CONTACT:
                    ManageChatUserCell manageCell = new ManageChatUserCell(context, 8, 0, true);
            manageCell.setDelegate(SpecialContactsActivity.this);
            view = manageCell;
                    break;
                default:
                    view = new TextSettingsCell(context);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            int type = items.get(position);
            switch (type) {
                case TYPE_ENABLE: {
                    int pos = GroupCardHelper.POS_SINGLE;
                    GroupCardHelper.applyCard(holder.itemView, pos);
                    ((TextCheckCell) holder.itemView).setTextAndCheck("Enable Special Contacts", isFeatureEnabled(), GroupCardHelper.needsDivider(pos));
                    break;
                }
                case TYPE_ENABLE_INFO:
                    ((TextInfoPrivacyCell) holder.itemView).setText("This feature helps by notifying you when your selected contacts do some specific actions. Like reading messages, changing profile details or going online.");
                    ((TextInfoPrivacyCell) holder.itemView).setBackgroundDrawable(null);
                    break;
                case TYPE_BACKGROUND: {
                    int pos = GroupCardHelper.POS_SINGLE;
                    GroupCardHelper.applyCard(holder.itemView, pos);
                    ((TextCheckCell) holder.itemView).setTextAndCheck("Enable for Background", isBackgroundEnabled(), false);
                    break;
                }
                case TYPE_BACKGROUND_INFO:
                    ((TextInfoPrivacyCell) holder.itemView).setText("Use Special Contacts while app is closed.\nKeep in mind that this might increase your phone battery usage.");
                    ((TextInfoPrivacyCell) holder.itemView).setBackgroundDrawable(null);
                    break;
                case TYPE_ADD_BUTTON: {
                    int pos = contactIds.isEmpty() ? GroupCardHelper.POS_SINGLE : GroupCardHelper.POS_TOP;
                    GroupCardHelper.applyCard(holder.itemView, pos);
                    ((TextSettingsCell) holder.itemView).setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                    ((TextSettingsCell) holder.itemView).setText("Add New Contact", GroupCardHelper.needsDivider(pos));
                    break;
                }
                case TYPE_CONTACT: {
                    int contactIndex = getContactIndexForPosition(position);
                    if (contactIndex >= 0) {
                        long userId = contactIds.get(contactIndex);
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(userId);
                        String name = user != null ? ContactsController.formatName(user.first_name, user.last_name) : "Unknown";
                        String lastAction = SpecialContactsMonitor.lastActionText(userId);
                        String status = lastAction != null ? lastAction : "No new action";
                        boolean isLast = contactIndex == contactIds.size() - 1;
                        int pos = isLast ? GroupCardHelper.POS_BOTTOM : GroupCardHelper.POS_MIDDLE;
                        GroupCardHelper.applyCard(holder.itemView, pos);
                        ((ManageChatUserCell) holder.itemView).setData(user, name, status, GroupCardHelper.needsDivider(pos));
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
