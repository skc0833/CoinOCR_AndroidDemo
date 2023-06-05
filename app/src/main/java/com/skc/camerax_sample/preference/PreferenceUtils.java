package com.skc.camerax_sample.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Size;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.camera.core.CameraSelector;

import com.skc.camerax_sample.CameraSource;
import com.skc.camerax_sample.CameraSource.SizePair;
import com.skc.camerax_sample.R;

/** Utility class to retrieve shared preferences. */
public class PreferenceUtils {

//  private static final int POSE_DETECTOR_PERFORMANCE_MODE_FAST = 1;

    static void saveString(Context context, @StringRes int prefKeyId, @Nullable String value) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(context.getString(prefKeyId), value)
            .apply();
    }

    @Nullable
    public static SizePair getCameraPreviewSizePair(Context context, int cameraId) {
        String previewSizePrefKey;
        String pictureSizePrefKey;
        if (cameraId == CameraSource.CAMERA_FACING_BACK) {
            previewSizePrefKey = context.getString(R.string.pref_key_rear_camera_preview_size);
            pictureSizePrefKey = context.getString(R.string.pref_key_rear_camera_picture_size);
        } else {
            previewSizePrefKey = context.getString(R.string.pref_key_front_camera_preview_size);
            pictureSizePrefKey = context.getString(R.string.pref_key_front_camera_picture_size);
        }

        try {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            return new SizePair(
                Size.parseSize(sharedPreferences.getString(previewSizePrefKey, null)),
                Size.parseSize(sharedPreferences.getString(pictureSizePrefKey, null)));
        } catch (Exception e) {
            return null;
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @Nullable
    public static android.util.Size getCameraXTargetResolution(Context context, int lensfacing) {
        String prefKey =
            lensfacing == CameraSelector.LENS_FACING_BACK
                ? context.getString(R.string.pref_key_camerax_rear_camera_target_resolution)
                : context.getString(R.string.pref_key_camerax_front_camera_target_resolution);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        try {
            return android.util.Size.parseSize(sharedPreferences.getString(prefKey, null));
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isCameraLiveViewportEnabled(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String prefKey = context.getString(R.string.pref_key_camera_live_viewport);
        return sharedPreferences.getBoolean(prefKey, false);
    }

    private PreferenceUtils() {}
}
