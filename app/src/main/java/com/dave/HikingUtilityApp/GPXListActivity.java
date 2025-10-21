package com.dave.HikingUtilityApp;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;


import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class GPXListActivity extends AppCompatActivity {

    private ListView listGPXTracks;
    private Button btnImportGPX, btnImportOSM;
    private TextView tvTrackCount;
    private List<GPXTrackItem> gpxTracks;
    private GPXListAdapter adapter;
    private static final int REQUEST_CODE_PICK_FILE = 1001;
    private File appGPXFolder;

    public static class GPXTrackItem {
        public File gpxFile;
        public String name, details;
        public GPXManager.GPXTrack track;

        public GPXTrackItem(File file) {
            this.gpxFile = file;
            this.name = file.getName().replace(".gpx", "");
            this.details = String.format("%d KB", file.length() / 1024);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gpx_list);

        initAppFolder();
        initViews();
        setupClickListeners();
        loadGPXFiles();

        // Handle GPX from other apps
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_SEND.equals(action)) {
            Uri uri = intent.getData();
            if (uri == null && Intent.ACTION_SEND.equals(action)) {
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            }

            if (uri != null && type != null && type.equals("application/gpx+xml")) {
                importGPXFile(uri);
            }
        }
    }

    private void initAppFolder() {
        appGPXFolder = new File(getExternalFilesDir(null), "GPXTracks");
        if (!appGPXFolder.exists()) appGPXFolder.mkdirs();
    }

    private void initViews() {
        listGPXTracks = findViewById(R.id.list_gpx_tracks);
        btnImportGPX = findViewById(R.id.btn_import_gpx);
        btnImportOSM = findViewById(R.id.btn_import_osm);
        tvTrackCount = findViewById(R.id.tv_track_count);
        gpxTracks = new ArrayList<>();
        adapter = new GPXListAdapter();
        listGPXTracks.setAdapter(adapter);
    }

    private void setupClickListeners() {
        btnImportGPX.setOnClickListener(v -> openFilePicker());
        btnImportOSM.setOnClickListener(v -> showOSMImportDialog());
    }

    private void showOSMImportDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Import OSM Trace");
        EditText input = new EditText(this);
        input.setHint("Enter OSM Trace ID");
        builder.setView(input);
        builder.setPositiveButton("Import", (dialog, which) -> {
            String traceId = input.getText().toString().trim();
            if (!traceId.isEmpty()) downloadOSMTrace(traceId);
            else Toast.makeText(this, "Trace ID cannot be empty", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void downloadOSMTrace(String traceId) {
        new Thread(() -> {
            try {
                GPXManager gpxManager = new GPXManager(this);
                GPXManager.GPXTrack track = gpxManager.importGPXFromUri(
                        new java.net.URL("https://www.openstreetmap.org/traces/" + traceId + "/data").openStream()
                );
                gpxManager.saveTrackToFile(track);
                runOnUiThread(this::loadGPXFiles);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void loadGPXFiles() {
        gpxTracks.clear();
        File[] files = appGPXFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".gpx"));
        if (files != null) {
            Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            List<String> seenNames = new ArrayList<>();
            for (File file : files) {
                String trackName = file.getName().replace(".gpx", "");
                if (!seenNames.contains(trackName)) {
                    gpxTracks.add(new GPXTrackItem(file));
                    seenNames.add(trackName);
                }
            }
        }
        adapter.notifyDataSetChanged();
        tvTrackCount.setText(String.format(Locale.US, "%d GPX tracks found", gpxTracks.size()));
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Select GPX File"), REQUEST_CODE_PICK_FILE);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "No file picker available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == RESULT_OK && data != null && data.getData() != null)
            importGPXFile(data.getData());
    }

    private void importGPXFile(Uri uri) {
        try {
            GPXManager gpxManager = new GPXManager(this);
            GPXManager.GPXTrack track = gpxManager.importGPXFromUri(getContentResolver().openInputStream(uri));
            if (track.points.isEmpty()) {
                Toast.makeText(this, "No points found", Toast.LENGTH_LONG).show();
                return;
            }
            gpxManager.saveTrackToFile(track); // no duplicates
            loadGPXFiles();
        } catch (Exception e) {
            Toast.makeText(this, "Error importing: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void deleteTrack(GPXTrackItem item) {
        if (item.gpxFile.delete()) {
            gpxTracks.remove(item);
            adapter.notifyDataSetChanged();
        } else Toast.makeText(this, "Failed to delete track", Toast.LENGTH_SHORT).show();
    }

    private void shareTrack(GPXTrackItem item) {
        try {
            Uri fileUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", item.gpxFile);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/gpx+xml");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "GPX Track: " + item.name);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share GPX Track"));
        } catch (Exception e) {
            Toast.makeText(this, "Failed to share: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setActiveTrack(GPXTrackItem item, boolean showOnMap) {
        SharedPreferences prefs = getSharedPreferences("GPX_PREFS", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if (showOnMap) editor.putString("ACTIVE_TRACK", item.gpxFile.getAbsolutePath());
        else editor.remove("ACTIVE_TRACK");
        editor.apply();
        Toast.makeText(this, showOnMap ? "Track showing on map" : "Track removed from map", Toast.LENGTH_SHORT).show();
    }

    private class GPXListAdapter extends BaseAdapter {
        @Override public int getCount() { return gpxTracks.size(); }
        @Override public Object getItem(int pos) { return gpxTracks.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            if (convertView == null)
                convertView = LayoutInflater.from(GPXListActivity.this)
                        .inflate(R.layout.item_gpx_track, parent, false);

            GPXTrackItem item = gpxTracks.get(pos);

            TextView tvName = convertView.findViewById(R.id.tv_track_name);
            Switch switchShow = convertView.findViewById(R.id.switch_show_on_map);
            Button btnShare = convertView.findViewById(R.id.btn_share_track);
            Button btnDelete = convertView.findViewById(R.id.btn_delete_track);

            tvName.setText(item.name);

            // Set switch state from preferences
            SharedPreferences prefs = getSharedPreferences("GPX_PREFS", MODE_PRIVATE);
            String activePath = prefs.getString("ACTIVE_TRACK", "");
            switchShow.setChecked(activePath.equals(item.gpxFile.getAbsolutePath()));
            switchShow.setVisibility(View.VISIBLE);

            switchShow.setOnCheckedChangeListener((buttonView, isChecked) -> setActiveTrack(item, isChecked));
            btnShare.setOnClickListener(v -> shareTrack(item));
            btnDelete.setOnClickListener(v -> deleteTrack(item));

            return convertView;
        }
    }
}