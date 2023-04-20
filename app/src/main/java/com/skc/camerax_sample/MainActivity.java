package com.skc.camerax_sample;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

  PreviewView mPreviewView;

  private final int REQUEST_CODE_PERMISSIONS = 1001;

  private final String[] REQUIRED_PERMISSIONS = new String[] {
    "android.permission.CAMERA"
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    mPreviewView = findViewById(R.id.previewView);

    if (allPermissionsGranted()) {
      startCamera();
    } else {
      ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
    }
  }

  private boolean allPermissionsGranted() {
    for (String permission : REQUIRED_PERMISSIONS) {
      if (ContextCompat.checkSelfPermission(this, permission)
        != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQUEST_CODE_PERMISSIONS) {
      if (allPermissionsGranted()) {
        startCamera();
      } else {
        Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
        this.finish();
      }
    }
  }

  void startCamera() {
    final ListenableFuture<ProcessCameraProvider> cameraProviderFuture
      = ProcessCameraProvider.getInstance(this);
    cameraProviderFuture.addListener(new Runnable() {
      @Override
      public void run() {
        try {
          ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
          bindPreview(cameraProvider);
        } catch (ExecutionException | InterruptedException e) {
          // No errors need to be handled for this Future.
          // This should never be reached.
        }
      }
    }, ContextCompat.getMainExecutor(this));
  }

  void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
    Preview preview = new Preview.Builder().build();
    CameraSelector cameraSelector = new CameraSelector.Builder()
      .requireLensFacing(CameraSelector.LENS_FACING_BACK)
      .build();
    preview.setSurfaceProvider(mPreviewView.getSurfaceProvider());
    cameraProvider.bindToLifecycle(this, cameraSelector, preview);
  }
}
