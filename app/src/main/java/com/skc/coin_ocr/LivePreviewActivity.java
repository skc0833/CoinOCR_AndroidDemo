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

    private static final String COIN_RECOGNITION_ch_PP_OCRv2 = "ch_PP-OCRv2";
    private static final String COIN_RECOGNITION_ch_PP_OCRv3_infer = "ch_PP-OCRv3_infer";
    private static final String COIN_RECOGNITION_en_PP_OCRv3_infer = "en_PP-OCRv3_infer";
    // skc trained
    private static final String COIN_RECOGNITION_ch_PP_OCRv3_add_60x2_230708 = "ch_PP-OCR_V3_add_60x2_230708";
    private static final String COIN_RECOGNITION_ch_PP_OCRv3_500_add_500_noflip_rot30 = "ch_PP-OCR_V3_500_add_500_noflip_rot30";
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
        options.add(COIN_RECOGNITION_ch_PP_OCRv3_infer);
        options.add(COIN_RECOGNITION_en_PP_OCRv3_infer);
        options.add(COIN_RECOGNITION_ch_PP_OCRv3_add_60x2_230708);
        options.add(COIN_RECOGNITION_ch_PP_OCRv3_500_add_500_noflip_rot30);

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
            String modelPath = "models/" + model;
            String labelPath = "labels/ppocr_keys_v1.txt";
            String det_model = "det_db.nb";
            String rec_model = "rec_crnn.nb";
            String cls_model = "cls.nb";
            switch (model) {
                case COIN_RECOGNITION_ch_PP_OCRv2:
                    det_model = "det_db.nb";
                    break;
                case COIN_RECOGNITION_ch_PP_OCRv3_infer:
                case COIN_RECOGNITION_en_PP_OCRv3_infer:
                case COIN_RECOGNITION_ch_PP_OCRv3_add_60x2_230708:
                case COIN_RECOGNITION_ch_PP_OCRv3_500_add_500_noflip_rot30:
                    det_model = "det_" + model + ".nb";
                    rec_model = "rec_" + model + ".nb";
                    break;
                default:
                    Log.e(TAG, "Unknown model: " + model);
                    Toast.makeText(getApplicationContext(), "Unknown model: " + model, Toast.LENGTH_LONG).show();
                    return;
            }
            // skc 현재 COIN_RECOGNITION_ch_PP_OCRv2 성능이 가장 좋아보임!!!
            switch (model) {
                case COIN_RECOGNITION_ch_PP_OCRv2:
                case COIN_RECOGNITION_ch_PP_OCRv3_infer:
                // 100원 동전의 경우 금액이 거의 NaN 으로 표시되고 있다(500원짜리는 ok)
                case COIN_RECOGNITION_ch_PP_OCRv3_add_60x2_230708:
                case COIN_RECOGNITION_ch_PP_OCRv3_500_add_500_noflip_rot30:
                    labelPath = "labels/ppocr_keys_v1.txt";
                    break;
                case COIN_RECOGNITION_en_PP_OCRv3_infer:
                    // ic15_dict.txt 는 영문자를 제대로 출력하지 못함
                    labelPath = "labels/en_dict.txt"; // ic15_dict.txt 는 숫자+알파벳(소문자), en_dict.txt 는 추가로 대문자, 특수문자까지 포함됨
                    break;
                default:
                    Log.e(TAG, "Unknown model: " + model);
                    Toast.makeText(getApplicationContext(), "Unknown model(label): " + model, Toast.LENGTH_LONG).show();
                    return;
            }
            Log.i(TAG, "Using " + model);
            predictor.init(this, modelPath, labelPath, det_model, rec_model, cls_model, 0, 1, "", 960, 0.1f);
            cameraSource.setMachineLearningFrameProcessor(new TextRecognitionProcessor(this, predictor));
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

