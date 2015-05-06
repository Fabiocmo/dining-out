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
 * Inserts the restaurants, updates their details, downloads their photos, and follows the contacts.
 */
public class InitService extends IntentService {
    /**
     * ContentValues ArrayList of restaurants to insert and update.
     */
    public static final String EXTRA_RESTAURANTS = "intent.extra.RESTAURANTS";

    /**
     * long array of contacts to follow.
     */
    public static final String EXTRA_CONTACT_IDS = "intent.extra.CONTACT_IDS";
    private static final String TAG = InitService.class.getSimpleName();

    public InitService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        List<ContentValues> restaurants = intent.getParcelableArrayListExtra(EXTRA_RESTAURANTS);
        long[] contactIds = intent.getLongArrayExtra(EXTRA_CONTACT_IDS);
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
        if (contactIds != null) { // follow the contacts
            ContentValues vals = new ContentValues(2);
            vals.put(Contacts.FOLLOWING, 1);
            vals.put(Contacts.DIRTY, 1);
            for (long id : contactIds) {
                cr.update(ContentUris.withAppendedId(Contacts.CONTENT_URI, id), vals, null, null);
            }
        }
        if (restaurantIds != null) { // update restaurant details, insert reviews and photos
            for (long id : restaurantIds) {
                if (id > 0) {
                    RestaurantService.download(id);
                }
            }
        }
        if (contactIds != null) { // get followee reviews
            for (long id : contactIds) {
                ReviewsService.download(id);
            }
        }
    }
}
