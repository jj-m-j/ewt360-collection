package eightbitlab.com.blurview.internal;

import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * Off-screen GL pipeline that blurs a snapshot bitmap and returns the result as a
 * {@link HardwareBuffer}-backed {@link Bitmap}. Owns the EGL context ({@link EglCore}), the output
 * {@link SwapChain}, the {@link SourceTexture}, the {@link RenderTexture} and the
 * {@link BlurProgram}. Per frame: upload the snapshot into the {@link SourceTexture}, run the
 * horizontal Gaussian into the {@link RenderTexture}, the vertical Gaussian to the output surface,
 * then read the result back as a HardwareBuffer bitmap.
 */
@RequiresApi(Build.VERSION_CODES.Q)
public final class OpenGLBlurPipeline {

    private static final int OUTPUT_FRAMEBUFFER = 0;
    private static final int[] OUTPUT_DISCARD = {GLES30.GL_COLOR};

    private final EglCore eglCore = new EglCore();
    private final SwapChain swapChain;
    private final GaussianKernel kernel = new GaussianKernel();
    private final SourceTexture source = new SourceTexture();
    private final RenderTexture horizontalPassTarget = new RenderTexture();

    private int outputWidth;
    private int outputHeight;

    private BlurProgram blurProgram;
    private int quadVbo;
    private int quadVao;

    public OpenGLBlurPipeline(int bufferCount) {
        swapChain = new SwapChain(eglCore, bufferCount);
    }

    public void setSize(int newWidth, int newHeight) {
        if (newWidth == outputWidth && newHeight == outputHeight) {
            return;
        }
        outputWidth = newWidth;
        outputHeight = newHeight;
        swapChain.setSize(outputWidth, outputHeight);
    }

    @Nullable
    public Lease render(Bitmap bitmap, float blurRadius) {
        if (!swapChain.makeCurrent()) {
            return null;
        }
        ensureGlObjects();
        horizontalPassTarget.ensureSize(outputWidth, outputHeight);
        GLES30.glBindVertexArray(quadVao);

        source.upload(bitmap);
        kernel.ensureRadius(blurRadius);

        horizontalPassTarget.bind();
        blurProgram.draw(kernel, source.id(), outputWidth, outputHeight, BlurProgram.Axis.HORIZONTAL, false);

        bindOutputSurface();
        blurProgram.draw(kernel, horizontalPassTarget.texture(), outputWidth, outputHeight, BlurProgram.Axis.VERTICAL, true);

        swapChain.swapBuffers();
        GLES20.glFinish();
        return swapChain.acquireLease();
    }

    public void release() {
        swapChain.makeCurrent();
        if (blurProgram != null) {
            blurProgram.release();
            GLES20.glDeleteBuffers(1, new int[]{quadVbo}, 0);
            GLES30.glDeleteVertexArrays(1, new int[]{quadVao}, 0);
            blurProgram = null;
            quadVbo = 0;
            quadVao = 0;
        }
        source.release();
        horizontalPassTarget.release();
        swapChain.release();
        eglCore.release();
    }

    private void bindOutputSurface() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, OUTPUT_FRAMEBUFFER);
        GLES20.glViewport(0, 0, outputWidth, outputHeight);
        GLES30.glInvalidateFramebuffer(GLES30.GL_FRAMEBUFFER, 1, OUTPUT_DISCARD, 0);
    }

    private void ensureGlObjects() {
        if (blurProgram != null) {
            return;
        }
        blurProgram = new BlurProgram();
        quadVbo = GlUtils.createFullscreenQuad();
        quadVao = GlUtils.createQuadVao(quadVbo);
    }

    public static final class Lease {
        public final Bitmap bitmap;
        private final Image image;
        private final HardwareBuffer buffer;

        Lease(Image image, HardwareBuffer buffer, Bitmap bitmap) {
            this.image = image;
            this.buffer = buffer;
            this.bitmap = bitmap;
        }

        public void close() {
            bitmap.recycle();
            buffer.close();
            image.close();
        }
    }
}
