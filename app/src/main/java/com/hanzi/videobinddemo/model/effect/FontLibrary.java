package com.hanzi.videobinddemo.model.effect;

import android.content.Context;
import android.content.res.AssetManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.List;

public class FontLibrary {
    private static FontLibrary library;
    private List<FontModel> fontModels;

    private FontLibrary(Context context)
            throws IOException {
        AssetManager assetManager = context.getResources().getAssets();
        InputStreamReader localInputStreamReader = new InputStreamReader(assetManager.open("FontLibrary.json"));
        Type type = new TypeToken<List<FontModel>>(){}.getType();
        this.fontModels = new Gson().fromJson(localInputStreamReader, type);
        int i = 0;
        while (i < this.fontModels.size()) {
            this.fontModels.get(i).init(assetManager);
            i += 1;
        }
    }

    public static FontLibrary getInstance() {
        return library;
    }

    public static void init(Context context)
            throws IOException {
        library = new FontLibrary(context);
    }

    public FontModel findFontModel(String effectId) {
        int i = 0;
        while (i < fontModels.size()) {
            FontModel fontModel =  fontModels.get(i);
            if (fontModel.id.equals(effectId)) {
                return fontModel;
            }
            i += 1;
        }
        return null;
    }

    public FontModel getDefaultFontModel() {
        return (FontModel) this.fontModels.get(0);
    }

    public List<FontModel> getFontModels() {
        return this.fontModels;
    }
}
