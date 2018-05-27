package com.hanzi.videobinddemo.model.effect;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Parcel;
import android.os.Parcelable;

import com.hanzi.videobinddemo.model.effect.keyFrame.EmptyKeyframe;
import com.hanzi.videobinddemo.model.effect.keyFrame.Keyframe;

import java.io.File;
import java.io.InputStream;

public class EffectModel implements Parcelable {
    private int colorBlendingEnabled = 0;
    public boolean downloadInProgress = false;
    public boolean downloadRequired = false;
    private float duration = 0.0F;
    public long durationUs;
    public String effectId;
    private int fullscreenEffect = 0;
    public String iconImageName;
    public int imageCount;
    public int[] imageResourceChunkSize;
    public String imageResourceName;
    private float maxDuration = -1.0F;
    public long maximumDurationUs;
    private float minDuration = -1.0F;
    public long minimumDurationUs;
    private float overlapDuration = -1.0F;
    public long overlapDurationUs;
    private float overlapStartTime = -1.0F;
    public long overlapStartTimeUs;
    public int repeatCount = 1;
    public int representationIndex;
    public String serverResourcePath;
    public String soundName;

    protected EffectModel(Parcel in) {
        colorBlendingEnabled = in.readInt();
        downloadInProgress = in.readByte() != 0;
        downloadRequired = in.readByte() != 0;
        duration = in.readFloat();
        durationUs = in.readLong();
        effectId = in.readString();
        fullscreenEffect = in.readInt();
        iconImageName = in.readString();
        imageCount = in.readInt();
        imageResourceChunkSize = in.createIntArray();
        imageResourceName = in.readString();
        maxDuration = in.readFloat();
        maximumDurationUs = in.readLong();
        minDuration = in.readFloat();
        minimumDurationUs = in.readLong();
        overlapDuration = in.readFloat();
        overlapDurationUs = in.readLong();
        overlapStartTime = in.readFloat();
        overlapStartTimeUs = in.readLong();
        repeatCount = in.readInt();
        representationIndex = in.readInt();
        serverResourcePath = in.readString();
        soundName = in.readString();
    }

    public static final Creator<EffectModel> CREATOR = new Creator<EffectModel>() {
        @Override
        public EffectModel createFromParcel(Parcel in) {
            return new EffectModel(in);
        }

        @Override
        public EffectModel[] newArray(int size) {
            return new EffectModel[size];
        }
    };

    public String getEffectPath() {
        return "effects" + File.separator + this.effectId + File.separator + this.imageResourceName + ".effect";
    }

    public String getIconImagePath() {
        return "effects" + File.separator + this.effectId + File.separator + this.iconImageName + ".png";
    }

    public Bitmap getImage(Context context, int pos) {
        try {
            InputStream inputStream;
            inputStream = context.getAssets().open(getEffectPath());
            byte[] data = new byte[getImageChunkSize(pos)];
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, inputStream.read(data));
            inputStream.close();
            return bitmap;
        } catch (Exception paramContext) {
            paramContext.printStackTrace();
        }
        return null;
    }

    public int getImageChunkSize(int pos) {
        return this.imageResourceChunkSize[pos];
    }

    public Keyframe getKeyframe() {
        return EmptyKeyframe.INSTANCE;
    }

    public void init() {
        durationUs = (long) (1000000L * duration);
        maximumDurationUs = (long) (1000000L * maxDuration);
        minimumDurationUs = (long) (1000000L * minDuration);
        overlapDurationUs = (long) (1000000L * overlapDuration);
        overlapStartTimeUs =0;// (long) (1000000L * overlapStartTime);
    }

    public boolean isColorBlendingEnabled() {
        return this.colorBlendingEnabled == 1;
    }

    public boolean isEditableText() {
        return CustomTextDrawingModel.CustomText.equals(this.effectId);
    }

    public boolean isFullscreen() {
        return this.fullscreenEffect == 1;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(colorBlendingEnabled);
        dest.writeByte((byte) (downloadInProgress ? 1 : 0));
        dest.writeByte((byte) (downloadRequired ? 1 : 0));
        dest.writeFloat(duration);
        dest.writeLong(durationUs);
        dest.writeString(effectId);
        dest.writeInt(fullscreenEffect);
        dest.writeString(iconImageName);
        dest.writeInt(imageCount);
        dest.writeIntArray(imageResourceChunkSize);
        dest.writeString(imageResourceName);
        dest.writeFloat(maxDuration);
        dest.writeLong(maximumDurationUs);
        dest.writeFloat(minDuration);
        dest.writeLong(minimumDurationUs);
        dest.writeFloat(overlapDuration);
        dest.writeLong(overlapDurationUs);
        dest.writeFloat(overlapStartTime);
        dest.writeLong(overlapStartTimeUs);
        dest.writeInt(repeatCount);
        dest.writeInt(representationIndex);
        dest.writeString(serverResourcePath);
        dest.writeString(soundName);
    }
}
