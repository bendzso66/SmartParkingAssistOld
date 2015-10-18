package com.iparking.smartparkingassist;

import android.os.Bundle;
import android.preference.PreferenceActivity;

/**
 * Created by Dave on 2014.12.10..
 */
public class PreferencesActivity extends PreferenceActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		// Display preferences from XML
        addPreferencesFromResource(R.xml.settings);

    }
}

