package eightbitlab.com.blurview;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import eightbitlab.com.blurview.internal.OpenGLBlurPipeline;

/**
 * Blur algorithm for API 29-30. The snapshot is captured into a software bitmap by the controller,
 * blurred by OpenGL into a HardwareBuffer-backed bitmap, and drawn the same frame.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class OpenGLBlurAlgorithm implements BlurAlgorithm {

    private static final int BUFFER_COUNT = 4;

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

    private OpenGLBlurPipeline pipeline;
    private OpenGLBlurPipeline.Lease currentLease;
    private OpenGLBlurPipeline.Lease previousLease;

    @Override
    public Bitmap blur(@NonNull Bitmap snapshot, float blurRadius) {
        if (pipeline == null) {
            pipeline = new OpenGLBlurPipeline(BUFFER_COUNT);
        }
        pipeline.setSize(snapshot.getWidth(), snapshot.getHeight());
        OpenGLBlurPipeline.Lease lease = pipeline.render(snapshot, blurRadius);
        if (lease == null) {
            return snapshot;
        }
        if (previousLease != null) {
            previousLease.close();
        }
        previousLease = currentLease;
        currentLease = lease;
        return lease.bitmap;
    }

    @Override
    public boolean canModifyBitmap() {
        return false;
    }

    @NonNull
    @Override
    public Bitmap.Config getSupportedBitmapConfig() {
        return Bitmap.Config.ARGB_8888;
    }

    @Override
    public void render(@NonNull Canvas canvas, @NonNull Bitmap bitmap) {
        canvas.drawBitmap(bitmap, 0f, 0f, paint);
    }

    @Override
    public void onDetached() {
        if (currentLease != null) {
            currentLease.close();
            currentLease = null;
        }
        if (previousLease != null) {
            previousLease.close();
            previousLease = null;
        }
        if (pipeline != null) {
            pipeline.release();
            pipeline = null;
        }
    }

    @Override
    public void destroy() {
        onDetached();
    }
}
