package io.github.michelbr84.flapforge.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * P0 probe: does Robolectric 4.16 boot against the project's SDK and exercise the
 * android.graphics surface the shims (P1) will sit on? A green run here is what lets the
 * shim tests be plain JVM tests instead of emulator tests. NATIVE graphics (real Skia
 * rasterisation) is what the Graphics2D shim tests need; the LEGACY shadows fake pixels.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RobolectricProbeTest {

    @Test
    public void bootsAndDrawsOnAnAndroidGraphicsCanvas() {
        Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setARGB(255, 255, 0, 0);
        canvas.drawRect(0, 0, 8, 8, paint);
        assertEquals(0xFFFF0000, bitmap.getPixel(4, 4));
        assertNotNull(canvas.getMatrix());
    }
}
