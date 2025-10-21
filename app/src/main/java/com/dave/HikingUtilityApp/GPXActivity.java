package com.dave.HikingUtilityApp;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.InputStream;
import java.text.DecimalFormat;
public class GPXActivity extends AppCompatActivity implements LocationHelper.LocationUpdateListener {

    private TextView tvCurrentTrack;
    private TextView tvTrackStats;
    private Button btnStartTracking;
    private Button btnStopTracking;
    private Button btnSaveTrack;
    private Button btnImportGPX;
    private Button btnViewOnMap;

    private LocationHelper locationHelper;
    private GPXManager gpxManager;
    private GPXManager.GPXTrack loadedTrack;
    private final DecimalFormat distanceFormat = new DecimalFormat("#.##");

    private static final int REQUEST_CODE_PICK_FILE = 1001;

    // Background service connection
    private GPXTrackingService trackingService;
    private boolean serviceBound = false;
    private final Handler updateHandler = new Handler();
    private Runnable updateRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gpx);

        initViews();
        initServices();
        setupClickListeners();
        updateUI();
    }

    private void initViews() {
        tvCurrentTrack = findViewById(R.id.tv_current_track);
        tvTrackStats = findViewById(R.id.tv_track_stats);
        btnStartTracking = findViewById(R.id.btn_start_tracking);
        btnStopTracking = findViewById(R.id.btn_stop_tracking);
        btnSaveTrack = findViewById(R.id.btn_save_track);
        btnImportGPX = findViewById(R.id.btn_import_gpx);
        btnViewOnMap = findViewById(R.id.btn_view_on_map);
    }

    private void initServices() {
        locationHelper = new LocationHelper(this, this);
        gpxManager = new GPXManager(this);
        locationHelper.startLocationUpdates();

        // Bind to tracking service
        bindToTrackingService();

        // Start periodic UI updates
        startPeriodicUpdates();
    }

    private void setupClickListeners() {
        btnStartTracking.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showStartTrackingDialog();
            }
        });

        btnStopTracking.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopTracking();
            }
        });

        btnSaveTrack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCurrentTrack();
            }
        });

        btnImportGPX.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFilePicker();
            }
        });

        btnViewOnMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewTrackOnMap();
            }
        });
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(Intent.createChooser(intent, "Select GPX File"), REQUEST_CODE_PICK_FILE);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "No file picker available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                importGPXFile(data.getData());
            }
        }
    }

    private void showStartTrackingDialog() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_start_tracking, null);

        EditText etTrackName = dialogView.findViewById(R.id.et_track_name);

        // Generate default name
        String defaultName = "Track_" + System.currentTimeMillis();
        etTrackName.setText(defaultName);
        etTrackName.selectAll();

        new AlertDialog.Builder(this)
                .setTitle("Start GPS Tracking")
                .setView(dialogView)
                .setPositiveButton("Start", (dialog, which) -> {
                    String trackName = etTrackName.getText().toString().trim();
                    if (trackName.isEmpty()) {
                        trackName = defaultName;
                    }
                    startTracking(trackName);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startTracking(String trackName) {
        // Start background service
        Intent serviceIntent = new Intent(this, GPXTrackingService.class);
        serviceIntent.setAction("START_TRACKING");
        serviceIntent.putExtra("track_name", trackName);
        startService(serviceIntent);

        updateUI();
        Toast.makeText(this, "Background GPS tracking started: " + trackName, Toast.LENGTH_LONG).show();
        Toast.makeText(this, "🔋 Warning: GPS tracking will use more battery", Toast.LENGTH_LONG).show();
    }

    private void stopTracking() {
        // Stop background service
        Intent serviceIntent = new Intent(this, GPXTrackingService.class);
        serviceIntent.setAction("STOP_TRACKING");
        startService(serviceIntent);

        updateUI();
        Toast.makeText(this, "GPS tracking stopped", Toast.LENGTH_SHORT).show();
    }

    private void saveCurrentTrack() {
        GPXManager.GPXTrack currentTrack = gpxManager.getCurrentTrack();

        if (currentTrack.points.isEmpty()) {
            Toast.makeText(this, "No track points to save", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            java.io.File savedFile = gpxManager.saveTrackToFile(currentTrack);
            Toast.makeText(this, "Track saved: " + savedFile.getName(), Toast.LENGTH_LONG).show();

            // Share the saved file
            shareGPXFile(savedFile);

        } catch (Exception e) {
            Toast.makeText(this, "Error saving track: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void shareGPXFile(java.io.File gpxFile) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/gpx+xml");
        shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(gpxFile));
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "GPX Track: " + gpxFile.getName());

        Intent chooser = Intent.createChooser(shareIntent, "Share GPX Track");
        startActivity(chooser);
    }

    private void importGPXFile(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            loadedTrack = gpxManager.importGPXFromUri(inputStream);
            inputStream.close();

            updateUI();

            String message = String.format("GPX imported: %s\n%d points, %.2f m",
                    loadedTrack.name,
                    loadedTrack.points.size(),
                    loadedTrack.getTotalDistance() / 0.621371);

            Toast.makeText(this, message, Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(this, "Error importing GPX: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void viewTrackOnMap() {
        GPXManager.GPXTrack trackToView = null;

        if (loadedTrack != null && !loadedTrack.points.isEmpty()) {
            trackToView = loadedTrack;
        } else if (gpxManager.getCurrentTrackSize() > 0) {
            trackToView = gpxManager.getCurrentTrack();
        }

        if (trackToView == null || trackToView.points.isEmpty()) {
            Toast.makeText(this, "No track to display", Toast.LENGTH_SHORT).show();
            return;
        }

        // Pass track data to MapActivity
        Intent intent = new Intent(this, MapActivity.class);
        intent.putExtra("show_gpx_track", true);
        intent.putExtra("track_name", trackToView.name);

        // Pass first few points (limitation of Intent size)
        int maxPoints = Math.min(trackToView.points.size(), 100);
        double[] lats = new double[maxPoints];
        double[] lons = new double[maxPoints];

        for (int i = 0; i < maxPoints; i++) {
            GPXManager.GPXPoint point = trackToView.points.get(i);
            lats[i] = point.latitude;
            lons[i] = point.longitude;
        }

        intent.putExtra("track_lats", lats);
        intent.putExtra("track_lons", lons);

        startActivity(intent);
    }

    private void bindToTrackingService() {
        Intent intent = new Intent(this, GPXTrackingService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            GPXTrackingService.GPXTrackingBinder binder = (GPXTrackingService.GPXTrackingBinder) service;
            trackingService = binder.getService();
            serviceBound = true;
            updateUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            serviceBound = false;
            trackingService = null;
        }
    };

    private void startPeriodicUpdates() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateUI();
                updateHandler.postDelayed(this, 2000); // Update every 2 seconds
            }
        };
        updateHandler.post(updateRunnable);
    }

    private void updateUI() {
        boolean isTracking = serviceBound && trackingService != null && trackingService.isTracking();

        if (isTracking) {
            String trackName = trackingService.getTrackName();
            tvCurrentTrack.setText("🔴 RECORDING: " + trackName );
            btnStartTracking.setVisibility(View.GONE);
            btnStopTracking.setVisibility(View.VISIBLE);
        } else {
            tvCurrentTrack.setText("⚫ Not recording");
            btnStartTracking.setVisibility(View.VISIBLE);
            btnStopTracking.setVisibility(View.GONE);
        }

        // Show stats for current track or loaded track
        GPXManager.GPXTrack displayTrack = null;

        if (isTracking && trackingService != null) {
            // Create temporary track from service data
            displayTrack = new GPXManager.GPXTrack(trackingService.getTrackName());
            displayTrack.points.addAll(trackingService.getCurrentTrackPoints());
        } else if (loadedTrack != null) {
            displayTrack = loadedTrack;
        }

        if (displayTrack != null && !displayTrack.points.isEmpty()) {
            double distance = displayTrack.getTotalDistance();
            long duration = isTracking
                    ? trackingService.getTrackingDuration()
                    : displayTrack.getDuration();

            String stats = String.format(
                    "Track: %s\nPoints: %d\nDistance: %.2f m\nDuration: %s",
                    displayTrack.name,
                    displayTrack.points.size(),
                    distance / 1609.34,
                    formatDuration(duration)
            );

            tvTrackStats.setText(stats);

            // Show Save button when not recording (either imported or stopped track)
            if (isTracking) {
                btnSaveTrack.setVisibility(View.GONE);
            } else {
                btnSaveTrack.setVisibility(View.VISIBLE);
            }

            btnViewOnMap.setVisibility(View.VISIBLE);
        } else {
            tvTrackStats.setText("No track data");
            btnSaveTrack.setVisibility(View.GONE);
            btnViewOnMap.setVisibility(View.GONE);
        }
    }

    private String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }

    @Override
    public void onLocationUpdate(Location location) {
        if (gpxManager.isTracking()) {
            gpxManager.addTrackPoint(location);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateUI();
                }
            });
        }
    }

    @Override
    public void onLocationError(String error) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(GPXActivity.this, "GPS error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationHelper != null) {
            locationHelper.stopLocationUpdates();
        }

        // Stop periodic updates
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }

        // Unbind from service
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }
}