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
import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteConstraintException;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;

import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;
import com.google.common.io.Files;

import net.sf.diningout.R;
import net.sf.diningout.data.Restaurant;
import net.sf.diningout.data.Review;
import net.sf.diningout.net.Server;
import net.sf.diningout.provider.Contract.OpenDays;
import net.sf.diningout.provider.Contract.OpenHours;
import net.sf.diningout.provider.Contract.RestaurantPhotos;
import net.sf.diningout.provider.Contract.Restaurants;
import net.sf.diningout.provider.Contract.Reviews;
import net.sf.sprockets.database.Cursors;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.google.Place;
import net.sf.sprockets.google.Places;
import net.sf.sprockets.google.Places.Params;
import net.sf.sprockets.google.Places.Response;
import net.sf.sprockets.google.Places.Response.Status;
import net.sf.sprockets.net.HttpClient;
import net.sf.sprockets.net.Uris;
import net.sf.sprockets.sql.SQLite;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.List;

import static android.provider.BaseColumns._ID;
import static net.sf.diningout.data.Review.Type.GOOGLE;
import static net.sf.diningout.data.Status.ACTIVE;
import static net.sf.diningout.data.Status.DELETED;
import static net.sf.diningout.provider.Contract.AUTHORITY_URI;
import static net.sf.diningout.provider.Contract.CALL_UPDATE_RESTAURANT_LAST_VISIT;
import static net.sf.diningout.provider.Contract.CALL_UPDATE_RESTAURANT_RATING;
import static net.sf.sprockets.app.SprocketsApplication.context;
import static net.sf.sprockets.app.SprocketsApplication.cr;
import static net.sf.sprockets.app.SprocketsApplication.res;
import static net.sf.sprockets.gms.analytics.Trackers.event;
import static net.sf.sprockets.gms.analytics.Trackers.exception;
import static net.sf.sprockets.google.Places.Response.Status.NOT_FOUND;
import static net.sf.sprockets.google.Places.Response.Status.OK;
import static net.sf.sprockets.io.MoreFiles.DOT_PART;

/**
 * Updates details, reviews, and photos for a restaurant. Callers must include {@link #EXTRA_ID} in
 * their Intent extras.
 */
public class RestaurantService extends IntentService {
    /**
     * ID of the restaurant.
     */
    public static final String EXTRA_ID = "intent.extra.ID";
    private static final String TAG = RestaurantService.class.getSimpleName();

    public RestaurantService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        download(intent.getLongExtra(EXTRA_ID, 0L));
    }

    /**
     * Download details, reviews, and photos for the restaurant.
     *
     * @return null if the restaurant is not a Google Place or it could not be downloaded
     */
    public static Result download(long id) {
        ContentResolver cr = cr();
        Uri uri = ContentUris.withAppendedId(Restaurants.CONTENT_URI, id);
        String[] proj = {Restaurants.GLOBAL_ID, Restaurants.PLACE_ID};
        EasyCursor c = new EasyCursor(cr.query(uri, proj, null, null, null));
        Restaurant restaurant = new Restaurant();
        restaurant.localId = id;
        if (c.moveToFirst()) {
            restaurant.globalId = c.getLong(Restaurants.GLOBAL_ID);
            restaurant.placeId = c.getString(Restaurants.PLACE_ID);
        }
        c.close();
        /* try to get place ID from server if don't already have */
        if (restaurant.globalId > 0 && TextUtils.isEmpty(restaurant.placeId)) {
            restaurant = Server.restaurant(restaurant);
        }
        /* get Google details, reviews, and photos if available */
        Result result = null;
        if (restaurant != null && !TextUtils.isEmpty(restaurant.placeId)) {
            try {
                ContentValues vals = new ContentValues(14);
                vals.put(Restaurants.PLACE_ID, restaurant.placeId);
                result = details(id, vals);
                if (result.status == NOT_FOUND) {
                    return result;
                }
                if (result.place != null) {
                    photo(result.photoId, id, result.place);
                }
            } catch (IOException e) {
                Log.e(TAG, "getting place details or downloading restaurant photo", e);
                exception(e);
            }
        }
        /* get server details if haven't already */
        if (restaurant != null && restaurant.status == null) {
            restaurant = Server.restaurant(restaurant);
        }
        if (restaurant != null) { // save details
            restaurant.status = ACTIVE; // in case re-adding after deleting
            ContentValues vals = Restaurants.values(restaurant);
            Restaurants.deleteConflict(restaurant.localId, restaurant.globalId);
            try { // while place_id has UNIQUE constraint
                cr.update(uri, vals, null, null);
            } catch (SQLiteConstraintException e) {
                Log.e(TAG, "updating restaurant from server", e);
                exception(e);
            }
            try {
                photo(id, vals);
            } catch (IOException e) {
                Log.e(TAG, "downloading Street View image", e);
                exception(e);
            }
            /* get server reviews, update existing or insert new */
            List<Review> reviews = Server.reviews(restaurant);
            if (reviews != null) {
                boolean hasOwn = false;
                for (Review review : reviews) {
                    vals = Reviews.values(review);
                    String sel = Reviews.GLOBAL_ID + " = ?";
                    String[] args = {String.valueOf(review.globalId)};
                    if (cr.update(Reviews.CONTENT_URI, vals, sel, args) == 0) {
                        review.localId = ContentUris.parseId(cr.insert(Reviews.CONTENT_URI, vals));
                        if (review.localId > 0 && review.userId == 0) {
                            hasOwn = true;
                        }
                    }
                }
                String restaurantId = String.valueOf(id);
                cr.call(AUTHORITY_URI, CALL_UPDATE_RESTAURANT_RATING, restaurantId, null);
                if (hasOwn) {
                    cr.call(AUTHORITY_URI, CALL_UPDATE_RESTAURANT_LAST_VISIT, restaurantId, null);
                }
            }
        }
        return result;
    }

    /**
     * Update the Google restaurant's details, reviews, and photos.
     *
     * @param vals should have a size of 14 or greater and must include
     *             {@link Restaurants#PLACE_ID PLACE_ID}
     */
    static Result details(long id, ContentValues vals) throws IOException {
        Result result = new Result();
        String restaurantId = String.valueOf(id);
        String placeId = vals.getAsString(Restaurants.PLACE_ID);
        if (placeId.startsWith("NOT_FOUND_")) {
            return result;
        }
        ContentResolver cr = cr();
        Response<Place> resp =
                Places.details(new Params().placeId(placeId), Restaurants.detailsFields());
        result.status = resp.getStatus();
        result.place = resp.getResult();
        if (result.status == OK && result.place != null) {
            try { // while place_id has UNIQUE constraint
                cr.update(ContentUris.withAppendedId(Restaurants.CONTENT_URI, id),
                        Restaurants.values(vals, result.place), null, null);
            } catch (SQLiteConstraintException e) {
                Log.e(TAG, "updating restaurant from Place details", e);
                exception(e);
            }
            /* insert/update reviews */
            List<Place.Review> reviews = result.place.getReviews();
            if (reviews != null) {
                Uri uri = Reviews.CONTENT_URI;
                /* get time of latest review */
                String[] proj = {SQLite.millis("max", Reviews.WRITTEN_ON)};
                String sel = Reviews.RESTAURANT_ID + " = ? AND " + Reviews.TYPE_ID + " = ?";
                String[] args = {restaurantId, String.valueOf(GOOGLE.id)};
                long latest = Cursors.firstLong(cr.query(uri, proj, sel, args, null));
                /* insert/update reviews */
                ContentValues reviewVals = new ContentValues();
                sel += " AND " + Reviews.WRITTEN_ON + " = ?";
                args = Arrays.copyOf(args, 3);
                for (Place.Review review : reviews) {
                    Reviews.values(reviewVals, id, review);
                    if (reviewVals.size() > 0) {
                        args[2] = reviewVals.getAsString(Reviews.WRITTEN_ON);
                        if (cr.update(uri, reviewVals, sel, args) == 0) {
                            long reviewId = ContentUris.parseId(cr.insert(uri, reviewVals));
                            if (reviewId > 0 && review.getTime() * 1000 > latest) {
                                result.addReviewTime(reviewId, args[2]);
                            }
                        }
                    }
                }
                cr.call(AUTHORITY_URI, CALL_UPDATE_RESTAURANT_RATING, restaurantId, null);
            }
            /* refresh open hours */
            deleteHours(cr, restaurantId);
            ContentValues[] hourVals = OpenHours.values(id, result.place);
            if (hourVals != null) {
                cr.bulkInsert(OpenHours.CONTENT_URI, hourVals);
            }
            /* refresh open days */
            ContentValues[] dayVals = OpenDays.values(id, result.place);
            if (dayVals != null) {
                cr.bulkInsert(OpenDays.CONTENT_URI, dayVals);
            }
            /* insert photos if none yet */
            String[] proj = {_ID};
            String sel = RestaurantPhotos.RESTAURANT_ID + " = ?";
            String[] args = {restaurantId};
            result.photoId = Cursors.firstLong(cr.query(
                    Uris.limit(RestaurantPhotos.CONTENT_URI, 1), proj, sel, args, _ID));
            if (result.photoId <= 0) {
                ContentValues[] photoVals = RestaurantPhotos.values(id, result.place);
                if (photoVals != null) {
                    for (ContentValues photoVal : photoVals) {
                        Uri uri = cr.insert(RestaurantPhotos.CONTENT_URI, photoVal);
                        if (result.photoId <= 0) {
                            result.photoId = ContentUris.parseId(uri);
                        }
                    }
                }
                if (result.photoId < 0) { // place doesn't have any photos so get Street View image
                    result.photoId = 0L;
                }
            }
        } else {
            if (result.status == NOT_FOUND) {
                vals.clear();
                vals.put(Restaurants.PLACE_ID, "NOT_FOUND_" + id);
                vals.put(Restaurants.REFRESHED_ON, SQLite.datetime());
                vals.put(Restaurants.STATUS_ID, DELETED.id);
                vals.put(Restaurants.DIRTY, 1);
                cr.update(ContentUris.withAppendedId(Restaurants.CONTENT_URI, id), vals,
                        null, null);
                deleteHours(cr, restaurantId); // so users don't think it's still open
            }
            Log.e(TAG, "Places.details failed, status: " + result.status);
            event("restaurant", "Places.details failed", result.status.toString());
        }
        return result;
    }

    /**
     * Delete the restaurant's open hours and open days.
     */
    private static void deleteHours(ContentResolver cr, String restaurantId) {
        String sel = OpenHours.RESTAURANT_ID + " = ?";
        String[] args = {restaurantId};
        cr.delete(OpenHours.CONTENT_URI, sel, args);
        cr.delete(OpenDays.CONTENT_URI, sel, args);
    }

    /**
     * Download a photo for the place and save it to disk.
     *
     * @param id can be 0 if the place doesn't have any photos and a Street View image should be
     *           downloaded
     */
    static void photo(long id, long restaurantId, Place place) throws IOException {
        String url = RestaurantPhotos.url(place,
                res().getDimensionPixelSize(R.dimen.restaurant_photo_width),
                res().getDimensionPixelSize(R.dimen.restaurant_photo_height));
        String etag = photo(id, restaurantId, url);
        if (id > 0 && !TextUtils.isEmpty(etag)) {
            ContentValues vals = new ContentValues(1);
            vals.put(RestaurantPhotos.ETAG, etag);
            cr().update(ContentUris.withAppendedId(RestaurantPhotos.CONTENT_URI, id), vals,
                    null, null);
        }
    }

    /**
     * If the values have {@link Restaurants#LATITUDE LATITUDE} and
     * {@link Restaurants#LONGITUDE LONGITUDE} in them, download a Street View image for the
     * location and save it to disk.
     */
    public static void photo(long restaurantId, ContentValues vals) throws IOException {
        Double lat = vals.getAsDouble(Restaurants.LATITUDE);
        Double lng = vals.getAsDouble(Restaurants.LONGITUDE);
        if (lat != null && lng != null) {
            photo(restaurantId, lat, lng);
        }
    }

    /**
     * Download a Street View image for the location and save it to disk.
     */
    static void photo(long restaurantId, double lat, double lng) throws IOException {
        String url = RestaurantPhotos.url(lat, lng,
                res().getDimensionPixelSize(R.dimen.restaurant_photo_width),
                res().getDimensionPixelSize(R.dimen.restaurant_photo_height));
        photo(0, restaurantId, url);
    }

    /**
     * Download the photo at the URL and save it to disk.
     *
     * @return ETag header value, if available
     */
    private static String photo(long id, final long restaurantId, String url) throws IOException {
        File file = RestaurantPhotos.file(id, restaurantId);
        if (file == null) {
            return null;
        }
        Files.createParentDirs(file);
        File part = new File(file.getParentFile(), file.getName() + DOT_PART);
        URLConnection con = HttpClient.openConnection(url);
        Closer closer = Closer.create();
        try {
            ByteStreams.copy(closer.register(con.getInputStream()),
                    closer.register(new FileOutputStream(part)));
        } catch (Throwable e) {
            throw closer.rethrow(e);
        } finally {
            closer.close();
        }
        part.renameTo(file);
        /* notify observers about new photo */
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                cr().notifyChange(ContentUris.withAppendedId(Restaurants.CONTENT_URI, restaurantId),
                        null, false);
            }
        }, 500L); // when the file will hopefully already be flushed to disk
        context().startService(new Intent(context(), RestaurantColorService.class)
                .putExtra(RestaurantColorService.EXTRA_ID, restaurantId));
        return con.getHeaderField("ETag");
    }

    /**
     * Result of trying to download a Google Place.  All fields may be null or zero if the attempt
     * was not successful or the Place didn't contain the properties.
     */
    public static class Result {
        public Status status;
        public Place place;
        public LongSparseArray<String> newReviewTimes;
        public long photoId;

        private void addReviewTime(long id, String time) {
            if (newReviewTimes == null) {
                newReviewTimes = new LongSparseArray<>();
            }
            newReviewTimes.append(id, time);
        }
    }
}
