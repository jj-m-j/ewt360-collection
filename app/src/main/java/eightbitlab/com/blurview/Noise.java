package eightbitlab.com.blurview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;

import androidx.annotation.NonNull;

import java.util.Random;

class Noise {
    private static Paint noisePaint;

    static void apply(Canvas canvas, Context context, int width, int height) {
        initPaint(context);
        canvas.drawRect(0, 0, width, height, noisePaint);
    }

    private static void initPaint(Context context) {
        if (noisePaint == null) {
            Bitmap noiseBitmap = getNoiseBitmap(context);
            noisePaint = new Paint();
            noisePaint.setAntiAlias(true);
            noisePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
            noisePaint.setShader(new BitmapShader(noiseBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        }
    }

    @NonNull
    private static Bitmap getNoiseBitmap(Context context) {
        // 代码生成 64x64 灰度噪声（替代二进制 drawable，15% 不透明度），带浅蓝色调
        int size = 64;
        int[] pixels = new int[size * size];
        Random random = new Random(42);
        for (int i = 0; i < pixels.length; i++) {
            int v = random.nextInt(256);
            pixels[i] = Color.argb(38, v, v, Math.min(255, v + 12));
        }
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size);
        return bitmap;
    }
}
