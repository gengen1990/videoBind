package com.hanzi.videobinddemo.model.effect;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.List;

public class EffectLibrary
{
    private static EffectLibrary library = null;
    private List<EffectGroup> effectGroups;

    private EffectLibrary(Context context)
            throws IOException
    {
        InputStreamReader reader;
        reader = new InputStreamReader(context.getAssets().open("EffectLibrary.json"));
        Type localType = new TypeToken <List<EffectGroup>>(){}.getType();
        effectGroups = new Gson().fromJson(reader, localType);
//        effectGroups.remove(0);
        for (int i=0;i<effectGroups.size();i++) {
            effectGroups.get(i).init();

        }
        reader.close();

    }

    public static EffectModel findEffectModel(String effectId)
    {
        Iterator localIterator = library.effectGroups.iterator();
        while (localIterator.hasNext())
        {
            EffectModel localEffectModel = ((EffectGroup)localIterator.next()).findEffectModel(effectId);
            if (localEffectModel != null) {
                return localEffectModel;
            }
        }
        return null;
    }

    public static List<EffectGroup> getEffectGroups()
    {
        return library.effectGroups;
    }

    public static void init(Context context)
            throws IOException
    {
        if (library == null) {
            library = new EffectLibrary(context);
        }
    }
}
