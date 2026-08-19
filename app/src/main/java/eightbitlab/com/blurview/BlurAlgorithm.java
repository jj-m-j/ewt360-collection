package eightbitlab.com.blurview;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import androidx.annotation.NonNull;

public interface BlurAlgorithm {
    /**
     * @param bitmap     bitmap to be blurred
     * @param blurRadius blur radius
     * @return blurred bitmap
     */
    Bitmap blur(@NonNull Bitmap bitmap, float blurRadius);

    /**
     * Frees allocated resources
     */
    void destroy();

    /**
     * @return true if this algorithm returns the same instance of bitmap as it accepted
     * false if it creates a new instance.
     */
    boolean canModifyBitmap();

    /**
     * Retrieve the {@link android.graphics.Bitmap.Config} on which the {@link BlurAlgorithm}
     * can actually work.
     */
    @NonNull
    Bitmap.Config getSupportedBitmapConfig();

    void render(@NonNull Canvas canvas, @NonNull Bitmap bitmap);

    /**
     * Called when the BlurView leaves the window. Release any transient resources here; they will be
     * rebuilt lazily on the next {@link #blur(Bitmap, float)}. Default is a no-op.
     */
    default void onDetached() {
    }
}
