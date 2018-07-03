package com.hanzi.videobinddemo.utils;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Create by xjs
 * _______date : 18/3/6
 * _______description:
 */
public class Utils
{
    public static ArrayList<int[]> getSpans(final String s, final char c) {
        final ArrayList<int[]> list = new ArrayList<int[]>();
        final Matcher matcher = Pattern.compile(c + "\\w+").matcher(s);
        while (matcher.find()) {
            list.add(new int[] { matcher.start(), matcher.end() });
        }
        return list;
    }

    public static boolean isEmpty(final Collection collection) {
        return collection == null || collection.isEmpty();
    }

    public static String saveFromAssets(Context context, String path) {
        try {
            InputStream inputStream = context.getAssets().open(path);

            String name = path.substring(path.lastIndexOf('/'));
            String outPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/1" + name;
            File outFile = new File(outPath);
            FileOutputStream outputStream = new FileOutputStream(outFile);

            byte[] buffer = new byte[inputStream.available()];
            int len;
            while ((len = inputStream.read(buffer)) > 0) {
                Log.d("test", "saveFromAssets: ");
                outputStream.write(buffer, 0, len);
            }
            inputStream.close();
            outputStream.close();
            return  outPath;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return path;
    }
}
