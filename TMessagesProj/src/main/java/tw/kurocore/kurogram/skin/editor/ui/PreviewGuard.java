package tw.kurocore.kurogram.skin.editor.ui;

import org.telegram.messenger.FileLog;

import tw.kurocore.kurogram.skin.KuroSkinManager;

/**
 * The only place the Builder talks to the real app's look. Implements the preview-safety protocol (docs/theme-builder.md):
 * a draft is shown through {@link KuroSkinManager#preview}, which is memory-only, and ended on every way out. The Builder
 * never calls apply() through this class, so previewing can never replace the theme the person has applied.
 */
public final class PreviewGuard {
    private boolean active;

    public boolean isActive() {
        return active;
    }

    /** Shows (or updates) the real-app preview. Returns null on success, otherwise why the draft could not be shown. */
    public String show(String json) {
        try {
            KuroSkinManager.get().preview(json);
            active = true;
            return null;
        } catch (Throwable t) {
            // a failed preview must never leave a half-shown look behind
            end();
            FileLog.e(t);
            return t.getMessage() == null ? t.toString() : t.getMessage();
        }
    }

    /** Idempotent; safe from onPause, destroy, cancel and error paths. Only ends a preview this guard started. */
    public void end() {
        if (!active) {
            return;
        }
        active = false;
        try {
            KuroSkinManager.get().endPreview();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
