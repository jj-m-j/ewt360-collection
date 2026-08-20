package com.kyant.backdrop

import android.os.Build

/** 能力检测：RenderEffect（Blur 等）需要 Android 12+ */
fun isRenderEffectSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** 能力检测：RuntimeShader（Lens 折射/AGSL 高光）需要 Android 13+ */
fun isRuntimeShaderSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
