package com.iparking.smartparkingassist;

import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Use this class to send and receive data from the server
 * Created by Dave on 2014.11.17..
 */
public class Communicator {

    String apiurl = "http://iparking.hit.bme.hu:4567/";	// Base server URL
    NavigatorActivity display;							// Reference to the GUI
    LoginActivity ldisplay;								// Reference to the Login GUI

	/**
	 * Constructor from base activity
	 */
    public Communicator(NavigatorActivity idisplay) {
        display = idisplay;
    }

	/**
	 * Constructor from LoginActivity
	 */
    public Communicator(LoginActivity idisplay) {
        ldisplay = idisplay;
    }

    /**
     * Send free lot signal - when we see a free lot
     * @param lat - gps latitude
     * @param lon - gps longitude
     * @return true on success
     */
    public boolean sendFreeLot(double lat, double lon) {
		// Use asynctask to handle this in background - dont freeze the gui
        new AsyncTask<Object, Void, Void>() {
            private double lat,lon;
            private String url;

			// Catch the parameters
            public AsyncTask<Object, Void, Void> setData(String iurl, double ilat, double ilon) {
                url = iurl;
                lat = ilat;
                lon = ilon;
                return this;	// Return myself, for the simple syntax (.execute)
            }

			// Main task
            @Override
            protected Void doInBackground(Object... param) {
                String ret = null;
                try {
                    // Log.d("smartparkingassist",url);
                    ret = LotManager.readUrl(url+"sendFreeLot?id=1&lat="+lat+"&lon="+lon+"&avail=free&id=1");
                    // Log.d("smartparkingassist", ret);
                } catch (Exception e) {	// Handle exceptions, eg network error
                    display.runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(display, "API ERROR", Toast.LENGTH_LONG).show();
                            display.hideLoader();
                        }
                    });
                    e.printStackTrace();	// Logcat

                }

				// After succeed, display toast on the GUI thread (because of security)
                display.runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(display, "Free lot sent", Toast.LENGTH_LONG).show();
                }
                });
                return null;
            }
        }.setData(apiurl, lat, lon).execute();	// Pass the parameters then run
        return true;
    }

    public boolean findParkingLot(double lat, double lon, LatLng from, int id, float walkdist) {
        //String url = apiurl+"findFreeLot?lat="+lat+"&lon="+lon+"&id="+id+"&rad="+walkdist;
        String url = apiurl+"findFreeLot?lat="+lat+"&lon="+lon+"&rad="+walkdist;
        new AsyncTask<Object, Void, Void>() {
            private String url;
            private Communicator parent;
            private NavigatorActivity display;
            protected List<List<HashMap<String, String>>> routes;
            LatLng current;
            ArrayList<LatLng> coords;

            public AsyncTask<Object, Void, Void> setData(Communicator cparent, String curl, NavigatorActivity disp, LatLng icurrent) {
                url = curl;
                parent = cparent;
                display = disp;
                current = icurrent;
                return this;

            }

            @Override
            protected Void doInBackground(Object... param) {
                coords = new ArrayList<LatLng>();
                String ret = null;
                try {
                    Log.d("[Communicator]parameterezett url findFreelot-nal: ",url);
                    ret = LotManager.readUrl(url);
                    Log.d("[Communicator]findFreelotra servertol kapott valasz: ",ret);
                } catch (Exception e) {
                    e.printStackTrace();
                    display.runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(display, "API ERROR", Toast.LENGTH_LONG).show();

                        }
                    });
                }
                JSONArray jArray = null;
                if(ret == null || ret.equalsIgnoreCase("[]") || ret.equalsIgnoreCase("UNSUCCESSFULL_REQUEST") ) {
                    Log.d("mylog","Empty array, returning");
                    display.runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(display, "No Places Available", Toast.LENGTH_LONG).show();
                            display.hideLoader();
                        }
                    });
                    return null;
                }
                try {
                    jArray = new JSONArray(ret);
                } catch (JSONException e) {
                    display.runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(display, "Unexpected response from the server", Toast.LENGTH_LONG).show();
                            display.hideLoader();
                        }
                    });
                    e.printStackTrace();
                    return null;
                }
                for (int i = 0; i < jArray.length(); i++) {
                    try {
                        JSONObject curr = jArray.getJSONObject(i);
                        Log.d("smartparkingassist",curr.getDouble("latitude")+" ");
                        coords.add(new LatLng(curr.getDouble("latitude"), curr.getDouble("longitude")));
                    } catch (JSONException e) {
                        display.runOnUiThread(new Runnable() {
                            public void run() {
                                Toast.makeText(display, "Unexpected response from the server", Toast.LENGTH_LONG).show();
                                display.hideLoader();
                            }
                        });
                        e.printStackTrace();
                        return null;
                    }
                }
                routes = null;
                if(coords.size() < 1) {
                    display.runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(display, "No route found", Toast.LENGTH_LONG).show();
                            display.hideLoader();
                        }
                    });
                    return null;
                }

                routes = planRoute(current, coords.get(0));

                display.runOnUiThread(new Runnable() {
                    public void run() {
                        display.displayRoute(routes);
                        display.showLotMarkers(coords);
                        //display.displayRoute(routes);
                        //parent.onShopReady(p);
                    }
                });
                return null;
            }
        }.setData(this, url, display, from).execute();
        return true; //new LatLng(lat, lon);
    }

	/**
	 * Plan a route between 2 points with google maps api
	 * @param current - start location
	 * @param to - end location
	 */
    public List<List<HashMap<String, String>>> planRoute(LatLng current, LatLng to) {
        String url = LotManager.getMapsApiDirectionsUrl(current, to);	// Get the proper string

        String ret = null;
        try {
            ret = LotManager.readUrl(url);		// Make the API request
        } catch (Exception e) {					// Handle the exceptions
            e.printStackTrace();
            display.runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(display, "API ERROR", Toast.LENGTH_LONG).show();
                    display.hideLoader();	// its over...
                }
            });
        }
        JSONObject jObject = null;	// store the return value
        try {
            jObject = new JSONObject(ret);	// decode as JSON
        } catch (JSONException e) {
            e.printStackTrace();	// oops
        }
        return LotManager.parse(jObject);	// Pass it to the parser then return the result

    }

	/**
	 * Run the login request
	 */
    public String loginUser(String username, String password) throws Exception {
        String url = this.apiurl + "login?mail="+username+"&pass="+password;
        String ret = LotManager.readUrl(url);
        return ret;
    }

	/**
	 * Register if not registered yet
	 */
    public String registerUser(String username, String password, float range) throws Exception {
        String url = this.apiurl + "registration?mail="+username+"&pass="+password+"&rad="+range;
        String ret = LotManager.readUrl(url);
        return ret;
    }
}
