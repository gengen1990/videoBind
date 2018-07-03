package com.hanzi.videobinddemo.bean.model.filter;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hanzi.videobinddemo.filter.AFilter;
import com.hanzi.videobinddemo.filter.BlendingFilter;
import com.hanzi.videobinddemo.filter.NoFilter;
import com.hanzi.videobinddemo.core.MyApplication;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.List;

public class FilterLibrary {
    private static FilterLibrary library = null;
    private List<FilterModel> filterModels;

    private FilterLibrary(Context context)
            throws IOException {
        try {
            InputStreamReader reader;
            InputStream inputStream = context.getAssets().open("FilterLibrary.json");
            reader = new InputStreamReader(inputStream);
            Type localType = new TypeToken<List<FilterModel>>() {
            }.getType();
            this.filterModels = new Gson().fromJson(reader, localType);
            inputStream.close();
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static FilterLibrary getInstance() {
        return library;
    }

    public static void init(Context context)
            throws IOException {
        if (library == null) {
            library = new FilterLibrary(context);
        }
    }

    public AFilter getFilter(String filterName) {
        if ((filterName == null || filterName.isEmpty()) || ("Original".equals(filterName))) {
            return new NoFilter(MyApplication.getContext().getResources());
        }
        for (FilterModel filterModel : filterModels) {
            if (filterName.equals(filterModel.getName())) {
                BlendingFilter filter = new BlendingFilter(filterModel, MyApplication.getContext().getResources());
                return filter;
            }
        }
        return null;
    }

    public AFilter getFilter(FilterModel filterModel, BlendingFilter.OnLoadblendingFilterListener loadblendingFilterListener) {
        if ((filterModel == null) || ("Original".equals(filterModel.name))) {
            return new NoFilter(MyApplication.getContext().getResources());
        }
        BlendingFilter filter = new BlendingFilter(filterModel, MyApplication.getContext().getResources());
        filter.setFilterListener(loadblendingFilterListener);
        return filter;
    }

//    public String getFilterId(String filterId, boolean squareSize) {
//        if (("Original".equals(filterId)) || (TextUtils.isEmpty(filterId))) {
//            return "Original";
//        }
//        int i = 0;
//        while (i < this.filterModels.size()) {
//            FilterModel localFilterModel = (FilterModel) this.filterModels.get(i);
//            if (filterId.equals(localFilterModel.name)) {
//                if (squareSize) {
//                    return localFilterModel.filterId.square;
//                }
//                return localFilterModel.filterId.wide;
//            }
//            i += 1;
//        }
//        return "Original";
//    }

    public List<FilterModel> getFilterModels() {
        return this.filterModels;
    }
}
