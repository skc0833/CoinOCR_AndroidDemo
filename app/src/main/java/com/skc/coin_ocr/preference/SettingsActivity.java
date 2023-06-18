package com.skc.coin_ocr.preference;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.skc.coin_ocr.R;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";

    public static final String EXTRA_LAUNCH_SOURCE = "extra_launch_source";

    /** Specifies where this activity is launched from. */
    @SuppressWarnings("NewApi") // CameraX is only available on API 21+
    public enum LaunchSource {
        LIVE_PREVIEW(R.string.pref_screen_title_live_preview, LivePreviewPreferenceFragment.class),
        //STILL_IMAGE(R.string.pref_screen_title_still_image, StillImagePreferenceFragment.class),
        CAMERAX_LIVE_PREVIEW(R.string.pref_screen_title_camerax_live_preview, CameraXLivePreviewPreferenceFragment.class);

        private final int titleResId;
        private final Class<? extends PreferenceFragment> prefFragmentClass;

        LaunchSource(int titleResId, Class<? extends PreferenceFragment> prefFragmentClass) {
            this.titleResId = titleResId;
            this.prefFragmentClass = prefFragmentClass;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);

        LaunchSource launchSource =
            (LaunchSource) getIntent().getSerializableExtra(EXTRA_LAUNCH_SOURCE);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(launchSource.titleResId);
        }

        try {
            getFragmentManager()
                .beginTransaction()
                .replace(
                    R.id.settings_container,
                    launchSource.prefFragmentClass.getDeclaredConstructor().newInstance())
                .commit();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
