/*
 * Copyright 2014-2015 pushbit <pushbit@gmail.com>
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
import android.content.Intent;

import net.sf.diningout.data.Review;
import net.sf.diningout.data.User;
import net.sf.diningout.net.Server;
import net.sf.diningout.provider.Contract.Contacts;
import net.sf.diningout.provider.Contract.Restaurants;
import net.sf.diningout.provider.Contract.Reviews;

import java.util.List;

import static net.sf.diningout.provider.Contract.AUTHORITY_URI;
import static net.sf.diningout.provider.Contract.CALL_UPDATE_RESTAURANT_RATING;
import static net.sf.sprockets.app.SprocketsApplication.cr;

/**
 * Gets reviews written by a user. Callers must include {@link #EXTRA_ID} in their Intent extras.
 */
public class ReviewsService extends IntentService {
    /**
     * ID of the user.
     */
    public static final String EXTRA_ID = "intent.extra.ID";
    private static final String TAG = ReviewsService.class.getSimpleName();

    public ReviewsService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        download(intent.getLongExtra(EXTRA_ID, 0L));
    }

    /**
     * Download reviews written by the user.
     */
    public static void download(long id) {
        User user = new User();
        user.globalId = Contacts.globalIdForId(id);
        if (user.globalId > 0) {
            List<Review> reviews = Server.reviews(user);
            if (reviews != null) {
                for (Review review : reviews) {
                    add(review);
                }
            }
        }
    }

    /**
     * Add the review, creating its restaurant if it doesn't already exist.
     */
    public static void add(Review review) {
        long restaurantId = Restaurants.idForGlobalId(review.restaurantId);
        boolean restaurantExists = restaurantId > 0;
        if (!restaurantExists) { // add placeholder
            restaurantId = Restaurants.add(review.restaurantId);
        }
        if (restaurantId > 0) { // add review
            ContentResolver cr = cr();
            review.localId =
                    ContentUris.parseId(cr.insert(Reviews.CONTENT_URI, Reviews.values(review)));
            cr.call(AUTHORITY_URI, CALL_UPDATE_RESTAURANT_RATING, String.valueOf(restaurantId),
                    null);
            if (!restaurantExists) { // fill in the placeholder
                RestaurantService.download(restaurantId);
            }
        }
    }
}
