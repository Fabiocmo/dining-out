/*
 * Copyright 2014 pushbit <pushbit@gmail.com>
 *
 * This file is part of Dining Out.
 *
 * Dining Out is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Dining Out is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Dining Out. If not,
 * see <http://www.gnu.org/licenses/>.
 */

package net.sf.diningout.app;

import android.app.IntentService;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import net.sf.diningout.provider.Contract.Restaurants;
import net.sf.sprockets.database.Cursors;
import net.sf.sprockets.util.Geos;

import java.io.IOException;
import java.util.List;

import static net.sf.sprockets.app.SprocketsApplication.context;
import static net.sf.sprockets.app.SprocketsApplication.cr;
import static net.sf.sprockets.gms.analytics.Trackers.exception;

/**
 * Gets latitude and longitude of a restaurant's address and downloads a Street View image. Callers
 * must include {@link #EXTRA_ID} in their Intent extras.
 */
public class RestaurantGeocodeService extends IntentService {
    /**
     * ID of the restaurant.
     */
    public static final String EXTRA_ID = "intent.extra.ID";
    private static final String TAG = RestaurantGeocodeService.class.getSimpleName();

    public RestaurantGeocodeService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        long id = intent.getLongExtra(EXTRA_ID, 0L);
        Uri uri = ContentUris.withAppendedId(Restaurants.CONTENT_URI, id);
        String[] proj = {Restaurants.ADDRESS};
        String address = Cursors.firstString(cr().query(uri, proj, null, null, null));
        if (!TextUtils.isEmpty(address)) {
            try {
                ContentValues vals = new ContentValues(3);
                if (geocode(address, vals)) {
                    cr().update(uri, vals, null, null);
                    RestaurantService.photo(id, vals);
                }
            } catch (IOException e) {
                Log.e(TAG, "geocoding address or downloading Street View image", e);
                exception(e);
            }
        }
    }

    /**
     * Put the {@link Restaurants#LATITUDE LATITUDE}, {@link Restaurants#LONGITUDE LONGITUDE}, and
     * {@link Restaurants#LONGITUDE_COS LONGITUDE_COS} in the values.
     *
     * @return true if coordinates were put in the values
     */
    public static boolean geocode(String address, ContentValues vals) throws IOException {
        List<Address> locs = new Geocoder(context()).getFromLocationName(address, 1);
        if (locs != null && locs.size() > 0) {
            Address loc = locs.get(0);
            if (loc.hasLatitude() && loc.hasLongitude()) {
                double lat = loc.getLatitude();
                vals.put(Restaurants.LATITUDE, lat);
                vals.put(Restaurants.LONGITUDE, loc.getLongitude());
                vals.put(Restaurants.LONGITUDE_COS, Geos.cos(lat));
                return true;
            }
        }
        return false;
    }
}
