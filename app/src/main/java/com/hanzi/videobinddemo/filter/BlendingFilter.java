package com.hanzi.videobinddemo.filter;

import android.content.res.Resources;
import android.opengl.ETC1Util;
import android.opengl.GLES20;
import android.util.Log;

import com.hanzi.videobinddemo.model.filter.BlendingType;
import com.hanzi.videobinddemo.model.filter.FilterModel;
import com.hanzi.videobinddemo.utils.EasyGlUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Created by gengen on 2018/4/10.
 */

public class BlendingFilter extends AFilter {

    private static final String TAG = BlendingFilter.class.getSimpleName();

    FilterModel filterModel;
    private int _uSFFilterHandler;
    private int _blendingOpacity;
    private int _blendingTextureA;
    private int _blendingTextureB;
    private int _curveTexture;
    private int _mixingOpacity;

    protected FloatBuffer mABuffer;

    private Curve curve;

    private OnLoadblendingFilterListener onFilterListener;

    private int texNum;
    private int[] textures;

    private int width, height;

    private int _mAPosition;

    private float uScaleFactFilter_y, uScaleFactFilter_x;

    private boolean isBinding = false;

    public BlendingFilter(Resources mRes) {
        super(mRes);
    }

    @Override
    public void draw() {
        onClear();
        onUseProgram();
        onBindTexture();
        loadTexture();
    }

    public FilterModel getFilterModel() {
        return filterModel;
    }

    public void setFilterListener(OnLoadblendingFilterListener onFilterListener) {
        this.onFilterListener = onFilterListener;
    }

    public BlendingFilter(FilterModel filterModel, Resources mRes) {
        super(mRes, true);
        this.filterModel = filterModel;
    }

    @Override
    protected void onCreate() {
        createProgram(uRes(mRes, "shader/filter_vs.sh"), getFragmentShaderCode());
    }

    @Override
    protected void onSizeChanged(int width, int height) {
        this.width = width;
        this.height = height;
        setScaleFactFilter();
//        onBindTexture();

    }

    private void setScaleFactFilter() {
        float f = (float) this.width / this.height;
        this.uScaleFactFilter_y = 1.0F;
        this.uScaleFactFilter_x = 1.0F;
        Log.d(TAG, "setScaleFactFilter: f:" + f);
        if (1.7777778F > f) {
            this.uScaleFactFilter_x = (f / 1.7777778F);
            return;
        }
        this.uScaleFactFilter_y = (1.7777778F / f);

    }

    @Override
    protected void onDraw() {
        GLES20.glEnableVertexAttribArray(_mAPosition);
        GLES20.glEnableVertexAttribArray(_mHPosition);
//        checkGlError("glEnableVertexAttribArray");
        GLES20.glVertexAttribPointer(_mAPosition, 2, GLES20.GL_FLOAT, false, 0, mABuffer);
//        GLES20.glVertexAttribPointer(_mHCoord, 2, GLES20.GL_FLOAT, false, 0, mTexBuffer);
        GLES20.glVertexAttribPointer(_mHPosition, 2, GLES20.GL_FLOAT, false, 0, mVerBuffer);
//        checkGlError("glVertexAttribPointer");
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
//        checkGlError("glDrawArrays");


//        GLES20.glUniform1i(mHTexture, 0);
//        checkGlError("glUniform1i(mHTexture)");
        GLES20.glUniform2f(_uSFFilterHandler, uScaleFactFilter_x, uScaleFactFilter_y);
        GLES20.glDisableVertexAttribArray(_mAPosition);
        GLES20.glDisableVertexAttribArray(_mHPosition);
//        GLES20.glDisableVertexAttribArray(_mHCoord);
    }

    @Override
    protected void createProgram(String vertex, String fragment) {
        Log.d(TAG, "createProgram: ");
//       super.createProgram(vertex,fragment);
        _mProgramHandler = uCreateGlProgram(vertex, fragment);
        checkGlError("uCreateGlProgram");
        _mAPosition = GLES20.glGetAttribLocation(_mProgramHandler, "aPosition");
        _mHPosition = GLES20.glGetAttribLocation(_mProgramHandler, "vPosition");
//        _mHCoord = GLES20.glGetAttribLocation(_mProgramHandler, "aCoord");
        _uSFFilterHandler = GLES20.glGetUniformLocation(_mProgramHandler, "uScaleFactFilter");
        mHTexture = GLES20.glGetUniformLocation(_mProgramHandler, "sTexture");

        _blendingOpacity = GLES20.glGetUniformLocation(_mProgramHandler, "blendingOpacity");
        _blendingTextureA = GLES20.glGetUniformLocation(_mProgramHandler, "blendingTextureA");
        _blendingTextureB = GLES20.glGetUniformLocation(_mProgramHandler, "blendingTextureB");
        _mixingOpacity = GLES20.glGetUniformLocation(_mProgramHandler, "mixingOpacity");
        if (filterModel.isCurveEnabled()) {
            _curveTexture = GLES20.glGetUniformLocation(_mProgramHandler, "curveTexture");
        }
        checkGlError("glGetUniformLocation");
        texNum = filterModel.resourceCount;
        Log.d(TAG, "createProgram: texNum:" + texNum);
        if (filterModel.isCurveEnabled()) {
            (this.curve = new Curve(filterModel.getCurveFilePath())).loadACV(mRes);
        }
//        loadTexture();
        if (!isBinding && onFilterListener!=null)
            onFilterListener.onLoadblendingFilterSuccess(filterModel);
    }

    @Override
    protected void initBuffer(boolean isReverse) {
        super.initBuffer(isReverse);
        ByteBuffer a = ByteBuffer.allocateDirect(32);
        a.order(ByteOrder.nativeOrder());
        mABuffer = a.asFloatBuffer();
        mABuffer.put(pos);
        mABuffer.position(0);
    }

    public void setBinding(boolean isBinding) {
        this.isBinding = isBinding;
    }

    protected void updateFramePosition(int n) {
        if (n >= 0L) {
            final float n2 = texNum * ((n / 6) / (texNum / 3.0f));//1000.0f *
            final int n3 = (int) n2;
            GLES20.glUniform1f(_mixingOpacity, n2 % 1.0f);
            GLES20.glUniform1f(_blendingOpacity, filterModel.blendingOpacity);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[n3 % texNum]);
            GLES20.glUniform1i(_blendingTextureA, 2);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[(n3 + 1) % texNum]);
            GLES20.glUniform1i(_blendingTextureB, 3);
            if (filterModel.isCurveEnabled()) {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE4);
                GLES20.glUniform1i(_curveTexture, 4);
            }
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getTextureId());

        onDraw();
    }

    protected void loadTexture() {
        if (textures == null ) {
            try {
                textures = new int[texNum];
                GLES20.glGenTextures(texNum, textures, 0);
                InputStream inputStream = mRes.getAssets().open(filterModel.getETCPath());
                for (int i = 0; i < texNum; i++) {
                    byte[] bytes = new byte[filterModel.resourceChunkSize];
                    if (inputStream.read(bytes) == -1) {
                        break;
                    }
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[i]);
                    EasyGlUtils.useTexParameter();
                    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
                    ETC1Util.loadTexture(GLES20.GL_TEXTURE_2D, GLES20.GL_NONE, 0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_SHORT_5_6_5, byteArrayInputStream);
                    byteArrayInputStream.close();
                    if (filterModel.isCurveEnabled()) {
                        curve.updateCurveTexture();
                    }
                }
                inputStream.close();
            } catch (Exception e) {
                onFilterListener.onLoadBlendingFilterError(filterModel);
                e.printStackTrace();
                return;
            }
        }
    }

    public void releaseTextures() {
        if (textures != null) {
            GLES20.glDeleteTextures(texNum, textures, 0);
            Log.d(TAG, "releaseTextures: ");
//            checkGlError("glDeleteTextures");
            textures = null;
        }
    }

    public interface OnLoadblendingFilterListener {
        void onLoadblendingFilterSuccess(FilterModel filterModel);

        void onLoadBlendingFilterError(FilterModel filterModel);
    }

    private String getFragmentShaderCode() {
        StringBuilder sb = new StringBuilder();
        BlendingType blendingType = filterModel.getBlendingType();
        if (blendingType == BlendingType.OVERLAY) {
            sb.append("#define BlendOverlay(base, blend, blendAlpha) " +
                    "(base < 0.5) ? 2.0 * base * blend + base * (1.0 - blendAlpha) : " +
                    "blendAlpha - 2.0 * (1.0 - base) * (blendAlpha - blend) + " +
                    "base * (1.0 - blendAlpha)\n");
        }
        if (blendingType == BlendingType.HARD_LIGHT) {
            sb.append("#define BlendOverlay(base, blend, blendAlpha) (blend < 0.5) ? " +
                    "2.0 * base * blend + base * (1.0 - blendAlpha) : " +
                    "blendAlpha - 2.0 * (1.0 - base) * (blendAlpha - blend) + " +
                    "base * (1.0 - blendAlpha)\n");
        }
        sb.append("precision mediump float;\n" +
                "precision highp float;\n" +
                "varying highp vec2 vTextureCoord;\n" +
                "varying highp vec2 vTextureCoordFilter;\n" +
                "uniform sampler2D sTexture;\n");
        if (blendingType != BlendingType.NONE) {
            sb.append("uniform lowp sampler2D curveTexture;\n" +
                    "uniform lowp sampler2D blendingTextureA;\n" +
                    "uniform lowp sampler2D blendingTextureB;\n" +
                    "uniform lowp float blendingOpacity;\n" +
                    "uniform lowp float mixingOpacity;\n");
        }
        sb.append("void main() {" +
                "\n\tmediump vec4 color = texture2D(sTexture, vTextureCoord);\n");

        if (filterModel.isGrayscale()) {
            sb.append("\tfloat grayscale = color.r * 0.2126 + color.g * 0.7152 + color.b * 0.0722;\n" +
                    "\tcolor.rgb = vec3(grayscale);\n");
        }
        if (blendingType != BlendingType.NONE) {
            sb.append("\tmediump vec4 c1 = texture2D(blendingTextureA, vTextureCoordFilter);" +
                    "\n\tmediump vec4 c2 = texture2D(blendingTextureB, vTextureCoordFilter);" +
                    "\n\tif (c1.a < 0.01) { c1 = vec4(0.0); } c1.rgb = c1.rgb * c1.a;\n\t" +
                    "if (c2.a < 0.01) { c2 = vec4(0.0); } c2.rgb = c2.rgb * c2.a;\n\t" +
                    "mediump vec4 blendingColor = mix(c1, c2, mixingOpacity);\n\t" +
                    "blendingColor.a = blendingColor.a * blendingOpacity;\n");
            if (blendingType != BlendingType.ALPHA) {
                sb.append("\tblendingColor.rgb = blendingColor.rgb * blendingColor.a;\n");
            }
        }
        switch (blendingType) {
            case ALPHA: {
                sb.append("color.rgb = mix(color.rgb, blendingColor.rgb, blendingColor.a);\n");
                break;
            }
            case ADDITION: {
                sb.append("color = min(color + blendingColor, 1.0);\n");
                break;
            }
            case MULTIPLY: {
                sb.append("color = blendingColor * color;\n");
                break;
            }
            case LIGHTEN: {
                sb.append("color = max(color, blendingColor);\n");
                break;
            }
            case DARKEN: {
                sb.append("color = min(color, blendingColor);\n");
                break;
            }
            case OVERLAY: {
                sb.append("color.r = BlendOverlay(color.r, blendingColor.r, blendingColor.a);\n");
                sb.append("color.g = BlendOverlay(color.g, blendingColor.g, blendingColor.a);\n");
                sb.append("color.b = BlendOverlay(color.b, blendingColor.b, blendingColor.a);\n");
                break;
            }
            case SOFT_LIGHT: {
                sb.append("lowp float alphaDivisor = color.a + step(color.a, 0.0);\n");
                sb.append("color = color * (blendingColor.a * (color / alphaDivisor) + (2.0 * blendingColor * (1.0 - (color / alphaDivisor)))) + color * (1.0 - blendingColor.a);\n");
                break;
            }
            case SCREEN: {
                sb.append("color = vec4(1.0) - ((vec4(1.0) - blendingColor) * (vec4(1.0) - color));\n");
                break;
            }
            case HARD_LIGHT: {
                sb.append("color.r = BlendOverlay(color.r, blendingColor.r, blendingColor.a);\n");
                sb.append("color.g = BlendOverlay(color.g, blendingColor.g, blendingColor.a);\n");
                sb.append("color.b = BlendOverlay(color.b, blendingColor.b, blendingColor.a);\n");
                break;
            }
        }

        if (filterModel.isCurveEnabled()) {
            sb.append("\tcolor.r = texture2D(curveTexture, vec2(color.r, 0.0)).r;" +
                    "\n\tcolor.g = texture2D(curveTexture, vec2(color.g, 0.0)).g;" +
                    "\n\tcolor.b = texture2D(curveTexture, vec2(color.b, 0.0)).b;\n");
        }
        sb.append("\tcolor.a = 1.0;\n\tgl_FragColor = color;\n}\n");
        return sb.toString();
    }

    public void checkGlError(String s) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            throw new RuntimeException(s + " " + TAG + ": glError " + error);
        }
    }
}
