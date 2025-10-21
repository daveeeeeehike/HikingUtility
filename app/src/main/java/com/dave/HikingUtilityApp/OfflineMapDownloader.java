package com.dave.HikingUtilityApp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.PolylineOptions;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.offline.OfflineManager;
import org.maplibre.android.offline.OfflineRegion;
import org.maplibre.android.offline.OfflineRegionDefinition;
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class OfflineMapDownloader extends AppCompatActivity {

    private MapView mapView;
    private MapLibreMap mapLibreMap;
    private Button btnDownload;
    private OfflineManager offlineManager;

    private static final String MAPTILER_KEY = "vOks4vjE4FYDiVJljUlu";
    private final List<OfflineRegion> regions = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapLibre.getInstance(this);
        setContentView(R.layout.activity_offline_map_downloader);

        mapView = findViewById(R.id.mapView);
        btnDownload = findViewById(R.id.btn_download);
        mapView.onCreate(savedInstanceState);

        offlineManager = OfflineManager.getInstance(this);

        mapView.getMapAsync(map -> {
            mapLibreMap = map;
            mapLibreMap.setStyle(
                    new Style.Builder().fromUri("https://api.maptiler.com/maps/topo-v2/style.json?key=" + MAPTILER_KEY),
                    style -> {
                        Toast.makeText(this, "Move/zoom to select area, then tap Download", Toast.LENGTH_LONG).show();
                        drawExistingOfflineRegions();
                    }
            );
        });

        btnDownload.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Name your offline map");
            final EditText input = new EditText(this);
            input.setHint("e.g. Snowdon Trail");
            builder.setView(input);
            builder.setPositiveButton("Download", (dialog, which) -> {
                String mapName = input.getText().toString().trim();
                if (mapName.isEmpty()) mapName = "Map " + System.currentTimeMillis();
                downloadVisibleRegion(mapName);
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        });
    }

    private void downloadVisibleRegion(String mapName) {
        if (mapLibreMap == null || mapLibreMap.getStyle() == null) return;

        LatLngBounds bounds = mapLibreMap.getProjection().getVisibleRegion().latLngBounds;
        double minZoom = Math.max(mapLibreMap.getCameraPosition().zoom - 1, 3);
        double maxZoom = mapLibreMap.getCameraPosition().zoom + 2;
        float pixelRatio = this.getResources().getDisplayMetrics().density;

        OfflineRegionDefinition definition = new OfflineTilePyramidRegionDefinition(
                mapLibreMap.getStyle().getUri(),
                bounds,
                minZoom,
                maxZoom,
                pixelRatio
        );

        byte[] metadata = ("{\"regionName\":\"" + mapName + "\"}").getBytes(StandardCharsets.UTF_8);

        offlineManager.createOfflineRegion(definition, metadata, new OfflineManager.CreateOfflineRegionCallback() {
            @Override
            public void onCreate(OfflineRegion region) {
                region.setDownloadState(OfflineRegion.STATE_ACTIVE);
                regions.add(region);

                region.setObserver(new OfflineRegion.OfflineRegionObserver() {
                    @Override
                    public void onStatusChanged(org.maplibre.android.offline.OfflineRegionStatus status) {
                        if (status.isComplete()) {
                            Toast.makeText(OfflineMapDownloader.this, "✅ Download complete: " + mapName, Toast.LENGTH_LONG).show();
                            drawExistingOfflineRegions();
                        }
                    }

                    @Override
                    public void onError(org.maplibre.android.offline.OfflineRegionError error) {
                        Toast.makeText(OfflineMapDownloader.this, "Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void mapboxTileCountLimitExceeded(long limit) {
                        Toast.makeText(OfflineMapDownloader.this, "Tile limit exceeded!", Toast.LENGTH_LONG).show();
                    }
                });

                Toast.makeText(OfflineMapDownloader.this, "Downloading " + mapName + "...", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(OfflineMapDownloader.this, "Error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void drawExistingOfflineRegions() {
        offlineManager.listOfflineRegions(new OfflineManager.ListOfflineRegionsCallback() {
            @Override
            public void onList(OfflineRegion[] offlineRegions) {
                if (offlineRegions == null || mapLibreMap == null) return;

                mapLibreMap.clear();
                regions.clear();

                for (OfflineRegion region : offlineRegions) {
                    regions.add(region);
                    OfflineRegionDefinition def = region.getDefinition();
                    if (def instanceof OfflineTilePyramidRegionDefinition) {
                        LatLngBounds bounds = ((OfflineTilePyramidRegionDefinition) def).getBounds();
                        drawOutline(bounds);
                    }
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(OfflineMapDownloader.this, "Error listing offline regions: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void drawOutline(LatLngBounds bounds) {
        List<LatLng> points = new ArrayList<>();
        points.add(bounds.getSouthWest());
        points.add(new LatLng(bounds.getSouthWest().getLatitude(), bounds.getNorthEast().getLongitude()));
        points.add(bounds.getNorthEast());
        points.add(new LatLng(bounds.getNorthEast().getLatitude(), bounds.getSouthWest().getLongitude()));
        points.add(bounds.getSouthWest()); // close loop

        mapLibreMap.addPolyline(new PolylineOptions()
                .addAll(points)
                .color(0xFF0000FF) // Blue line
                .width(3f));
    }

    // MapView lifecycle
    @Override protected void onStart() { super.onStart(); mapView.onStart(); }
    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onPause() { super.onPause(); mapView.onPause(); }
    @Override protected void onStop() { super.onStop(); mapView.onStop(); }
    @Override protected void onDestroy() { super.onDestroy(); mapView.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
}