/*
 * Copyright 2015 pushbit <pushbit@gmail.com>
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
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.util.Log;

import net.sf.diningout.app.RestaurantService.Result;
import net.sf.diningout.provider.Contract.Restaurants;
import net.sf.sprockets.database.EasyCursor;

import java.io.IOException;

import static android.provider.BaseColumns._ID;
import static net.sf.diningout.data.Status.ACTIVE;
import static net.sf.sprockets.app.SprocketsApplication.cr;
import static net.sf.sprockets.gms.analytics.Trackers.exception;

/**
 * Downloads and updates the details of all Google Places.
 */
public class RestaurantsRefreshService extends IntentService {
    private static final String TAG = RestaurantsRefreshService.class.getSimpleName();

    public RestaurantsRefreshService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String[] proj = {_ID, Restaurants.PLACE_ID};
        String sel = Restaurants.PLACE_ID + " IS NOT NULL AND "
                + Restaurants.PLACE_ID + " NOT LIKE 'NOT_FOUND_%' AND "
                + Restaurants.STATUS_ID + " = ?";
        String[] args = {String.valueOf(ACTIVE.id)};
        Cursor c = cr().query(Restaurants.CONTENT_URI, proj, sel, args, null);
        try { // can try with resources when min API level 19
            refresh(new EasyCursor(c));
        } catch (IOException e) {
            Log.e(TAG, "refreshing restaurants", e);
            exception(e);
        } finally {
            c.close();
        }
    }

    /**
     * Download and update the details of the restaurants in the cursor.  Each must have
     * {@link BaseColumns#_ID _ID} and {@link Restaurants#PLACE_ID PLACE_ID}.
     *
     * @return null if the cursor is empty
     */
    public static Result[] refresh(EasyCursor c) throws IOException {
        Result[] results = null;
        int count = c.getCount();
        if (count > 0) {
            ContentValues vals = new ContentValues(14);
            results = new Result[count];
            while (c.moveToNext()) {
                vals.put(Restaurants.PLACE_ID, c.getString(Restaurants.PLACE_ID));
                results[c.getPosition()] = RestaurantService.details(c.getLong(_ID), vals);
            }
        }
        return results;
    }
}
