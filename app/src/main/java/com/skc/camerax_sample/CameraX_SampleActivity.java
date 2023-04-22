package com.skc.camerax_sample;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
    Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector,  mImageCapture, imageAnalysis, preview);

    Button captureButton = findViewById(R.id.image_capture_button);
    captureButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        // e.g, /data/user/0/com.skc.camerax_sample/files/skc_capture/20230422004802.jpg
        String filename = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(new Date())+ ".jpg";
        File save_file = new File(getImageCaptureSaveDirPath(), filename);

        ImageCapture.OutputFileOptions outputFileOptions =
          new ImageCapture.OutputFileOptions.Builder(save_file).build();

        mImageCapture.takePicture(
          outputFileOptions,
          mExecutor,
          new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
              // e.g, getSavedUri=file:///data/user/0/com.skc.camerax_sample/files/skc_capture/20230422004802.jpg
              // adb shell 에서 ls /data 는 권한이 없어 실패한다(ls /sdcard 는 성공함)
              // 윈도우 탐색기에서 내 PC\신광철의 Note10+\내장 메모리\Android\data\com.skc.camerax_sample\files\skc_capture 에 가끔 접근 가능함
              Log.d(TAG, "onImageSaved getSavedUri=" + String.valueOf(outputFileResults.getSavedUri()));

              if (true) {
                String target_filename = save_file.getName();
                File target_file = new File(getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), target_filename);
                //File target_file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), target_filename);
                // --> Operation not permitted IOException 발생함(/storage/emulated/0/Movies/20230422112214.jpg)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                  try {
                    // copy from /data/user/0/com.skc.camerax_sample/files/skc_capture/20230422111621.jpg
                    // to /storage/emulated/0/Android/data/com.skc.camerax_sample/files/Pictures/20230422111621.jpg
                    // --> IOException 은 발생하지 않지만 해당 폴더 확인해보면 실제 복사는 이뤄지지 않았음
                    Log.d(TAG, "copy from " + save_file.toPath() + " to " + target_file.toPath());
                    Files.copy(save_file.toPath(), target_file.toPath());
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                }
              }
              new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                  Toast.makeText(CameraX_SampleActivity.this, save_file.toString() + " Saved!", Toast.LENGTH_SHORT).show();
                }
              });
            }

            @Override
            public void onError(ImageCaptureException e) {
              Log.e(TAG, " takePicture() failed! error=" + e.toString());
              e.printStackTrace();
            }
          });
      }
    });
  }

  private String getImageCaptureSaveDirPath() {
    // getExternalStoragePublicDirectory() 사용시 권한 오류(EPERM (Operation not permitted))
    // Android 10 (API level 29) 부터 scoped access into external storage 라서 발생
    // File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES));
    // getExternalFilesDir(Environment.DIRECTORY_PICTURES) 사용시에는 takePicture() 시에 Failed to write temp file 에러
    File dir = new File(getApplicationContext().getFilesDir(), "skc_capture");
    if (!dir.exists()) {
      Log.d(TAG, "getImageCaptureSaveDirPath() mkdir " + dir.toString());
      if (!dir.mkdirs()) {
        Log.e(TAG, "getImageCaptureSaveDirPath() mkdir failed! " + dir.toString());
      }
    }
    return dir.toString();
  }
}
