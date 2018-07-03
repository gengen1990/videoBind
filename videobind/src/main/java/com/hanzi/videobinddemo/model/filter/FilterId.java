package com.hanzi.videobinddemo.model.filter;

import android.os.Parcel;
import android.os.Parcelable;

public class FilterId implements Parcelable {
    public String square;
    public String wide;

    protected FilterId(Parcel in) {
        square = in.readString();
        wide = in.readString();
    }

    public static final Creator<FilterId> CREATOR = new Creator<FilterId>() {
        @Override
        public FilterId createFromParcel(Parcel in) {
            return new FilterId(in);
        }

        @Override
        public FilterId[] newArray(int size) {
            return new FilterId[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(square);
        dest.writeString(wide);
    }
}
