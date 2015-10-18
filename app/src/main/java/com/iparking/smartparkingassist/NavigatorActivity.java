package com.iparking.smartparkingassist;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import hu.bute.daai.amorg.vehicleict.platform.lib.Platform;

/**
 *	Base GUI class, displays everything
 */
public class NavigatorActivity extends FragmentActivity {

    private GoogleMap mMap; 	// Might be null if Google Play services APK is not available.
    private Communicator cm;	// Use this class for communication 
    private LotManager lm;		// This class handles operations with parking lots
    private LatLng loc;			// Current location
    private Platform platform;	// VehicleICT platform
    private Marker currentmarker;	// Current marker container
    private Marker selectmarker;	// Currently selected marker
    private Polyline currentRoute;	// Currently displayed route
    private ArrayList<Marker> lotmarkers;		// Non-selected parking lot markers	
    private ActionBarDrawerToggle mDrawerToggle;// Button to toggle action bar drawer
    private DrawerLayout mDrawerLayout;			// DrawerLayout
    private ListView mDrawerList;				// menu item list
    private LinearLayout searchcont;	// Search container element - to hide and show
    private ProgressDialog loader;		// Loader icon, if something is happening
    private float currentDistance = 0;	// Actual distance

	/**
	 * On activity create - initialize stuff
	 */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigator);	// Layout
        setUpMapIfNeeded();	// Google Maps
        if(mMap == null) {
                Log.e("smartparkingassist","NO MAP");	// Todo: Error handling
                return;
        }
        mMap.setMyLocationEnabled(true);	// Use GPS
        //initVehicleICT(); // VehicleICT module

        // Initialize classes
        cm = new Communicator(this);	// Communicator
        lm = new LotManager(cm,this);	// Manager

        initLayout();   // Initialize layout stuff
    }

    /**
     * DO everything on layout.
     */
    private void initLayout() {
        lotmarkers = new ArrayList<Marker>();	// Init markers

        // Free lot button
        Button push = (Button) findViewById(R.id.button);
        push.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("smartparking","Free parking lot pressed.");
                lm.handleFreeLot(loc.latitude, loc.longitude);
                mMap.addMarker(new MarkerOptions().position(loc));
            }
        });

		// Find parking lots on currently selected point
        Button stop = (Button) findViewById(R.id.button2);	// stop is a legacy name for vehicleICT
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("smartparking","Free parking find button pressed.");
                //Log.d("selectmarker value:", selectmarker);
                if(selectmarker == null) return;
                loader=ProgressDialog.show(NavigatorActivity.this,"Processing", "Getting available places...");
                lm.findFreeLot(loc, selectmarker.getPosition());	// Run the API requests
            }
        });
		
        mMap.setOnMyLocationChangeListener(myLocationChangeListener);	// Follow GPS
        mMap.setOnMapClickListener(mapClickListener);					// Allow selection points
        currentRoute = null;

        searchcont = (LinearLayout) findViewById(R.id.searchcont);	// Init but hide search field
        searchcont.setVisibility(View.INVISIBLE);

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);	// Init drawer - menu on the left
        mDrawerList = (ListView) findViewById(R.id.left_drawer);

        //mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

        buildMainMenu();	// set up

        // Set the list's click listener
        mDrawerList.setOnItemClickListener(new DrawerItemClickListener());

        getActionBar().setDisplayHomeAsUpEnabled(true);	// Enable actionbar buttons
        getActionBar().setHomeButtonEnabled(true);

        mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                R.drawable.ic_drawer,  /* nav drawer image to replace 'Up' caret */
                R.string.drawer_open,  /* "open drawer" description for accessibility */
                R.string.drawer_close  /* "close drawer" description for accessibility */
        ) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                getActionBar().setTitle("Smart Parking Assist");
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                getActionBar().setTitle("What to do?");
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }
        };

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        //selectItem(0);
    }

	/**
	 * If menu is called
	 */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);	// Display items from XML
        return super.onCreateOptionsMenu(menu);
    }

	/**
	 * When something is selected in the menu
	 */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        // Handle your other action bar items...
        toggleSearchBar();
        return super.onOptionsItemSelected(item);
    }

    /* Called whenever we call invalidateOptionsMenu() */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        // If the nav drawer is open, hide action items related to the content view
        boolean drawerOpen = mDrawerLayout.isDrawerOpen(mDrawerList);
        menu.findItem(R.id.action_websearch).setVisible(!drawerOpen);

        return super.onPrepareOptionsMenu(menu);
    }
	
	/**
	 * Display or hide the search box
	 */
    public void toggleSearchBar() {
        if(searchcont.getVisibility() == View.VISIBLE)
            searchcont.setVisibility(View.INVISIBLE);
        else
            searchcont.setVisibility(View.VISIBLE);
    }

	/**
	 * Class to handle actions in the menu
	 */
    private class DrawerItemClickListener implements ListView.OnItemClickListener {

        @Override
        public void onItemClick(AdapterView parent, View view, int position, long id) {

			// See order in the buildMainMenu function
		
            if(id == 0) {	// Search
                toggleSearchBar();
            }
            if(id == 1) {	// Authentication

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                String usertoken = prefs.getString("logintoken","NO_TOKEN");
                if(usertoken.equalsIgnoreCase("NO_TOKEN")) {
                    Intent i = new Intent(getApplicationContext(), LoginActivity.class);
                    startActivityForResult(i, 0);
                } else {
                    prefs.edit().putString("logintoken", "NO_TOKEN").apply();
                    Toast.makeText(getApplicationContext(), "Successfully logged out", Toast.LENGTH_LONG).show();
                    buildMainMenu();
                }
            }
            if(id == 2) {	// Settings
                Intent i = new Intent(getApplicationContext(), PreferencesActivity.class);
                startActivityForResult(i, 0);
            }
            if(id == 3) {	// Start navigation
                Intent i = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("google.navigation:q="+selectmarker.getPosition().latitude+","+selectmarker.getPosition().longitude));
                startActivity(i);
            }
            mDrawerLayout.closeDrawers();	// close on finnish
        }
    }

	/**
	 *	Change title when drawer is visible
	 */
    @Override
    public void setTitle(CharSequence title) {
        CharSequence mTitle = title;
        getActionBar().setTitle(mTitle);
    }

	/**
	 *	Refresh the main menu after login/logout
	 */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        buildMainMenu();	// Update the items!
    }

	/**
	 * Update menu items
	 */
    protected void buildMainMenu() {
		// TODO: Dynamic or XML based menu?
        String[] menuItems = new String[4];	// 4 elements so far
        menuItems[0] = "Find location...";
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String usertoken = prefs.getString("logintoken","NO_TOKEN");	// Get login status from settings
        if(usertoken.equalsIgnoreCase("NO_TOKEN")) {	// Login or logout based on status
            menuItems[1] = "Login";
        } else {
            menuItems[1] = "Logout";
        }
        menuItems[2] = "Settings";
        menuItems[3] = "Navigate!";

        // Set the adapter for the list view
        mDrawerList.setAdapter(new ArrayAdapter<String>(this,
                R.layout.drawer_list_item, menuItems));
    }

	/**
	 * From drawer sample...
	 */
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

	/**
	 * Config change handler
	 */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Pass any configuration change to the drawer toggls
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

	/**
	 * Google maps event listener
	 */
    private GoogleMap.OnMapClickListener mapClickListener = new GoogleMap.OnMapClickListener() {

        @Override
        public void onMapClick(LatLng latLng) {	// on click
			// Clean up...
            if(currentRoute != null)
                currentRoute.remove();
            if(selectmarker != null)
                selectmarker.remove();

            if(lotmarkers != null && lotmarkers.size() > 0) {
                for(int i = 0; i < lotmarkers.size(); i++) {
                    lotmarkers.get(i).remove();
                }
                lotmarkers.clear();
            }

			// Add new marker to map as selected
            selectmarker = mMap.addMarker(new MarkerOptions().position(latLng));
        }
    };

	/**
	 * On gps update refresh our position
	 */
    private GoogleMap.OnMyLocationChangeListener myLocationChangeListener = new GoogleMap.OnMyLocationChangeListener() {
        @Override
        public void onMyLocationChange(Location location) {
            loc = new LatLng(location.getLatitude(), location.getLongitude());
            //mMap.clear();
            if(currentmarker != null)
                currentmarker.remove();
            else if(mMap != null && selectmarker == null) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(loc, 16.0f));	// If nothing is selected zoom to our position
            }
            currentmarker = mMap.addMarker(new MarkerOptions().position(loc));	// Display a marker on our position

        }
    };
	
	/**
	 * Initialize the vehicle ICT platform
	 */
    protected void initVehicleICT() {
        /*
        Configuration configuration = new Configuration.Builder( android.os.Build.MODEL , 0 ).setLimit(30).build();
        Log.d("smartparking","Configured");
        platform = new Platform( getApplicationContext() , configuration ,
                new SampleListener()
                {
                    @Override
                    public void receiverSample ( Sample sample )
                    {
                        Log.d("smartparking", sample.getLatitude() + " " +sample.getLongitude());
                    }
                });
        platform.connect();
        Log.d("smartparking","Connected");
        */
    }

	/**
	 * Repoening the app
	 */
    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        //mMap.addMarker(new MarkerOptions().position(new LatLng(47, 20)).title("HU!"));
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                if(lotmarkers.indexOf(marker) != -1) {

                    return false;
                }
                return true;
            }
        });
    }

	/**
	 * Start sampling via the vehicleICT platform
	 */
    private void vehicleICTcmd() {

        platform.startSampling();
        Log.d("smartparking","Started");

    }

	/**
	 * Callback after retrieving the markers - display 'em
	 */
    public void showLotMarkers(ArrayList<LatLng> m) {
		// Clean up
        if(lotmarkers != null && lotmarkers.size() > 0) {
            for(int i = 0; i < lotmarkers.size(); i++) {
                lotmarkers.get(i).remove();
            }
            lotmarkers.clear();
        }
		// Display with nice opacity fade effect - far = invisible
        float opacity = 1;
        for(int i = 0; i < m.size(); i++) {

            if(i == 0) // First one should be green, as selected. - TODO: Make user able to select one
                lotmarkers.add(mMap.addMarker(new MarkerOptions().position(m.get(i)).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))));
            else
                lotmarkers.add(mMap.addMarker(new MarkerOptions().position(m.get(i)).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)).alpha(opacity)));
            if(opacity > 0.1)
                opacity -= 0.02;	// Decrease opacity
        }
    }

	/**
	 * Draw the planned route to the map
	 */
    public void displayRoute(List<List<HashMap<String, String>>> routes) {
        ArrayList points = null;
        PolylineOptions polyLineOptions = null;

        // traversing through routes
        for (int i = 0; i < routes.size(); i++) {
            points = new ArrayList();
            polyLineOptions = new PolylineOptions();
            List<HashMap<String, String>> path = routes.get(i);

            for (int j = 0; j < path.size(); j++) {
                HashMap point = path.get(j);

                double lat = Double.parseDouble((String) point.get("lat"));
                double lng = Double.parseDouble((String) point.get("lng"));
                LatLng position = new LatLng(lat, lng);

                points.add(position);
            }

            polyLineOptions.addAll(points);
            polyLineOptions.width(2);
            polyLineOptions.color(Color.BLUE);	// make it nice here
        }

        currentRoute = mMap.addPolyline(polyLineOptions);

        hideLoader();	// If it were loading, stop it
    }

	/**
	 * Hides the spinning loading icon
	 */
    public void hideLoader() {

        if(loader != null)
            loader.dismiss();
    }

}
