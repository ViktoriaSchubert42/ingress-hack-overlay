package de.goofygecko.hackoverlay;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** A pointy-top hexagon filling its bounds, with an optional border. */
final class HexDrawable extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    HexDrawable() {
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(Color.TRANSPARENT);
    }

    void setFillColor(int color) {
        if (fill.getColor() == color) return;
        fill.setColor(color);
        invalidateSelf();
    }

    /** Pass {@link Color#TRANSPARENT} for no border. */
    void setStroke(float width, int color) {
        if (stroke.getStrokeWidth() == width && stroke.getColor() == color) return;
        stroke.setStrokeWidth(width);
        stroke.setColor(color);
        buildPath(getBounds());
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        buildPath(bounds);
    }

    private void buildPath(Rect b) {
        // Inset by half the border so it isn't clipped at the edges.
        float in = stroke.getStrokeWidth() / 2f;
        float l = b.left + in, r = b.right - in, t = b.top + in, bot = b.bottom - in;
        float cx = (l + r) / 2f, h = bot - t;
        path.reset();
        path.moveTo(cx, t);
        path.lineTo(r, t + h / 4f);
        path.lineTo(r, t + h * 3f / 4f);
        path.lineTo(cx, bot);
        path.lineTo(l, t + h * 3f / 4f);
        path.lineTo(l, t + h / 4f);
        path.close();
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawPath(path, fill);
        if (Color.alpha(stroke.getColor()) > 0 && stroke.getStrokeWidth() > 0) {
            canvas.drawPath(path, stroke);
        }
    }

    @Override
    public void setAlpha(int alpha) {}

    @Override
    public void setColorFilter(ColorFilter colorFilter) {}

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
