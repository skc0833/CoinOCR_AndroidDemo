package com.skc.coin_ocr;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;

import com.skc.coin_ocr.ocr.Predictor;
import com.skc.coin_ocr.preference.SettingsActivity;
import com.skc.coin_ocr.textdetector.TextRecognitionProcessor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LivePreviewActivity extends AppCompatActivity
    implements AdapterView.OnItemSelectedListener, CompoundButton.OnCheckedChangeListener {

    private static final String TAG = "LivePreviewActivity";

    private static final String COIN_RECOGNITION_ch_PP_OCRv2 = "Coin Recognition ch_PP-OCRv2";
    private static final String COIN_RECOGNITION_en_PP_OCRv3 = "Coin Recognition ev_PP-OCRv3";
    private CameraSource cameraSource = null;
    private CameraSourcePreview preview;
    private GraphicOverlay graphicOverlay;
    private String selectedModel = COIN_RECOGNITION_ch_PP_OCRv2;

    protected Predictor predictor = new Predictor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_preview);

        preview = findViewById(R.id.preview_view);
        if (preview == null) {
            Log.d(TAG, "Preview is null");
        }
        graphicOverlay = findViewById(R.id.graphic_overlay);
        if (graphicOverlay == null) {
            Log.d(TAG, "graphicOverlay is null");
        }

        Spinner spinner = findViewById(R.id.spinner);
        List<String> options = new ArrayList<>();
        options.add(COIN_RECOGNITION_ch_PP_OCRv2);
        //options.add(COIN_RECOGNITION_en_PP_OCRv3); // skc TODO

        // Creating adapter for spinner
        ArrayAdapter<String> dataAdapter = new ArrayAdapter<>(this, R.layout.spinner_style, options);
        // Drop down layout style - list view with radio button
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // attaching data adapter to spinner
        spinner.setAdapter(dataAdapter);
        spinner.setOnItemSelectedListener(this);

        ToggleButton facingSwitch = findViewById(R.id.facing_switch);
        facingSwitch.setOnCheckedChangeListener(this);

        ImageView settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(
            v -> {
                Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
                intent.putExtra(
                    SettingsActivity.EXTRA_LAUNCH_SOURCE, SettingsActivity.LaunchSource.LIVE_PREVIEW);
                startActivity(intent);
            });

        //createCameraSource(selectedModel); // skc 불필요해 보이므로 주석처리함
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        createCameraSource(selectedModel);
        startCameraSource();
    }

    /** Stops the camera. */
    @Override
    protected void onPause() {
        super.onPause();
        preview.stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraSource != null) {
            cameraSource.release();
        }
    }

    @Override
    public synchronized void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        // An item was selected. You can retrieve the selected item using
        // parent.getItemAtPosition(pos)
        selectedModel = parent.getItemAtPosition(pos).toString();
        Log.d(TAG, "Selected model: " + selectedModel);
        preview.stop();
        createCameraSource(selectedModel);
        startCameraSource();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Do nothing.
    }
    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        Log.d(TAG, "Set facing");
        if (cameraSource != null) {
            if (isChecked) {
                cameraSource.setFacing(CameraSource.CAMERA_FACING_FRONT);
            } else {
                cameraSource.setFacing(CameraSource.CAMERA_FACING_BACK);
            }
        }
        preview.stop();
        startCameraSource();
    }

    /**
     * Starts or restarts the camera source, if it exists. If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {
        if (cameraSource != null) {
            try {
                if (preview == null) {
                    Log.d(TAG, "resume: Preview is null");
                }
                if (graphicOverlay == null) {
                    Log.d(TAG, "resume: graphOverlay is null");
                }
                preview.start(cameraSource, graphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                cameraSource.release();
                cameraSource = null;
            }
        }
    }

    // skc 시작시 onCreate() 에서 호출하는 부분은 주석처리함(불필요해보임)
    // onResume() onItemSelected() 에서만 호출됨
    private void createCameraSource(String model) {
        // If there's no existing cameraSource, create one.
        if (cameraSource == null) {
            cameraSource = new CameraSource(this, graphicOverlay);
        }

        try {
            switch (model) {
                case COIN_RECOGNITION_ch_PP_OCRv2:
                    Log.i(TAG, "Using COIN_RECOGNITION_ch_PP_OCRv2");
                    predictor.init(this, "models/ch_PP-OCRv2", "labels/ppocr_keys_v1.txt",
                        0, 1, "", 960, 0.1f);
                    cameraSource.setMachineLearningFrameProcessor(new TextRecognitionProcessor(this, predictor));

                    break;
                case COIN_RECOGNITION_en_PP_OCRv3:
                    Log.i(TAG, "Using COIN_RECOGNITION_en_PP_OCRv3");
                    //cameraSource.setMachineLearningFrameProcessor(
                    //    new TextRecognitionProcessor(this, predictor));
                    break;
                default:
                    Log.e(TAG, "Unknown model: " + model);
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Can not create image processor: " + model, e);
            Toast.makeText(
                    getApplicationContext(),
                    "Can not create image processor: " + e.getMessage(),
                    Toast.LENGTH_LONG)
                .show();
        }
    }
}

