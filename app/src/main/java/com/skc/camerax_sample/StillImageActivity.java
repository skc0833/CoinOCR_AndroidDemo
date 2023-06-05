package com.skc.camerax_sample;

import static java.lang.Math.max;

import android.content.ContentValues;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
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

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Activity demonstrating different image detector features with a still image from camera. */
public class StillImageActivity extends AppCompatActivity {

    private static final String TAG = "StillImageActivity";

    private static final String SIZE_SCREEN = "w:screen"; // Match screen width
    private static final String SIZE_1024_768 = "w:1024"; // ~1024*768 in a normal ratio
    private static final String SIZE_640_480 = "w:640"; // ~640*480 in a normal ratio
    private static final String SIZE_ORIGINAL = "w:original"; // Original image size

    private static final String KEY_IMAGE_URI = "com.skc.camerax_sample.KEY_IMAGE_URI";
    private static final String KEY_SELECTED_SIZE = "com.skc.camerax_sample.KEY_SELECTED_SIZE";

    private static final int REQUEST_IMAGE_CAPTURE = 1001;
    private static final int REQUEST_CHOOSE_IMAGE = 1002;

    private ImageView preview;
    private String selectedSize = SIZE_SCREEN;

    boolean isLandScape;

    private Uri imageUri;
    private int imageMaxWidth;
    private int imageMaxHeight;

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
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        rootView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        imageMaxWidth = rootView.getWidth();
                        imageMaxHeight = rootView.getHeight() - findViewById(R.id.control).getHeight();
                        if (SIZE_SCREEN.equals(selectedSize)) {
                            tryReloadImage();
                        }
                    }
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
        tryReloadImage();
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
            tryReloadImage();
        } else if (requestCode == REQUEST_CHOOSE_IMAGE && resultCode == RESULT_OK) {
            // In this case, imageUri is returned by the chooser, save it.
            imageUri = data.getData();
            tryReloadImage();
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

    private void tryReloadImage() {
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
            preview.setImageBitmap(resizedBitmap);
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
                    tryReloadImage();
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {}
            });
    }

}