package com.dave.HikingUtilityApp;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GPXTrackingService extends Service implements LocationListener {

    private static final String CHANNEL_ID = "GPX_TRACKING_CHANNEL";
    private static final int NOTIFICATION_ID = 1001;

    private LocationManager locationManager;
    private PowerManager.WakeLock wakeLock;
    private GPXManager gpxManager;
    private String trackName = "Background Track";
    private final List<GPXManager.GPXPoint> trackPoints = new ArrayList<>();

    private long startTime;
    private boolean isTracking = false;
    private boolean isPaused = false;

    // ===== Binder for Activities =====
    public class GPXTrackingBinder extends Binder {
        public GPXTrackingService getService() {
            return GPXTrackingService.this;
        }
    }

    private final IBinder binder = new GPXTrackingBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        gpxManager = new GPXManager(this);
        createNotificationChannel();
        acquireWakeLock();
        initLocationManager();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // ================== START/STOP LOGIC ==================
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if ("START_TRACKING".equals(action)) {
                trackName = intent.getStringExtra("track_name");
                if (trackName == null) trackName = "Background Track";
                startTracking();
            } else if ("STOP_TRACKING".equals(action)) {
                stopTracking();
            } else if ("PAUSE_TRACKING".equals(action)) {
                pauseTracking();
            } else if ("RESUME_TRACKING".equals(action)) {
                resumeTracking();
            }
        }
        return START_STICKY;
    }

    private void startTracking() {
        if (isTracking) return;
        if (!hasLocationPermission()) {
            stopSelf();
            return;
        }

        isTracking = true;
        isPaused = false;
        startTime = System.currentTimeMillis();
        trackPoints.clear();

        startForeground(NOTIFICATION_ID, createTrackingNotification());

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        5000,
                        2,
                        this
                );
            }
        } catch (SecurityException e) {
            stopSelf();
        }
    }

    private void pauseTracking() {
        if (!isTracking || isPaused) return;
        isPaused = true;
        if (locationManager != null) locationManager.removeUpdates(this);
        updateNotification();
    }

    private void resumeTracking() {
        if (!isTracking || !isPaused || !hasLocationPermission()) return;
        isPaused = false;
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    5000,
                    2,
                    this
            );
        }
        updateNotification();
    }

    private void stopTracking() {
        if (!isTracking) return;
        isTracking = false;
        if (locationManager != null) locationManager.removeUpdates(this);
        if (!trackPoints.isEmpty()) saveTrackToGPXManager();
        stopForeground(true);
        stopSelf();
    }

    // ================== LOCATION LISTENER ==================
    @Override
    public void onLocationChanged(Location location) {
        if (isTracking && !isPaused && location != null) {
            GPXManager.GPXPoint point = new GPXManager.GPXPoint(location);
            trackPoints.add(point);
            updateNotification();

            // Save periodically
            if (trackPoints.size() % 100 == 0) {
                saveTrackToGPXManager();
            }
        }
    }

    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { }
    @Override public void onStatusChanged(String provider, int status, android.os.Bundle extras) { }

    // ================== NOTIFICATION ==================
    private Notification createTrackingNotification() {
        Intent notificationIntent = new Intent(this, MapActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, GPXTrackingService.class);
        stopIntent.setAction("STOP_TRACKING");
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent toggleIntent = new Intent(this, GPXTrackingService.class);
        toggleIntent.setAction(isPaused ? "RESUME_TRACKING" : "PAUSE_TRACKING");
        PendingIntent togglePendingIntent = PendingIntent.getService(
                this, 2, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        double miles = calculateTotalDistance() * 0.000621371;
        long durationMs = System.currentTimeMillis() - startTime;
        String timeStr = formatDuration(durationMs);
        double avgSpeed = calculateAverageSpeedMph();
        double pace = calculatePaceMinPerMile();
        double elevation = calculateElevationGain();

        String contentText = String.format(
                Locale.US,
                "%.2f mi • %s\nAvg: %.1f mph • Pace: %.1f min/mi • ↑%.0f ft",
                miles, timeStr, avgSpeed, pace, elevation
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🔴 GPS Tracking Active")
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_media_play, isPaused ? "Resume" : "Pause", togglePendingIntent)
                .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSound(null)
                .setVibrate(new long[]{0L})
                .setDefaults(0)
                .build();
    }

    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, createTrackingNotification());
    }

    // ================== CALCULATIONS ==================
    public double calculateTotalDistance() {
        if (trackPoints.size() < 2) return 0;
        double total = 0;
        for (int i = 1; i < trackPoints.size(); i++) {
            GPXManager.GPXPoint p1 = trackPoints.get(i - 1);
            GPXManager.GPXPoint p2 = trackPoints.get(i);
            total += LocationHelper.calculateDistance(
                    p1.latitude, p1.longitude,
                    p2.latitude, p2.longitude
            );
        }
        return total;
    }

    public double calculateAverageSpeedMph() {
        double miles = calculateTotalDistance() * 0.000621371;
        double hours = (System.currentTimeMillis() - startTime) / 1000.0 / 3600.0;
        return hours > 0 ? miles / hours : 0;
    }

    public double calculatePaceMinPerMile() {
        double miles = calculateTotalDistance() * 0.000621371;
        double hours = (System.currentTimeMillis() - startTime) / 1000.0 / 3600.0;
        return miles > 0 ? (hours * 60) / miles : 0;
    }

    // ✅ NEW: "Current pace" (recent segment)
    public double calculateCurrentPaceMinPerMile() {
        int size = trackPoints.size();
        if (size < 3) return 0;

        int N = Math.min(size, 5);
        GPXManager.GPXPoint start = trackPoints.get(size - N);
        GPXManager.GPXPoint end = trackPoints.get(size - 1);

        long timeDiff = end.timestamp - start.timestamp;
        if (timeDiff <= 0) return 0;

        float dist = 0;
        for (int i = size - N; i < size - 1; i++) {
            Location l1 = trackPoints.get(i).toLocation();
            Location l2 = trackPoints.get(i + 1).toLocation();
            dist += l1.distanceTo(l2);
        }

        double miles = dist * 0.000621371;
        double minutes = timeDiff / 1000.0 / 60.0;
        return miles > 0 ? minutes / miles : 0;
    }

    public double calculateElevationGain() {
        if (trackPoints.size() < 2) return 0;
        double gain = 0;
        for (int i = 1; i < trackPoints.size(); i++) {
            double diff = trackPoints.get(i).altitude - trackPoints.get(i - 1).altitude;
            if (diff > 0) gain += diff;
        }
        return gain;
    }

    public long getTrackingDuration() {
        return isTracking ? System.currentTimeMillis() - startTime : 0;
    }

    // ================== HELPERS ==================
    private void saveTrackToGPXManager() {
        if (trackPoints.isEmpty()) return;

        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yy-HH-mm-ss", Locale.US);
        String fileName = sdf.format(new java.util.Date());

        GPXManager.GPXTrack track = new GPXManager.GPXTrack(fileName);
        track.points.addAll(trackPoints);

        try {
            gpxManager.saveTrackToFile(track);
        } catch (Exception ignored) { }
    }

    private String formatDuration(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        return hours > 0
                ? String.format(Locale.US, "%dh %dm", hours, minutes % 60)
                : String.format(Locale.US, "%dm", minutes);
    }

    private void initLocationManager() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "HikingUtilityApp:GPXTracking"
        );
        wakeLock.acquire();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "GPS Tracking",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background GPS tracking for hiking");
            channel.setSound(null, null);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    // ================== CLEANUP ==================
    @Override
    public void onDestroy() {
        super.onDestroy();

        if (locationManager != null) locationManager.removeUpdates(this);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (!trackPoints.isEmpty()) saveTrackToGPXManager();
    }

    // ================== PUBLIC ACCESSORS ==================
    public boolean isTracking() {
        return isTracking && !isPaused;
    }

    public List<GPXManager.GPXPoint> getCurrentTrackPoints() {
        return new ArrayList<>(trackPoints);
    }

    public String getTrackName() {
        return trackName;
    }
}