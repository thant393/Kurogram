package tw.nekomimi.nekogram.stickerstore;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Wraps its children onto new lines, like the tag chips on a store page. */
final class ChipFlow extends ViewGroup {
    private final int gap;

    ChipFlow(Context context, int gapPx) {
        super(context);
        this.gap = gapPx;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int maxW = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, lineH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            v.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED);
            int cw = v.getMeasuredWidth(), ch = v.getMeasuredHeight();
            if (x > 0 && x + cw > maxW) {
                x = 0;
                y += lineH + gap;
                lineH = 0;
            }
            x += cw + gap;
            lineH = Math.max(lineH, ch);
        }
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), y + lineH + getPaddingTop() + getPaddingBottom());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int maxW = r - l - getPaddingLeft() - getPaddingRight();
        int x = 0, y = 0, lineH = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            int cw = v.getMeasuredWidth(), ch = v.getMeasuredHeight();
            if (x > 0 && x + cw > maxW) {
                x = 0;
                y += lineH + gap;
                lineH = 0;
            }
            v.layout(getPaddingLeft() + x, getPaddingTop() + y, getPaddingLeft() + x + cw, getPaddingTop() + y + ch);
            x += cw + gap;
            lineH = Math.max(lineH, ch);
        }
    }
}
