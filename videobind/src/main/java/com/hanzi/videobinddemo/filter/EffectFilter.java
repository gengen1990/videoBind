package com.hanzi.videobinddemo.filter;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;

/**
 * effect的Filter
 */

public class EffectFilter extends NoFilter{
    /**effect 's location(left top) and size*/
    private int x,y, bitmapW, bitmapH;
    /**the whole view's size*/
    private int width,height;
    /**effect图片的bitmap*/
    private Bitmap mBitmap;
    /***/
    private NoFilter mFilter;

    private boolean isBitmap = false;

    private boolean isfrist = true;

    public EffectFilter(Resources mRes) {
        super(mRes);
        mFilter=new NoFilter(mRes){
            @Override
            protected void onClear() {
            }
        };

    }
    public void setBitmap(Bitmap bitmap){
        this.isBitmap = isBitmap;
        if(this.mBitmap!=null){
            this.mBitmap.recycle();
        }
        this.mBitmap=bitmap;
        /*if (isBitmap) {
            try {
                File file = new File(Environment.getExternalStorageDirectory() + "/ed/" + System.currentTimeMillis() + ".png");
                FileOutputStream out = new FileOutputStream(file);
                mBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }*/
    }
    @Override
    public void draw() {
        super.draw();
        if(mBitmap != null){
            GLES20.glViewport(x, y, bitmapW == 0 ? mBitmap.getWidth() : bitmapW, bitmapH == 0 ? mBitmap.getHeight() : bitmapH);
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            mFilter.draw();
            GLES20.glDisable(GLES20.GL_BLEND);
            GLES20.glViewport(0, 0, width, height);
        }
    }

    @Override
    protected void onCreate() {
        if(isfrist) {
            super.onCreate();
            mFilter.create();
            isfrist = false;
        }
        createTexture();
    }
    private int[] textures=new int[1];
    private void createTexture() {
        if(mBitmap!=null){
            GLES20.glDeleteTextures(1,textures,0);
            //生成纹理
            GLES20.glGenTextures(1,textures,0);
            //生成纹理
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textures[0]);
            //设置缩小过滤为使用纹理中坐标最接近的一个像素的颜色作为需要绘制的像素颜色
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            //设置放大过滤为使用纹理中坐标最接近的若干个颜色，通过加权平均算法得到需要绘制的像素颜色
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            //设置环绕方向S，截取纹理坐标到[1/2n,1-1/2n]。将导致永远不会与border融合
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            //设置环绕方向T，截取纹理坐标到[1/2n,1-1/2n]。将导致永远不会与border融合
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mBitmap, 0);
            //对画面进行矩阵旋转
            //MatrixUtils.flip(mFilter.getMatrix(),false,true);
            /*for (int j= 0;j < mFilter.getMatrix().length;j++){
                Log.e("=-----"+j+":",mFilter.getMatrix()[j]+"");
            }*/
            mBitmap.recycle();
            mFilter.setTextureId(textures[0]);
        }
    }

    @Override
    protected void onSizeChanged(int width, int height) {
        this.width=width;
        this.height=height;
        mFilter.setSize(width,height);
    }
    public void setPosition(int x,int y,int width,int height){
        this.x=x;
        this.y=y;
        this.bitmapW =width;
        this.bitmapH =height;
    }
}
