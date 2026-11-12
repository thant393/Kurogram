package tw.kurocore.kurogram.stickerstore;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

import tw.kurocore.kurogram.skin.KuroUi;

/**
 * Loading placeholder (a "skeleton"): a grid of soft tiles with a light sweeping across them, instead of a
 * spinning circle. It only animates while it is visible.
 */
final class SkeletonView extends View {
    private final int columns;
    private final float tileAspect; // tile height / tile width
    private final boolean captions; // two grey lines under each tile
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF rect = new RectF();
    private LinearGradient sweep;
    private int sweepWidth;

    SkeletonView(Context context, int columns, float tileAspect, boolean captions) {
        super(context);
        this.columns = Math.max(1, columns);
        this.tileAspect = tileAspect;
        this.captions = captions;
        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int w = getMeasuredWidth();
        final int h = getMeasuredHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        final int base = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        final int tileColor = (base & 0x00FFFFFF) | (0x22 << 24);
        final int gap = AndroidUtilities.dp(10);
        final int pad = AndroidUtilities.dp(12);
        final float tileW = (w - pad * 2 - gap * (columns - 1)) / (float) columns;
        final float tileH = tileW * tileAspect;
        final float capH = captions ? AndroidUtilities.dp(34) : 0;
        final float radius = AndroidUtilities.dp(Math.max(6, Math.min(24, KuroUi.t().previewRadiusDp >= 0 ? KuroUi.t().previewRadiusDp : 12)));
        paint.setColor(tileColor);

        if (sweep == null || sweepWidth != w) {
            sweepWidth = w;
            final int hi = Color.argb(0x55, 255, 255, 255);
            sweep = new LinearGradient(0, 0, w * 0.6f, 0, new int[]{0x00FFFFFF, hi, 0x00FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            sweepPaint.setShader(sweep);
        }
        final float phase = (SystemClock.uptimeMillis() % 1400L) / 1400f;
        matrix.setTranslate(-w * 0.6f + phase * (w * 1.6f), 0);
        sweep.setLocalMatrix(matrix);

        float y = pad;
        while (y < h) {
            for (int c = 0; c < columns; c++) {
                float x = pad + c * (tileW + gap);
                rect.set(x, y, x + tileW, y + tileH);
                canvas.drawRoundRect(rect, radius, radius, paint);
                canvas.drawRoundRect(rect, radius, radius, sweepPaint);
                if (captions) {
                    rect.set(x, y + tileH + AndroidUtilities.dp(8), x + tileW * 0.8f, y + tileH + AndroidUtilities.dp(8) + AndroidUtilities.dp(10));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(5), AndroidUtilities.dp(5), paint);
                    rect.set(x, y + tileH + AndroidUtilities.dp(22), x + tileW * 0.5f, y + tileH + AndroidUtilities.dp(22) + AndroidUtilities.dp(8));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);
                }
            }
            y += tileH + capH + gap;
        }
        if (getVisibility() == VISIBLE) {
            postInvalidateOnAnimation();
        }
    }
}
