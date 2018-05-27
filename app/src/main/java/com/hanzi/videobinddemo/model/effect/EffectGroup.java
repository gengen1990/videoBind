package com.hanzi.videobinddemo.model.effect;

/**
 * Created by gengen on 2018/3/7.
 */


import android.graphics.Color;
import android.text.TextUtils;
import android.util.Log;

import com.hanzi.videobinddemo.utils.UIUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class EffectGroup {
    private static final String TAG = EffectGroup.class.getSimpleName();
    private List<EffectModel> effects;
    private String groupBgColor;
    private int iconEffectIndex;
    private boolean iconFitToView = false;
    private boolean lockedForInvites = false;
    private boolean lockedForReview = false;
    private String name;

    public EffectModel findEffectModel(String paramString) {
        int i = 0;
        while (i < effects.size()) {
            EffectModel localEffectModel = (EffectModel) effects.get(i);
            if (localEffectModel.effectId.equals(paramString)) {
                return localEffectModel;
            }
            i += 1;
        }
        return null;
    }

    public List<EffectModel> getEffectModels() {
        return effects;
    }

    public int getGroupBgColor() {
        if (TextUtils.isEmpty(groupBgColor)) {
            groupBgColor = "#66FFFFFF";
        }
        return Color.parseColor(groupBgColor);
    }

    public String getGroupIconPath() {
        if (CustomTextDrawingModel.CustomText.equals(name)) {
            return "effects" + File.separator + name + File.separator + "ic_object_custom_text.png";
        }
        return ((EffectModel) effects.get(iconEffectIndex)).getIconImagePath();
    }

    public String getName() {
        return name;
    }

    public void init() {
        if (UIUtil.isEmpty(effects)) {
            effects = new ArrayList();
            return;
        }
        for (int i =0 ;i<effects.size();i++) {
            (effects.get(i)).init();
            if (i==0) {
                Log.d(TAG, "init: effect name :"+effects.get(0).iconImageName);
            }
        }

    }

    public boolean isIconFitToView() {
        return iconFitToView;
    }

    public boolean isLockedForInvites() {
        return lockedForInvites;
    }

    public boolean isLockedForReview() {
        return lockedForReview;
    }
}
