package com.skc.camerax_sample;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class ChooserActivity extends AppCompatActivity {
    private static final String TAG = "ChooserActivity";

    private final int REQUEST_CODE_PERMISSIONS = 1001;
    private final String[] REQUIRED_PERMISSIONS = new String[]{
        "android.permission.CAMERA",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_EXTERNAL_STORAGE"
    };

    private static final Class<?>[] CLASSES =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
            ? new Class<?>[]{
            LivePreviewActivity.class,
            StillImageActivity.class,
        }
            : new Class<?>[]{
            LivePreviewActivity.class,
            StillImageActivity.class,
            CameraXLivePreviewActivity.class,
        };
    private static final int[] DESCRIPTION_IDS =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
            ? new int[]{
            R.string.desc_camera_source_activity,
            R.string.desc_still_image_activity,
        }
            : new int[]{
            R.string.desc_camera_source_activity,
            R.string.desc_still_image_activity,
            R.string.desc_camerax_live_preview_activity,
        };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chooser);

        if (allPermissionsGranted()) {
            setupUI();
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
                setupUI();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                this.finish();
            }
        }
    }

    private void setupUI() {
        List<String> list = new ArrayList<>();
        for (int idx : DESCRIPTION_IDS) {
            list.add(getResources().getString(idx));
        }

        ListView listView = findViewById(R.id.test_activity_list_view);
        if (true) {
            // 2 line text view
            MyArrayAdapter adapter = new MyArrayAdapter(this, android.R.layout.simple_list_item_2, CLASSES);
            adapter.setDescriptionIds(DESCRIPTION_IDS);
            listView.setAdapter(adapter);
        } else {
            // 1 line text view
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, list);
            listView.setAdapter(adapter);
        }
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                Class<?> clicked = CLASSES[position];
                startActivity(new Intent(getApplicationContext(), clicked));
            }
        });
    }

    private static class MyArrayAdapter extends ArrayAdapter<Class<?>> {
        private final Context context;
        private final Class<?>[] classes;
        private int[] descriptionIds;

        MyArrayAdapter(Context context, int resource, Class<?>[] objects) {
            super(context, resource, objects);
            this.context = context;
            this.classes = objects;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(android.R.layout.simple_list_item_2, null);
            }
            ((TextView) view.findViewById(android.R.id.text1)).setText(classes[position].getSimpleName());
            ((TextView) view.findViewById(android.R.id.text2)).setText(descriptionIds[position]);
            return view;
        }

        void setDescriptionIds(int[] descriptionIds) {
            this.descriptionIds = descriptionIds;
        }
    }
}