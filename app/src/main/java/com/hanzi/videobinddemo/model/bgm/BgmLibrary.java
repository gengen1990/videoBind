package com.hanzi.videobinddemo.model.bgm;

import android.content.Context;
import android.support.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.List;

public class BgmLibrary {
    private static BgmLibrary bgmLibrary = null;
    private List bgmModels;

    private BgmLibrary(Context context)
            throws IOException {
        try {
            InputStreamReader reader;
            InputStream inputStream = context.getAssets().open("BgmLibrary.json");
            reader = new InputStreamReader(inputStream);
            Type localType = new TypeToken<List<BgmModel>>() {
            }.getType();
            this.bgmModels = ((List) new Gson().fromJson(reader, localType));
            inputStream.close();
            reader.close();
            return;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static BgmLibrary getInstance() {
        return bgmLibrary;
    }

    public static void init(Context context)
            throws IOException {
        if (bgmLibrary == null) {
            bgmLibrary = new BgmLibrary(context);
        }
    }

    public List<BgmModel> getBgmModelList() {
        return this.bgmModels;
    }

    @Nullable
    public String getBgmPath(String bgmPath) {
        Iterator iterator = this.bgmModels.iterator();
        while (iterator.hasNext()) {
            BgmModel bgmModel = (BgmModel) iterator.next();
            if (bgmModel.id.equals(bgmPath)) {
                return bgmModel.getBgmPath();
            }
        }
        return null;
    }
}
