package tw.kurocore.kurogram.feature;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Special Contacts: pick contacts and get told when they go online, read a message, change photo or username.
 * The screens (SpecialContacts*Activity) are core UI; the storage and the background monitoring live in the
 * implementation. A stub answers with empty/default values.
 */
public interface SpecialContacts extends KuroFeature {

    /** One recorded action of a contact ("is now online"). */
    final class Action {
        public String text;
        public long time;
    }

    boolean isEnabled();

    void setEnabled(boolean enabled);

    boolean isBackground();

    void setBackground(boolean background);

    /** The chosen contacts (user ids). Never null. */
    ArrayList<Long> contactIds();

    void saveContactIds(List<Long> ids);

    /** The saved per-contact settings, or null when nothing was saved yet. */
    JSONObject settings(long userId);

    void saveSettings(long userId, JSONObject settings);

    /** Forget the saved settings of a contact that was removed from the list. */
    void removeSettings(long userId);

    /** Recorded actions of a contact, newest first. Never null. */
    ArrayList<Action> actions(long userId);

    /** "is now online • 5m ago" for the list subtitle, or null when there is none. */
    String lastActionText(long userId);

    static String formatAgo(long time) {
        long minutes = (System.currentTimeMillis() - time) / 60000;
        if (minutes < 1) {
            return "just now";
        } else if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h ago";
        }
        return (hours / 24) + "d ago";
    }
}
