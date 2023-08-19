package com.skc.coin_ocr;

import static java.lang.Math.max;

import android.content.ContentValues;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.skc.coin_ocr.ocr.Predictor;
import com.skc.coin_ocr.textdetector.TextRecognitionProcessor;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Activity demonstrating different image detector features with a still image from camera. */
public class StillImageActivity extends AppCompatActivity {

    private static final String TAG = "StillImageActivity";

    // private static final String COIN_RECOGNITION_ch_PP_OCRv3_Student_99_en = "ch_PP-OCRv3_Student_99_en";
    private static final String COIN_RECOGNITION_ch_PP_OCRv3_Student_99 = "ch_PP-OCRv3_Student_99";
    private static final String COIN_RECOGNITION_ch_PP_OCRv2_org = "ch_PP-OCRv2_org";
    private static final String COIN_RECOGNITION_ch_PP_OCRv2_infer = "ch_PP-OCRv2_infer";
    private static final String COIN_RECOGNITION_ch_PP_OCRv2_train_Student = "ch_PP-OCRv2_train_Student";
    private static final String COIN_RECOGNITION_ch_PP_OCRv3_train_Student = "ch_PP-OCRv3_train_Student";
    private static final String SIZE_SCREEN = "w:screen"; // Match screen width
    private static final String SIZE_1024_768 = "w:1024"; // ~1024*768 in a normal ratio
    private static final String SIZE_640_480 = "w:640"; // ~640*480 in a normal ratio
    private static final String SIZE_960_720 = "w:960"; //skc add
    private static final String SIZE_ORIGINAL = "w:original"; // Original image size

    private static final String KEY_IMAGE_URI = "com.skc.coin_ocr.KEY_IMAGE_URI";
    private static final String KEY_SELECTED_SIZE = "com.skc.coin_ocr.KEY_SELECTED_SIZE";

    private static final int REQUEST_IMAGE_CAPTURE = 1001;
    private static final int REQUEST_CHOOSE_IMAGE = 1002;

    private ImageView preview;
    private GraphicOverlay graphicOverlay;
    private String selectedMode = COIN_RECOGNITION_ch_PP_OCRv3_Student_99; // COIN_RECOGNITION_ch_PP_OCRv2_org;

    private String selectedSize = SIZE_SCREEN;

    boolean isLandScape;

    private Uri imageUri;
    private int imageMaxWidth;
    private int imageMaxHeight;
    private VisionImageProcessor imageProcessor;

    protected Predictor predictor = new Predictor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_still_image);

        findViewById(R.id.select_image_button)
            .setOnClickListener(
                view -> {
                    PopupMenu popup = new PopupMenu(this, view);
                    popup.setOnMenuItemClickListener(
                        menuItem -> {
                            int itemId = menuItem.getItemId();
                            if (itemId == R.id.select_images_from_local) {
                                startChooseImageIntentForResult();
                                return true;
                            } else if (itemId == R.id.take_photo_using_camera) {
                                startCameraIntentForResult();
                                return true;
                            }
                            return false;
                        });
                    MenuInflater inflater = popup.getMenuInflater();
                    inflater.inflate(R.menu.camera_button_menu, popup.getMenu());
                    popup.show();
                });
        preview = findViewById(R.id.preview);
        graphicOverlay = findViewById(R.id.graphic_overlay);

        populateFeatureSelector();
        populateSizeSelector();

        isLandScape =
            (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);

        if (savedInstanceState != null) {
            imageUri = savedInstanceState.getParcelable(KEY_IMAGE_URI);
            selectedSize = savedInstanceState.getString(KEY_SELECTED_SIZE);
        }

        View rootView = findViewById(R.id.root);
        rootView
            .getViewTreeObserver()
            .addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() { // skc 전체 뷰가 그려질 때 호출됨
                    @Override
                    public void onGlobalLayout() {
                        rootView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        imageMaxWidth = rootView.getWidth();
                        imageMaxHeight = rootView.getHeight() - findViewById(R.id.control).getHeight();
                        if (SIZE_SCREEN.equals(selectedSize)) {
                            //tryReloadAndDetectInImage(); // skc 불필요해 보이므로 주석처리함
                        }
                    }
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
        //createImageProcessor(); // skc 불필요해 보이므로 주석처리함
        //tryReloadAndDetectInImage();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (imageProcessor != null) {
            imageProcessor.stop();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (imageProcessor != null) {
            imageProcessor.stop();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_IMAGE_URI, imageUri);
        outState.putString(KEY_SELECTED_SIZE, selectedSize);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            tryReloadAndDetectInImage();
        } else if (requestCode == REQUEST_CHOOSE_IMAGE && resultCode == RESULT_OK) {
            // In this case, imageUri is returned by the chooser, save it.
            imageUri = data.getData();
            tryReloadAndDetectInImage();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void startChooseImageIntentForResult() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), REQUEST_CHOOSE_IMAGE);
    }

    private void startCameraIntentForResult() {
        // Clean up last time's image
        imageUri = null;
        preview.setImageBitmap(null);

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.TITLE, "New Picture");
            values.put(MediaStore.Images.Media.DESCRIPTION, "From Camera");
            imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    private void populateFeatureSelector() {
        Spinner featureSpinner = findViewById(R.id.feature_selector);
        List<String> options = new ArrayList<>();
        // options.add(COIN_RECOGNITION_ch_PP_OCRv3_Student_99_en);
        options.add(COIN_RECOGNITION_ch_PP_OCRv3_Student_99);
        options.add(COIN_RECOGNITION_ch_PP_OCRv2_org);
        options.add(COIN_RECOGNITION_ch_PP_OCRv2_infer);
        options.add(COIN_RECOGNITION_ch_PP_OCRv2_train_Student);
        options.add(COIN_RECOGNITION_ch_PP_OCRv3_train_Student);

        // Creating adapter for featureSpinner
        ArrayAdapter<String> dataAdapter = new ArrayAdapter<>(this, R.layout.spinner_style, options);
        // Drop down layout style - list view with radio button
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // attaching data adapter to spinner
        featureSpinner.setAdapter(dataAdapter);
        featureSpinner.setOnItemSelectedListener(
            new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(
                    AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                    selectedMode = parentView.getItemAtPosition(pos).toString();
                    createImageProcessor(selectedMode);
                    tryReloadAndDetectInImage();
                }
                @Override
                public void onNothingSelected(AdapterView<?> arg0) {}
            });
    }

    // skc 시작시 onResume() 에서 호출하는 부분은 주석처리함(불필요해보임)
    // featureSpinner.onItemSelected() 에서만 호출됨
    private void createImageProcessor(String model) {
        if (imageProcessor != null) {
            imageProcessor.stop();
        }
        try {
            String modelPath = "models/" + model;
            String labelPath = "labels/ppocr_keys_v1.txt";
            String det_model = "det_db.nb";
            String rec_model = "rec_crnn.nb";
            String cls_model = "cls.nb";
            switch (model) {
                case COIN_RECOGNITION_ch_PP_OCRv2_org:
                    det_model = "det_db.nb";
                    break;
                // case COIN_RECOGNITION_ch_PP_OCRv3_Student_99_en:
                case COIN_RECOGNITION_ch_PP_OCRv3_Student_99:
                case COIN_RECOGNITION_ch_PP_OCRv2_infer:
                case COIN_RECOGNITION_ch_PP_OCRv2_train_Student:
                case COIN_RECOGNITION_ch_PP_OCRv3_train_Student:
                    det_model = "det_" + model + ".nb";
                    rec_model = "rec_" + model + ".nb";
                    break;
                default:
                    Log.e(TAG, "Unknown model: " + model);
                    Toast.makeText(getApplicationContext(), "Unknown model: " + model, Toast.LENGTH_LONG).show();
                    return;
            }
            switch (model) {
                // case COIN_RECOGNITION_ch_PP_OCRv3_Student_99_en:
                case COIN_RECOGNITION_ch_PP_OCRv3_Student_99:
                case COIN_RECOGNITION_ch_PP_OCRv2_org:
                case COIN_RECOGNITION_ch_PP_OCRv2_infer:
                case COIN_RECOGNITION_ch_PP_OCRv2_train_Student:
                case COIN_RECOGNITION_ch_PP_OCRv3_train_Student:
                    labelPath = "labels/ppocr_keys_v1.txt";
                    break;
//                case COIN_RECOGNITION_en_PP_OCRv3_infer:
//                    // ic15_dict.txt 는 영문자를 제대로 출력하지 못함
//                    labelPath = "labels/en_dict.txt";
//                    break;
                default:
                    Log.e(TAG, "Unknown model: " + model);
                    Toast.makeText(getApplicationContext(), "Unknown model(label): " + model, Toast.LENGTH_LONG).show();
                    return;
            }
            Log.i(TAG, "Using " + model);
            predictor.init(this, modelPath, labelPath, det_model, rec_model, cls_model, 0, 1, "", 960, 0.1f);
            imageProcessor = new TextRecognitionProcessor(this, predictor);
        } catch (Exception e) {
            Log.e(TAG, "Can not create image processor: " + selectedMode, e);
            Toast.makeText(
                    getApplicationContext(),
                    "Can not create image processor: " + e.getMessage(),
                    Toast.LENGTH_LONG)
                .show();
        }
    }

    private void tryReloadAndDetectInImage() {
        Log.d(TAG, "Try reload and detect image");
        try {
            if (imageUri == null) {
                return;
            }

            if (SIZE_SCREEN.equals(selectedSize) && imageMaxWidth == 0) {
                // UI layout has not finished yet, will reload once it's ready.
                return;
            }

            Bitmap imageBitmap = BitmapUtils.getBitmapFromContentUri(getContentResolver(), imageUri);
            if (imageBitmap == null) {
                return;
            }

            // Clear the overlay first
            graphicOverlay.clear();

            Bitmap resizedBitmap;
            if (selectedSize.equals(SIZE_ORIGINAL)) {
                resizedBitmap = imageBitmap;
            } else {
                // Get the dimensions of the image view
                Pair<Integer, Integer> targetedSize = getTargetedWidthHeight();

                // Determine how much to scale down the image
                float scaleFactor =
                    max(
                        (float) imageBitmap.getWidth() / (float) targetedSize.first,
                        (float) imageBitmap.getHeight() / (float) targetedSize.second);

                resizedBitmap =
                    Bitmap.createScaledBitmap(
                        imageBitmap,
                        (int) (imageBitmap.getWidth() / scaleFactor),
                        (int) (imageBitmap.getHeight() / scaleFactor),
                        true);
            }

            if (!true) { //skc test coin_multi.jpg 이미지로 python 결과와 비교해보기
                Bitmap              bmp = null;
                InputStream is  = null;
                AssetManager am = this.getAssets();

                try {
                    is = am.open( "test/coin_multi.jpg", AssetManager.ACCESS_UNKNOWN );
                    if( is == null ) {
                        throw new Exception("fail to open asset");
                    }
                    bmp = BitmapFactory.decodeStream( is, null, null );
                    //bmp = BitmapFactory.decodeFileDescriptor( fd );
                    if( bmp == null ) {
                        throw new Exception("fail to load bitmap from InputStream");
                    }
                    is.close();
                    is = null;
                }
                catch( Exception e ) {
                    Log.d( TAG, e.getMessage() );
                    e.printStackTrace();
                }
                finally {
                    try {
                        if( is != null ) {
                            is.close();
                        }
                    }
                    catch( Exception e ) { }
                }
                resizedBitmap = bmp;
            }

            preview.setImageBitmap(resizedBitmap);

            if (imageProcessor != null) {
                graphicOverlay.setImageSourceInfo(
                    resizedBitmap.getWidth(), resizedBitmap.getHeight(), /* isFlipped= */ false);
                imageProcessor.processBitmap(resizedBitmap, graphicOverlay);
            } else {
                Log.e(TAG, "Null imageProcessor, please check adb logs for imageProcessor creation error");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error retrieving saved image");
            imageUri = null;
        }
    }

    private Pair<Integer, Integer> getTargetedWidthHeight() {
        int targetWidth;
        int targetHeight;

        switch (selectedSize) {
            case SIZE_SCREEN:
                targetWidth = imageMaxWidth;
                targetHeight = imageMaxHeight;
                break;
            case SIZE_640_480:
                targetWidth = isLandScape ? 640 : 480;
                targetHeight = isLandScape ? 480 : 640;
                break;
            case SIZE_960_720:
                targetWidth = isLandScape ? 960 : 720;
                targetHeight = isLandScape ? 720 : 960;
                break;
            case SIZE_1024_768:
                targetWidth = isLandScape ? 1024 : 768;
                targetHeight = isLandScape ? 768 : 1024;
                break;
            default:
                throw new IllegalStateException("Unknown size");
        }

        return new Pair<>(targetWidth, targetHeight);
    }

    private void populateSizeSelector() {
        Spinner sizeSpinner = findViewById(R.id.size_selector);
        List<String> options = new ArrayList<>();
        options.add(SIZE_SCREEN);
        options.add(SIZE_1024_768);
        options.add(SIZE_960_720);
        options.add(SIZE_640_480);
        options.add(SIZE_ORIGINAL);

        // Creating adapter for featureSpinner
        ArrayAdapter<String> dataAdapter = new ArrayAdapter<>(this, R.layout.spinner_style, options);
        // Drop down layout style - list view with radio button
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // attaching data adapter to spinner
        sizeSpinner.setAdapter(dataAdapter);
        sizeSpinner.setOnItemSelectedListener(
            new AdapterView.OnItemSelectedListener() {

                @Override
                public void onItemSelected(
                    AdapterView<?> parentView, View selectedItemView, int pos, long id) {
                    selectedSize = parentView.getItemAtPosition(pos).toString();
                    tryReloadAndDetectInImage();
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {}
            });
    }
}