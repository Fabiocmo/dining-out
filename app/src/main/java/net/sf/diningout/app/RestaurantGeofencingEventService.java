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
import android.content.ContentUris;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofenceStatusCodes;
import com.google.android.gms.location.GeofencingEvent;

import net.sf.diningout.R;
import net.sf.diningout.provider.Contract.Restaurants;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.gms.location.Locations;
import net.sf.sprockets.preference.Prefs;

import java.util.ArrayList;
import java.util.List;

import static com.google.android.gms.location.LocationServices.GeofencingApi;
import static net.sf.diningout.data.Status.ACTIVE;
import static net.sf.diningout.preference.Keys.SHOW_NOTIFICATIONS;
import static net.sf.sprockets.app.SprocketsApplication.cr;
import static net.sf.sprockets.gms.analytics.Trackers.event;

/**
 * Posts notifications for restaurant geofence events.
 */
public class RestaurantGeofencingEventService extends IntentService {
    private static final String TAG = RestaurantGeofencingEventService.class.getSimpleName();

    public RestaurantGeofencingEventService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event.hasError()) {
            String error = GeofenceStatusCodes.getStatusCodeString(event.getErrorCode());
            Log.e(TAG, "geofencing event error: " + error);
            event("gms", "geofencing event error", error);
            return;
        }
        boolean showNotif = Prefs.getStringSet(this, SHOW_NOTIFICATIONS)
                .contains(getString(R.string.at_restaurant_notifications_value));
        List<Geofence> geofences = event.getTriggeringGeofences();
        int size = geofences.size();
        List<String> removeIds = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Geofence geofence = geofences.get(i);
            String requestId = geofence.getRequestId();
            if (showNotif) {
                long restaurantId = Long.parseLong(requestId);
                Uri uri = ContentUris.withAppendedId(Restaurants.CONTENT_URI, restaurantId);
                String[] proj = {"1"};
                String sel = Restaurants.GEOFENCE_NOTIFICATIONS + " = 1 AND "
                        + Restaurants.STATUS_ID + " = ?";
                String[] args = {String.valueOf(ACTIVE.id)};
                EasyCursor c = new EasyCursor(cr().query(uri, proj, sel, args, null));
                if (c.moveToFirst()) {
                    Notifications.geofence(this, event.getGeofenceTransition(), restaurantId);
                } else {
                    removeIds.add(requestId);
                }
                c.close();
            } else {
                removeIds.add(requestId);
            }
        }
        if (removeIds.size() > 0) {
            GoogleApiClient client = Locations.client(this);
            if (client != null) {
                PendingResult<Status> result = GeofencingApi.removeGeofences(client, removeIds);
                Status status = result.await();
                if (!status.isSuccess()) {
                    String msg = GeofenceStatusCodes.getStatusCodeString(status.getStatusCode());
                    Log.e(TAG, "remove geofences failed: " + msg);
                    event("gms", "remove geofences failed", msg);
                }
                client.disconnect();
            }
        }
    }
}
