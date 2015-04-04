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

package net.sf.diningout.provider;

import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

import com.google.common.base.Strings;

import net.sf.diningout.R;
import net.sf.diningout.app.RestaurantGeocodeService;
import net.sf.diningout.data.OpenHour;
import net.sf.diningout.data.Restaurant;
import net.sf.diningout.data.Review;
import net.sf.diningout.data.Review.Type;
import net.sf.diningout.data.Status;
import net.sf.diningout.data.Sync;
import net.sf.diningout.data.Sync.Action;
import net.sf.diningout.data.Synced;
import net.sf.diningout.data.User;
import net.sf.diningout.preference.Keys;
import net.sf.sprockets.database.Cursors;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.google.Place;
import net.sf.sprockets.google.Place.OpeningHours;
import net.sf.sprockets.google.Place.Photo;
import net.sf.sprockets.google.Places;
import net.sf.sprockets.google.Places.Field;
import net.sf.sprockets.google.Places.Params;
import net.sf.sprockets.google.StreetView;
import net.sf.sprockets.graphics.Colors;
import net.sf.sprockets.preference.Prefs;
import net.sf.sprockets.sql.SQLite;
import net.sf.sprockets.time.DayOfWeek;
import net.sf.sprockets.util.Elements;
import net.sf.sprockets.util.Geos;
import net.sf.sprockets.util.MeasureUnit;
import net.sf.sprockets.util.StringArrays;

import org.apache.commons.io.filefilter.FileFilterUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static android.content.ContentResolver.CURSOR_DIR_BASE_TYPE;
import static android.content.ContentResolver.CURSOR_ITEM_BASE_TYPE;
import static java.io.File.separator;
import static java.lang.Double.NEGATIVE_INFINITY;
import static net.sf.diningout.data.OpenHour.Type.CLOSE;
import static net.sf.diningout.data.OpenHour.Type.OPEN;
import static net.sf.diningout.data.Review.Type.GOOGLE;
import static net.sf.diningout.data.Review.Type.PRIVATE;
import static net.sf.diningout.data.Status.DELETED;
import static net.sf.diningout.data.Status.INACTIVE;
import static net.sf.diningout.data.Sync.Action.INSERT;
import static net.sf.diningout.data.Sync.Type.RESTAURANT;
import static net.sf.diningout.data.Sync.Type.REVIEW;
import static net.sf.diningout.data.Sync.Type.USER;
import static net.sf.diningout.preference.Keys.SHOW_NOTIFICATIONS;
import static net.sf.sprockets.app.SprocketsApplication.context;
import static net.sf.sprockets.app.SprocketsApplication.cr;
import static net.sf.sprockets.app.SprocketsApplication.res;
import static net.sf.sprockets.gms.analytics.Trackers.exception;
import static net.sf.sprockets.google.Places.Field.FORMATTED_ADDRESS;
import static net.sf.sprockets.google.Places.Field.FORMATTED_OPENING_HOURS;
import static net.sf.sprockets.google.Places.Field.FORMATTED_PHONE_NUMBER;
import static net.sf.sprockets.google.Places.Field.GEOMETRY;
import static net.sf.sprockets.google.Places.Field.INTL_PHONE_NUMBER;
import static net.sf.sprockets.google.Places.Field.OPENING_HOURS;
import static net.sf.sprockets.google.Places.Field.PHOTOS;
import static net.sf.sprockets.google.Places.Field.PRICE_LEVEL;
import static net.sf.sprockets.google.Places.Field.REVIEWS;
import static net.sf.sprockets.google.Places.Field.WEBSITE;
import static net.sf.sprockets.google.Places.Request.PHOTO;
import static net.sf.sprockets.io.MoreFiles.DOT_PART;
import static net.sf.sprockets.util.MeasureUnit.KILOMETER;
import static net.sf.sprockets.util.MeasureUnit.MILE;

/**
 * Constants and methods for working with the content provider.
 */
public class Contract {
    /**
     * Authority of the content provider.
     */
    public static final String AUTHORITY = "net.sf.diningout";

    /**
     * URI for the authority of the content provider.
     */
    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    /**
     * Local Broadcast: The user has been logged into the server and is being initialised.
     * <p>
     * Input: {@link #EXTRA_HAS_RESTAURANTS}
     * </p>
     */
    public static final String ACTION_USER_LOGGED_IN = "provider.action.USER_LOGGED_IN";

    /**
     * True if the user already has restaurants.
     */
    public static final String EXTRA_HAS_RESTAURANTS = "intent.extra.HAS_RESTAURANTS";

    /**
     * Local Broadcast: Contacts are being synchronised with the server.
     */
    public static final String ACTION_CONTACTS_SYNCING = "provider.action.CONTACTS_SYNCING";

    /**
     * Local Broadcast: Contacts have been synchronised with the server.
     */
    public static final String ACTION_CONTACTS_SYNCED = "provider.action.CONTACTS_SYNCED";

    /**
     * True if only contacts should be synchronised.
     */
    public static final String SYNC_EXTRAS_CONTACTS_ONLY = "contacts_only";

    /**
     * Update a restaurant's rating with the average of its reviews. The String argument must be the
     * ID of the restaurant to update.
     */
    public static final String CALL_UPDATE_RESTAURANT_RATING = "update_restaurant_rating";

    /**
     * Update a restaurant's last visit time with the time of the latest review. The String argument
     * must be the ID of the restaurant to update.
     */
    public static final String CALL_UPDATE_RESTAURANT_LAST_VISIT = "update_restaurant_last_visit";
    private static final String TAG = Contract.class.getSimpleName();

    private Contract() {
    }

    /**
     * Common columns that can be referenced without specifying a table. Be careful to ensure that
     * the queried table does actually contain the column.
     */
    public interface Columns extends StatefulColumns, ServerColumns, SyncColumns, PhotoColumns {
    }

    /**
     * Columns of objects that have a status.
     */
    protected interface StatefulColumns {
        /**
         * {@link Status} of the object.
         */
        String STATUS_ID = "status_id";
    }

    /**
     * Columns of objects that can exist on the server.
     */
    protected interface ServerColumns {
        /**
         * Object on the server. Null if the object has not been uploaded to the server yet.
         */
        String GLOBAL_ID = "global_id";
    }

    /**
     * Columns of objects that are synchronised with the server.
     */
    protected interface SyncColumns {
        /**
         * 1 if the object needs to be synchronised with the server or 0 otherwise.
         */
        String DIRTY = "dirty";

        /**
         * Incremented by one each time {@link #DIRTY} is set to 1.
         */
        String VERSION = "version";
    }

    /**
     * Columns of objects that have a photo.
     */
    protected interface PhotoColumns {
        /**
         * Most prominent color in the photo. Null if not available.
         */
        String COLOR = "color";
    }

    /**
     * Methods for working with {@link Synced} objects.
     */
    private static class Syncing implements BaseColumns, StatefulColumns, ServerColumns,
            SyncColumns {
        /**
         * Get field values from the cursor.
         */
        private static void from(Cursor c, Synced synced) {
            int col = c.getColumnIndex(_ID);
            if (col >= 0) {
                synced.localId = c.getLong(col);
            }
            col = c.getColumnIndex(GLOBAL_ID);
            if (col >= 0) {
                synced.globalId = c.getLong(col);
            }
            col = c.getColumnIndex(STATUS_ID);
            if (col >= 0) {
                synced.status = Status.get(c.getInt(col));
            }
            col = c.getColumnIndex(DIRTY);
            if (col >= 0) {
                synced.dirty = c.getInt(col) == 1;
            }
            col = c.getColumnIndex(VERSION);
            if (col >= 0) {
                synced.version = c.getLong(col);
            }
        }

        /**
         * Get the ID of the object with the global ID.
         *
         * @return {@link Long#MIN_VALUE} if the global ID is not found
         */
        private static long idForGlobalId(Uri uri, long globalId) {
            String[] proj = {_ID};
            String sel = GLOBAL_ID + " = ?";
            String[] args = {String.valueOf(globalId)};
            return Cursors.firstLong(cr().query(uri, proj, sel, args, null));
        }

        /**
         * Get the global ID of the object with the ID.
         *
         * @return {@link Long#MIN_VALUE} if the ID is not found
         */
        private static long globalIdForId(Uri uri, long id) {
            uri = ContentUris.withAppendedId(uri, id);
            String[] proj = {GLOBAL_ID};
            return Cursors.firstLong(cr().query(uri, proj, null, null, null));
        }
    }

    protected interface ContactsColumns {
        /**
         * For the contacts provider. Null if the user is only on the server.
         */
        String ANDROID_LOOKUP_KEY = "android_lookup_key";

        /**
         * For the contacts provider. Null if the user is only on the server.
         */
        String ANDROID_ID = "android_id";

        /**
         * Null if the user is only on the server.
         */
        String NAME = "name";

        /**
         * Null if the user is only on the server.
         */
        String EMAIL = "email";

        /**
         * Base64 encoded SHA-512 hash of the email address.
         */
        String EMAIL_HASH = "email_hash";

        /**
         * 1 if the user is following this contact or 0 otherwise.
         */
        String FOLLOWING = "following";
    }

    protected interface ContactsJoinColumns {
        String CONTACT__ID = "c._id";
        String CONTACT_GLOBAL_ID = "c.global_id";
        String CONTACT_NAME = "c.name";
        String CONTACT_COLOR = "c.color";
        String CONTACT_STATUS_ID = "c.status_id";
        String CONTACT_DIRTY = "c.dirty";
        String CONTACT_VERSION = "c.version";
    }

    public static class Contacts implements ContactsColumns, BaseColumns, StatefulColumns,
            ServerColumns, SyncColumns, PhotoColumns {
        /**
         * URI for the contacts table.
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "contact");
        private static final String SUB_TYPE = "/vnd.diningout.contact";

        /**
         * MIME type of {@link #CONTENT_URI} providing a directory of contacts.
         */
        public static final String CONTENT_TYPE = CURSOR_DIR_BASE_TYPE + SUB_TYPE;

        /**
         * MIME type of a {@link #CONTENT_URI} subdirectory of a single contact.
         */
        public static final String CONTENT_ITEM_TYPE = CURSOR_ITEM_BASE_TYPE + SUB_TYPE;

        private Contacts() {
        }

        /**
         * Get values from the user.
         */
        public static ContentValues values(User user) {
            return values(new ContentValues(5), user);
        }

        /**
         * Put values from the user.
         *
         * @param vals should have a size of 5 or greater
         */
        public static ContentValues values(ContentValues vals, User user) {
            vals.put(GLOBAL_ID, user.globalId);
            vals.put(EMAIL_HASH, user.emailHash);
            vals.put(FOLLOWING, user.isFollowing ? 1 : 0);
            vals.put(STATUS_ID, user.status.id);
            vals.put(DIRTY, 0);
            return vals;
        }

        /**
         * Get contacts from the cursor and then close it.
         *
         * @return null if the cursor is empty
         */
        public static List<User> from(Cursor c) {
            List<User> users = null;
            int count = c.getCount();
            if (count > 0) {
                users = new ArrayList<>(count);
                while (c.moveToNext()) {
                    User user = new User();
                    Syncing.from(c, user);
                    int col = c.getColumnIndex(EMAIL_HASH);
                    if (col >= 0) {
                        user.emailHash = c.getString(col);
                    }
                    col = c.getColumnIndex(FOLLOWING);
                    if (col >= 0) {
                        user.isFollowing = c.getInt(col) == 1;
                    }
                    users.add(user);
                }
            }
            c.close();
            return users;
        }

        /**
         * Get the ID of the contact with the global ID.
         *
         * @return {@link Long#MIN_VALUE} if the global ID is not found
         */
        public static long idForGlobalId(long globalId) {
            return Syncing.idForGlobalId(CONTENT_URI, globalId);
        }

        /**
         * Get the global ID of the contact with the ID.
         *
         * @return {@link Long#MIN_VALUE} if the ID is not found
         */
        public static long globalIdForId(long id) {
            return Syncing.globalIdForId(CONTENT_URI, id);
        }

        /**
         * Get the ID of the contact with the email hash.
         *
         * @return {@link Long#MIN_VALUE} if the email hash is not found
         */
        public static long idForHash(String hash) {
            String[] proj = {_ID};
            String sel = EMAIL_HASH + " = ?";
            String[] args = {hash};
            return Cursors.firstLong(cr().query(CONTENT_URI, proj, sel, args, null));
        }
    }

    protected interface RestaurantsColumns {
        /**
         * Google's ID for the restaurant. Null if it's not a Google Place.
         */
        String PLACE_ID = "place_id";

        /**
         * Google's ID for the restaurant. Null if it's not a Google Place.
         *
         * @deprecated use {@link #PLACE_ID} instead
         */
        @Deprecated
        String GOOGLE_ID = "google_id";

        /**
         * Token used to get the details of the restaurant. Null if it's not a Google Place.
         *
         * @deprecated use {@link #PLACE_ID} instead
         */
        @Deprecated
        String GOOGLE_REFERENCE = "google_reference";

        /**
         * Google's web page for the restaurant. Null if it's not a Google Place.
         */
        String GOOGLE_URL = "google_url";
        String NAME = "name";

        /**
         * Upper case name without diacritics.
         */
        String NORMALISED_NAME = "normalised_name";
        String ADDRESS = "address";

        /**
         * If a Google Place, simplified address that stops after the city level.
         */
        String VICINITY = "vicinity";
        String LATITUDE = "latitude";
        String LONGITUDE = "longitude";

        /**
         * {@code Math.cos(latitude / 57.295779579d)}, used in distance calculation.
         */
        String LONGITUDE_COS = "longitude_cos";

        /**
         * Squared distance to the restaurant. Only available when
         * {@link Restaurants#distance(Location)} is included in the query projection.
         */
        String DISTANCE = "distance";

        /**
         * If a Google Place, includes prefixed country code.
         */
        String INTL_PHONE = "intl_phone";

        /**
         * If a Google Place, in local format.
         */
        String LOCAL_PHONE = "local_phone";

        /**
         * Restaurant's website.
         */
        String URL = "url";

        /**
         * From 1 to 4, if available.
         */
        String PRICE = "price";

        /**
         * From 1.0 to 5.0, if available.
         */
        String RATING = "rating";

        /**
         * User's private scratchpad. Null if empty.
         */
        String NOTES = "notes";

        /**
         * When the user last visited the restaurant. Null if not visited.
         */
        String LAST_VISIT_ON = "last_visit_on";

        /**
         * Last time the Google Place details were refreshed, if ever.
         */
        String REFRESHED_ON = "refreshed_on";

        /**
         * Restaurant that this one was merged into. Null if it hasn't been merged.
         */
        String MERGED_INTO_ID = "merged_into_id";
    }

    protected interface RestaurantsJoinColumns {
        String RESTAURANT__ID = "r._id";
        String RESTAURANT_GLOBAL_ID = "r.global_id";
        String RESTAURANT_NAME = "r.name";
        String RESTAURANT_RATING = "r.rating";
        String RESTAURANT_COLOR = "r.color";
        String RESTAURANT_STATUS_ID = "r.status_id";
        String RESTAURANT_DIRTY = "r.dirty";
        String RESTAURANT_VERSION = "r.version";
    }

    public static class Restaurants implements RestaurantsColumns, BaseColumns, StatefulColumns,
            ServerColumns, SyncColumns, PhotoColumns {
        /**
         * URI for the restaurants table.
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "restaurant");
        private static final String SUB_TYPE = "/vnd.diningout.restaurant";

        /**
         * MIME type of {@link #CONTENT_URI} providing a directory of restaurants.
         */
        public static final String CONTENT_TYPE = CURSOR_DIR_BASE_TYPE + SUB_TYPE;

        /**
         * MIME type of a {@link #CONTENT_URI} subdirectory of a single restaurant.
         */
        public static final String CONTENT_ITEM_TYPE = CURSOR_ITEM_BASE_TYPE + SUB_TYPE;

        /**
         * Distance in metres from a location to search for restaurants.
         */
        public static final int SEARCH_RADIUS = res().getInteger(R.integer.search_radius);

        /**
         * Place types to search for when searching for restaurants.
         */
        public static final String SEARCH_TYPES = "restaurant";

        private Restaurants() {
        }

        /**
         * Add a placeholder for a restaurant with the global ID.
         *
         * @return id of the new restaurant or -1 if an error occurred
         */
        public static long add(long globalId) {
            ContentValues vals = new ContentValues(4);
            vals.put(GLOBAL_ID, globalId);
            vals.put(NAME, "");
            vals.put(NORMALISED_NAME, "");
            vals.put(COLOR, defaultColor());
            vals.put(DIRTY, 0);
            return ContentUris.parseId(cr().insert(CONTENT_URI, vals));
        }

        /**
         * Delete any other restaurant that has the global ID and move its reviews to the
         * restaurant.
         *
         * @return true if another restaurant was deleted
         */
        public static boolean deleteConflict(long id, long globalId) {
            String[] proj = {_ID};
            String sel = GLOBAL_ID + " = ? AND " + _ID + " <> ?";
            String[] args = StringArrays.from(globalId, id);
            long otherId = Cursors.firstLong(cr().query(CONTENT_URI, proj, sel, args, null));
            if (otherId > 0) {
                /* delete other restaurant */
                ContentValues vals = new ContentValues(2);
                vals.putNull(GLOBAL_ID);
                vals.put(STATUS_ID, DELETED.id);
                cr().update(CONTENT_URI, vals, sel, args);
                /* move its reviews - though is this necessary? would be downloaded again */
                vals.clear();
                vals.put(Reviews.RESTAURANT_ID, id);
                sel = Reviews.GLOBAL_ID + " IS NOT NULL AND " + Reviews.RESTAURANT_ID + " = ?";
                args = new String[]{String.valueOf(otherId)};
                cr().update(Reviews.CONTENT_URI, vals, sel, args);
                return true;
            }
            return false;
        }

        /**
         * Get a color to use when adding a restaurant.
         */
        public static int defaultColor() {
            return Colors.dark();
        }

        private static final Field[] sSearchFields = {GEOMETRY, Field.NAME, Field.RATING, PHOTOS};

        /**
         * Get the fields that should be requested in {@link Places} search methods.
         */
        public static Field[] searchFields() {
            return sSearchFields.clone();
        }

        private static final Field[] sDetailsFields = {Field.URL, GEOMETRY, Field.NAME,
                FORMATTED_ADDRESS, Field.VICINITY, INTL_PHONE_NUMBER, FORMATTED_PHONE_NUMBER,
                WEBSITE, PRICE_LEVEL, Field.RATING, REVIEWS, OPENING_HOURS, FORMATTED_OPENING_HOURS,
                PHOTOS};

        /**
         * Get the fields that should be requested in {@link Places#details(Params, Field...)}.
         */
        public static Field[] detailsFields() {
            return sDetailsFields.clone();
        }

        /**
         * Get values from the place.
         */
        public static ContentValues values(Place place) {
            return values(new ContentValues(15), place); // extra space in vals in case color added
        }

        /**
         * Put values from the place.
         *
         * @param vals should have a size of 14 or greater
         */
        public static ContentValues values(ContentValues vals, Place place) {
            vals.put(PLACE_ID, place.getPlaceId().getId());
            vals.put(GOOGLE_URL, place.getUrl());
            String name = place.getName();
            vals.put(NAME, name);
            vals.put(NORMALISED_NAME, SQLite.normalise(name));
            vals.put(ADDRESS, place.getFormattedAddress());
            vals.put(VICINITY, place.getVicinity());
            double lat = place.getLatitude();
            vals.put(LATITUDE, lat != NEGATIVE_INFINITY ? lat : null);
            double lng = place.getLongitude();
            vals.put(LONGITUDE, lng != NEGATIVE_INFINITY ? lng : null);
            vals.put(LONGITUDE_COS, lat != NEGATIVE_INFINITY ? Geos.cos(lat) : null);
            vals.put(INTL_PHONE, place.getIntlPhoneNumber());
            vals.put(LOCAL_PHONE, place.getFormattedPhoneNumber());
            vals.put(URL, place.getWebsite());
            int price = place.getPriceLevel();
            vals.put(PRICE, price > 0 ? price : null);
            vals.put(REFRESHED_ON, SQLite.datetime());
            return vals;
        }

        /**
         * Get values from the restaurant.
         */
        public static ContentValues values(Restaurant restaurant) {
            /* get current values for comparison */
            String[] proj = {ADDRESS};
            String sel = _ID + " = ? OR " + GLOBAL_ID + " = ?";
            String[] args = StringArrays.from(restaurant.localId, restaurant.globalId);
            EasyCursor c = new EasyCursor(cr().query(CONTENT_URI, proj, sel, args, null));
            int count = c.getCount();
            String address = Cursors.firstString(c);
            /* prepare for insert/update */
            ContentValues vals = new ContentValues(16); // one extra space in case color added
            vals.put(GLOBAL_ID, restaurant.globalId);
            vals.put(PLACE_ID, restaurant.placeId);
            if (!TextUtils.isEmpty(restaurant.placeId)) {
                if (count == 0) { // insert placeholder
                    String name = Strings.nullToEmpty(restaurant.name);
                    vals.put(NAME, name);
                    vals.put(NORMALISED_NAME, SQLite.normalise(name));
                }
            } else {
                vals.put(NAME, restaurant.name);
                vals.put(NORMALISED_NAME, SQLite.normalise(restaurant.name));
                if (!TextUtils.isEmpty(restaurant.address) && !restaurant.address.equals(address)) {
                    vals.put(ADDRESS, restaurant.address);
                    vals.put(VICINITY, restaurant.address);
                    try {
                        RestaurantGeocodeService.geocode(restaurant.address, vals);
                    } catch (IOException e) {
                        Log.e(TAG, "geocoding restaurant address", e);
                        exception(e);
                    }
                }
                vals.put(INTL_PHONE, restaurant.phone);
                vals.put(LOCAL_PHONE, restaurant.phone);
                vals.put(URL, restaurant.url);
            }
            vals.put(NOTES, restaurant.notes);
            vals.put(STATUS_ID, restaurant.status.id);
            if (count == 0) { // adding from sync
                vals.put(DIRTY, 0);
            }
            return vals;
        }

        /**
         * Get restaurants from the cursor and then close it.
         *
         * @return null if the cursor is empty
         */
        public static List<Restaurant> from(Cursor c) {
            List<Restaurant> restaurants = null;
            while (c.moveToNext()) {
                Restaurant restaurant = new Restaurant();
                Syncing.from(c, restaurant);
                int col = c.getColumnIndex(PLACE_ID);
                if (col >= 0) {
                    restaurant.placeId = c.getString(col);
                }
                if (TextUtils.isEmpty(restaurant.placeId)) {
                    col = c.getColumnIndex(NAME);
                    if (col >= 0) {
                        restaurant.name = c.getString(col);
                    }
                    col = c.getColumnIndex(ADDRESS);
                    if (col >= 0) {
                        restaurant.address = c.getString(col);
                    }
                    col = c.getColumnIndex(INTL_PHONE);
                    if (col >= 0) {
                        restaurant.phone = c.getString(col);
                    }
                    col = c.getColumnIndex(URL);
                    if (col >= 0) {
                        restaurant.url = c.getString(col);
                    }
                }
                col = c.getColumnIndex(NOTES);
                if (col >= 0) {
                    restaurant.notes = c.getString(col);
                }
                if (restaurants == null) {
                    restaurants = new ArrayList<>(c.getCount());
                }
                restaurants.add(restaurant);
            }
            c.close();
            return restaurants;
        }

        /**
         * Get the ID of the restaurant with the global ID.
         *
         * @return {@link Long#MIN_VALUE} if the global ID is not found
         */
        public static long idForGlobalId(long globalId) {
            return Syncing.idForGlobalId(CONTENT_URI, globalId);
        }

        /**
         * Get a {@link #DISTANCE} result column that returns the squared distance (in km or mi,
         * depending on user setting) from the location to a restaurant.
         */
        public static String distance(Location location) {
            if (location != null) {
                MeasureUnit unit = Keys.isDistanceUnit(MILE) ? MILE : KILOMETER;
                return SQLite.distance(LATITUDE, LONGITUDE, LONGITUDE_COS,
                        location.getLatitude(), location.getLongitude(), unit, DISTANCE);
            } else {
                return "null AS " + DISTANCE;
            }
        }
    }

    protected interface OpenHoursColumns {
        /**
         * Restaurant the opening or closing time is for.
         */
        String RESTAURANT_ID = "restaurant_id";

        /**
         * Indicates whether it is an opening or closing time.
         */
        String TYPE_ID = "type_id";

        /**
         * 0 (Monday) to 6 (Sunday).
         */
        String DAY = "day";

        /**
         * 0 - 2359.
         */
        String TIME = "time";
    }

    public static class OpenHours implements OpenHoursColumns {
        /**
         * URI for the open hours table.
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "open_hour");
        private static final String SUB_TYPE = "/vnd.diningout.open_hour";

        /**
         * MIME type of {@link #CONTENT_URI} providing a directory of open hours.
         */
        public static final String CONTENT_TYPE = CURSOR_DIR_BASE_TYPE + SUB_TYPE;

        private OpenHours() {
        }

        /**
         * Get values from the place's opening hours.
         *
         * @return null if the place doesn't have any opening hours
         */
        public static ContentValues[] values(long restaurantId, Place place) {
            List<OpeningHours> hours = place.getOpeningHours();
            if (hours != null) {
                List<ContentValues> vals = new ArrayList<>(hours.size() * 2); // open + close
                for (OpeningHours hour : hours) {
                    DayOfWeek day = hour.getOpenDay();
                    int time = hour.getOpenTime();
                    if (day != null && time >= 0) {
                        vals.add(values(restaurantId, OPEN, day, time));
                    }
                    day = hour.getCloseDay();
                    time = hour.getCloseTime();
                    if (day != null && time >= 0) {
                        vals.add(values(restaurantId, CLOSE, day, time));
                    }
                }
                return vals.toArray(new ContentValues[vals.size()]);
            }
            return null;
        }

        private static ContentValues values(long restaurantId, OpenHour.Type type,
                                            DayOfWeek day, int time) {
            ContentValues vals = new ContentValues(4);
            vals.put(RESTAURANT_ID, restaurantId);
            vals.put(TYPE_ID, type.id);
            vals.put(DAY, day.ordinal());
            vals.put(TIME, time);
            return vals;
        }
    }

    protected interface OpenDaysColumns {
        /**
         * Restaurant the hours are for.
         */
        String RESTAURANT_ID = "restaurant_id";

        /**
         * 0 (Monday) to 6 (Sunday).
         */
        String DAY = "day";

        /**
         * E.g. "Monday: 10:00 am – 6:00 pm" or "Sunday: Closed".
         */
        String HOURS = "hours";
    }

    public static class OpenDays implements OpenDaysColumns {
        /**
         * URI for the open days table.
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "open_day");
        private static final String SUB_TYPE = "/vnd.diningout.open_day";

        /**
         * MIME type of {@link #CONTENT_URI} providing a directory of open days.
         */
        public static final String CONTENT_TYPE = CURSOR_DIR_BASE_TYPE + SUB_TYPE;

        private OpenDays() {
        }

        /**
         * Get values from the place's formatted opening hours.
         *
         * @return null if the place doesn't have any formatted opening hours
         */
        public static ContentValues[] values(long restaurantId, Place place) {
            List<String> days = place.getFormattedOpeningHours();
            if (days != null) {
                ContentValues[] vals = new ContentValues[days.size()];
                for (int i = 0; i < vals.length; i++) {
                    ContentValues val = new ContentValues(3);
                    val.put(RESTAURANT_ID, restaurantId);
                    val.put(DAY, i);
                    val.put(HOURS, days.get(i));
                    vals[i] = val;
                }
                return vals;
            }
            return null;
        }
    }

    protected interface RestaurantPhotosColumns {
        /**
         * Restaurant the photo is for.
         */
        String RESTAURANT_ID = "restaurant_id";

        /**
         * Token used to download the photo.
         */
        String GOOGLE_REFERENCE = "google_reference";

        /**
         * Maximum width on the server. The local width depends on the screen size.
         */
        String WIDTH = "width";

        /**
         * Maximum height on the server. The local height depends on the screen size.
         */
        String HEIGHT = "height";

        /**
         * Null if the photo hasn't been downloaded yet or an ETag wasn't returned.
         */
        String ETAG = "etag";
    }

    public static class RestaurantPhotos implements RestaurantPhotosColumns, BaseColumns {
        /**
         * URI for the restaurant photos table.
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI,
                "restaurant_photo");
        private static final String SUB_TYPE = "/vnd.diningout.restaurant_photo";

        /**
         * MIME type of {@link #CONTENT_URI} providing a directory of restaurant photos.
         */
        public static final String CONTENT_TYPE = CURSOR_DIR_BASE_TYPE + SUB_TYPE;

        /**
         * MIME type of a {@link #CONTENT_URI} subdirectory of a single restaurant photo.
         */
        public static final String CONTENT_ITEM_TYPE = CURSOR_ITEM_BASE_TYPE + SUB_TYPE;

        /**
         * Directory for restaurant image files.
         */
        private static final String RESTAURANT_IMAGES = "images" + separator + "restaurants"
                + separator;

        private RestaurantPhotos() {
        }

        /**
         * Get a Uri for one of the restaurant's photos.
         */
        public static Uri uriForRestaurant(long id) {
            return CONTENT_URI.buildUpon().appendQueryParameter(RESTAURANT_ID, String.valueOf(id))
                    .build();
        }

        /**
         * Get values from the place's photos.
         *
         * @return null if the place doesn't have any photos
         */
        public static ContentValues[] values(long restaurantId, Place place) {
            List<Photo> photos = place.getPhotos();
            return photos != null ? values(new ContentValues[photos.size()], restaurantId, place)
                    : null;
        }

        /**
         * Put values from the place's photos. If {@code vals} is larger than the number of photos,
         * the extra elements will remain null or will be cleared.
         *
         * @param vals if an element is not null, it should have a size of 4 or greater
         */
        public static ContentValues[] values(ContentValues[] vals, long restaurantId, Place place) {
            List<Photo> photos = place.getPhotos();
            int size = photos != null ? photos.size() : 0;
            for (int i = 0; i < vals.length; i++) {
                if (i < size) {
                    Photo photo = photos.get(i);
                    if (vals[i] == null) {
                        vals[i] = new ContentValues(4);
                    }
                    vals[i].put(RESTAURANT_ID, restaurantId);
                    vals[i].put(GOOGLE_REFERENCE, photo.getReference());
                    vals[i].put(WIDTH, photo.getWidth());
                    vals[i].put(HEIGHT, photo.getHeight());
                } else if (vals[i] != null) {
                    vals[i].clear();
                }
            }
            return vals;
        }

        /**
         * Get a URL to download the main photo for the place that is resized according to the
         * target width and height in pixels.
         */
        public static String url(Place place, int targetWidth, int targetHeight) {
            List<Photo> photos = place.getPhotos();
            if (photos != null) {
                Photo photo = photos.get(0);
                Params params = new Places.Params().reference(photo.getReference());
                /* limit width or height depending on how it will fit in the target */
                int width = photo.getWidth();
                int height = photo.getHeight();
                if (width > 0 && height > 0) {
                    if ((double) targetWidth / targetHeight > (double) width / height) {
                        params.maxWidth(targetWidth);
                    } else {
                        params.maxHeight(targetHeight);
                    }
                } else {
                    params.maxWidth(targetWidth).maxHeight(targetHeight);
                }
                return params.format(PHOTO);
            } else {
                return url(place.getLatitude(), place.getLongitude(), targetWidth, targetHeight);
            }
        }

        /**
         * Get a URL to download a Street View image that is resized according to the target width
         * and height in pixels.
         */
        public static String url(double lat, double lng, int targetWidth, int targetHeight) {
            return new StreetView.Params().location(lat, lng).pitch(10)
                    .size(targetWidth, targetHeight).format();
        }

        /**
         * Get a file for one of the restaurant's photos.
         *
         * @return null if external storage is not mounted or the restaurant doesn't have any photos
         */
        public static File file(long restaurantId) {
            return file(-1L, restaurantId);
        }

        /**
         * Get the file for the restaurant's photo.
         *
         * @param id can be -1 to get one of the restaurant's photos
         * @return null if external storage is not mounted or the restaurant doesn't have any photos
         */
        public static File file(long id, long restaurantId) {
            File file = context().getExternalFilesDir(null);
            if (file != null) {
                if (id >= 0) {
                    return new File(file, RESTAURANT_IMAGES + restaurantId + separator + id);
                } else {
                    file = new File(file, RESTAURANT_IMAGES + restaurantId);
                    return Elements.get(file.listFiles(PART_FILTER), 0);
                }
            }
            return null;
        }

        /**
         * Filters out partially downloaded files.
         */
        private static final FilenameFilter PART_FILTER =
                FileFilterUtils.notFileFilter(FileFilterUtils.suffixFileFilter(DOT_PART));
    }

    protected interface ReviewsColumns {
        /**
         * Restaurant reviewed.
         */
        String RESTAURANT_ID = "restaurant_id";

        /**
         * {@link Review.Type Type} of review.
         */
        String TYPE_ID = "type_id";

        /**
         * Friend that wrote the review. Null if the user wrote it.
         */
        String CONTACT_ID = "contact_id";

        /**
         * Public review author. Null if not a public review.
         */
        String AUTHOR_NAME = "author_name";
        String COMMENTS = "comments";

        /**
         * From 1 to 5, if available.
         */
        String RATING = "rating";

        /**
         * When the review was written.
         */
        String WRITTEN_ON = "written_on";
    }

    protected interface ReviewsJoinColumns {
        String REVIEW__ID = "w._id";
        String REVIEW_GLOBAL_ID = "w.global_id";
        String REVIEW_TYPE_ID = "w.type_id";
        String REVIEW_RATING = "w.rating";
        String REVIEW_STATUS_ID = "w.status_id";
        String REVIEW_DIRTY = "w.dirty";
        String REVIEW_VERSION = "w.version";
    }

    public static class Reviews implements ReviewsColumns, BaseColumns, StatefulColumns,
            ServerColumns, SyncColumns {
        /**
         * URI for the reviews table.
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "review");
        private static final String SUB_TYPE = "/vnd.diningout.review";

        /**
         * MIME type of {@link #CONTENT_URI} providing a directory of reviews.
         */
        public static final String CONTENT_TYPE = CURSOR_DIR_BASE_TYPE + SUB_TYPE;

        /**
         * MIME type of a {@link #CONTENT_URI} subdirectory of a single review.
         */
        public static final String CONTENT_ITEM_TYPE = CURSOR_ITEM_BASE_TYPE + SUB_TYPE;

        private Reviews() {
        }

        /**
         * Get values from the place's reviews.
         *
         * @return null if the place doesn't have any reviews
         */
        public static ContentValues[] values(long restaurantId, Place place) {
            List<Place.Review> reviews = place.getReviews();
            return reviews != null ? values(new ContentValues[reviews.size()], restaurantId, place)
                    : null;
        }

        /**
         * Put values from the place's reviews. If {@code vals} is larger than the number of
         * reviews, the extra elements will remain null or will be cleared.
         *
         * @param vals if an element is not null, it should have a size of 7 or greater
         */
        public static ContentValues[] values(ContentValues[] vals, long restaurantId, Place place) {
            List<Place.Review> reviews = place.getReviews();
            int size = reviews != null ? reviews.size() : 0;
            for (int i = 0; i < vals.length; i++) {
                if (i < size) {
                    Place.Review review = reviews.get(i);
                    String text = review.getText();
                    if (!TextUtils.isEmpty(text)) {
                        if (vals[i] == null) {
                            vals[i] = new ContentValues(7);
                        }
                        vals[i].put(RESTAURANT_ID, restaurantId);
                        vals[i].put(TYPE_ID, GOOGLE.id);
                        vals[i].put(AUTHOR_NAME, review.getAuthorName());
                        vals[i].put(COMMENTS, text);
                        int rating = review.getRating();
                        vals[i].put(RATING, rating > 0 ? rating : null);
                        vals[i].put(WRITTEN_ON, SQLite.datetime(review.getTime() * 1000));
                        vals[i].put(DIRTY, 0); // not synced to server
                    } else if (vals[i] != null) {
                        vals[i].clear();
                    }
                } else if (vals[i] != null) {
                    vals[i].clear();
                }
            }
            return vals;
        }

        /**
         * Get values from the review.
         */
        public static ContentValues values(Review review) {
            ContentValues vals = new ContentValues(9);
            vals.put(GLOBAL_ID, review.globalId);
            vals.put(RESTAURANT_ID, Restaurants.idForGlobalId(review.restaurantId));
            vals.put(TYPE_ID, PRIVATE.id);
            if (review.userId > 0) {
                long contactId = Contacts.idForGlobalId(review.userId);
                if (contactId > 0) {
                    vals.put(CONTACT_ID, contactId);
                }
            }
            vals.put(COMMENTS, review.comments);
            vals.put(RATING, review.rating);
            vals.put(WRITTEN_ON, review.writtenOn);
            vals.put(STATUS_ID, review.status.id);
            vals.put(DIRTY, 0);
            return vals;
        }

        /**
         * Get reviews from the cursor and then close it.
         *
         * @return null if the cursor is empty
         */
        public static List<Review> from(Cursor c) {
            List<Review> reviews = null;
            int count = c.getCount();
            if (count > 0) {
                reviews = new ArrayList<>(count);
                while (c.moveToNext()) {
                    reviews.add(current(c));
                }
            }
            c.close();
            return reviews;
        }

        /**
         * Get the first review from the cursor and then optionally close it.
         *
         * @return null if the cursor is empty
         */
        public static Review first(Cursor c, boolean close) {
            Review review = null;
            if (c.moveToFirst()) {
                review = current(c);
            }
            if (close) {
                c.close();
            }
            return review;
        }

        /**
         * Get the current review from the cursor.
         */
        private static Review current(Cursor c) {
            Review review = new Review();
            Syncing.from(c, review);
            int col = c.getColumnIndex(RESTAURANT_ID);
            if (col >= 0) {
                review.restaurantId = c.getLong(col);
            }
            col = c.getColumnIndex(TYPE_ID);
            if (col >= 0) {
                review.type = Type.get(c.getInt(col));
            }
            col = c.getColumnIndex(CONTACT_ID);
            if (col >= 0) {
                review.userId = c.getLong(col);
            }
            col = c.getColumnIndex(COMMENTS);
            if (col >= 0) {
                review.comments = c.getString(col);
            }
            col = c.getColumnIndex(RATING);
            if (col >= 0) {
                review.rating = c.getInt(col);
            }
            col = c.getColumnIndex(WRITTEN_ON);
            if (col >= 0) {
                review.writtenOn = c.getString(col);
            }
            return review;
        }

        /**
         * Get the ID of the review with the global ID.
         *
         * @return {@link Long#MIN_VALUE} if the global ID is not found
         */
        public static long idForGlobalId(long globalId) {
            return Syncing.idForGlobalId(CONTENT_URI, globalId);
        }
    }

    public static class ReviewsJoinRestaurants implements ReviewsColumns, RestaurantsColumns,
            ReviewsJoinColumns, RestaurantsJoinColumns {
        /**
         * URI for the reviews table joined with the restaurants table.
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI,
                "review_join_restaurant");
        private static final String SUB_TYPE = "/vnd.diningout.review_join_restaurant";

        /**
         * MIME type of {@link #CONTENT_URI} providing a directory of reviews and their restaurants.
         */
        public static final String CONTENT_TYPE = CURSOR_DIR_BASE_TYPE + SUB_TYPE;

        /**
         * MIME type of a {@link #CONTENT_URI} subdirectory of a single review and its restaurant.
         */
        public static final String CONTENT_ITEM_TYPE = CURSOR_ITEM_BASE_TYPE + SUB_TYPE;

        private ReviewsJoinRestaurants() {
        }
    }

    public static class ReviewsJoinContacts implements ReviewsColumns, ContactsColumns,
            ReviewsJoinColumns, ContactsJoinColumns {
        /**
         * URI for the reviews table joined with the contacts table.
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI,
                "review_join_contact");
        private static final String SUB_TYPE = "/vnd.diningout.review_join_contact";

        /**
         * MIME type of {@link #CONTENT_URI} providing a directory of reviews and their contacts.
         */
        public static final String CONTENT_TYPE = CURSOR_DIR_BASE_TYPE + SUB_TYPE;

        /**
         * MIME type of a {@link #CONTENT_URI} subdirectory of a single review and its contact.
         */
        public static final String CONTENT_ITEM_TYPE = CURSOR_ITEM_BASE_TYPE + SUB_TYPE;

        private ReviewsJoinContacts() {
        }
    }

    public static class ReviewsJoinAll implements ReviewsColumns, RestaurantsColumns,
            ContactsColumns, ReviewsJoinColumns, RestaurantsJoinColumns, ContactsJoinColumns {
        /**
         * URI for the reviews table joined with the restaurants and contacts tables.
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI,
                "review_join_all");
        private static final String SUB_TYPE = "/vnd.diningout.review_join_all";

        /**
         * MIME type of {@link #CONTENT_URI} providing a directory of reviews and their restaurants
         * and contacts.
         */
        public static final String CONTENT_TYPE = CURSOR_DIR_BASE_TYPE + SUB_TYPE;

        /**
         * MIME type of a {@link #CONTENT_URI} subdirectory of a single review and its restaurant
         * and contact.
         */
        public static final String CONTENT_ITEM_TYPE = CURSOR_ITEM_BASE_TYPE + SUB_TYPE;

        private ReviewsJoinAll() {
        }
    }

    protected interface ReviewDraftsColumns {
        /**
         * Restaurant that is being reviewed.
         */
        String RESTAURANT_ID = "restaurant_id";
        String COMMENTS = "comments";

        /**
         * From 1 to 5, if available.
         */
        String RATING = "rating";
    }

    protected interface ReviewDraftsJoinColumns {
        String REVIEW_DRAFT_RATING = "d.rating";
        String REVIEW_DRAFT_STATUS_ID = "d.status_id";
        String REVIEW_DRAFT_DIRTY = "d.dirty";
        String REVIEW_DRAFT_VERSION = "d.version";
    }

    public static class ReviewDrafts implements ReviewDraftsColumns, StatefulColumns, SyncColumns {
        /**
         * URI for the review drafts table.
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "review_draft");
        private static final String SUB_TYPE = "/vnd.diningout.review_draft";

        /**
         * MIME type of {@link #CONTENT_URI} providing a directory of review drafts.
         */
        public static final String CONTENT_TYPE = CURSOR_DIR_BASE_TYPE + SUB_TYPE;

        /**
         * MIME type of a {@link #CONTENT_URI} subdirectory of a single review draft.
         */
        public static final String CONTENT_ITEM_TYPE = CURSOR_ITEM_BASE_TYPE + SUB_TYPE;

        private ReviewDrafts() {
        }

        /**
         * Get values from the review draft.
         */
        public static ContentValues values(Review draft) {
            ContentValues vals = new ContentValues(6); // 1 extra for incremented version
            vals.put(RESTAURANT_ID, Restaurants.idForGlobalId(draft.restaurantId));
            vals.put(COMMENTS, draft.comments);
            vals.put(RATING, draft.rating);
            vals.put(STATUS_ID, draft.status.id);
            vals.put(DIRTY, 0);
            return vals;
        }
    }

    public static class ReviewDraftsJoinRestaurants implements ReviewDraftsColumns,
            RestaurantsColumns, ReviewDraftsJoinColumns, RestaurantsJoinColumns {
        /**
         * URI for the review drafts table joined with the restaurants table.
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI,
                "review_draft_join_restaurant");
        private static final String SUB_TYPE = "/vnd.diningout.review_draft_join_restaurant";

        /**
         * MIME type of {@link #CONTENT_URI} providing a directory of review drafts and their
         * restaurants.
         */
        public static final String CONTENT_TYPE = CURSOR_DIR_BASE_TYPE + SUB_TYPE;

        /**
         * MIME type of a {@link #CONTENT_URI} subdirectory of a single review draft and its
         * restaurant.
         */
        public static final String CONTENT_ITEM_TYPE = CURSOR_ITEM_BASE_TYPE + SUB_TYPE;

        private ReviewDraftsJoinRestaurants() {
        }
    }

    protected interface SyncsColumns {
        /**
         * {@link Sync.Type Type} of subject.
         */
        String TYPE_ID = "type_id";

        /**
         * Local ID of subject.
         */
        String OBJECT_ID = "object_id";

        /**
         * {@link Action} taken on the subject.
         */
        String ACTION_ID = "action_id";

        /**
         * When the action was taken, .
         */
        String ACTION_ON = "action_on";
    }

    protected interface SyncsJoinColumns {
        String SYNC__ID = "s._id";
        String SYNC_GLOBAL_ID = "s.global_id";
        String SYNC_TYPE_ID = "s.type_id";
        String SYNC_STATUS_ID = "s.status_id";
    }

    public static class Syncs implements SyncsColumns, BaseColumns, StatefulColumns, ServerColumns {
        /**
         * URI for the syncs table.
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "sync");
        private static final String SUB_TYPE = "/vnd.diningout.sync";

        /**
         * MIME type of {@link #CONTENT_URI} providing a directory of syncs.
         */
        public static final String CONTENT_TYPE = CURSOR_DIR_BASE_TYPE + SUB_TYPE;

        /**
         * MIME type of a {@link #CONTENT_URI} subdirectory of a single sync.
         */
        public static final String CONTENT_ITEM_TYPE = CURSOR_ITEM_BASE_TYPE + SUB_TYPE;

        private Syncs() {
        }

        /**
         * Get values from the sync.
         */
        public static ContentValues values(Sync<? extends Synced> sync) {
            ContentValues vals = new ContentValues(6);
            vals.put(GLOBAL_ID, sync.globalId);
            vals.put(TYPE_ID, type(sync).id);
            vals.put(OBJECT_ID, sync.object.localId);
            vals.put(ACTION_ID, sync.action.id);
            vals.put(ACTION_ON, sync.actionOn);
            if (!Prefs.getStringSet(context(), SHOW_NOTIFICATIONS)
                    .contains(context().getString(R.string.friend_notifications_value))) {
                vals.put(STATUS_ID, INACTIVE.id);
            }
            return vals;
        }

        /**
         * Get the type of object that was acted on.
         */
        private static Sync.Type type(Sync<? extends Synced> sync) {
            if (sync.object instanceof Review) { // ordered by likelihood
                return REVIEW;
            } else if (sync.object instanceof User) {
                return USER;
            } else if (sync.object instanceof Restaurant) {
                return RESTAURANT;
            }
            return null; // should never happen, all implementations should be covered above
        }

        /**
         * Get values from the inserted Google Place review.
         */
        public static ContentValues values(long id, ContentValues review) {
            ContentValues vals = new ContentValues(5);
            vals.put(TYPE_ID, REVIEW.id);
            vals.put(OBJECT_ID, id);
            vals.put(ACTION_ID, INSERT.id);
            vals.put(ACTION_ON, review.getAsString(Reviews.WRITTEN_ON));
            if (!Prefs.getStringSet(context(), SHOW_NOTIFICATIONS)
                    .contains(context().getString(R.string.public_notifications_value))) {
                vals.put(STATUS_ID, INACTIVE.id);
            }
            return vals;
        }
    }

    public static class SyncsJoinAll implements SyncsColumns, ReviewsColumns, RestaurantsColumns,
            ContactsColumns, SyncsJoinColumns, ReviewsJoinColumns, RestaurantsJoinColumns,
            ContactsJoinColumns {
        /**
         * URI for the syncs table joined with the reviews, restaurants, and contacts tables.
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "sync_join_all");
        private static final String SUB_TYPE = "/vnd.diningout.sync_join_all";

        /**
         * MIME type of {@link #CONTENT_URI} providing a directory of syncs and their reviews,
         * restaurants, and contacts.
         */
        public static final String CONTENT_TYPE = CURSOR_DIR_BASE_TYPE + SUB_TYPE;

        /**
         * MIME type of a {@link #CONTENT_URI} subdirectory of a single sync and its review,
         * restaurant, and contact.
         */
        public static final String CONTENT_ITEM_TYPE = CURSOR_ITEM_BASE_TYPE + SUB_TYPE;

        private SyncsJoinAll() {
        }
    }
}
