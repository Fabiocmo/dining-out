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

package net.sf.diningout.content;

import android.accounts.Account;
import android.app.PendingIntent;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.Cursor;
import android.database.CursorJoiner;
import android.database.sqlite.SQLiteConstraintException;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.RawContacts;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;

import net.sf.diningout.R;
import net.sf.diningout.accounts.Accounts;
import net.sf.diningout.app.FriendColorService;
import net.sf.diningout.app.Notifications;
import net.sf.diningout.app.RestaurantGeofencingEventService;
import net.sf.diningout.app.RestaurantService;
import net.sf.diningout.app.RestaurantService.Result;
import net.sf.diningout.app.RestaurantsRefreshService;
import net.sf.diningout.app.ReviewsService;
import net.sf.diningout.data.Init;
import net.sf.diningout.data.Restaurant;
import net.sf.diningout.data.Review;
import net.sf.diningout.data.Sync;
import net.sf.diningout.data.Synced;
import net.sf.diningout.data.Syncing;
import net.sf.diningout.data.User;
import net.sf.diningout.net.AuthToken;
import net.sf.diningout.net.Server;
import net.sf.diningout.provider.Contract.Columns;
import net.sf.diningout.provider.Contract.Contacts;
import net.sf.diningout.provider.Contract.Restaurants;
import net.sf.diningout.provider.Contract.ReviewDrafts;
import net.sf.diningout.provider.Contract.ReviewDraftsJoinRestaurants;
import net.sf.diningout.provider.Contract.Reviews;
import net.sf.diningout.provider.Contract.ReviewsJoinRestaurants;
import net.sf.diningout.provider.Contract.Syncs;
import net.sf.sprockets.content.Content;
import net.sf.sprockets.database.Cursors;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.gms.gcm.Gcm;
import net.sf.sprockets.gms.location.Locations;
import net.sf.sprockets.net.Uris;
import net.sf.sprockets.preference.Prefs;
import net.sf.sprockets.util.StringArrays;

import java.io.IOException;
import java.util.List;

import retrofit.RetrofitError;

import static android.content.ContentResolver.SYNC_EXTRAS_INITIALIZE;
import static android.content.ContentResolver.SYNC_EXTRAS_MANUAL;
import static android.content.ContentResolver.SYNC_EXTRAS_UPLOAD;
import static android.provider.BaseColumns._ID;
import static android.text.format.DateUtils.DAY_IN_MILLIS;
import static android.text.format.DateUtils.MINUTE_IN_MILLIS;
import static com.google.android.gms.location.Geofence.NEVER_EXPIRE;
import static com.google.android.gms.location.LocationServices.GeofencingApi;
import static com.google.common.base.Charsets.UTF_8;
import static java.util.Locale.ENGLISH;
import static net.sf.diningout.data.Review.Type.PRIVATE;
import static net.sf.diningout.data.Status.ACTIVE;
import static net.sf.diningout.data.Status.DELETED;
import static net.sf.diningout.data.Sync.Action.INSERT;
import static net.sf.diningout.data.Sync.Action.UPDATE;
import static net.sf.diningout.net.Server.BACKOFF_RETRIES;
import static net.sf.diningout.preference.Keys.App.ACCOUNT_INITIALISED;
import static net.sf.diningout.preference.Keys.App.APP;
import static net.sf.diningout.preference.Keys.App.CLOUD_ID;
import static net.sf.diningout.preference.Keys.App.INSTALL_ID;
import static net.sf.diningout.preference.Keys.App.LAST_SYNC;
import static net.sf.diningout.preference.Keys.App.ONBOARDED;
import static net.sf.diningout.preference.Keys.SHOW_NOTIFICATIONS;
import static net.sf.diningout.provider.Contract.ACTION_CONTACTS_SYNCED;
import static net.sf.diningout.provider.Contract.ACTION_CONTACTS_SYNCING;
import static net.sf.diningout.provider.Contract.ACTION_USER_LOGGED_IN;
import static net.sf.diningout.provider.Contract.AUTHORITY;
import static net.sf.diningout.provider.Contract.AUTHORITY_URI;
import static net.sf.diningout.provider.Contract.CALL_UPDATE_RESTAURANT_LAST_VISIT;
import static net.sf.diningout.provider.Contract.CALL_UPDATE_RESTAURANT_RATING;
import static net.sf.diningout.provider.Contract.EXTRA_HAS_RESTAURANTS;
import static net.sf.diningout.provider.Contract.SYNC_EXTRAS_CONTACTS_ONLY;
import static net.sf.sprockets.app.SprocketsApplication.cr;
import static net.sf.sprockets.content.Content.SYNC_EXTRAS_DOWNLOAD;
import static net.sf.sprockets.gms.analytics.Trackers.event;
import static net.sf.sprockets.gms.analytics.Trackers.exception;
import static net.sf.sprockets.gms.location.Geofences.GEOFENCE_RADIUS;
import static net.sf.sprockets.gms.location.Geofences.GEOFENCE_TRANSITION_ALL;
import static net.sf.sprockets.gms.location.Geofences.MAX_GEOFENCES;
import static net.sf.sprockets.sql.SQLite.alias;

/**
 * Synchronises the content provider with the server.
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {
    private static final String TAG = SyncAdapter.class.getSimpleName();
    private static final String PROJECT_ID = "77419503291"; // from Google Developers Console
    /**
     * Contacts content URI specifying that the caller is a sync adapter.
     */
    private static final Uri CONTACTS_URI = Uris.callerIsSyncAdapter(Contacts.CONTENT_URI);
    /**
     * Restaurants content URI specifying that the caller is a sync adapter.
     */
    private static final Uri RESTAURANTS_URI = Uris.callerIsSyncAdapter(Restaurants.CONTENT_URI);
    /**
     * Reviews content URI specifying that the caller is a sync adapter.
     */
    private static final Uri REVIEWS_URI = Uris.callerIsSyncAdapter(Reviews.CONTENT_URI);
    /**
     * Review drafts content URI specifying that the caller is a sync adapter.
     */
    private static final Uri REVIEW_DRAFTS_URI = Uris.callerIsSyncAdapter(ReviewDrafts.CONTENT_URI);
    /**
     * Syncs content URI specifying that the caller is a sync adapter.
     */
    private static final Uri SYNCS_URI = Uris.callerIsSyncAdapter(Syncs.CONTENT_URI);

    public SyncAdapter(Context context) {
        super(context, false, false);
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
                              ContentProviderClient provider, SyncResult result) {
        Account selected = Accounts.selected();
        if (!account.equals(selected)) {
            if (selected != null) { // never going to sync this account that wasn't selected
                ContentResolver.setIsSyncable(account, AUTHORITY, 0);
            }
            return;
        }
        if (extras.containsKey(SYNC_EXTRAS_INITIALIZE)) {
            return; // will initialise on first normal sync
        }
        Context context = getContext();
        SharedPreferences prefs = Prefs.get(context, APP);
        try {
            if (!prefs.getBoolean(ACCOUNT_INITIALISED, false)) { // first run, log the user in
                initUser(context, provider);
            }
            if (extras.getBoolean(SYNC_EXTRAS_CONTACTS_ONLY)) {
                refreshContacts(context, provider);
                uploadContacts(context, provider);
                return;
            }
            if (!prefs.getBoolean(ONBOARDED, false)) {
                return; // don't sync yet
            }
            long now = System.currentTimeMillis(); // full upload and download daily
            boolean dailySync = now - prefs.getLong(LAST_SYNC, 0L) >= DAY_IN_MILLIS;
            if (dailySync || extras.containsKey(SYNC_EXTRAS_MANUAL)) {
                extras.putBoolean(SYNC_EXTRAS_UPLOAD, true);
                extras.putBoolean(SYNC_EXTRAS_DOWNLOAD, true);
                refreshContacts(context, provider);
            }
            if (extras.containsKey(SYNC_EXTRAS_UPLOAD)
                    || extras.containsKey(SYNC_EXTRAS_DOWNLOAD)) {
                uploadContacts(context, provider);
                uploadRestaurants(provider);
                uploadReviews(provider);
                uploadReviewDrafts(provider);
            }
            if (extras.containsKey(SYNC_EXTRAS_DOWNLOAD)) {
                download(provider);
                prefs.edit().putLong(LAST_SYNC, now).apply();
                if (dailySync) {
                    refreshRestaurants(context, provider);
                    geofenceRestaurants(context, provider);
                }
                Notifications.sync(context);
            }
            if (!prefs.contains(CLOUD_ID)) { // get GCM registration ID and user notification key
                String id = uploadCloudId(context);
                if (!TextUtils.isEmpty(id)) {
                    prefs.edit().putString(CLOUD_ID, id).apply();
                    // todo service isn't working yet
                    // String key = getCloudNotificationKey(context, selected, id);
                    // if (!TextUtils.isEmpty(key)) {
                    //     prefs.edit().putString(CLOUD_NOTIFICATION_KEY, key).apply();
                    // }
                }
            }
        } catch (RemoteException e) {
            result.databaseError = true;
            Log.e(TAG, "syncing the ContentProvider", e);
            exception(e);
        }
    }

    /**
     * Log the user into the server and restore any contacts or restaurants.
     */
    private void initUser(Context context, ContentProviderClient cp) throws RemoteException {
        Init init = Server.init();
        Intent intent = new Intent(ACTION_USER_LOGGED_IN);
        intent.putExtra(EXTRA_HAS_RESTAURANTS, init != null && init.restaurants != null);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        if (init != null) {
            Prefs.edit(context, APP).putBoolean(ACCOUNT_INITIALISED, true)
                    .putLong(INSTALL_ID, init.installId).apply();
            if (init.users != null) {
                ContentValues vals = new ContentValues(5);
                for (User user : init.users) {
                    cp.insert(CONTACTS_URI, Contacts.values(vals, user));
                }
            }
            if (init.restaurants != null) {
                Prefs.putBoolean(context, APP, ONBOARDED, true);
                for (Restaurant restaurant : init.restaurants) {
                    restaurant.localId = Restaurants.add(restaurant.globalId);
                    if (restaurant.localId > 0) {
                        RestaurantService.download(restaurant.localId);
                    }
                }
            }
        }
    }

    /**
     * Insert new system contacts, delete orphaned app contacts, and synchronise any changes to
     * existing.
     */
    private void refreshContacts(Context context, ContentProviderClient cp) throws RemoteException {
        /* get system contacts */
        String[] proj = {Email.ADDRESS, ContactsContract.Contacts.LOOKUP_KEY,
                RawContacts.CONTACT_ID, ContactsContract.Contacts.DISPLAY_NAME};
        String sel = Email.IN_VISIBLE_GROUP + " = 1 AND " + Email.ADDRESS + " <> ?";
        String[] args = {Accounts.selected().name};
        EasyCursor sys = new EasyCursor(cr().query(Email.CONTENT_URI, proj, sel, args,
                Email.ADDRESS));
        /* get app contacts */
        proj = new String[]{Contacts.EMAIL, Contacts.ANDROID_LOOKUP_KEY, Contacts.ANDROID_ID,
                Contacts.NAME, _ID, Contacts.FOLLOWING, Contacts.STATUS_ID};
        sel = Contacts.EMAIL + " IS NOT NULL";
        EasyCursor app = new EasyCursor(cp.query(CONTACTS_URI, proj, sel, null, Contacts.EMAIL));
        /* compare and sync */
        ContentValues vals = new ContentValues(5);
        for (CursorJoiner.Result result : new CursorJoiner(sys, new String[]{Email.ADDRESS},
                app, new String[]{Contacts.EMAIL})) {
            switch (result) {
                case LEFT: // new system contact, insert into app contacts
                    String email = sys.getString(Email.ADDRESS);
                    String hash = BaseEncoding.base64().encode(Hashing.sha512()
                            .hashString(email.toLowerCase(ENGLISH), UTF_8).asBytes());
                    long id = Contacts.idForHash(hash); // do we have this contact and not know it?
                    /* insert or update values */
                    vals.put(Contacts.ANDROID_LOOKUP_KEY,
                            sys.getString(ContactsContract.Contacts.LOOKUP_KEY));
                    vals.put(Contacts.ANDROID_ID, sys.getLong(RawContacts.CONTACT_ID));
                    vals.put(Contacts.NAME, sys.getString(ContactsContract.Contacts.DISPLAY_NAME));
                    vals.put(Contacts.EMAIL, email);
                    if (id <= 0) {
                        vals.put(Contacts.EMAIL_HASH, hash);
                        id = ContentUris.parseId(cp.insert(CONTACTS_URI, vals));
                    } else {
                        cp.update(ContentUris.withAppendedId(CONTACTS_URI, id), vals, null, null);
                    }
                    if (id > 0) {
                        context.startService(new Intent(context, FriendColorService.class)
                                .putExtra(FriendColorService.EXTRA_ID, id));
                    }
                    break;
                case RIGHT: // orphaned app contact, delete unless user is following
                    if (app.getInt(Contacts.FOLLOWING) == 0
                            && app.getInt(Contacts.STATUS_ID) == ACTIVE.id) {
                        vals.put(Contacts.STATUS_ID, DELETED.id);
                        vals.put(Contacts.DIRTY, 1);
                        cp.update(Uris.appendId(CONTACTS_URI, app), vals, null, null);
                    }
                    break;
                case BOTH: // matching contacts, update details in app if needed
                    String s = sys.getString(ContactsContract.Contacts.LOOKUP_KEY);
                    if (!s.equals(app.getString(Contacts.ANDROID_LOOKUP_KEY))) {
                        vals.put(Contacts.ANDROID_LOOKUP_KEY, s);
                    }
                    long l = sys.getLong(RawContacts.CONTACT_ID);
                    if (l != app.getLong(Contacts.ANDROID_ID)) {
                        vals.put(Contacts.ANDROID_ID, l);
                    }
                    s = sys.getString(ContactsContract.Contacts.DISPLAY_NAME);
                    if (!s.equals(app.getString(Contacts.NAME))) {
                        vals.put(Contacts.NAME, s);
                    }
                    if (app.getInt(Contacts.STATUS_ID) == DELETED.id) {
                        vals.put(Contacts.STATUS_ID, ACTIVE.id);
                        vals.put(Contacts.DIRTY, 1);
                    }
                    if (vals.size() > 0) {
                        cp.update(Uris.appendId(CONTACTS_URI, app), vals, null, null);
                        context.startService(new Intent(context, FriendColorService.class)
                                .putExtra(FriendColorService.EXTRA_ID, app.getLong(_ID)));
                    }
                    break;
            }
            vals.clear();
        }
        sys.close();
        app.close();
    }

    /**
     * Upload contact changes to the server.
     */
    private void uploadContacts(Context context, ContentProviderClient cp) throws RemoteException {
        String[] proj = {_ID, Contacts.GLOBAL_ID, Contacts.EMAIL_HASH, Contacts.FOLLOWING,
                Contacts.STATUS_ID, Contacts.DIRTY, Contacts.VERSION};
        String sel = Contacts.DIRTY + " = 1";
        List<User> users = Contacts.from(cp.query(CONTACTS_URI, proj, sel, null, null));
        if (users != null) {
            LocalBroadcastManager bm = LocalBroadcastManager.getInstance(context);
            bm.sendBroadcast(new Intent(ACTION_CONTACTS_SYNCING));
            response(Server.syncContacts(users), cp, CONTACTS_URI);
            bm.sendBroadcast(new Intent(ACTION_CONTACTS_SYNCED));
        }
    }

    /**
     * Upload restaurant changes to the server.
     */
    private void uploadRestaurants(ContentProviderClient cp) throws RemoteException {
        String[] proj = {_ID, Restaurants.GLOBAL_ID, Restaurants.PLACE_ID, Restaurants.NAME,
                Restaurants.ADDRESS, Restaurants.INTL_PHONE, Restaurants.URL, Restaurants.NOTES,
                Restaurants.GEOFENCE_NOTIFICATIONS, Restaurants.STATUS_ID, Restaurants.DIRTY,
                Restaurants.VERSION};
        String sel = Restaurants.DIRTY + " = 1";
        List<Restaurant> restaurants = Restaurants.from(cp.query(RESTAURANTS_URI, proj, sel, null,
                null));
        if (restaurants != null) {
            response(Server.syncRestaurants(restaurants), cp, RESTAURANTS_URI);
        }
    }

    /**
     * Upload review changes to the server.
     */
    private void uploadReviews(ContentProviderClient cp) throws RemoteException {
        String[] proj = {alias(ReviewsJoinRestaurants.REVIEW__ID),
                alias(ReviewsJoinRestaurants.REVIEW_GLOBAL_ID),
                ReviewsJoinRestaurants.RESTAURANT_GLOBAL_ID + " AS " + Reviews.RESTAURANT_ID,
                Reviews.COMMENTS, alias(ReviewsJoinRestaurants.REVIEW_RATING),
                Reviews.WRITTEN_ON, alias(ReviewsJoinRestaurants.REVIEW_STATUS_ID),
                alias(ReviewsJoinRestaurants.REVIEW_DIRTY),
                alias(ReviewsJoinRestaurants.REVIEW_VERSION)};
        String sel = Reviews.TYPE_ID + " = ? AND " + ReviewsJoinRestaurants.REVIEW_DIRTY + " = 1";
        String[] args = {String.valueOf(PRIVATE.id)};
        List<Review> reviews = Reviews.from(cp.query(ReviewsJoinRestaurants.CONTENT_URI, proj, sel,
                args, null));
        if (reviews != null) {
            response(Server.syncReviews(reviews), cp, REVIEWS_URI);
        }
    }

    /**
     * Upload review draft changes to the server.
     */
    private void uploadReviewDrafts(ContentProviderClient cp) throws RemoteException {
        String[] proj = {ReviewDrafts.RESTAURANT_ID + " AS " + _ID,
                Restaurants.GLOBAL_ID + " AS " + ReviewDrafts.RESTAURANT_ID, ReviewDrafts.COMMENTS,
                alias(ReviewDraftsJoinRestaurants.REVIEW_DRAFT_RATING),
                alias(ReviewDraftsJoinRestaurants.REVIEW_DRAFT_STATUS_ID),
                alias(ReviewDraftsJoinRestaurants.REVIEW_DRAFT_DIRTY),
                alias(ReviewDraftsJoinRestaurants.REVIEW_DRAFT_VERSION)};
        String sel = ReviewDraftsJoinRestaurants.REVIEW_DRAFT_DIRTY + " = 1";
        List<Review> drafts = Reviews.from(cp.query(ReviewDraftsJoinRestaurants.CONTENT_URI, proj,
                sel, null, null));
        if (drafts != null) {
            response(Server.syncReviewDrafts(drafts), cp, REVIEW_DRAFTS_URI);
        }
    }

    /**
     * Update the synchronised object at the URI with the values received from the server.
     *
     * @param synceds can be null
     */
    private void response(List<? extends Synced> synceds, ContentProviderClient cp, Uri uri)
            throws RemoteException {
        if (synceds == null) {
            return;
        }
        ContentValues vals = new ContentValues(2);
        String sel = Columns.VERSION + " = ?";
        String[] args = new String[1];
        for (Synced synced : synceds) {
            if (synced.globalId > 0 && uri != REVIEW_DRAFTS_URI) {
                vals.put(Columns.GLOBAL_ID, synced.globalId);
                if (uri == RESTAURANTS_URI) {
                    Restaurants.deleteConflict(synced.localId, synced.globalId);
                }
            }
            vals.put(Columns.DIRTY, synced.dirty);
            args[0] = String.valueOf(synced.version);
            cp.update(ContentUris.withAppendedId(uri, synced.localId), vals, sel, args);
            vals.clear();
        }
    }

    /**
     * Download changes from the server and synchronise them with the content provider.
     */
    private void download(ContentProviderClient cp) throws RemoteException {
        Syncing syncing = Server.sync();
        if (syncing != null) {
            if (syncing.users != null) {
                for (Sync<User> sync : syncing.users) {
                    if (sync.userId == 0) { // in case server starts sending changes by other users
                        syncUser(cp, sync);
                    }
                }
            }
            if (syncing.restaurants != null) {
                for (Sync<Restaurant> sync : syncing.restaurants) {
                    if (sync.userId == 0) { // in case server starts sending changes by other users
                        syncRestaurant(cp, sync);
                    }
                }
            }
            if (syncing.reviews != null) {
                for (Sync<Review> sync : syncing.reviews) {
                    syncReview(cp, sync);
                }
            }
            if (syncing.reviewDrafts != null) {
                for (Sync<Review> sync : syncing.reviewDrafts) {
                    syncReviewDraft(cp, sync);
                }
            }
        }
    }

    /**
     * Convert a contact when they become a user and sync remote changes to a followed user.
     */
    private void syncUser(ContentProviderClient cp, Sync<User> sync) throws RemoteException {
        User user = sync.object;
        ContentValues vals = new ContentValues(1);
        switch (sync.action) {
            case INSERT:
                user.localId = Contacts.idForHash(user.emailHash);
                if (user.localId > 0) {
                    vals.put(Contacts.GLOBAL_ID, user.globalId);
                    cp.update(ContentUris.withAppendedId(CONTACTS_URI, user.localId), vals,
                            null, null);
                    cp.insert(SYNCS_URI, Syncs.values(sync));
                }
                break;
            case UPDATE:
                int following = user.isFollowing ? 1 : 0;
                vals.put(Contacts.FOLLOWING, following);
                String sel = Contacts.GLOBAL_ID + " = ? AND " + Contacts.FOLLOWING + " <> ?";
                String[] args = StringArrays.from(user.globalId, following);
                if (cp.update(CONTACTS_URI, vals, sel, args) > 0 && user.isFollowing) {
                    ReviewsService.download(Contacts.idForGlobalId(user.globalId));
                }
                break;
        }
    }

    /**
     * Sync remote changes to a restaurant.
     */
    private void syncRestaurant(ContentProviderClient cp, Sync<Restaurant> sync)
            throws RemoteException {
        Restaurant restaurant = sync.object;
        switch (sync.action) {
            case INSERT:
                ContentValues vals = Restaurants.values(restaurant);
                vals.put(Restaurants.COLOR, Restaurants.defaultColor());
                restaurant.localId = ContentUris.parseId(cp.insert(RESTAURANTS_URI, vals));
                if (restaurant.localId > 0 && restaurant.status == ACTIVE) {
                    RestaurantService.download(restaurant.localId);
                    try {
                        RestaurantService.photo(restaurant.localId, vals);
                    } catch (IOException e) {
                        Log.e(TAG, "downloading Street View image", e);
                        exception(e);
                    }
                }
                break;
            case UPDATE:
                restaurant.localId = Restaurants.idForGlobalId(restaurant.globalId);
                if (restaurant.localId > 0) {
                    vals = Restaurants.values(restaurant);
                    try { // while place_id has UNIQUE constraint
                        cp.update(ContentUris.withAppendedId(RESTAURANTS_URI, restaurant.localId),
                                vals, null, null);
                    } catch (SQLiteConstraintException e) {
                        Log.e(TAG, "updating restaurant from sync", e);
                        exception(e);
                    }
                    try {
                        RestaurantService.photo(restaurant.localId, vals);
                    } catch (IOException e) {
                        Log.e(TAG, "downloading Street View image", e);
                        exception(e);
                    }
                } else { // re-added on other device
                    sync.action = INSERT;
                    syncRestaurant(cp, sync);
                }
                break;
        }
    }

    /**
     * Sync remote changes to a review.
     */
    private void syncReview(ContentProviderClient cp, Sync<Review> sync) throws RemoteException {
        Review review = sync.object;
        switch (sync.action) {
            case INSERT:
                ReviewsService.add(review);
                if (review.localId > 0 && review.userId > 0) { // friend's review
                    cp.insert(SYNCS_URI, Syncs.values(sync));
                }
                break;
            case UPDATE:
                ContentValues vals = new ContentValues(3);
                vals.put(Reviews.COMMENTS, review.comments);
                vals.put(Reviews.RATING, review.rating);
                vals.put(Reviews.STATUS_ID, review.status.id);
                String sel = Reviews.GLOBAL_ID + " = ?";
                String[] args = {String.valueOf(review.globalId)};
                cp.update(REVIEWS_URI, vals, sel, args);
                break;
        }
        /* update restaurant rating and last visit if own review */
        String id = String.valueOf(Restaurants.idForGlobalId(review.restaurantId));
        if (sync.action != INSERT) { // already called in add
            cr().call(AUTHORITY_URI, CALL_UPDATE_RESTAURANT_RATING, id, null);
        }
        if (review.userId == 0 && sync.action != UPDATE) {
            cr().call(AUTHORITY_URI, CALL_UPDATE_RESTAURANT_LAST_VISIT, id, null);
        }
    }

    /**
     * Sync remote changes to a review draft.
     */
    private void syncReviewDraft(ContentProviderClient cp, Sync<Review> sync)
            throws RemoteException {
        ContentValues vals = ReviewDrafts.values(sync.object);
        /* get current version and increment it */
        Uri uri = ContentUris.withAppendedId(REVIEW_DRAFTS_URI,
                vals.getAsLong(ReviewDrafts.RESTAURANT_ID));
        String[] proj = {ReviewDrafts.VERSION};
        long version = Cursors.firstLong(cp.query(uri, proj, null, null, null));
        if (version >= 0) {
            vals.put(ReviewDrafts.VERSION, version + 1);
            cp.update(uri, vals, null, null);
        } else {
            cp.insert(REVIEW_DRAFTS_URI, vals);
        }
    }

    /**
     * Download and update the details of Google Places that haven't been refreshed recently.
     */
    private void refreshRestaurants(Context context, ContentProviderClient cp)
            throws RemoteException {
        int rows = Content.getCount(context, RESTAURANTS_URI);
        int days = 30; // age when restaurants should be refreshed
        Uri uri = Uris.limit(RESTAURANTS_URI, rows / days + 1);
        String[] proj = {_ID, Restaurants.PLACE_ID};
        String sel = Restaurants.PLACE_ID + " IS NOT NULL AND "
                + Restaurants.PLACE_ID + " NOT LIKE 'NOT_FOUND_%' AND ("
                + Restaurants.REFRESHED_ON + " IS NULL OR "
                + Restaurants.REFRESHED_ON + " <= datetime('now', '-" + days + " days')) AND "
                + Restaurants.STATUS_ID + " = ?";
        String[] args = {String.valueOf(ACTIVE.id)};
        String order = Restaurants.REFRESHED_ON + " IS NULL DESC, " + Restaurants.REFRESHED_ON
                + ", " + _ID;
        Cursor c = cp.query(uri, proj, sel, args, order);
        Result[] results = null;
        try {
            results = RestaurantsRefreshService.refresh(new EasyCursor(c));
        } catch (IOException e) {
            Log.e(TAG, "refreshing restaurants", e);
            exception(e);
        }
        c.close();
        if (results != null) {
            for (Result result : results) {
                if (result.newReviews != null) {
                    for (int i = 0; i < result.newReviews.size(); i++) {
                        cp.insert(SYNCS_URI, Syncs.values(
                                result.newReviews.keyAt(i), result.newReviews.valueAt(i)));
                    }
                }
            }
        }
    }

    /**
     * Refresh the list of restaurants that are geofenced.
     */
    private void geofenceRestaurants(Context context, ContentProviderClient cp)
            throws RemoteException {
        GoogleApiClient client = Locations.client(context);
        if (client == null) {
            return;
        }
        /* remove existing geofences */
        PendingIntent intent = PendingIntent.getService(context, 0,
                new Intent(context, RestaurantGeofencingEventService.class), 0);
        PendingResult<Status> result = GeofencingApi.removeGeofences(client, intent);
        Status status = result.await();
        if (!status.isSuccess()) {
            String message = status.getStatusMessage();
            Log.e(TAG, "remove geofences failed: " + message);
            event("gms", "remove geofences failed", message);
        }
        /* add geofences if user wants to be notified */
        if (!Prefs.getStringSet(context, SHOW_NOTIFICATIONS)
                .contains(context.getString(R.string.at_restaurant_notifications_value))) {
            client.disconnect();
            return;
        }
        GeofencingRequest.Builder request = new GeofencingRequest.Builder();
        Uri uri = Uris.limit(Restaurants.CONTENT_URI, MAX_GEOFENCES);
        String[] proj = {_ID, Restaurants.LATITUDE, Restaurants.LONGITUDE};
        String sel = Restaurants.LATITUDE + " IS NOT NULL AND "
                + Restaurants.LONGITUDE + " IS NOT NULL AND "
                + Restaurants.GEOFENCE_NOTIFICATIONS + " = 1 AND " + Restaurants.STATUS_ID + " = ?";
        String[] args = {String.valueOf(ACTIVE.id)};
        EasyCursor c = new EasyCursor(cp.query(uri, proj, sel, args, _ID + " DESC"));
        while (c.moveToNext()) {
            request.addGeofence(new Geofence.Builder().setRequestId(String.valueOf(c.getLong(_ID)))
                    .setCircularRegion(c.getDouble(Restaurants.LATITUDE),
                            c.getDouble(Restaurants.LONGITUDE), GEOFENCE_RADIUS)
                    .setTransitionTypes(GEOFENCE_TRANSITION_ALL)
                    .setLoiteringDelay(15 * (int) MINUTE_IN_MILLIS)
                    .setNotificationResponsiveness(15 * (int) MINUTE_IN_MILLIS)
                    .setExpirationDuration(NEVER_EXPIRE).build());
        }
        if (c.getCount() > 0) {
            result = GeofencingApi.addGeofences(client, request.build(), intent);
            status = result.await();
            if (!status.isSuccess()) {
                String message = status.getStatusMessage();
                Log.e(TAG, "add geofences failed: " + message);
                event("gms", "add geofences failed", message);
            }
        }
        c.close();
        client.disconnect();
    }

    /**
     * Get the device's cloud ID and upload it to the server.
     *
     * @return null if the cloud ID could not be retrieved or sent to the server successfully
     */
    private String uploadCloudId(Context context) {
        for (int i = 0; i < BACKOFF_RETRIES; i++) {
            try {
                String id = GoogleCloudMessaging.getInstance(context).register(PROJECT_ID);
                Boolean synced = Server.syncCloudId(id);
                if (synced != null) {
                    return synced ? id : null;
                }
            } catch (IOException e) {
                Log.e(TAG, "registering with GCM", e);
                exception(e);
            }
            if (i + 1 < BACKOFF_RETRIES) {
                SystemClock.sleep((1 << i) * 1000); // wait and retry, register can error
                event("gcm", "register retry", i + 1);
            } else {
                event("gcm", "couldn't register after retries", BACKOFF_RETRIES);
            }
        }
        return null;
    }

    /**
     * Get the user's cloud notification key.
     *
     * @return null if the notification key could not be retrieved
     */
    private String getCloudNotificationKey(Context context, Account account, String cloudId) {
        if (AuthToken.isAvailable()) {
            try {
                String name = context.getPackageName() + ':' + account.name;
                String key = Gcm.getNotificationKey(PROJECT_ID, AuthToken.get(), name, cloudId);
                Log.v(null, "key:" + key); // todo return directly from func
                return key;
            } catch (RetrofitError e) {
                Log.e(TAG, "getting GCM notification key", e);
                exception(e);
            }
        }
        return null;
    }
}
