package com.dave.HikingUtilityApp;

import android.content.Context;
import android.location.Location;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GPXManager {

    private final Context context;
    private final List<GPXPoint> currentTrack;
    private boolean isTracking = false;
    private String currentTrackName;

    public static class GPXPoint {
        public double latitude;
        public double longitude;
        public double elevation;
        public long timestamp;
        public String name;
        public String description;
        public double altitude;

        public GPXPoint(double lat, double lon) {
            this.latitude = lat;
            this.longitude = lon;
            this.timestamp = System.currentTimeMillis();
        }

        public GPXPoint(double lat, double lon, double elevation) {
            this(lat, lon);
            this.elevation = elevation;
        }

        public Location toLocation() {
            Location location = new Location("GPXPoint");
            location.setLatitude(latitude);
            location.setLongitude(longitude);
            location.setAltitude(elevation);
            location.setTime(timestamp);
            return location;
        }
        public GPXPoint(Location location) {
            this.latitude = location.getLatitude();
            this.longitude = location.getLongitude();
            this.timestamp = location.getTime();
            if (location.hasAltitude()) {
                this.elevation = location.getAltitude();
            }
        }
    }

    public static class GPXTrack {
        public String name;
        public List<GPXPoint> points;
        public String description;
        public Date createdDate;

        public GPXTrack(String name) {
            this.name = name;
            this.points = new ArrayList<>();
            this.createdDate = new Date();
        }

        public double getTotalDistance() {
            if (points.size() < 2) return 0;

            double total = 0;
            for (int i = 1; i < points.size(); i++) {
                GPXPoint p1 = points.get(i - 1);
                GPXPoint p2 = points.get(i);
                total += LocationHelper.calculateDistance(p1.latitude, p1.longitude, p2.latitude, p2.longitude);
            }
            return total;
        }

        public long getDuration() {
            if (points.size() < 2) return 0;
            return points.get(points.size() - 1).timestamp - points.get(0).timestamp;
        }
    }

    public GPXManager(Context context) {
        this.context = context;
        this.currentTrack = new ArrayList<>();
    }

    // GPX Import Methods
    public GPXTrack importGPXFromUri(InputStream inputStream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder content = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }
        reader.close();

        return parseGPXContent(content.toString());
    }

    public GPXTrack importGPXFromFile(String filePath) throws Exception {
        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        StringBuilder content = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }
        reader.close();

        return parseGPXContent(content.toString());
    }

    private GPXTrack parseGPXContent(String content) {
        GPXTrack track = new GPXTrack("Imported Track");

        // Simple regex parsing (works with most GPX files)
        Pattern trackNamePattern = Pattern.compile("<name>(.*?)</name>");
        Pattern trkptPattern = Pattern.compile("<trkpt lat=\"([^\"]+)\" lon=\"([^\"]+)\"");
        Pattern elePattern = Pattern.compile("<ele>([^<]+)</ele>");
        Pattern timePattern = Pattern.compile("<time>([^<]+)</time>");

        // Extract track name
        Matcher nameMatcher = trackNamePattern.matcher(content);
        if (nameMatcher.find()) {
            track.name = nameMatcher.group(1);
        }

        // Extract track points
        Matcher trkptMatcher = trkptPattern.matcher(content);
        String[] lines = content.split("\n");

        int lineIndex = 0;
        while (trkptMatcher.find()) {
            double lat = Double.parseDouble(trkptMatcher.group(1));
            double lon = Double.parseDouble(trkptMatcher.group(2));

            GPXPoint point = new GPXPoint(lat, lon);

            // Look for elevation and time in nearby lines
            int start = Math.max(0, lineIndex - 5);
            int end = Math.min(lines.length, lineIndex + 10);

            for (int i = start; i < end; i++) {
                // Extract elevation
                Matcher eleMatcher = elePattern.matcher(lines[i]);
                if (eleMatcher.find()) {
                    try {
                        point.elevation = Double.parseDouble(eleMatcher.group(1));
                    } catch (NumberFormatException e) {
                        // Ignore invalid elevation
                    }
                }

                // Extract time
                Matcher timeMatcher = timePattern.matcher(lines[i]);
                if (timeMatcher.find()) {
                    try {
                        // Simple timestamp parsing
                        point.timestamp = System.currentTimeMillis(); // Fallback to current time
                    } catch (Exception e) {
                        // Use current time if parsing fails
                    }
                }
            }

            track.points.add(point);
            lineIndex++;
        }

        // Also try to extract waypoints as single points


        return track;
    }

    // GPX Tracking Methods
    public void startTracking(String trackName) {
        this.currentTrackName = trackName != null ? trackName : "Track_" + System.currentTimeMillis();
        this.currentTrack.clear();
        this.isTracking = true;
    }

    public void stopTracking() {
        this.isTracking = false;
    }

    public void addTrackPoint(Location location) {
        if (isTracking) {
            currentTrack.add(new GPXPoint(location));
        }
    }

    public GPXTrack getCurrentTrack() {
        GPXTrack track = new GPXTrack(currentTrackName != null ? currentTrackName : "Current Track");
        track.points.addAll(currentTrack);
        return track;
    }

    public boolean isTracking() {
        return isTracking;
    }

    public int getCurrentTrackSize() {
        return currentTrack.size();
    }

    // GPX Export Methods
    public String exportTrackToGPX(GPXTrack track) {
        StringBuilder gpx = new StringBuilder();
        SimpleDateFormat iso8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);

        // GPX Header
        gpx.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        gpx.append("<gpx version=\"1.1\" creator=\"HikingUtilityApp\"\n");
        gpx.append(" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");

        // Metadata
        gpx.append(" <metadata>\n");
        gpx.append(" <name>").append(track.name).append("</name>\n");
        gpx.append(" <time>").append(iso8601.format(track.createdDate)).append("</time>\n");
        gpx.append(" </metadata>\n");

        // Track
        gpx.append(" <trk>\n");
        gpx.append(" <name>").append(track.name).append("</name>\n");
        if (track.description != null) {
            gpx.append(" <desc>").append(track.description).append("</desc>\n");
        }
        gpx.append(" <trkseg>\n");

        // Track Points
        for (GPXPoint point : track.points) {
            gpx.append(" <trkpt lat=\"").append(point.latitude)
                    .append("\" lon=\"").append(point.longitude).append("\">\n");

            if (point.elevation != 0) {
                gpx.append(" <ele>").append(point.elevation).append("</ele>\n");
            }

            gpx.append(" <time>").append(iso8601.format(new Date(point.timestamp))).append("</time>\n");
            gpx.append(" </trkpt>\n");
        }

        // Close tags
        gpx.append(" </trkseg>\n");
        gpx.append(" </trk>\n");
        gpx.append("</gpx>\n");

        return gpx.toString();
    }

    public File saveTrackToFile(GPXTrack track) throws Exception {
        String gpxContent = exportTrackToGPX(track);

        File appFolder = new File(context.getExternalFilesDir(null), "GPXTracks");
        if (!appFolder.exists())
            appFolder.mkdirs();

        String safeName = track.name.replaceAll("a-zA-Z0-9_-]", "_");
        File gpxFile = new File(appFolder, safeName + ".gpx");

        try (FileOutputStream fos = new FileOutputStream(gpxFile)) {

            fos.write(gpxContent.getBytes(StandardCharsets.UTF_8));
        }
        return gpxFile;

    }



    public List<GPXPoint> getTrackPoints() {
        return new ArrayList<>(currentTrack);
    }

}