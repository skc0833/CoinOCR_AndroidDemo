package com.skc.camerax_sample;

import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CameraX_SampleActivity extends AppCompatActivity {
  private static final String TAG = "CameraX_SampleActivity";

  private PreviewView mPreviewView;
  private ImageCapture mImageCapture;
  private Executor mExecutor = Executors.newSingleThreadExecutor();

  private final int REQUEST_CODE_PERMISSIONS = 1001;
  private final String[] REQUIRED_PERMISSIONS = new String[] {
          "android.permission.CAMERA",
          "android.permission.WRITE_EXTERNAL_STORAGE",
          "android.permission.READ_EXTERNAL_STORAGE"
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

  private void startCamera() {
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
          Log.e(TAG, "startCamera() Exception e=" + e.toString());
        }
      }
    }, ContextCompat.getMainExecutor(this));
  }

  private String getPathFromUri(Uri uri){
    Cursor cursor = getContentResolver().query(uri, null, null, null, null );
    cursor.moveToNext();
    int idx = cursor.getColumnIndex("_data");
    String path = "";
    if (idx >= 0) {
      path = cursor.getString(idx);
    } else {
      assert false;
    }
    cursor.close();
    return path;
  }

  private void takePicture() {
    String filename = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())+ ".jpg";
    ContentValues contentValues = new ContentValues();
    contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
    contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
    contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/skc"); // 내장 메모리\Pictures\skc
    mImageCapture.takePicture(
        new ImageCapture.OutputFileOptions.Builder(
          getContentResolver(),
          MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
          contentValues
        ).build(),
        mExecutor,
        new ImageCapture.OnImageSavedCallback() {
          @Override
          public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
            // e.g., outputFileResults.getSavedUri() = content://media/external/images/media/88296
            Log.d(TAG, "onImageSaved getSavedUri=" + outputFileResults.getSavedUri());
            // e.g., path=/storage/emulated/0/Pictures/skc/20230603_224115.jpg
            String path = getPathFromUri(outputFileResults.getSavedUri());
            new Handler(Looper.getMainLooper()).post(new Runnable() {
              @Override
              public void run() {
                Toast.makeText(CameraX_SampleActivity.this, path + " Saved!", Toast.LENGTH_SHORT).show();
              }
            });
          }

          @Override
          public void onError(ImageCaptureException e) {
            Log.e(TAG, " takePicture() failed! error=" + e.toString());
            e.printStackTrace();
            new Handler(Looper.getMainLooper()).post(new Runnable() {
              @Override
              public void run() {
                Toast.makeText(CameraX_SampleActivity.this, "Error takePicture() " + e, Toast.LENGTH_SHORT).show();
              }
            });
          }
        }
    );
  }

  private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
    Preview preview = new Preview.Builder().build();
    CameraSelector cameraSelector = new CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build();

    ImageAnalysis imageAnalysis = new ImageAnalysis.Builder().build();

    mImageCapture = new ImageCapture.Builder()
            .setTargetRotation(this.getWindowManager().getDefaultDisplay().getRotation())
            .build();
    //skc 원래는 mPreviewView.createSurfaceProvider() 였는데 빌드 에러로 변경함(없는 method)
    preview.setSurfaceProvider(mPreviewView.getSurfaceProvider());
    cameraProvider.bindToLifecycle(this, cameraSelector,  mImageCapture, imageAnalysis, preview);

    Button captureButton = findViewById(R.id.image_capture_button);
    captureButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        // mImageCapture.takePicture() 함수로 Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getPath(); 에 저장시
        // androidx.camera.core.ImageCaptureException: Failed to write temp file 에러 발생중
        takePicture();
      }
    });
  }
}
