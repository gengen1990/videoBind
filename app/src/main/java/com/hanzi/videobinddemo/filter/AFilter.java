package com.hanzi.videobinddemo.filter;

import android.content.res.Resources;
import android.opengl.GLES20;
import android.util.Log;

import com.hanzi.videobinddemo.utils.MatrixUtils;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * Description:
 */
public abstract class AFilter implements Cloneable {

    private static final String TAG = "Filter";

    public static boolean DEBUG = true;
    /**
     * 单位矩阵
     */
    public static final float[] OM = MatrixUtils.getOriginalMatrix();
    /**
     * 程序句柄
     */
    protected int _mProgramHandler;
    /**
     * 顶点坐标句柄
     */
    protected int _mHPosition;
    /**
     * 纹理坐标句柄
     */
    protected int _mHCoord;
    /**
     * 总变换矩阵句柄
     */
    protected int mHMatrix;
    /**
     * 默认纹理贴图句柄
     */
    protected int mHTexture;

    protected Resources mRes;

    private int vertexHandler, fragmentHandler;

    /**
     * 顶点坐标Buffer
     */
    protected FloatBuffer mVerBuffer;

    /**
     * 纹理坐标Buffer
     */
    protected FloatBuffer mTexBuffer;

    /**
     * 索引坐标Buffer
     */

    protected int mFlag = 0;

    private float[] matrix = Arrays.copyOf(OM, 16);

    private int textureType = 0;      //默认使用Texture2D0
    private int textureId = 0;
    //顶点坐标
    public float pos[] = {
            -1.0f, 1.0f,
            -1.0f, -1.0f,
            1.0f, 1.0f,
            1.0f, -1.0f,
    };

    public float pos_180_mirror[] = {
            -1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, -1.0f,
            1.0f, 1.0f,
    };

    //纹理坐标
    public float[] coord = {
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 0.0f,
            1.0f, 1.0f,
    };

    public AFilter(Resources mRes) {
        deleteProgram();
        this.mRes = mRes;
        initBuffer(false);
    }

    public AFilter(Resources mRes, boolean isReverse) {
        deleteProgram();
        this.mRes = mRes;
        initBuffer(isReverse);
    }

    protected AFilter() {
    }

    public final void create() {
        onCreate();
    }

    public final void setSize(int width, int height) {
        onSizeChanged(width, height);
    }

    public void draw() {
        onClear();
        onUseProgram();
        onSetExpandData();
        onBindTexture();
        onDraw();
    }

    public final void setMatrix(float[] matrix) {
        this.matrix = matrix;
    }

    public float[] getMatrix() {
        return matrix;
    }

    public final void setTextureType(int type) {
        this.textureType = type;
    }

    public final int getTextureType() {
        return textureType;
    }

    public final int getTextureId() {
        return textureId;
    }

    public final void setTextureId(int textureId) {
        this.textureId = textureId;
    }

    public void setFlag(int flag) {
        this.mFlag = flag;
    }

    public int getFlag() {
        return mFlag;
    }


    public int getOutputTexture() {
        return -1;
    }

    /**
     * 实现此方法，完成程序的创建，可直接调用createProgram来实现
     */
    protected abstract void onCreate();

    protected abstract void onSizeChanged(int width, int height);

    protected void createProgram(String vertex, String fragment) {
        _mProgramHandler = uCreateGlProgram(vertex, fragment);
        _mHPosition = GLES20.glGetAttribLocation(_mProgramHandler, "vPosition");
        _mHCoord = GLES20.glGetAttribLocation(_mProgramHandler, "vCoord");
        mHMatrix = GLES20.glGetUniformLocation(_mProgramHandler, "vMatrix");
        mHTexture = GLES20.glGetUniformLocation(_mProgramHandler, "vTexture");
    }

    protected final void createProgramByAssetsFile(String vertex, String fragment) {
        createProgram(uRes(mRes, vertex), uRes(mRes, fragment));
    }

    /**
     * Buffer初始化
     */
    protected void initBuffer(boolean isReverse) {
        ByteBuffer a = ByteBuffer.allocateDirect(32);
        a.order(ByteOrder.nativeOrder());
        mVerBuffer = a.asFloatBuffer();
        if (isReverse) {
            mVerBuffer.put(pos_180_mirror);
            Log.d(TAG, "initBuffer: 180");
        } else {
            mVerBuffer.put(pos);
            Log.d(TAG, "initBuffer: 0");
        }
        mVerBuffer.position(0);
        ByteBuffer b = ByteBuffer.allocateDirect(32);
        b.order(ByteOrder.nativeOrder());
        mTexBuffer = b.asFloatBuffer();
        mTexBuffer.put(coord);
        mTexBuffer.position(0);
    }

    protected void onUseProgram() {
        GLES20.glUseProgram(_mProgramHandler);
    }

    /**
     * 启用顶点坐标和纹理坐标进行绘制
     */
    protected void onDraw() {
        GLES20.glEnableVertexAttribArray(_mHPosition);
        GLES20.glVertexAttribPointer(_mHPosition, 2, GLES20.GL_FLOAT, false, 0, mVerBuffer);
        GLES20.glEnableVertexAttribArray(_mHCoord);
        GLES20.glVertexAttribPointer(_mHCoord, 2, GLES20.GL_FLOAT, false, 0, mTexBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(_mHPosition);
        GLES20.glDisableVertexAttribArray(_mHCoord);
    }

    /**
     * 清除画布
     */
    protected void onClear() {
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
    }

    /**
     * 设置其他扩展数据
     */
    protected void onSetExpandData() {
        GLES20.glUniformMatrix4fv(mHMatrix, 1, false, matrix, 0);
    }

    /**
     * 绑定默认纹理
     */
    protected void onBindTexture() {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + textureType);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getTextureId());
        GLES20.glUniform1i(mHTexture, textureType);
    }

    public static void glError(int code, Object index) {
        if (DEBUG && code != 0) {
            Log.e(TAG, "glError:" + code + "---" + index);
        }
    }

    //通过路径加载Assets中的文本内容
    public static String uRes(Resources mRes, String path) {
        StringBuilder result = new StringBuilder();
        try {
            InputStream is = mRes.getAssets().open(path);
            int ch;
            byte[] buffer = new byte[1024];
            while (-1 != (ch = is.read(buffer))) {
                result.append(new String(buffer, 0, ch));
            }
        } catch (Exception e) {
            return null;
        }
        return result.toString().replaceAll("\\r\\n", "\n");
    }

    //创建GL程序
    public int uCreateGlProgram(String vertexSource, String fragmentSource) {
        vertexHandler = uLoadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vertexHandler == 0) return 0;
        fragmentHandler = uLoadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (fragmentHandler == 0) return 0;
        int program = GLES20.glCreateProgram();
        if (program != 0) {
            GLES20.glAttachShader(program, vertexHandler);
            GLES20.glAttachShader(program, fragmentHandler);
            GLES20.glLinkProgram(program);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES20.GL_TRUE) {
                glError(1, "Could not link program:" + GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                program = 0;
            }
        }
        return program;
    }

    /**
     * 加载shader
     */
    public int uLoadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        if (0 != shader) {
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                glError(1, "Could not compile shader:" + shaderType);
                glError(1, "GLES20 Error:" + GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
        }
        return shader;
    }

    public void deleteProgram() {
        if (fragmentHandler != 0) {
            GLES20.glDeleteShader(this.fragmentHandler);
            this.fragmentHandler = 0;
        }
        if (vertexHandler != 0) {
            GLES20.glDeleteShader(this.vertexHandler);
            this.vertexHandler = 0;
        }
        if (_mProgramHandler != 0) {
            GLES20.glDeleteProgram(_mProgramHandler);
        }
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
