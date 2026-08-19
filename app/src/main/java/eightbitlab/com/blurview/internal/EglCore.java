package eightbitlab.com.blurview.internal;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.view.Surface;

/**
 * Minimal EGL bring-up for off-screen rendering: a default-display, RGBA8888, OpenGL ES 3.0 context.
 * ES 3.0 is required - the blur pipeline uses VAOs, "#version 300 es" shaders, and explicit
 * layout(location = ...) attributes. Must be constructed and used on a single thread.
 */
class EglCore {

    private EGLDisplay display;
    private EGLContext context;
    private EGLConfig config;

    EglCore() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version = new int[2];
        EGL14.eglInitialize(display, version, 0, version, 1);

        int[] configAttributes = {
                EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfig = new int[1];
        EGL14.eglChooseConfig(display, configAttributes, 0, configs, 0, 1, numConfig, 0);
        config = configs[0];

        int[] contextAttributes = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE};
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttributes, 0);
    }

    EGLSurface createWindowSurface(Surface surface) {
        int[] surfaceAttributes = {EGL14.EGL_NONE};
        return EGL14.eglCreateWindowSurface(display, config, surface, surfaceAttributes, 0);
    }

    boolean makeCurrent(EGLSurface eglSurface) {
        return EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context);
    }

    boolean makeNothingCurrent() {
        return EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
    }

    boolean swapBuffers(EGLSurface eglSurface) {
        return EGL14.eglSwapBuffers(display, eglSurface);
    }

    void destroySurface(EGLSurface eglSurface) {
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(display, eglSurface);
        }
    }

    void release() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            makeNothingCurrent();
            if (context != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(display, context);
            }
            EGL14.eglTerminate(display);
        }
        display = EGL14.EGL_NO_DISPLAY;
        context = EGL14.EGL_NO_CONTEXT;
        config = null;
    }
}
