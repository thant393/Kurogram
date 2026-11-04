package tw.kurocore.kurogram.feature;

import android.content.Context;

/**
 * Common base of every optional Kurogram feature. The public core only knows these interfaces; the real
 * implementation can live in the private kuro-features module. A feature that is not installed answers
 * {@link #isAvailable()} with false and must never crash when called.
 */
public interface KuroFeature {

    /** False for the safe stub that community (fork) builds use. */
    boolean isAvailable();

    /** Called once from ApplicationLoader after the app is initialised. Start background work here. */
    default void onAppStart(Context app) {
    }
}
