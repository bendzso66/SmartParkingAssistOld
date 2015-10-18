package com.iparking.smartparkingassist;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
/**
 * This handles parking lots - basicly the main functions
 * Created by Dave on 2014.11.17..
 */
public class LotManager {

    /**
     * Class to communicate with the server
     */
    private Communicator cm;

    private NavigatorActivity display;

    /**
     * Constructor
     * @param icm Communicator class
     */
    public LotManager(Communicator icm, NavigatorActivity par) {
        cm = icm;
        display = par;
    }

    /**
     * Send it to the server!
     * @param lat
     * @param lon
     * @return
     */
    public boolean handleFreeLot(double lat, double lon) {
        return cm.sendFreeLot(lat,lon);
    }

	/**
	 * Calculate the api string for google maps to plan a route between 2 points
	 */
    public static String getMapsApiDirectionsUrl(LatLng from, LatLng to) {
            /*
            String waypoints = "waypoints=optimize:true|"
                    + from.latitude + "," + from.longitude
                    + "|" + "|" + to.latitude + ","
                    + to.longitude;
            */
            String origin = "origin=" + from.latitude + "," + from.longitude;
            String destination = "destination=" + to.latitude + "," + to.longitude;
            String waypoints = "waypoints=optimize:true|"
                    + from.latitude + "," + from.longitude
                    + "|" + "|" + to.latitude + "," + to.longitude;
            String sensor = "sensor=false";

            String params = origin + "&" + destination + "&" + waypoints + "&" + sensor;
            Log.d("[LotManager]params:", params);
            String output = "json";
            String url = "https://maps.googleapis.com/maps/api/directions/"
                    + output + "?" + params;
            url = url.replaceAll("\\s+", "");
            //url = urlEncode(url);
            Log.d("[LotManager]URL:", url);

        return url;
    }

    static String urlEncode(String value) {
    try {
        return URLEncoder.encode(value, "UTF-8");
    } catch (UnsupportedEncodingException e) {
        return value;
    }
}
	/**
	 *	Find lots around the target place
	 */
    public boolean findFreeLot(LatLng current, LatLng target) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(display.getApplicationContext());	// Get the settigns
        float walk = prefs.getInt("maxWalkDist", 50)/(float)1000;	// restrict to the max walking distance
        cm.findParkingLot(target.latitude,target.longitude, current,1,walk);	// Run API requests
        return true; // Return now, it's running on a separate thread.
    }

	/**
	 * Connect through HTTP to the specified location, and get the response
	 */
    public static String readUrl(String mapsApiDirectionsUrl) throws Exception {
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try {
            Log.d("[LotManager]:",mapsApiDirectionsUrl);
            URL url = new URL(mapsApiDirectionsUrl);	
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.connect();
            iStream = urlConnection.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    iStream));
            StringBuffer sb = new StringBuffer();		
            String line = "";
            while ((line = br.readLine()) != null) {	// read response
                sb.append(line);
            }
            data = sb.toString();
            Log.d("[LotManager]:",data);
            br.close();
        } catch (Exception e) {
            Log.d("Exception while reading url", e.toString());
            throw e;
        } finally {
            if(iStream != null)
                iStream.close();
            urlConnection.disconnect();
        }
        return data;	// return the response
    }

	/** 
	 *	Parse the route from the given JSON object from google maps
	 *	@return array of directions
	 */
    public static List<List<HashMap<String, String>>> parse(JSONObject jObject) {
        List<List<HashMap<String, String>>> routes = new ArrayList<List<HashMap<String, String>>>();
        JSONArray jRoutes = null;
        JSONArray jLegs = null;
        JSONArray jSteps = null;
        try {
            jRoutes = jObject.getJSONArray("routes");
            /** Traversing all routes */
            for (int i = 0; i < jRoutes.length(); i++) {
                jLegs = ((JSONObject) jRoutes.get(i)).getJSONArray("legs");
                List<HashMap<String, String>> path = new ArrayList<HashMap<String, String>>();

                /** Traversing all legs */
                for (int j = 0; j < jLegs.length(); j++) {
                    jSteps = ((JSONObject) jLegs.get(j)).getJSONArray("steps");

                    /** Traversing all steps */
                    for (int k = 0; k < jSteps.length(); k++) {
                        String polyline = "";
                        polyline = (String) ((JSONObject) ((JSONObject) jSteps
                                .get(k)).get("polyline")).get("points");
                        List<LatLng> list = LotManager.decodePoly(polyline);

                        /** Traversing all points */
                        for (int l = 0; l < list.size(); l++) {
                            HashMap<String, String> hm = new HashMap<String, String>();
                            hm.put("lat",
                                    Double.toString(((LatLng) list.get(l)).latitude));
                            hm.put("lng",
                                    Double.toString(((LatLng) list.get(l)).longitude));
                            path.add(hm);
                        }
                    }
                    routes.add(path);
                }
            }

        } catch (JSONException e) {
            e.printStackTrace();
        } catch (Exception e) {
        }
        return routes;
    }
	
	/**
	 * stuff from google
	 */
    public static List<LatLng> decodePoly(String encoded) {

        List<LatLng> poly = new ArrayList<LatLng>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((((double) lat / 1E5)),
                    (((double) lng / 1E5)));
            poly.add(p);
        }
        return poly;
    }

	/**
	 * Search by place name - not functioning because of google apis.
	 * This request must run through webserver instead of direct query by android app
	 */
    public void findPlace(CharSequence text) {
        String url = "https://maps.googleapis.com/maps/api/geocode/json?address=" + text + "&key=AIzaSyAFE4r2EK71AJzD27pxt74rris4tRm-GWY";
        new AsyncTask<Object, Void, Void>() {
            private String url;	// target URL
            private LotManager parent;	// reference to the parent
            private NavigatorActivity display;	// reference to the display
            protected List<List<HashMap<String, String>>> routes;	// response should be placed here

			// Catch the parameters
            public AsyncTask<Object, Void, Void> setData(LotManager cparent, String curl, NavigatorActivity disp) {
                url = curl;
                parent = cparent;
                display = disp;
                return this;	// return myself for the .execute syntax
            }

			// Run API requests in the background
            @Override
            protected Void doInBackground(Object... param) {
                try {
					// query the URL
                    String ret = readUrl(url);
                    JSONObject jObject = null;
                    jObject = new JSONObject(ret);	// we got bad results, google not allows us the use this query
                    Log.d("smartparking", ret);

                } catch (Exception e) {
                    Log.d("smartparking", "BIGTRABEL: ");	// jep
                    display.runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(display, "API ERROR", Toast.LENGTH_LONG).show();

                        }
                    });
                    e.printStackTrace();
                    return null;
                }


                display.runOnUiThread(new Runnable() {
                    public void run() {	// we got nothing, but can be usefull later
                        //display.displayRoute(routes);
                        //parent.onShopReady(p);
                    }
                });
                return null;
            }
        }.setData(this, url, display).execute();	// pass params and run
    }
}
