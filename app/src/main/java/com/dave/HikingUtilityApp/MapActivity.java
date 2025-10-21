package com.dave.HikingUtilityApp;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.location.LocationComponentActivationOptions;
import org.maplibre.android.location.modes.RenderMode;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.LineString;
import org.maplibre.geojson.Point;

import java.util.ArrayList;
import java.util.List;

public class MapActivity extends AppCompatActivity implements LocationHelper.LocationUpdateListener {

    private MapView mapView;
    private MapLibreMap mapLibreMap;
    private LocationHelper locationHelper;
    private Button btnCenterLocation;

    private double currentLat = 40.7128; // default
    private double currentLon = -74.0060;

    private GPXTrackingService trackingService;
    private boolean serviceBound = false;

    private boolean autoCentreEnabled = false;

    // ================== LIFECYCLE ==================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Initialize MapLibre before inflating layout
        MapLibre.getInstance(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        initViews();
        initServices();
        setupClickListeners();

        // Map async setup
        mapView.getMapAsync(map -> {
            mapLibreMap = map;
            String styleURL = "https://api.maptiler.com/maps/topo-v2/style.json?key=vOks4vjE4FYDiVJljUlu";
            mapLibreMap.setStyle(new Style.Builder().fromUri(styleURL), style -> {
                initGPXTrackLayer();
                if (hasLocationPermission()) enableLocationComponent(style);
                centerCameraOnCurrentLocation();
                handleActiveGPXTrack();
            });
        });
    }

    private void initViews() {
        mapView = findViewById(R.id.mapView);
        btnCenterLocation = findViewById(R.id.btn_center_location);
    }

    private void setupClickListeners() {
        btnCenterLocation.setOnClickListener(v -> {
            autoCentreEnabled = true;
            centerCameraOnCurrentLocation();
            Toast.makeText(this, "Centered on current location", Toast.LENGTH_SHORT).show();
        });
    }

    private void initServices() {
        locationHelper = new LocationHelper(this, this);
        if (hasLocationPermission()) locationHelper.startLocationUpdates();

        bindToTrackingService();
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void enableLocationComponent(@NonNull Style style) {
        mapLibreMap.getLocationComponent().activateLocationComponent(
                LocationComponentActivationOptions.builder(this, style).build()
        );
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
        mapLibreMap.getLocationComponent().setLocationComponentEnabled(true);
        mapLibreMap.getLocationComponent().setRenderMode(RenderMode.COMPASS);
    }

    // ================== GPX TRACK ==================
    private void initGPXTrackLayer() {
        if (mapLibreMap == null) return;

        mapLibreMap.getStyle(style -> {
            if (style.getSource("live-gpx-track") == null) {
                GeoJsonSource gpxSource = new GeoJsonSource("live-gpx-track",
                        LineString.fromLngLats(new ArrayList<>()));
                style.addSource(gpxSource);

                LineLayer gpxLayer = new LineLayer("live-gpx-layer", "live-gpx-track")
                        .withProperties(
                                org.maplibre.android.style.layers.PropertyFactory.lineColor("#FF0000"),
                                org.maplibre.android.style.layers.PropertyFactory.lineWidth(4f)
                        );
                style.addLayer(gpxLayer);
            }

            if (style.getSource("imported-gpx-track") == null) {
                GeoJsonSource imported = new GeoJsonSource("imported-gpx-track",
                        LineString.fromLngLats(new ArrayList<>()));
                style.addSource(imported);

                LineLayer importedLayer = new LineLayer("imported-gpx-layer", "imported-gpx-track")
                        .withProperties(
                                org.maplibre.android.style.layers.PropertyFactory.lineColor("#0000FF"),
                                org.maplibre.android.style.layers.PropertyFactory.lineWidth(3f)
                        );
                style.addLayer(importedLayer);
            }
        });
    }

    private void updateGPXTrack(List<GPXManager.GPXPoint> points) {
        if (mapLibreMap == null || points == null) return;

        List<Point> linePoints = new ArrayList<>();
        for (GPXManager.GPXPoint p : points) {
            linePoints.add(Point.fromLngLat(p.longitude, p.latitude));
        }

        mapLibreMap.getStyle(style -> {
            GeoJsonSource gpxSource = style.getSourceAs("live-gpx-track");
            if (gpxSource != null) gpxSource.setGeoJson(LineString.fromLngLats(linePoints));
        });
    }

    private void updateImportedGPXTrack(List<GPXManager.GPXPoint> points) {
        if (mapLibreMap == null || points == null) return;

        List<Point> linePoints = new ArrayList<>();
        for (GPXManager.GPXPoint p : points) {
            linePoints.add(Point.fromLngLat(p.longitude, p.latitude));
        }

        mapLibreMap.getStyle(style -> {
            GeoJsonSource imported = style.getSourceAs("imported-gpx-track");
            if (imported != null) imported.setGeoJson(LineString.fromLngLats(linePoints));
        });
    }

    private void handleActiveGPXTrack() {
        SharedPreferences prefs = getSharedPreferences("GPX_PREFS", MODE_PRIVATE);
        String activeGPXPath = prefs.getString("ACTIVE_TRACK", null);

        if (activeGPXPath != null) {
            GPXManager gpxManager = new GPXManager(this);
            try {
                GPXManager.GPXTrack track = gpxManager.importGPXFromFile(activeGPXPath);
                if (!track.points.isEmpty()) updateImportedGPXTrack(track.points);
            } catch (Exception e) {
                Toast.makeText(this, "." + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void centerCameraOnCurrentLocation() {
        if (mapLibreMap != null) {
            mapLibreMap.setCameraPosition(new CameraPosition.Builder()
                    .target(new LatLng(currentLat, currentLon))
                    .zoom(14)
                    .build());
        }
    }

    @Override
    public void onLocationUpdate(Location location) {
        currentLat = location.getLatitude();
        currentLon = location.getLongitude();

        if (mapLibreMap != null) {
            if (serviceBound && trackingService != null && trackingService.isTracking()) {
                updateGPXTrack(trackingService.getCurrentTrackPoints());
            }

            if (autoCentreEnabled) {
                mapLibreMap.animateCamera(
                        org.maplibre.android.camera.CameraUpdateFactory.newLatLng(
                                new LatLng(currentLat, currentLon)
                        )
                );
            }
        }
    }

    @Override
    public void onLocationError(String error) {
        Toast.makeText(this, "GPS error: " + error, Toast.LENGTH_SHORT).show();
    }

    // ================== SERVICE BINDING ==================
    private void bindToTrackingService() {
        Intent intent = new Intent(this, GPXTrackingService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            GPXTrackingService.GPXTrackingBinder binder =
                    (GPXTrackingService.GPXTrackingBinder) service;
            trackingService = binder.getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            trackingService = null;
            serviceBound = false;
        }
    };

    // ================== LIFECYCLE ==================
    @Override
    protected void onStart() { super.onStart(); if (mapView != null) mapView.onStart(); }

    @Override
    protected void onResume() {
        super.onResume();
        if (locationHelper != null && hasLocationPermission()) locationHelper.startLocationUpdates();
        if (mapLibreMap != null && mapLibreMap.getStyle() != null) handleActiveGPXTrack();
        if (mapView != null) mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (locationHelper != null) locationHelper.stopLocationUpdates();
        if (mapView != null) mapView.onPause();
    }

    @Override
    protected void onStop() { super.onStop(); if (mapView != null) mapView.onStop(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationHelper != null) locationHelper.stopLocationUpdates();
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
        if (mapView != null) mapView.onDestroy();
    }

    @Override
    public void onLowMemory() { super.onLowMemory(); if (mapView != null) mapView.onLowMemory(); }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) mapView.onSaveInstanceState(outState);
    }
}