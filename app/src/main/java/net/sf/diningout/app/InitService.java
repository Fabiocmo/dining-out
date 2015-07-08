/*
 * Copyright 2013-2015 pushbit <pushbit@gmail.com>
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
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;

import net.sf.diningout.provider.Contract.Contacts;
import net.sf.diningout.provider.Contract.Restaurants;

import java.util.List;

import static net.sf.sprockets.app.SprocketsApplication.cr;

/**
 * Inserts the restaurants, updates their details, downloads their photos, and follows the users.
 */
public class InitService extends IntentService {
    /**
     * Restaurants to insert and update. (ArrayList<ContentValues>)
     */
    public static final String EXTRA_RESTAURANTS = "intent.extra.RESTAURANTS";

    /**
     * Global IDs of users to follow. (long[])
     */
    public static final String EXTRA_FOLLOW_IDS = "intent.extra.FOLLOW_IDS";

    private static final String TAG = InitService.class.getSimpleName();

    public InitService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        List<ContentValues> restaurants = intent.getParcelableArrayListExtra(EXTRA_RESTAURANTS);
        long[] followIds = intent.getLongArrayExtra(EXTRA_FOLLOW_IDS);
        ContentResolver cr = cr();
        long[] restaurantIds = null;
        if (restaurants != null) { // insert the restaurants
            int size = restaurants.size();
            restaurantIds = new long[size];
            for (int i = 0; i < size; i++) {
                ContentValues vals = restaurants.get(i);
                vals.put(Restaurants.COLOR, Restaurants.defaultColor());
                restaurantIds[i] = ContentUris.parseId(cr.insert(Restaurants.CONTENT_URI, vals));
            }
        }
        if (followIds != null) { // follow the users
            ContentValues vals = new ContentValues(2);
            vals.put(Contacts.FOLLOWING, 1);
            vals.put(Contacts.DIRTY, 1);
            String sel = Contacts.GLOBAL_ID + " = ?";
            String[] args = new String[1];
            for (long followId : followIds) {
                args[0] = String.valueOf(followId);
                cr.update(Contacts.CONTENT_URI, vals, sel, args);
            }
        }
        if (restaurantIds != null) { // update restaurant details, insert reviews and photos
            for (long restaurantId : restaurantIds) {
                if (restaurantId > 0) {
                    RestaurantService.download(restaurantId);
                }
            }
        }
        if (followIds != null) { // get followee reviews
            for (long followId : followIds) {
                ReviewsService.download(followId);
            }
        }
    }
}
