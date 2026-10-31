package tw.nekomimi.nekogram.skin;

import org.telegram.ui.ActionBar.BaseFragment;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * What a skin can customise on each screen, as verified in the source (docs: Screen Capability Matrix).
 * Information only: nothing here changes how a screen renders. It exists so validators, the warning
 * list and later phases ask one place instead of guessing.
 *
 * Levels (a screen is only marked S where the behaviour was read in the code):
 *   S  supported today
 *   P  partially supported
 *   N  needs native work in that screen (not available yet)
 *   X  not feasible through a skin (kept native on purpose)
 */
public final class ScreenCapabilities {

    public static final char SUPPORTED = 'S';
    public static final char PARTIAL = 'P';
    public static final char NATIVE = 'N';
    public static final char INFEASIBLE = 'X';

    /** Column order of every row in {@link #TABLE}. */
    public enum Cap {
        COLORS, TYPOGRAPHY, SPACING, COMPONENTS, VISIBILITY, STRUCTURE, ORDERING, SLOTS, NAVIGATION, ANIMATION
    }

    //                                     colors typo spacing comps visib struct order slots nav anim
    private static final Map<String, String> TABLE = new HashMap<>();
    private static final Map<String, Set<String>> LOCKED = new HashMap<>();

    static {
        TABLE.put("chats",         "SPSSSSNPSN"); // DialogsActivity, DialogCell
        TABLE.put("contacts",      "SNSPSSNNSN"); // ContactsActivity, UserCell
        TABLE.put("people",        "SNNNSPNNSN"); // OnlineContactsActivity
        TABLE.put("unread",        "SNNNNNNNSN"); // UnreadChatsActivity
        TABLE.put("settings",      "SNNPSPPNSS"); // SettingsActivity (UniversalRecyclerView)
        TABLE.put("kuroSettings",  "SNNPNNNNNN"); // BaseKuroSettingsActivity screens
        TABLE.put("profile",       "PNNPNNNNNN"); // ProfileActivity (own ListAdapter)
        TABLE.put("themeStore",    "SPPSNPNNNN"); // stickerstore/*
        TABLE.put("privacy",       "SPPPNNNNNN"); // PrivacySettingsActivity (row ints); Phase 2: TextSettingsCell, TextCell, TextCheckCell, HeaderCell, TextInfoPrivacyCell, Switch tokens, section card style/radius/gap
        TABLE.put("notifications", "SPPPNNNNNN"); // NotificationsSettingsActivity (row ints)
        TABLE.put("dataStorage",   "SPPPNNNNNN"); // DataSettingsActivity (row ints)
        TABLE.put("devices",       "SPPPNNNNNN"); // SessionsActivity (row ints)
        TABLE.put("chat",          "SPPPNXXXNX"); // ChatActivity: colours/bubbles only, structure stays native

        // Rows a skin may never hide or reorder (security). Verified names: SessionsActivity row fields.
        LOCKED.put("devices", set("terminateAllSessions", "qrLogin", "autoTerminate"));
    }

    private ScreenCapabilities() {
    }

    private static Set<String> set(String... items) {
        Set<String> s = new HashSet<>();
        Collections.addAll(s, items);
        return Collections.unmodifiableSet(s);
    }

    public static boolean isKnown(String screenId) {
        return screenId != null && TABLE.containsKey(screenId);
    }

    /** One of S, P, N, X; 0 when the screen id is unknown. */
    public static char level(String screenId, Cap cap) {
        String row = screenId == null ? null : TABLE.get(screenId);
        return row == null ? 0 : row.charAt(cap.ordinal());
    }

    /** True for S and P. */
    public static boolean canUse(String screenId, Cap cap) {
        char l = level(screenId, cap);
        return l == SUPPORTED || l == PARTIAL;
    }

    public static boolean isLockedRow(String screenId, String rowId) {
        Set<String> s = screenId == null ? null : LOCKED.get(screenId);
        return s != null && s.contains(rowId);
    }

    /** The screen id a fragment declares (BaseFragment.screenId()), or null. */
    public static String idOf(BaseFragment fragment) {
        return fragment == null ? null : fragment.screenId();
    }
}
