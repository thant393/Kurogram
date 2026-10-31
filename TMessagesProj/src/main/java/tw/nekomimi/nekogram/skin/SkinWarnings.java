package tw.nekomimi.nekogram.skin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Non-fatal findings about a skin file. A warning never rejects a theme and never changes how it is
 * applied: it only tells an author (or a debug screen) that a key is not understood or not wired yet.
 * Invalid VALUES of known keys are not warnings; those still reject the skin exactly as before.
 */
public final class SkinWarnings {

    /** Key is not part of the schema (a typo or a foreign key). Never raised below an "open" node (meta). */
    public static final String UNKNOWN_FIELD = "UNKNOWN_FIELD";
    /** Key is part of the schema and valid, but no screen reads it yet (or the screen does not support it). */
    public static final String RECOGNIZED_NOT_YET_SUPPORTED = "RECOGNIZED_NOT_YET_SUPPORTED";
    /** Key is supported elsewhere but not on the screen it targets. Reserved for later phases. */
    public static final String UNSUPPORTED_ON_SCREEN = "UNSUPPORTED_ON_SCREEN";
    /** A number outside its range was clamped. Reserved: today out-of-range values are rejected. */
    public static final String CLAMPED = "CLAMPED";
    /** schemaMinor is higher than this app knows; the file is still applied (unknown keys are ignored). */
    public static final String NEWER_MINOR = "NEWER_MINOR";
    /** A layout tried to hide or reorder a protected row. Reserved for the row-map phase. */
    public static final String LOCKED_ROW = "LOCKED_ROW";

    private static final int MAX_ENTRIES = 50;

    public static final class Entry {
        public final String code;
        public final String path;

        Entry(String code, String path) {
            this.code = code;
            this.path = path;
        }

        @Override
        public String toString() {
            return code + " " + path;
        }
    }

    /** Shared "no warnings" instance. Never add to it. */
    public static final SkinWarnings NONE = new SkinWarnings();

    private final List<Entry> entries = new ArrayList<>();

    void add(String code, String path) {
        if (this == NONE || entries.size() >= MAX_ENTRIES) {
            return;
        }
        entries.add(new Entry(code, path));
    }

    public List<Entry> all() {
        return Collections.unmodifiableList(entries);
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int count(String code) {
        int n = 0;
        for (Entry e : entries) {
            if (e.code.equals(code)) {
                n++;
            }
        }
        return n;
    }
}
