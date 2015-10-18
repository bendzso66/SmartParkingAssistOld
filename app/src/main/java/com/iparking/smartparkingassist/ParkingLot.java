package com.iparking.smartparkingassist;

/**
 * General class for storing parking lots
 * Created by Dave on 2014.11.20..
 */
public class ParkingLot {
    double lat;
    double lon;
    boolean isFree;

    public ParkingLot(double ilat, double ilon) {
        isFree = true;
        lat = ilat;
        lon = ilon;
    }

}
