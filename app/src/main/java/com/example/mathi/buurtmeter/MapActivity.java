package com.example.mathi.buurtmeter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapActivity extends AppCompatActivity {

    private TextView mTextMessage;
    private TextView searchField;
    private Button searchButton;

    private MapView mapView;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private RequestQueue mRequestQueue;
    private String urlSearch = "http://nominatim.openstreetmap.org/search?q=";
    private String urlZones = "http://datasets.antwerpen.be/v1/opendata/statistieken.json";
    private JSONObject zones;
    final ArrayList<OverlayItem> items = new ArrayList<OverlayItem>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= 23) {
            checkPermissions();
        }

        // https://github.com/osmdroid/osmdroid/wiki/How-to-use-the-osmdroid-library
        mapView = (MapView) findViewById(R.id.mapview);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(18);
        // default = grote markt
        mapView.getController().setCenter(new GeoPoint(51.221311, 4.399160));

        // http://code.tutsplus.com/tutorials/an-introduction-to-volley--cms-23800
        mRequestQueue = Volley.newRequestQueue(this);


        BottomNavigationView bottomNavigationView = findViewById(R.id.navigation);
        bottomNavigationView.setSelectedItemId(R.id.action_map);
        if (bottomNavigationView != null) {
            // Set action to perform when any menu-item is selected.
            bottomNavigationView.setOnNavigationItemSelectedListener(
                    new BottomNavigationView.OnNavigationItemSelectedListener() {
                        @Override
                        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                            selectFragment(item);
                            return false;
                        }
                    });
        }

    }

    protected void selectFragment(MenuItem item) {

        item.setChecked(true);

        switch (item.getItemId()) {
            case R.id.action_map:
                // Action to perform when Home Menu item is selected.

                break;
            case R.id.action_data:
                // Action to perform when Bag Menu item is selected.
                Intent intent = new Intent(MapActivity.this, DataActivity.class);
                startActivity(intent);
                break;

        }
    }




    // http://codetheory.in/android-ontouchevent-ontouchlistener-motionevent-to-detect-common-gestures/
 /*   @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int actionType = ev.getAction();
        switch(actionType) {
            case MotionEvent.ACTION_UP:
                // A Projection serves to translate between the coordinate system of
                // x/y on-screen pixel coordinates and that of latitude/longitude points
                // on the surface of the earth. You obtain a Projection from MapView.getProjection().
                // You should not hold on to this object for more than one draw, since the projection of the map could change.
                Projection proj = this.mapView.getProjection();
                GeoPoint loc = (GeoPoint)proj.fromPixels((int)ev.getX(), (int)ev.getY() - (searchField.getHeight() * 2));

                findZone(loc);
                //Log.d("edu.ap.be", )
        }
        return super.dispatchTouchEvent(ev);
    }

    private void hideSoftKeyBoard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

        if(imm.isAcceptingText()) { // verify if the soft keyboard is open
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }*/

    // http://alienryderflex.com/polygon/
    // The basic idea is to find all edges of the polygon that span the 'x' position of the point you're testing against.
    // Then you find how many of them intersect the vertical line that extends above your point. If an even number cross above the point,
    // then you're outside the polygon. If an odd number cross above, then you're inside.
    private boolean contains(GeoPoint location, ArrayList<GeoPoint> polyLoc) {
        if(location==null)
            return false;
        if(polyLoc.size() == 0)
            return false;

        GeoPoint lastPoint = polyLoc.get(polyLoc.size()-1);
        boolean isInside = false;
        double x = location.getLongitude();

        for(GeoPoint point: polyLoc) {
            double x1 = lastPoint.getLongitude();
            double x2 = point.getLongitude();
            double dx = x2 - x1;

            if (Math.abs(dx) > 180.0) {
                // we have, most likely, just jumped the dateline
                // (could do further validation to this effect if needed).
                // normalise the numbers.
                if (x > 0) {
                    while (x1 < 0)
                        x1 += 360;
                    while (x2 < 0)
                        x2 += 360;
                }
                else {
                    while (x1 > 0)
                        x1 -= 360;
                    while (x2 > 0)
                        x2 -= 360;
                }
                dx = x2 - x1;
            }

            if ((x1 <= x && x2 > x) || (x1 >= x && x2 < x)) {
                double grad = (point.getLatitude() - lastPoint.getLatitude()) / dx;
                double intersectAtLat = lastPoint.getLatitude() + ((x - x1) * grad);

                if (intersectAtLat > location.getLatitude())
                    isInside = !isInside;
            }
            lastPoint = point;
        }
        return isInside;
    }


    private void addMarker(GeoPoint g) {
        OverlayItem myLocationOverlayItem = new OverlayItem("Here", "Current Position", g);
        Drawable myCurrentLocationMarker = ResourcesCompat.getDrawable(getResources(), R.drawable.marker, null);
        myLocationOverlayItem.setMarker(myCurrentLocationMarker);

        items.add(myLocationOverlayItem);
        DefaultResourceProxyImpl resourceProxy = new DefaultResourceProxyImpl(getApplicationContext());

        ItemizedIconOverlay<OverlayItem> currentLocationOverlay = new ItemizedIconOverlay<OverlayItem>(items,
                new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>() {
                    public boolean onItemSingleTapUp(final int index, final OverlayItem item) {
                        return true;
                    }
                    public boolean onItemLongPress(final int index, final OverlayItem item) {
                        return true;
                    }
                }, resourceProxy);
        this.mapView.getOverlays().add(currentLocationOverlay);
        this.mapView.invalidate();
    }




    private class MyLocationListener implements LocationListener {
        @Override
        public void onLocationChanged(Location loc) {
            mapView.getController().setCenter(new GeoPoint(loc.getLatitude(), loc.getLongitude()));
        }

        @Override
        public void onProviderDisabled(String provider) {}
        @Override
        public void onProviderEnabled(String provider) {}
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}
    }

    // START PERMISSION CHECK
    final private int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 124;

    @SuppressLint("NewApi")
    private void checkPermissions() {
        List<String> permissions = new ArrayList<>();
        String message = "osmdroid permissions:";
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            message += "\nLocation to show user location.";
        }
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            message += "\nStorage access to store map tiles.";
        }
        if(!permissions.isEmpty()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            String[] params = permissions.toArray(new String[permissions.size()]);
            requestPermissions(params, REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
        } // else: We already have permissions, so handle as normal
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS: {
                Map<String, Integer> perms = new HashMap<>();
                // Initial
                perms.put(Manifest.permission.ACCESS_FINE_LOCATION, PackageManager.PERMISSION_GRANTED);
                perms.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED);
                // Fill with results
                for (int i = 0; i < permissions.length; i++)
                    perms.put(permissions[i], grantResults[i]);
                // Check for ACCESS_FINE_LOCATION and WRITE_EXTERNAL_STORAGE
                Boolean location = perms.get(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                Boolean storage = perms.get(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
                if(location && storage) {
                    // All Permissions Granted
                    Toast.makeText(MapActivity.this, "All permissions granted", Toast.LENGTH_SHORT).show();
                }
                else if (location) {
                    Toast.makeText(this, "Storage permission is required to store map tiles to reduce data usage and for offline usage.", Toast.LENGTH_LONG).show();
                }
                else if (storage) {
                    Toast.makeText(this, "Location permission is required to show the user's location on map.", Toast.LENGTH_LONG).show();
                }
                else { // !location && !storage case
                    // Permission Denied
                    Toast.makeText(MapActivity.this, "Storage permission is required to store map tiles to reduce data usage and for offline usage." +
                            "\nLocation permission is required to show the user's location on map.", Toast.LENGTH_SHORT).show();
                }
            }
            break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
    // END PERMISSION CHECK
}

