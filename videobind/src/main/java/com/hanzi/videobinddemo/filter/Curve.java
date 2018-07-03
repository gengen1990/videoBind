package com.hanzi.videobinddemo.filter;

import android.content.res.Resources;
import android.opengl.GLES20;

import com.hanzi.videobinddemo.model.Point;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Curve {
    protected int _uniformCurveTexture;
    private String acvFileName;
    protected ArrayList<Point> mBlueControlPoints;
    protected float[] mBlueCurve;
    protected ArrayList<Point> mCompositeControlPoints;
    protected float[] mCompositeCurve;
    protected ArrayList<Point> mGreenControlPoints;
    protected float[] mGreenCurve;
    protected ArrayList<Point> mRedControlPoints;
    protected float[] mRedCurve;

    public Curve(final String acvFileName) {
        this.acvFileName = acvFileName;
    }

    private float[] getCurve(final List<Point> points) {
        float[] array;
        if (points.size() == 0) {
            array = null;
        } else {
            Collections.sort(points, new Comparator<Point>() {
                @Override
                public int compare(final Point point, final Point point2) {
                    return (int) (point.x - point2.x);
                }
            });
            final ArrayList<Point> b = this.b(points);
            final Point point = b.get(0);
            if (point.x > 0.0f) {
                for (float x = point.x; x > 0.0f; --x) {
                    b.add(0, new Point(x, 0.0f));
                }
            }
            final Point point2 = b.get(b.size() - 1);
            if (point2.x < 255.0f) {
                for (int i = (int) point2.x + 1; i <= 255; ++i) {
                    b.add(new Point(i, 255.0f));
                }
            }
            final float[] array2 = new float[b.size()];
            int n = 0;
            while (true) {
                array = array2;
                if (n >= b.size()) {
                    break;
                }
                final Point point3 = b.get(n);
                float n2 = (float) Math.sqrt(Math.pow(point3.x - point3.y, 2.0));
                if (point3.x > point3.y) {
                    n2 *= -1.0f;
                }
                array2[n] = n2;
                ++n;
            }
        }
        return array;
    }

    private ArrayList<Point> b(final List<Point> list) {
        final float[] c = this.c(list);
        if (c.length < 1) {
            return null;
        }
        final ArrayList<Point> list2 = new ArrayList<Point>();
        for (int i = 0; i < c.length - 1; ++i) {
            final Point point = list.get(i);
            final Point point2 = list.get(i + 1);
            for (int j = (int) point.x; j < (int) point2.x; ++j) {
                final double n = (j - point.x) / (point2.x - point.x);
                final double n2 = 1.0 - n;
                final double n3 = point.y;
                final double n4 = point2.y;
                final double n5 = Math.pow(point.x, 2.0) / 6.0;
                final double n6 = (Math.pow(n2, 3.0) - n2) * c[i];
                final float n7 = (float) (n5 * ((Math.pow(n6, 3.0) - n) * c[i + 1] + n6) + (n3 * n2 + n4 * n));
                float n8;
                if (n7 < 0.0f) {
                    n8 = 0.0f;
                } else {
                    n8 = n7;
                    if (n7 > 255.0f) {
                        n8 = 255.0f;
                    }
                }
                list2.add(new Point(j, n8));
            }
        }
        list2.add(list.get(list.size() - 1));
        return list2;
    }

    private int byte2Int(final byte b, final byte b2) {
        return (b & 0xFF) << 8 | (b2 & 0xFF);
    }

    private float[] c(final List<Point> list) {
        float[] array;
        if (list.size() <= 1) {
            array = null;
        } else {
            final double[][] array2 = (double[][]) Array.newInstance(Double.TYPE, list.size(), 3);
            final double[] array3 = new double[list.size()];
            array2[0][0] = 0L;
            array2[0][1] = 4607182418800017408L;
            array2[0][2] = 0L;
            array3[0] = 0.0;
            for (int i = 1; i < list.size() - 1; ++i) {
                final Point point = list.get(i - 1);
                final Point point2 = list.get(i);
                final Point point3 = list.get(i + 1);
                array2[i][0] = (point2.x - point.x) / 6.0;
                array2[i][1] = (point3.x - point.x) / 3.0;
                array2[i][2] = (point3.x - point2.x) / 6.0;
                array3[i] = (point3.y - point2.y) / (point3.x - point2.x) - (point2.y - point.y) / (point2.x - point.x);
            }
            array2[list.size() - 1][0] = 0L;
            array2[list.size() - 1][1] = 4607182418800017408L;
            array2[list.size() - 1][2] = 0L;
            array3[list.size() - 1] = 0.0;
            for (int j = 1; j < list.size(); ++j) {
                final double n = array2[j][0] / array2[j - 1][1];
                array2[j][0] = 0L;
                array2[j][1] -= array2[j - 1][2] * n;
                array3[j] -= array3[j - 1] * n;
            }
            for (int k = list.size() - 2; k >= 0; --k) {
                final double n2 = array2[k][2] / array2[k + 1][1];
                array2[k][1] -= array2[k + 1][0] * n2;
                array2[k][2] = 0L;
                array3[k] -= array3[k + 1] * n2;
            }
            final float[] array4 = new float[list.size()];
            int n3 = 0;
            while (true) {
                array = array4;
                if (n3 >= list.size()) {
                    break;
                }
                array4[n3] = (float) (array3[n3] / array2[n3][1]);
                ++n3;
            }
        }
        return array;
    }

    private void decode(final byte[] data) {
        final int xCount = this.byte2Int(data[2], data[3]);
        int n = 4;
        final ArrayList<ArrayList<Point>> pointList = new ArrayList<>(xCount);
        for (int i = 0; i < xCount; ++i) {
            final int yCount = this.byte2Int(data[n], data[n + 1]);
            n += 2;
            final ArrayList<Point> list2 = new ArrayList<Point>();
            for (int j = 0; j < yCount; ++j) {
                final int byte1 = this.byte2Int(data[n], data[n + 1]);
                final int n2 = n + 2;
                final int byte2 = this.byte2Int(data[n2], data[n2 + 1]);
                n = n2 + 2;
                list2.add(new Point(byte1, byte2));
            }
            pointList.add(list2);
        }
        this.init(pointList.get(0), pointList.get(1), pointList.get(2), pointList.get(3));
    }

    private void init(final ArrayList<Point> list, final ArrayList<Point> list2, final ArrayList<Point> list3, final ArrayList<Point> list4) {
        this.init(list, list2, list3, list4, true);
    }

    private void init(final ArrayList<Point> mCompositeControlPoints, final ArrayList<Point> mRedControlPoints, final ArrayList<Point> mGreenControlPoints, final ArrayList<Point> mBlueControlPoints, final boolean enableToUpdateCurveTexture) {
        this.mCompositeControlPoints = mCompositeControlPoints;
        this.mRedControlPoints = mRedControlPoints;
        this.mGreenControlPoints = mGreenControlPoints;
        this.mBlueControlPoints = mBlueControlPoints;
        this.mCompositeCurve = getCurve(this.mCompositeControlPoints);
        this.mRedCurve = getCurve(this.mRedControlPoints);
        this.mGreenCurve = getCurve(this.mGreenControlPoints);
        this.mBlueCurve = getCurve(this.mBlueControlPoints);
        if (enableToUpdateCurveTexture) {
            this.updateCurveTexture();
        }
    }

    public void loadACV(final Resources resources) {
        try {
            final InputStream inputStream = resources.getAssets().open(this.acvFileName);
            final byte[] data = new byte[inputStream.available()];
            inputStream.read(data);
            inputStream.close();
            this.decode(data);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void updateCurveTexture() {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE4);
        if (this.mRedCurve.length >= 256
                && this.mGreenCurve.length >= 256
                && this.mBlueCurve.length >= 256
                && this.mCompositeCurve.length >= 256) {
            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(1024);
            byteBuffer.order(ByteOrder.BIG_ENDIAN);
            final IntBuffer intBuffer = byteBuffer.asIntBuffer();
            for (int i = 0; i < 256; ++i) {
                final int min = Math.min((int) Math.max(i + this.mRedCurve[i], 0.0f), 255);
                final int min2 = Math.min((int) Math.max(i + this.mGreenCurve[i], 0.0f), 255);
                final int min3 = Math.min((int) Math.max(i + this.mBlueCurve[i], 0.0f), 255);
                intBuffer.put(Math.min((int) Math.max(min + this.mCompositeCurve[min], 0.0f), 255) << 24
                        | Math.min((int) Math.max(min2 + this.mCompositeCurve[min2], 0.0f), 255) << 16
                        | Math.min((int) Math.max(min3 + this.mCompositeCurve[min3], 0.0f), 255) << 8
                        | 0xFF);
            }
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 256, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, byteBuffer);
        }
    }
}
