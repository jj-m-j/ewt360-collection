package eightbitlab.com.blurview;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import eightbitlab.com.blurview.SizeScaler.Size;

@RequiresApi(api = Build.VERSION_CODES.S)
public class RenderNodeBlurController implements BlurController {
    private final int[] targetLocation = new int[2];
    private final int[] blurViewLocation = new int[2];

    private final BlurView blurView;
    private final BlurTarget target;
    private final RenderNode blurNode = new RenderNode("BlurView node");
    private final float scaleFactor;
    private final boolean applyNoise;

    private Drawable frameClearDrawable;
    private int overlayColor;
    private float blurRadius = 1f;
    private boolean enabled = true;

    @Nullable
    private Bitmap cachedBitmap;
    @Nullable
    private RenderScriptBlur fallbackBlur;

    // This tracks BlurView location in scrollable containers, during animations, etc.
    private final ViewTreeObserver.OnPreDrawListener drawListener = () -> {
        saveOnScreenLocation();
        updateRenderNodeProperties();
        return true;
    };

    public RenderNodeBlurController(@NonNull BlurView blurView, @NonNull BlurTarget target, int overlayColor, float scaleFactor, boolean applyNoise) {
        this.blurView = blurView;
        this.overlayColor = overlayColor;
        this.target = target;
        this.scaleFactor = scaleFactor;
        this.applyNoise = applyNoise;
        blurView.setWillNotDraw(false);
        blurView.getViewTreeObserver().addOnPreDrawListener(drawListener);
    }

    @Override
    public boolean draw(Canvas canvas) {
        if (!enabled) {
            return true;
        }
        saveOnScreenLocation();

        if (canvas.isHardwareAccelerated()) {
            if (!target.renderNode.hasDisplayList()) {
                // The target and the BlurView are presumably in different windows
                // and the BlurView's Window was traversed before the target's, so
                // we don't have a valid renderNode to blur.
                blurView.invalidate();
            }
            hardwarePath(canvas);
        } else {
            softwarePath(canvas);
        }
        return true;
    }

    private void hardwarePath(Canvas canvas) {
        blurNode.setPosition(0, 0, target.getWidth(), target.getHeight());
        updateRenderNodeProperties();

        drawSnapshot();

        canvas.save();
        canvas.clipRect(0f, 0f, blurView.getWidth(), blurView.getHeight());
        canvas.drawRenderNode(blurNode);
        if (applyNoise) {
            Noise.apply(canvas, blurView.getContext(), blurView.getWidth(), blurView.getHeight());
        }
        if (overlayColor != Color.TRANSPARENT) {
            canvas.drawColor(overlayColor);
        }
        canvas.restore();
    }

    private void updateRenderNodeProperties() {
        BlurViewTransform t = BlurViewTransform.compute(blurView, blurViewLocation, targetLocation);

        float layoutTranslationX = -t.layoutLeft;
        float layoutTranslationY = -t.layoutTop;

        blurNode.setPivotX(blurView.getWidth() / 2f - layoutTranslationX);
        blurNode.setPivotY(blurView.getHeight() / 2f - layoutTranslationY);
        blurNode.setTranslationX(layoutTranslationX);
        blurNode.setTranslationY(layoutTranslationY);
        blurNode.setScaleX(1f / t.scaleX);
        blurNode.setScaleY(1f / t.scaleY);
        blurNode.setRotationZ(-t.rotationDeg);

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.S) {
            applyBlur();
        }
    }

    private void drawSnapshot() {
        RecordingCanvas recordingCanvas = blurNode.beginRecording();
        if (frameClearDrawable != null) {
            frameClearDrawable.draw(recordingCanvas);
        }
        recordingCanvas.drawRenderNode(target.renderNode);
        applyBlur();
        blurNode.endRecording();
    }

    private void softwarePath(Canvas canvas) {
        SizeScaler sizeScaler = new SizeScaler(scaleFactor);
        Size original = new Size(blurView.getWidth(), blurView.getHeight());
        Size scaled = sizeScaler.scale(original);
        if (cachedBitmap == null || cachedBitmap.getWidth() != scaled.width || cachedBitmap.getHeight() != scaled.height) {
            cachedBitmap = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888);
        }
        Canvas softwareCanvas = new Canvas(cachedBitmap);

        softwareCanvas.save();
        setupCanvasMatrix(softwareCanvas, original, scaled);
        if (frameClearDrawable != null) {
            frameClearDrawable.draw(canvas);
        }
        try {
            target.draw(softwareCanvas);
        } catch (Exception e) {
            Log.e("BlurView", "Error during snapshot capturing", e);
        }
        softwareCanvas.restore();

        if (fallbackBlur == null) {
            fallbackBlur = new RenderScriptBlur(blurView.getContext());
        }
        fallbackBlur.blur(cachedBitmap, blurRadius);
        canvas.save();
        canvas.scale((float) original.width / scaled.width, (float) original.height / scaled.height);
        fallbackBlur.render(canvas, cachedBitmap);
        canvas.restore();
        if (applyNoise) {
            Noise.apply(canvas, blurView.getContext(), blurView.getWidth(), blurView.getHeight());
        }
        if (overlayColor != Color.TRANSPARENT) {
            canvas.drawColor(overlayColor);
        }
    }

    private void setupCanvasMatrix(Canvas canvas, Size targetSize, Size scaledSize) {
        float scaleFactorH = (float) targetSize.height / scaledSize.height;
        float scaleFactorW = (float) targetSize.width / scaledSize.width;

        float scaledLeftPosition = -getLeft() / scaleFactorW;
        float scaledTopPosition = -getTop() / scaleFactorH;

        canvas.translate(scaledLeftPosition, scaledTopPosition);
        canvas.scale(1 / scaleFactorW, 1 / scaleFactorH);
    }

    private int getTop() {
        return blurViewLocation[1] - targetLocation[1];
    }

    private int getLeft() {
        return blurViewLocation[0] - targetLocation[0];
    }

    @Override
    public void updateBlurViewSize() {
    }

    @Override
    public void destroy() {
        blurNode.discardDisplayList();
        if (fallbackBlur != null) {
            fallbackBlur.destroy();
            fallbackBlur = null;
        }
    }

    @Override
    public BlurViewFacade setBlurEnabled(boolean enabled) {
        this.enabled = enabled;
        blurView.invalidate();
        return this;
    }

    @Override
    public BlurViewFacade setBlurAutoUpdate(boolean enabled) {
        blurView.getViewTreeObserver().removeOnPreDrawListener(drawListener);
        if (enabled) {
            blurView.getViewTreeObserver().addOnPreDrawListener(drawListener);
        }
        return this;
    }

    @Override
    public BlurViewFacade setFrameClearDrawable(@Nullable Drawable frameClearDrawable) {
        this.frameClearDrawable = frameClearDrawable;
        return this;
    }

    @Override
    public BlurViewFacade setBlurRadius(float radius) {
        this.blurRadius = radius;
        applyBlur();
        return this;
    }

    private void applyBlur() {
        float realBlurRadius = blurRadius * scaleFactor;
        RenderEffect blur = RenderEffect.createBlurEffect(realBlurRadius, realBlurRadius, Shader.TileMode.CLAMP);
        blurNode.setRenderEffect(blur);
    }

    @Override
    public BlurViewFacade setOverlayColor(int overlayColor) {
        if (this.overlayColor != overlayColor) {
            this.overlayColor = overlayColor;
            blurView.invalidate();
        }
        return this;
    }

    void updateRotation(float rotation) {
        blurNode.setRotationZ(-rotation);
    }

    public void updateScaleX(float scaleX) {
        blurNode.setScaleX(1 / scaleX);
    }

    public void updateScaleY(float scaleY) {
        blurNode.setScaleY(1 / scaleY);
    }

    private void saveOnScreenLocation() {
        target.getLocationOnScreen(targetLocation);
        blurView.getLocationOnScreen(blurViewLocation);
    }
}
