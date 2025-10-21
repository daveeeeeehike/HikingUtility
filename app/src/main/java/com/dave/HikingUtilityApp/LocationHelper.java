package com.dave.HikingUtilityApp;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import androidx.core.content.ContextCompat;

public class LocationHelper implements LocationListener {

    private final LocationManager locationManager;
    private final Context context;
    private final LocationUpdateListener listener;
    private Location currentLocation;
    private boolean isGPSEnabled = false;
    private boolean isNetworkEnabled = false;

    public interface LocationUpdateListener {
        void onLocationUpdate(Location location);
        void onLocationError(String error);
    }

    public LocationHelper(Context context, LocationUpdateListener listener) {
        this.context = context;
        this.listener = listener;
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    public void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            listener.onLocationError("Location permission not granted");
            return;
        }

        try {
            // Check which providers are available
            isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (!isGPSEnabled && !isNetworkEnabled) {
                listener.onLocationError("No location providers available");
                return;
            }

            // Start GPS updates if available
            if (isGPSEnabled) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        2000, // 2 seconds
                        1,    // 1 meter
                        this
                );

                // Get last known GPS location
                Location lastGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastGPS != null) {
                    currentLocation = lastGPS;
                    listener.onLocationUpdate(lastGPS);
                }
            }

            // Start Network updates as backup
            if (isNetworkEnabled) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        5000, // 5 seconds
                        10,   // 10 meters
                        this
                );

                // Get last known network location if no GPS
                if (currentLocation == null) {
                    Location lastNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    if (lastNetwork != null) {
                        currentLocation = lastNetwork;
                        listener.onLocationUpdate(lastNetwork);
                    }
                }
            }

        } catch (Exception e) {
            listener.onLocationError("Failed to start location updates: " + e.getMessage());
        }
    }

    public void stopLocationUpdates() {
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }

    public Location getCurrentLocation() {
        return currentLocation;
    }

    public boolean isLocationAvailable() {
        return currentLocation != null;
    }

    @Override
    public void onLocationChanged(Location location) {
        // Prefer GPS over network location
        if (currentLocation == null || isBetterLocation(location, currentLocation)) {
            currentLocation = location;
            listener.onLocationUpdate(location);
        }
    }

    @Override
    public void onProviderEnabled(String provider) {
        // Provider enabled
    }

    @Override
    public void onProviderDisabled(String provider) {
        // Provider disabled
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // Status changed
    }

    // Determine if new location is better than current
    private boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            return true;
        }

        // Check if new location is more accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(), currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (!isLessAccurate && isFromSameProvider) {
            return true;
        } else return !isSignificantlyLessAccurate && location.getProvider().equals(LocationManager.GPS_PROVIDER);
    }

    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }

    // Utility methods for distance and bearing calculations
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Radius of the earth in meters
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c; // Distance in meters
    }

    public static double calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double deltaLon = Math.toRadians(lon2 - lon1);
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);

        double y = Math.sin(deltaLon) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad) -
                Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(deltaLon);

        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360; // Normalize to 0-360
    }
}