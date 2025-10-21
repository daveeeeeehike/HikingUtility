package com.dave.HikingUtilityApp;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.maplibre.android.MapLibre;
import org.maplibre.android.offline.OfflineManager;
import org.maplibre.android.offline.OfflineRegion;

import java.util.ArrayList;
import java.util.List;

public class OfflineMapManager extends AppCompatActivity {

    private ListView listView;
    private OfflineManager offlineManager;
    private List<OfflineRegion> regions = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private List<String> regionNames = new ArrayList<>();

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapLibre.getInstance(this);
        setContentView(R.layout.activity_offline_map_manager);

        listView = findViewById(R.id.offline_list);
        offlineManager = OfflineManager.getInstance(this);

        loadRegions();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            OfflineRegion region = regions.get(position);
            new AlertDialog.Builder(this)
                    .setTitle(regionNames.get(position))
                    .setMessage("Choose an action")
                    .setPositiveButton("Delete", (d, w) -> deleteRegion(region))
                    .show();
        });
    }

    private void loadRegions() {
        offlineManager.listOfflineRegions(new OfflineManager.ListOfflineRegionsCallback() {
            @Override
            public void onList(OfflineRegion[] offlineRegions) {
                regions.clear();
                regionNames.clear();

                if (offlineRegions == null || offlineRegions.length == 0) {
                    Toast.makeText(OfflineMapManager.this, "No offline maps found", Toast.LENGTH_LONG).show();
                    return;
                }

                for (OfflineRegion region : offlineRegions) {
                    regions.add(region);
                    try {
                        String name = new String(region.getMetadata());
                        regionNames.add(name);
                    } catch (Exception e) {
                        regionNames.add("Unnamed Region");
                    }
                }

                runOnUiThread(() -> {
                    adapter = new ArrayAdapter<>(OfflineMapManager.this, android.R.layout.simple_list_item_1, regionNames);
                    listView.setAdapter(adapter);
                });
            }

            @Override
            public void onError(String error) {
                Toast.makeText(OfflineMapManager.this, "Error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void deleteRegion(OfflineRegion region) {
        region.delete(new OfflineRegion.OfflineRegionDeleteCallback() {
            @Override
            public void onDelete() {
                Toast.makeText(OfflineMapManager.this, "Deleted", Toast.LENGTH_SHORT).show();
                loadRegions();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(OfflineMapManager.this, "Delete error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }
}