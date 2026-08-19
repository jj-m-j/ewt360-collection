package eightbitlab.com.blurview;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Blur Controller that handles all blur logic for the attached View.
 * It honors View size changes, View animation and Visibility changes.
 * It uses {@link ViewTreeObserver.OnPreDrawListener} to detect when blur should be updated.
 */
public final class PreDrawBlurController implements BlurController {

    @ColorInt
    public static final int TRANSPARENT = 0;

    private float blurRadius = DEFAULT_BLUR_RADIUS;

    private final BlurAlgorithm blurAlgorithm;
    private final float scaleFactor;
    private final boolean applyNoise;
    private BlurViewCanvas internalCanvas;
    private Bitmap internalBitmap;
    @Nullable
    private Bitmap displayBitmap;

    @SuppressWarnings("WeakerAccess")
    final View blurView;
    private int overlayColor;
    private final BlurTarget rootView;
    private final int[] rootLocation = new int[2];
    private final int[] blurViewLocation = new int[2];

    private int lastGeneration = -1;
    private int lastLeft = Integer.MIN_VALUE;
    private int lastTop = Integer.MIN_VALUE;
    private float lastScaleX = Float.NaN;
    private float lastScaleY = Float.NaN;
    private float lastRotation = Float.NaN;
    private boolean forceNextCapture;

    private float capturedScaleX = 1f;
    private float capturedScaleY = 1f;

    private final ViewTreeObserver.OnPreDrawListener drawListener = new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
            if (shouldUpdate()) {
                updateBlur();
            }
            return true;
        }
    };

    private final View.OnAttachStateChangeListener attachStateListener = new View.OnAttachStateChangeListener() {
        @Override
        public void onViewAttachedToWindow(@NonNull View view) {
        }

        @Override
        public void onViewDetachedFromWindow(@NonNull View view) {
            blurAlgorithm.onDetached();
            if (!blurAlgorithm.canModifyBitmap()) {
                displayBitmap = null;
            }
        }
    };

    private boolean blurEnabled = true;
    private boolean initialized;

    @Nullable
    private Drawable frameClearDrawable;

    public PreDrawBlurController(@NonNull View blurView,
                                 @NonNull BlurTarget rootView,
                                 @ColorInt int overlayColor,
                                 BlurAlgorithm algorithm,
                                 float scaleFactor,
                                 boolean applyNoise) {
        this.rootView = rootView;
        this.blurView = blurView;
        this.overlayColor = overlayColor;
        this.blurAlgorithm = algorithm;
        this.scaleFactor = scaleFactor;
        this.applyNoise = applyNoise;
        blurView.addOnAttachStateChangeListener(attachStateListener);

        int measuredWidth = blurView.getMeasuredWidth();
        int measuredHeight = blurView.getMeasuredHeight();

        init(measuredWidth, measuredHeight);
    }

    @SuppressWarnings("WeakerAccess")
    void init(int measuredWidth, int measuredHeight) {
        setBlurAutoUpdate(true);
        SizeScaler sizeScaler = new SizeScaler(scaleFactor);
        if (sizeScaler.isZeroSized(measuredWidth, measuredHeight)) {
            blurView.setWillNotDraw(true);
            return;
        }

        blurView.setWillNotDraw(false);
        SizeScaler.Size bitmapSize = sizeScaler.scale(measuredWidth, measuredHeight);
        internalBitmap = Bitmap.createBitmap(bitmapSize.width, bitmapSize.height, blurAlgorithm.getSupportedBitmapConfig());
        internalCanvas = new BlurViewCanvas(internalBitmap);
        initialized = true;
        updateBlur();
    }

    @SuppressWarnings("WeakerAccess")
    void updateBlur() {
        if (!blurEnabled || !initialized) {
            return;
        }

        if (frameClearDrawable == null) {
            internalBitmap.eraseColor(Color.TRANSPARENT);
        } else {
            frameClearDrawable.draw(internalCanvas);
        }

        internalCanvas.save();
        setupInternalCanvasMatrix();
        try {
            rootView.draw(internalCanvas);
        } catch (Exception e) {
            Log.e("BlurView", "Error during snapshot capturing", e);
        }
        internalCanvas.restore();

        blurAndSave();
    }

    private void setupInternalCanvasMatrix() {
        rootView.getLocationOnScreen(rootLocation);
        blurView.getLocationOnScreen(blurViewLocation);

        BlurViewTransform t = BlurViewTransform.compute(blurView, blurViewLocation, rootLocation);

        float rootCenterX = t.layoutLeft + blurView.getWidth() / 2f;
        float rootCenterY = t.layoutTop + blurView.getHeight() / 2f;

        float scaleFactorH = (float) blurView.getHeight() / internalBitmap.getHeight();
        float scaleFactorW = (float) blurView.getWidth() / internalBitmap.getWidth();
        float bitmapCenterX = internalBitmap.getWidth() / 2f;
        float bitmapCenterY = internalBitmap.getHeight() / 2f;

        internalCanvas.translate(bitmapCenterX, bitmapCenterY);
        internalCanvas.rotate(-t.rotationDeg);
        internalCanvas.scale(1f / (scaleFactorW * t.scaleX), 1f / (scaleFactorH * t.scaleY));
        internalCanvas.translate(-rootCenterX, -rootCenterY);

        capturedScaleX = t.scaleX;
        capturedScaleY = t.scaleY;
    }

    @Override
    public boolean draw(Canvas canvas) {
        if (!blurEnabled || !initialized || displayBitmap == null) {
            return true;
        }
        if (canvas instanceof BlurViewCanvas) {
            return false;
        }

        float scaleFactorH = (float) blurView.getHeight() / displayBitmap.getHeight();
        float scaleFactorW = (float) blurView.getWidth() / displayBitmap.getWidth();

        canvas.save();
        canvas.clipRect(0f, 0f, blurView.getWidth(), blurView.getHeight());
        canvas.save();
        canvas.scale(scaleFactorW, scaleFactorH);
        blurAlgorithm.render(canvas, displayBitmap);
        canvas.restore();
        if (applyNoise) {
            Noise.apply(canvas, blurView.getContext(), blurView.getWidth(), blurView.getHeight());
        }
        if (overlayColor != TRANSPARENT) {
            canvas.drawColor(overlayColor);
        }
        canvas.restore();
        return true;
    }

    private void blurAndSave() {
        float scaleCompensation = (capturedScaleX + capturedScaleY) / 2f;
        displayBitmap = blurAlgorithm.blur(internalBitmap, blurRadius / scaleCompensation);
        if (!blurAlgorithm.canModifyBitmap()) {
            blurView.invalidate();
        }
    }

    private boolean shouldUpdate() {
        if (blurAlgorithm.canModifyBitmap()) {
            return true;
        }
        int generation = rootView.contentGeneration;
        rootView.getLocationOnScreen(rootLocation);
        blurView.getLocationOnScreen(blurViewLocation);
        int left = blurViewLocation[0] - rootLocation[0];
        int top = blurViewLocation[1] - rootLocation[1];
        float scaleX = blurView.getScaleX();
        float scaleY = blurView.getScaleY();
        float rotation = blurView.getRotation();
        boolean changed = forceNextCapture
                || generation != lastGeneration
                || left != lastLeft
                || top != lastTop
                || scaleX != lastScaleX
                || scaleY != lastScaleY
                || rotation != lastRotation;
        forceNextCapture = false;
        lastGeneration = generation;
        lastLeft = left;
        lastTop = top;
        lastScaleX = scaleX;
        lastScaleY = scaleY;
        lastRotation = rotation;
        return changed;
    }

    @Override
    public void updateBlurViewSize() {
        int measuredWidth = blurView.getMeasuredWidth();
        int measuredHeight = blurView.getMeasuredHeight();

        init(measuredWidth, measuredHeight);
    }

    @Override
    public void destroy() {
        setBlurAutoUpdate(false);
        blurView.removeOnAttachStateChangeListener(attachStateListener);
        blurAlgorithm.destroy();
        initialized = false;
    }

    @Override
    public BlurViewFacade setBlurRadius(float radius) {
        this.blurRadius = radius;
        if (!blurAlgorithm.canModifyBitmap()) {
            forceNextCapture = true;
            blurView.invalidate();
        }
        return this;
    }

    @Override
    public BlurViewFacade setFrameClearDrawable(@Nullable Drawable frameClearDrawable) {
        this.frameClearDrawable = frameClearDrawable;
        return this;
    }

    @Override
    public BlurViewFacade setBlurEnabled(boolean enabled) {
        this.blurEnabled = enabled;
        forceNextCapture = enabled;
        setBlurAutoUpdate(enabled);
        blurView.invalidate();
        return this;
    }

    public BlurViewFacade setBlurAutoUpdate(final boolean enabled) {
        rootView.getViewTreeObserver().removeOnPreDrawListener(drawListener);
        blurView.getViewTreeObserver().removeOnPreDrawListener(drawListener);
        if (enabled) {
            rootView.getViewTreeObserver().addOnPreDrawListener(drawListener);
            if (rootView.getWindowId() != blurView.getWindowId()) {
                blurView.getViewTreeObserver().addOnPreDrawListener(drawListener);
            }
        }
        return this;
    }

    @Override
    public BlurViewFacade setOverlayColor(int overlayColor) {
        if (this.overlayColor != overlayColor) {
            this.overlayColor = overlayColor;
            blurView.invalidate();
        }
        return this;
    }
}
