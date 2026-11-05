package tw.kurocore.kurogram.feature;

import java.util.ArrayList;

/**
 * Contact Changes: a log of name and profile photo changes of the user's contacts. The screen
 * (ContactChangesActivity) is core UI; detection and storage live in the implementation.
 */
public interface ContactChanges extends KuroFeature {

    /** One recorded change. type is "name" or "photo". */
    final class Entry {
        public long userId;
        public String name;
        public String type;
        public String oldValue;
        public String newValue;
        public long time;
    }

    /** The recorded changes, newest first. Never null. */
    ArrayList<Entry> log();

    void clearLog();

    /** Compares the contacts of the account with the last snapshot and records differences. True if anything changed. */
    boolean checkForChanges(int account);
}
