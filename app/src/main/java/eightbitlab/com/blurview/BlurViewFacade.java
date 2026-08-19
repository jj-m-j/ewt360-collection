package eightbitlab.com.blurview;

import android.graphics.drawable.Drawable;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

public interface BlurViewFacade {

    /**
     * Enables/disables the blur. Enabled by default
     */
    BlurViewFacade setBlurEnabled(boolean enabled);

    /**
     * Can be used to stop blur auto update or resume if it was stopped before.
     */
    BlurViewFacade setBlurAutoUpdate(boolean enabled);

    /**
     * @param frameClearDrawable sets the drawable to draw before view hierarchy.
     *                           Optional, by default frame is cleared with a transparent color.
     */
    BlurViewFacade setFrameClearDrawable(@Nullable Drawable frameClearDrawable);

    /**
     * @param radius sets the blur radius. The real blur radius is radius * scaleFactor.
     *               Default value is {@link BlurController#DEFAULT_BLUR_RADIUS}
     */
    BlurViewFacade setBlurRadius(float radius);

    /**
     * Sets the color overlay to be drawn on top of blurred content
     */
    BlurViewFacade setOverlayColor(@ColorInt int overlayColor);
}
