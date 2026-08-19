package eightbitlab.com.blurview.internal;

import android.opengl.GLES20;
import android.opengl.GLES30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Stateless GL helpers shared by the OpenGL blur pipeline: shader and program building, and the
 * fixed fullscreen-quad setup. All methods must be called on the thread that owns the GL context.
 */
final class GlUtils {

    static final int POSITION_LOCATION = 0;
    static final int UV_LOCATION = 1;

    private static final int FLOAT_BYTES = 4;
    private static final int STRIDE_BYTES = 4 * FLOAT_BYTES;
    private static final int UV_OFFSET_BYTES = 2 * FLOAT_BYTES;

    private GlUtils() {
    }

    static int linkProgram(String vertexSource, String fragmentSource) {
        int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader;
        try {
            fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        } catch (RuntimeException fragmentFailed) {
            GLES20.glDeleteShader(vertexShader);
            throw fragmentFailed;
        }
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);
        if (linkStatus[0] == 0) {
            String infoLog = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            throw new RuntimeException("GlUtils: program link failed: " + infoLog);
        }
        return program;
    }

    private static int compileShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == 0) {
            String infoLog = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new RuntimeException("GlUtils: shader compile failed: " + infoLog);
        }
        return shader;
    }

    static int createFullscreenQuad() {
        float[] vertices = {
                -1f, -1f, 0f, 1f,
                1f, -1f, 1f, 1f,
                -1f, 1f, 0f, 0f,
                1f, 1f, 1f, 0f
        };
        FloatBuffer vertexBuffer = ByteBuffer
                .allocateDirect(vertices.length * FLOAT_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        vertexBuffer.put(vertices);
        vertexBuffer.position(0);

        int[] bufferIds = new int[1];
        GLES20.glGenBuffers(1, bufferIds, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bufferIds[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.length * FLOAT_BYTES, vertexBuffer, GLES20.GL_STATIC_DRAW);
        return bufferIds[0];
    }

    static int createQuadVao(int vbo) {
        int[] vaoIds = new int[1];
        GLES30.glGenVertexArrays(1, vaoIds, 0);
        GLES30.glBindVertexArray(vaoIds[0]);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glEnableVertexAttribArray(POSITION_LOCATION);
        GLES20.glVertexAttribPointer(POSITION_LOCATION, 2, GLES20.GL_FLOAT, false, STRIDE_BYTES, 0);
        GLES20.glEnableVertexAttribArray(UV_LOCATION);
        GLES20.glVertexAttribPointer(UV_LOCATION, 2, GLES20.GL_FLOAT, false, STRIDE_BYTES, UV_OFFSET_BYTES);
        GLES30.glBindVertexArray(0);
        return vaoIds[0];
    }

    static void setDefaultTextureParams() {
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
    }
}
