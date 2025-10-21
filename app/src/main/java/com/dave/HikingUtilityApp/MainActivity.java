package com.dave.HikingUtilityApp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;

    private Button btnTrack;
    private Button btnMaps;
    private Button btnOfflineMapDownloader;
    private Button btnGPX;
    private Button btnOfflineMapManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ✅ Initialize buttons
        btnTrack = findViewById(R.id.btn_start_tracking);
        btnMaps = findViewById(R.id.btn_maps);
        btnGPX = findViewById(R.id.btn_gpx);
        btnOfflineMapManager = findViewById(R.id.btn_offline_map_manager);
        btnOfflineMapDownloader = findViewById(R.id.btn_offline_map_downloader);

        // ✅ Set button listeners
        btnTrack.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, TrackingActivity.class)));

        btnMaps.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, MapActivity.class)));

        btnGPX.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, GPXListActivity.class)));

        btnOfflineMapManager.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, OfflineMapManager.class)));

        btnOfflineMapDownloader.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, OfflineMapDownloader.class)));

        // ✅ Request permissions
        checkPermissions();
    }

    private void checkPermissions() {
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        };

        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean granted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }

            if (!granted) {
                Toast.makeText(this, "Location permission is required to track hikes.", Toast.LENGTH_LONG).show();
            }
        }
    }
}