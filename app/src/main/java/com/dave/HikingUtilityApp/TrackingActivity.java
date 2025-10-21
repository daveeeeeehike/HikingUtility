package com.dave.HikingUtilityApp;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class TrackingActivity extends AppCompatActivity {

    private TextView tvDistance, tvDuration, tvCurrentPace, tvAvgPace;
    private Button btnStartStop, btnOpenMap;

    private GPXTrackingService gpxService;
    private boolean serviceBound = false;
    private boolean tracking = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            GPXTrackingService.GPXTrackingBinder binder = (GPXTrackingService.GPXTrackingBinder) service;
            gpxService = binder.getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            gpxService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracking);

        tvDistance = findViewById(R.id.tv_distance);
        tvDuration = findViewById(R.id.tv_duration);
        tvCurrentPace = findViewById(R.id.tv_current_pace);
        tvAvgPace = findViewById(R.id.tv_avg_pace);

        btnStartStop = findViewById(R.id.btn_start_stop);
        btnOpenMap = findViewById(R.id.btn_open_map);

        // Bind to GPXTrackingService
        Intent intent = new Intent(this, GPXTrackingService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);


        btnStartStop.setOnClickListener(v -> {
            if (!tracking) startTracking();
            else stopTracking();
        });

        btnOpenMap.setOnClickListener(v ->
                startActivity(new Intent(TrackingActivity.this, MapActivity.class)));
    }

    private void startTracking() {
        if (!serviceBound) return;

        Intent startIntent = new Intent(this, GPXTrackingService.class);
        startIntent.setAction("START_TRACKING");
        startService(startIntent);

        tracking = true;
        btnStartStop.setText("⏹ Stop");


        // Start updating UI every second
        runOnUiThread(updateRunnable);
    }

    private void stopTracking() {
        if (!serviceBound) return;

        Intent stopIntent = new Intent(this, GPXTrackingService.class);
        stopIntent.setAction("STOP_TRACKING");
        startService(stopIntent);

        tracking = false;
        btnStartStop.setText("▶ Start");

    }

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (tracking && serviceBound && gpxService != null) {
                double distance = gpxService.calculateTotalDistance();
                long duration = gpxService.getTrackingDuration();
                double avgPace = gpxService.calculatePaceMinPerMile();
                double currentPace = gpxService.calculateCurrentPaceMinPerMile();// For simplicity, could calculate from last N points


                tvDistance.setText(String.format(Locale.US, "%.2f mi", distance * 0.000621371));
                tvDuration.setText(formatDuration(duration));
                tvCurrentPace.setText(String.format(Locale.US, "%.1f min/mi", currentPace));
                tvAvgPace.setText(String.format(Locale.US, "%.1f min/mi", avgPace));

                // repeat every second
                tvDistance.postDelayed(this, 1000);
            }
        }
    };

    private String formatDuration(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        return hours > 0 ? String.format("%dh %dm", hours, minutes % 60) : String.format("%02d %02d %02d", hours, minutes, seconds);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) unbindService(serviceConnection);
    }
}