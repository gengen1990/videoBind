package com.hanzi.videobinddemo.model.filter;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;

//implements Parcelable
public class FilterModel  implements Parcelable {
    public static final String TAG = "FilterModel";
    public float blendingOpacity;
    private String blendingType;
    private boolean curve = false;
    public boolean downloadInProgress = false;
    public boolean downloadRequired = false;
    public FilterId filterId;
    private boolean grayscale = false;
    public String name;
    public int resourceChunkSize;
    public int resourceCount;
    public String resourceName;
    public String serverResourcePath;
    public String iconImagePath;

    protected FilterModel(Parcel in) {
        blendingOpacity = in.readFloat();
        blendingType = in.readString();
        curve = in.readByte() != 0;
        downloadInProgress = in.readByte() != 0;
        downloadRequired = in.readByte() != 0;
        filterId = in.readParcelable(FilterId.class.getClassLoader());
        grayscale = in.readByte() != 0;
        name = in.readString();
        resourceChunkSize = in.readInt();
        resourceCount = in.readInt();
        resourceName = in.readString();
        serverResourcePath = in.readString();
        iconImagePath = in.readString();
    }

    public static final Creator<FilterModel> CREATOR = new Creator<FilterModel>() {
        @Override
        public FilterModel createFromParcel(Parcel in) {
            return new FilterModel(in);
        }

        @Override
        public FilterModel[] newArray(int size) {
            return new FilterModel[size];
        }
    };

    public BlendingType getBlendingType()
    {
        if ("addition".equals(this.blendingType)) {
            return BlendingType.ADDITION;
        }
        if ("screen".equals(this.blendingType)) {
            return BlendingType.SCREEN;
        }
        if ("lighten".equals(this.blendingType)) {
            return BlendingType.LIGHTEN;
        }
        if ("darken".equals(this.blendingType)) {
            return BlendingType.DARKEN;
        }
        if ("overlay".equals(this.blendingType)) {
            return BlendingType.OVERLAY;
        }
        if ("multiply".equals(this.blendingType)) {
            return BlendingType.MULTIPLY;
        }
        if ("alpha".equals(this.blendingType)) {
            return BlendingType.ALPHA;
        }
        if ("softlight".equals(this.blendingType)) {
            return BlendingType.SOFT_LIGHT;
        }
        if ("hardlight".equals(this.blendingType)) {
            return BlendingType.HARD_LIGHT;
        }
        return BlendingType.NONE;
    }

    public String getCurveFilePath()
    {
        return "filters" + File.separator + this.filterId.wide + File.separator + this.resourceName + ".acv";
    }

    public String getETCPath()
    {
        return "filters" + File.separator + this.filterId.wide + File.separator + this.resourceName + ".etc";
    }

    public String getIconImagePath()
    {
        iconImagePath = "file:///android_asset/filters" + File.separator + this.filterId.wide + File.separator + this.resourceName + ".png";
        return iconImagePath;
    }

    public boolean isCurveEnabled()
    {
        return this.curve;
    }

    public boolean isGrayscale()
    {
        return this.grayscale;
    }

    public float getBlendingOpacity() {
        return blendingOpacity;
    }

    public void setBlendingOpacity(float blendingOpacity) {
        this.blendingOpacity = blendingOpacity;
    }

    public void setBlendingType(String blendingType) {
        this.blendingType = blendingType;
    }

    public boolean isCurve() {
        return curve;
    }

    public void setCurve(boolean curve) {
        this.curve = curve;
    }

    public boolean isDownloadInProgress() {
        return downloadInProgress;
    }

    public void setDownloadInProgress(boolean downloadInProgress) {
        this.downloadInProgress = downloadInProgress;
    }

    public boolean isDownloadRequired() {
        return downloadRequired;
    }

    public void setDownloadRequired(boolean downloadRequired) {
        this.downloadRequired = downloadRequired;
    }

    public FilterId getFilterId() {
        return filterId;
    }

    public void setFilterId(FilterId filterId) {
        this.filterId = filterId;
    }

    public void setGrayscale(boolean grayscale) {
        this.grayscale = grayscale;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getResourceChunkSize() {
        return resourceChunkSize;
    }

    public void setResourceChunkSize(int resourceChunkSize) {
        this.resourceChunkSize = resourceChunkSize;
    }

    public int getResourceCount() {
        return resourceCount;
    }

    public void setResourceCount(int resourceCount) {
        this.resourceCount = resourceCount;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public String getServerResourcePath() {
        return serverResourcePath;
    }

    public void setServerResourcePath(String serverResourcePath) {
        this.serverResourcePath = serverResourcePath;
    }

    public void setIconImagePath(String iconImagePath) {
        this.iconImagePath = iconImagePath;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeFloat(blendingOpacity);
        dest.writeString(blendingType);
        dest.writeByte((byte) (curve ? 1 : 0));
        dest.writeByte((byte) (downloadInProgress ? 1 : 0));
        dest.writeByte((byte) (downloadRequired ? 1 : 0));
        dest.writeParcelable(filterId, flags);
        dest.writeByte((byte) (grayscale ? 1 : 0));
        dest.writeString(name);
        dest.writeInt(resourceChunkSize);
        dest.writeInt(resourceCount);
        dest.writeString(resourceName);
        dest.writeString(serverResourcePath);
        dest.writeString(iconImagePath);
    }

//    @Override
//    public int describeContents() {
//        return 0;
//    }
//
//    @Override
//    public void writeToParcel(Parcel dest, int flags) {
//
//    }
}
