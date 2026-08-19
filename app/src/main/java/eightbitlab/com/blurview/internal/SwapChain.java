package eightbitlab.com.blurview.internal;

import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.ImageFormat;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * Producer/consumer swap chain for the blur pipeline: an {@link ImageReader}-backed EGL window
 * surface that the final blur pass renders into. All methods run on the thread that owns the shared
 * {@link EglCore}.
 */
@RequiresApi(Build.VERSION_CODES.Q)
final class SwapChain {

    private static final ColorSpace SRGB = ColorSpace.get(ColorSpace.Named.SRGB);

    private final EglCore eglCore;
    private final int bufferCount;

    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
    private ImageReader imageReader;

    SwapChain(EglCore eglCore, int bufferCount) {
        this.eglCore = eglCore;
        this.bufferCount = bufferCount;
    }

    void setSize(int width, int height) {
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
            eglCore.makeNothingCurrent();
            eglCore.destroySurface(eglSurface);
            eglSurface = EGL14.EGL_NO_SURFACE;
        }
        if (imageReader != null) {
            imageReader.close();
        }
        imageReader = ImageReader.newInstance(width, height, ImageFormat.PRIVATE, bufferCount,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT);
        eglSurface = eglCore.createWindowSurface(imageReader.getSurface());
    }

    boolean makeCurrent() {
        return eglSurface != EGL14.EGL_NO_SURFACE && eglCore.makeCurrent(eglSurface);
    }

    void swapBuffers() {
        eglCore.swapBuffers(eglSurface);
    }

    @Nullable
    OpenGLBlurPipeline.Lease acquireLease() {
        Image image = imageReader.acquireLatestImage();
        if (image == null) {
            return null;
        }
        HardwareBuffer buffer = image.getHardwareBuffer();
        Bitmap bitmap = wrapBuffer(buffer);
        if (bitmap == null) {
            if (buffer != null) {
                buffer.close();
            }
            image.close();
            return null;
        }
        return new OpenGLBlurPipeline.Lease(image, buffer, bitmap);
    }

    void release() {
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
            eglCore.makeNothingCurrent();
            eglCore.destroySurface(eglSurface);
            eglSurface = EGL14.EGL_NO_SURFACE;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    @Nullable
    private static Bitmap wrapBuffer(@Nullable HardwareBuffer buffer) {
        if (buffer == null) {
            return null;
        }
        try {
            return Bitmap.wrapHardwareBuffer(buffer, SRGB);
        } catch (Exception wrapFailed) {
            return null;
        }
    }
}
