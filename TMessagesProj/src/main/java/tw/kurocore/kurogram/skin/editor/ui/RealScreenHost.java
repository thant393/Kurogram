package tw.kurocore.kurogram.skin.editor.ui;

import android.app.Activity;
import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ContactsActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.SettingsActivity;

import java.util.ArrayList;

/**
 * Shows one of the app's own screens inside the Studio's phone frame, in its own navigation layout, so it is drawn with
 * the real views and the theme being edited. Only scrolling gets through: a plain tap is turned into a cancel, so the
 * preview can never open a chat, change a setting or sign out.
 */
final class RealScreenHost extends FrameLayout {
    private final ActionBarLayout layout;
    private final int slop;
    private float downX, downY;
    private boolean moved;

    /** The scenes that have a real screen to show. */
    static boolean supports(String scene) {
        return "chats".equals(scene) || "settings".equals(scene) || "contacts".equals(scene);
    }

    RealScreenHost(Context context, String scene) {
        super(context);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        layout = new ActionBarLayout((Activity) context, false);
        layout.setFragmentStack(new ArrayList<>());
        addView(layout, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        BaseFragment f;
        switch (scene) {
            case "settings":
                f = new SettingsActivity();
                break;
            case "contacts":
                f = new ContactsActivity(null);
                break;
            default:
                f = new DialogsActivity(null);
        }
        layout.presentFragment(f);
        layout.onResume();
    }

    void release() {
        try {
            layout.onPause();
            layout.removeAllFragments();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                moved = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(ev.getX() - downX) > slop || Math.abs(ev.getY() - downY) > slop) {
                    moved = true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (!moved) {
                    ev.setAction(MotionEvent.ACTION_CANCEL);
                }
                break;
            default:
        }
        return super.dispatchTouchEvent(ev);
    }
}
