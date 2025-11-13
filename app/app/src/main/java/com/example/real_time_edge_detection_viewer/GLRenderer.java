package com.example.real_time_edge_detection_viewer;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.egl.EGLConfig;

public class GLRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "GLRenderer";

    // fullscreen quad (X,Y, S,T)
    private final float[] VERTEX_DATA = {
            -1f, -1f, 0f, 1f,
             1f, -1f, 1f, 1f,
            -1f,  1f, 0f, 0f,
             1f,  1f, 1f, 0f
    };
    private final FloatBuffer vertexBuffer;

    private int program = 0;
    private int aPositionLocation;
    private int aTexCoordLocation;
    private int uTextureLocation;
    private int[] texId = new int[1];

    private int frameWidth = 0;
    private int frameHeight = 0;

    public GLRenderer() {
        vertexBuffer = ByteBuffer.allocateDirect(VERTEX_DATA.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        vertexBuffer.put(VERTEX_DATA).position(0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        aPositionLocation = GLES20.glGetAttribLocation(program, "aPosition");
        aTexCoordLocation = GLES20.glGetAttribLocation(program, "aTexCoord");
        uTextureLocation = GLES20.glGetUniformLocation(program, "uTexture");

        // create texture
        GLES20.glGenTextures(1, texId, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0,0,width,height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // fetch processed buffer from native
        ByteBuffer buf = MainActivity.nativeGetProcessedFrameBuffer();
        int w = MainActivity.nativeGetProcessedFrameWidth();
        int h = MainActivity.nativeGetProcessedFrameHeight();

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        if (buf != null && w>0 && h>0) {
            // ensure pixel storage
            buf.position(0);
            // bind texture and upload
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId[0]);

            // If texture size changed, allocate. We'll use RGBA format (GL_RGBA, GL_UNSIGNED_BYTE)
            if (frameWidth != w || frameHeight != h) {
                frameWidth = w; frameHeight = h;
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, frameWidth, frameHeight, 0,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
            } else {
                GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, frameWidth, frameHeight,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
            }

            // draw textured quad
            GLES20.glUseProgram(program);

            vertexBuffer.position(0);
            GLES20.glEnableVertexAttribArray(aPositionLocation);
            GLES20.glVertexAttribPointer(aPositionLocation, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer);

            vertexBuffer.position(2);
            GLES20.glEnableVertexAttribArray(aTexCoordLocation);
            GLES20.glVertexAttribPointer(aTexCoordLocation, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId[0]);
            GLES20.glUniform1i(uTextureLocation, 0);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            GLES20.glDisableVertexAttribArray(aPositionLocation);
            GLES20.glDisableVertexAttribArray(aTexCoordLocation);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        }
    }

    private int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new RuntimeException("Shader compile error: " + log);
        }
        return shader;
    }

    private int createProgram(String vs, String fs) {
        int v = compileShader(GLES20.GL_VERTEX_SHADER, vs);
        int f = compileShader(GLES20.GL_FRAGMENT_SHADER, fs);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(p);
            GLES20.glDeleteProgram(p);
            throw new RuntimeException("Program link error: " + log);
        }
        return p;
    }

    // simple passthrough vertex shader
    private final String VERTEX_SHADER =
            "attribute vec2 aPosition;\n" +
            "attribute vec2 aTexCoord;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "  vTexCoord = aTexCoord;\n" +
            "  gl_Position = vec4(aPosition, 0.0, 1.0);\n" +
            "}\n";

    // fragment shader samples the RGBA texture
    private final String FRAGMENT_SHADER =
            "precision mediump float;\n" +
            "varying vec2 vTexCoord;\n" +
            "uniform sampler2D uTexture;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(uTexture, vTexCoord);\n" +
            "}\n";
}
