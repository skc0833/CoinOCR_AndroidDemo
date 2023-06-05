package com.skc.camerax_sample;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import com.skc.camerax_sample.preference.SettingsActivity;

public class CameraXLivePreviewActivity extends AppCompatActivity
    implements CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "CameraXLivePreview";

    private PreviewView previewView;
    private GraphicOverlay graphicOverlay;

    private ProcessCameraProvider cameraProvider;
    @Nullable
    private Preview previewUseCase;
    private int lensFacing = CameraSelector.LENS_FACING_BACK;
    private CameraSelector cameraSelector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cameraSelector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();

        setContentView(R.layout.activity_camera_xlive_preview);
        previewView = findViewById(R.id.preview_view);
        if (previewView == null) {
            Log.d(TAG, "previewView is null");
        }
        graphicOverlay = findViewById(R.id.graphic_overlay);
        if (graphicOverlay == null) {
            Log.d(TAG, "graphicOverlay is null");
        }

        ToggleButton facingSwitch = findViewById(R.id.facing_switch);
        facingSwitch.setOnCheckedChangeListener(this);

        new ViewModelProvider((ViewModelStoreOwner) this,
            (ViewModelProvider.Factory) ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication()))
            .get(CameraXViewModel.class)
            .getProcessCameraProvider()
            .observe(this, provider -> {
                cameraProvider = provider;
                bindAllCameraUseCases();
            });

        ImageView settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(
            view -> {
                Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
                intent.putExtra(
                    SettingsActivity.EXTRA_LAUNCH_SOURCE,
                    SettingsActivity.LaunchSource.CAMERAX_LIVE_PREVIEW);
                startActivity(intent);
            });
    }

    @Override
    public void onResume() {
        super.onResume();
        bindAllCameraUseCases();
    }

    private void bindAllCameraUseCases() {
        if (cameraProvider != null) {
            // As required by CameraX API, unbinds all use cases before trying to re-bind any of them.
            cameraProvider.unbindAll();
            bindPreviewUseCase();
        }
    }

    private void bindPreviewUseCase() {
        if (cameraProvider == null) {
            return;
        }
        if (previewUseCase != null) {
            cameraProvider.unbind(previewUseCase);
        }
        Preview.Builder builder = new Preview.Builder();
        previewUseCase = builder.build();
        previewUseCase.setSurfaceProvider(previewView.getSurfaceProvider());
        cameraProvider.bindToLifecycle(this, cameraSelector, previewUseCase);
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        if (cameraProvider == null) {
            return;
        }
        int newLensFacing =
            lensFacing == CameraSelector.LENS_FACING_FRONT
                ? CameraSelector.LENS_FACING_BACK
                : CameraSelector.LENS_FACING_FRONT;
        CameraSelector newCameraSelector =
            new CameraSelector.Builder().requireLensFacing(newLensFacing).build();
        try {
            if (cameraProvider.hasCamera(newCameraSelector)) {
                Log.d(TAG, "Set facing to " + newLensFacing);
                lensFacing = newLensFacing;
                cameraSelector = newCameraSelector;
                bindAllCameraUseCases();
                return;
            }
        } catch (CameraInfoUnavailableException e) {
            // Falls through
        }
        Toast.makeText(getApplicationContext(),
            "This device does not have lens with facing: " + newLensFacing,
            Toast.LENGTH_SHORT).show();
    }
}
