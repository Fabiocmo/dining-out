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

import android.app.Notification;
import android.app.Notification.BigTextStyle;
import android.app.Notification.Builder;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.squareup.picasso.Picasso;

import net.sf.diningout.R;
import net.sf.diningout.app.ui.FriendsActivity;
import net.sf.diningout.app.ui.NotificationsActivity;
import net.sf.diningout.app.ui.RestaurantActivity;
import net.sf.diningout.data.Review;
import net.sf.diningout.data.Review.Type;
import net.sf.diningout.data.Sync;
import net.sf.diningout.provider.Contract.Contacts;
import net.sf.diningout.provider.Contract.RestaurantPhotos;
import net.sf.diningout.provider.Contract.Restaurants;
import net.sf.diningout.provider.Contract.Reviews;
import net.sf.diningout.provider.Contract.ReviewsJoinAll;
import net.sf.diningout.provider.Contract.ReviewsJoinContacts;
import net.sf.diningout.provider.Contract.Syncs;
import net.sf.diningout.widget.ReviewAdapter;
import net.sf.sprockets.app.ContentService;
import net.sf.sprockets.content.Managers;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.net.Uris;
import net.sf.sprockets.preference.Prefs;
import net.sf.sprockets.util.Elements;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import static android.app.PendingIntent.FLAG_CANCEL_CURRENT;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.Intent.ACTION_EDIT;
import static android.content.Intent.ACTION_INSERT;
import static android.content.Intent.ACTION_VIEW;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.net.Uri.EMPTY;
import static android.provider.BaseColumns._ID;
import static com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_DWELL;
import static com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_ENTER;
import static com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_EXIT;
import static java.lang.Boolean.FALSE;
import static net.sf.diningout.app.RequestCodes.UPDATE_GEOFENCE_NOTIFICATIONS;
import static net.sf.diningout.app.SyncsReadService.EXTRA_ACTIVITIES;
import static net.sf.diningout.app.ui.RestaurantActivity.EXTRA_ID;
import static net.sf.diningout.app.ui.RestaurantActivity.EXTRA_TAB;
import static net.sf.diningout.app.ui.RestaurantActivity.TAB_NOTES;
import static net.sf.diningout.app.ui.RestaurantActivity.TAB_PRIVATE;
import static net.sf.diningout.app.ui.RestaurantActivity.TAB_PUBLIC;
import static net.sf.diningout.data.Review.Type.GOOGLE;
import static net.sf.diningout.data.Review.Type.PRIVATE;
import static net.sf.diningout.data.Status.ACTIVE;
import static net.sf.diningout.preference.Keys.App.APP;
import static net.sf.diningout.preference.Keys.App.NEW_SYNC_IDS;
import static net.sf.diningout.preference.Keys.RINGTONE;
import static net.sf.sprockets.app.ContentService.EXTRA_NOTIFICATION_ID;
import static net.sf.sprockets.app.ContentService.EXTRA_NOTIFICATION_TAG;
import static net.sf.sprockets.app.ContentService.EXTRA_VALUES;
import static net.sf.sprockets.app.SprocketsApplication.cr;
import static net.sf.sprockets.gms.analytics.Trackers.event;
import static net.sf.sprockets.sql.SQLite.alias;
import static net.sf.sprockets.sql.SQLite.alias_;
import static net.sf.sprockets.sql.SQLite.aliased_;
import static net.sf.sprockets.sql.SQLite.millis;

/**
 * Methods for posting system notifications.
 */
public class Notifications {
    private static final String TAG = Notifications.class.getSimpleName();
    private static final String TAG_SYNC = "sync";
    private static final String TAG_GEOFENCE = "geofence";

    private Notifications() {
    }

    /**
     * Post a notification for any unread server changes.
     */
    public static void sync(Context context) {
        ContentResolver cr = cr();
        String[] proj = {_ID, Syncs.TYPE_ID, Syncs.OBJECT_ID, millis(Syncs.ACTION_ON)};
        String sel = Syncs.STATUS_ID + " = ?";
        String[] args = {String.valueOf(ACTIVE.id)};
        String order = Syncs.ACTION_ON + " DESC";
        EasyCursor c = new EasyCursor(cr.query(Syncs.CONTENT_URI, proj, sel, args, order));
        int count = c.getCount();
        if (count > 0) {
            int users = 0;
            int reviews = 0;
            Review review = null; // newest, headlines notification
            Set<CharSequence> lines = new LinkedHashSet<>(); // ignore dupes
            long when = 0L;
            Bitmap icon = null;
            Set<String> syncIds = new HashSet<>(count);
            /* get the change details */
            while (c.moveToNext()) {
                Uri photo = null;
                switch (Sync.Type.get(c.getInt(Syncs.TYPE_ID))) {
                    case USER:
                        photo = user(context, cr, c.getLong(Syncs.OBJECT_ID), lines, icon);
                        if (photo != null) {
                            users++;
                            syncIds.add(String.valueOf(c.getLong(_ID)));
                        }
                        break;
                    case REVIEW:
                        Pair<Uri, Review> pair =
                                review(context, cr, c.getLong(Syncs.OBJECT_ID), lines, icon);
                        photo = pair.first;
                        if (pair.second != null) {
                            reviews++;
                            syncIds.add(String.valueOf(c.getLong(_ID)));
                            if (review == null) {
                                review = pair.second;
                            }
                        }
                        break;
                }
                if (when == 0) {
                    when = c.getLong(Syncs.ACTION_ON);
                }
                if (photo != null && photo != EMPTY) {
                    icon = photo(context, photo);
                }
            }
            int size = lines.size();
            if (size > 0) { // have something to notify about
                CharSequence bigText = null;
                CharSequence summary = null;
                Intent activity;
                if (users > 0 && reviews == 0) {
                    activity = new Intent(context, FriendsActivity.class);
                } else if (users == 0 && (reviews == 1 || size == 1)) {
                    bigText = ReviewAdapter.comments(review.comments);
                    summary = context.getString(R.string.n_stars, review.rating);
                    activity = new Intent(context, RestaurantActivity.class)
                            .putExtra(EXTRA_ID, review.restaurantId);
                    if (review.type == GOOGLE) {
                        activity.putExtra(EXTRA_TAB, TAB_PUBLIC);
                    }
                } else {
                    activity = new Intent(context, NotificationsActivity.class);
                }
                notify(context, lines, bigText, summary, when, icon, users + reviews, activity);
                Prefs.putStringSet(context, APP, NEW_SYNC_IDS, syncIds);
                event("notification", "notify", "sync", size);
            } else { // sync object was deleted
                Managers.notification(context).cancel(TAG_SYNC, 0);
                context.startService(new Intent(context, SyncsReadService.class));
            }
        }
        c.close();
    }

    /**
     * Add a message to the list about the user.
     *
     * @return photo if available, {@link Uri#EMPTY} if not needed, or null if the user wasn't found
     */
    private static Uri user(Context context, ContentResolver cr, long id, Set<CharSequence> lines,
                            Bitmap icon) {
        Uri photo = null;
        String[] proj = {Contacts.ANDROID_LOOKUP_KEY, Contacts.ANDROID_ID, Contacts.NAME};
        String sel = Contacts.STATUS_ID + " = ?";
        String[] args = {String.valueOf(ACTIVE.id)};
        EasyCursor c = new EasyCursor(cr.query(ContentUris.withAppendedId(Contacts.CONTENT_URI, id),
                proj, sel, args, null));
        if (c.moveToFirst()) {
            String name = c.getString(Contacts.NAME);
            if (name == null) {
                name = context.getString(R.string.non_contact);
            }
            lines.add(context.getString(R.string.new_friend, name));
            photo = EMPTY;
            if (icon == null) {
                String androidKey = c.getString(Contacts.ANDROID_LOOKUP_KEY);
                long androidId = c.getLong(Contacts.ANDROID_ID);
                if (androidKey != null && androidId > 0) {
                    photo = ContactsContract.Contacts.getLookupUri(androidId, androidKey);
                }
            }
        }
        c.close();
        return photo;
    }

    /**
     * Add a message to the list about the review.
     *
     * @return photo if available, {@link Uri#EMPTY} if not needed, or null if the review wasn't
     * found, and the review or null if it wasn't found
     */
    private static Pair<Uri, Review> review(Context context, ContentResolver cr, long id,
                                            Set<CharSequence> lines, Bitmap icon) {
        Uri photo = null;
        Review review = null;
        String[] proj = {Reviews.RESTAURANT_ID, Reviews.TYPE_ID, Reviews.COMMENTS,
                alias(ReviewsJoinAll.REVIEW_RATING), alias_(ReviewsJoinAll.RESTAURANT_NAME),
                alias_(ReviewsJoinAll.CONTACT_NAME)};
        String sel = ReviewsJoinAll.REVIEW_STATUS_ID + " = ? AND "
                + ReviewsJoinAll.RESTAURANT_STATUS_ID + " = ?";
        String[] args = Elements.toStrings(ACTIVE.id, ACTIVE.id);
        EasyCursor c = new EasyCursor(cr.query(
                ContentUris.withAppendedId(ReviewsJoinAll.CONTENT_URI, id), proj, sel, args, null));
        if (c.moveToFirst()) {
            review = Reviews.first(c, false);
            String restaurant = c.getString(aliased_(ReviewsJoinAll.RESTAURANT_NAME));
            switch (review.type) {
                case PRIVATE:
                    String contact = c.getString(aliased_(ReviewsJoinAll.CONTACT_NAME));
                    if (contact == null) {
                        contact = context.getString(R.string.non_contact);
                    }
                    lines.add(context.getString(R.string.new_friend_review, contact, restaurant));
                    break;
                case GOOGLE:
                    lines.add(context.getString(R.string.new_public_review, restaurant));
                    break;
            }
            photo = icon == null ? RestaurantPhotos.uriForRestaurant(review.restaurantId) : EMPTY;
        }
        c.close();
        return Pair.create(photo, review);
    }

    private static void notify(Context context, Set<CharSequence> lines, CharSequence bigText,
                               CharSequence summary, long when, Bitmap icon, int totalItems,
                               Intent activity) {
        Iterator<CharSequence> linesIter = lines.iterator();
        CharSequence title = linesIter.next();
        BigTextStyle style = new BigTextStyle();
        if (lines.size() == 1) {
            if (bigText != null) {
                style.bigText(bigText);
            }
            if (summary != null) {
                style.setSummaryText(summary);
            }
        } else { // add lines after title
            linesIter.remove();
            StringBuilder text = new StringBuilder(lines.size() * 48);
            while (linesIter.hasNext()) {
                text.append(context.getString(R.string.sync_item, linesIter.next()));
            }
            style.bigText(text);
        }
        TaskStackBuilder task = TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(activity.addFlags(FLAG_ACTIVITY_NEW_TASK));
        /* don't use SyncsReadService when Android issue 41253 is fixed, flag for issue 61850 */
        PendingIntent content = PendingIntent.getService(context, 0,
                new Intent(context, SyncsReadService.class)
                        .putExtra(EXTRA_ACTIVITIES, task.getIntents()), FLAG_CANCEL_CURRENT);
        PendingIntent delete = PendingIntent.getService(context, 1,
                new Intent(context, SyncsReadService.class), FLAG_CANCEL_CURRENT);
        Builder notif = new Builder(context).setOnlyAlertOnce(true).setTicker(title)
                .setContentTitle(title).setStyle(style).setWhen(when)
                .setLargeIcon(icon).setSmallIcon(R.drawable.stat_logo)
                .setContentIntent(content).setAutoCancel(true).setDeleteIntent(delete);
        String ringtone = Prefs.getString(context, RINGTONE);
        if (!TextUtils.isEmpty(ringtone)) {
            notif.setSound(Uri.parse(ringtone));
        }
        if (totalItems > 1) {
            notif.setNumber(totalItems);
        }
        Managers.notification(context).notify(TAG_SYNC, 0, notif.build());
    }

    /**
     * Post a notification when entering the restaurant, update the restaurant when dwelling at it,
     * and cancel the notification when exiting the restaurant.
     */
    public static void geofence(Context context, int transition, long restaurantId) {
        switch (transition) {
            case GEOFENCE_TRANSITION_ENTER:
                event("restaurant", "enter");
                if (Restaurants.isOpen(restaurantId) == FALSE) {
                    break;
                }
                EasyCursor restaurant = restaurant(restaurantId);
                if (restaurant.moveToFirst()) {
                    String name = restaurant.getString(Restaurants.NAME);
                    BigTextStyle style = new BigTextStyle();
                    Review.Type reviewType = null;
                    EasyCursor review = review(restaurantId);
                    if (review.moveToFirst()) {
                        style.bigText(context.getString(R.string.review_metadata,
                                ReviewAdapter.name(context, review),
                                ReviewAdapter.time(context, review), review.getInt(Reviews.RATING))
                                + "\n" + ReviewAdapter.comments(review));
                        reviewType = Type.get(review.getInt(Reviews.TYPE_ID));
                    } else {
                        style.bigText(restaurant.getString(Restaurants.NOTES));
                    }
                    review.close();
                    Bitmap icon = photo(context, RestaurantPhotos.uriForRestaurant(restaurantId));
                    Notification notif = new Builder(context).setOnlyAlertOnce(true)
                            .setTicker(name).setContentTitle(name).setStyle(style)
                            .setLargeIcon(icon).setSmallIcon(R.drawable.stat_logo)
                            .setContentIntent(view(context, restaurantId, false, reviewType))
                            .addAction(R.drawable.ic_action_location_off,
                                    context.getString(R.string.ignore_restaurant),
                                    ignore(context, restaurantId)).build();
                    Managers.notification(context).notify(TAG_GEOFENCE, (int) restaurantId, notif);
                }
                restaurant.close();
                break;
            case GEOFENCE_TRANSITION_DWELL:
                event("restaurant", "dwell");
                if (Restaurants.isOpen(restaurantId) == FALSE) {
                    break;
                }
                visiting(restaurantId, true);
                break;
            case GEOFENCE_TRANSITION_EXIT:
                event("restaurant", "exit");
                restaurant = restaurant(restaurantId);
                if (restaurant.moveToFirst() && restaurant.getInt(Restaurants.VISITING) != 0) {
                    visiting(restaurantId, false);
                    if (!alreadyReviewed(restaurantId)) {
                        String name = restaurant.getString(Restaurants.NAME);
                        Bitmap icon =
                                photo(context, RestaurantPhotos.uriForRestaurant(restaurantId));
                        Notification notif = new Builder(context).setOnlyAlertOnce(true)
                                .setTicker(name).setContentTitle(name)
                                .setContentText(context.getString(R.string.comments_hint))
                                .setLargeIcon(icon).setSmallIcon(R.drawable.stat_logo)
                                .setContentIntent(view(context, restaurantId, true, PRIVATE))
                                .setAutoCancel(true).addAction(R.drawable.ic_action_location_off,
                                        context.getString(R.string.ignore_restaurant),
                                        ignore(context, restaurantId)).build();
                        Managers.notification(context)
                                .notify(TAG_GEOFENCE, (int) restaurantId, notif);
                    } else {
                        Managers.notification(context).cancel(TAG_GEOFENCE, (int) restaurantId);
                    }
                } else {
                    Managers.notification(context).cancel(TAG_GEOFENCE, (int) restaurantId);
                }
                restaurant.close();
                break;
        }
    }

    private static EasyCursor restaurant(long id) {
        Uri uri = ContentUris.withAppendedId(Restaurants.CONTENT_URI, id);
        String[] proj = {Restaurants.NAME, Restaurants.NOTES, Restaurants.VISITING};
        return new EasyCursor(cr().query(uri, proj, null, null, null));
    }

    private static EasyCursor review(long restaurantId) {
        Uri uri = Uris.limit(ReviewsJoinContacts.CONTENT_URI, 1);
        String[] proj = {Reviews.TYPE_ID, Reviews.CONTACT_ID, Reviews.AUTHOR_NAME, Reviews.COMMENTS,
                Reviews.RATING, millis(Reviews.WRITTEN_ON), Contacts.NAME};
        String sel = Reviews.RESTAURANT_ID + " = ? AND length(" + Reviews.COMMENTS + ") > 0 AND "
                + ReviewsJoinContacts.REVIEW_STATUS_ID + " = ?";
        String[] args = Elements.toStrings(restaurantId, ACTIVE.id);
        String order = Reviews.TYPE_ID + ", " + Reviews.WRITTEN_ON + " DESC, "
                + ReviewsJoinContacts.REVIEW__ID + " DESC";
        return new EasyCursor(cr().query(uri, proj, sel, args, order));
    }

    private static Bitmap photo(Context context, Uri uri) {
        try {
            return Picasso.with(context).load(uri)
                    .resizeDimen(android.R.dimen.notification_large_icon_width,
                            android.R.dimen.notification_large_icon_height).centerCrop().get();
        } catch (IOException e) { // contact or own restaurant may not have photo
            Log.w(TAG, "loading contact or restaurant photo", e);
        }
        return null;
    }

    private static PendingIntent view(Context context, long restaurantId, boolean addReview,
                                      Review.Type reviewType) {
        Intent intent = new Intent(addReview ? ACTION_INSERT : ACTION_VIEW,
                ContentUris.withAppendedId(Restaurants.CONTENT_URI, restaurantId),
                context, RestaurantActivity.class)
                .addFlags(FLAG_ACTIVITY_NEW_TASK).putExtra(EXTRA_ID, restaurantId)
                .putExtra(EXTRA_TAB, reviewType == PRIVATE ? TAB_PRIVATE
                        : reviewType == GOOGLE ? TAB_PUBLIC : TAB_NOTES);
        return TaskStackBuilder.create(context).addNextIntentWithParentStack(intent)
                .getPendingIntent(0, FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent ignore(Context context, long restaurantId) {
        Uri uri = ContentUris.withAppendedId(Restaurants.CONTENT_URI, restaurantId);
        ContentValues vals = new ContentValues(2);
        vals.put(Restaurants.GEOFENCE_NOTIFICATIONS, 0);
        vals.put(Restaurants.DIRTY, 1);
        Intent service = new Intent(ACTION_EDIT, uri, context, ContentService.class)
                .putExtra(EXTRA_VALUES, vals).putExtra(EXTRA_NOTIFICATION_TAG, TAG_GEOFENCE)
                .putExtra(EXTRA_NOTIFICATION_ID, (int) restaurantId);
        return PendingIntent.getService(context, UPDATE_GEOFENCE_NOTIFICATIONS, service, 0);
    }

    private static void visiting(long restaurantId, boolean yes) {
        ContentValues vals = new ContentValues(1);
        vals.put(Restaurants.VISITING, yes ? 1 : 0);
        cr().update(ContentUris.withAppendedId(Restaurants.CONTENT_URI, restaurantId), vals,
                null, null);
    }

    private static boolean alreadyReviewed(long restaurantId) {
        String[] proj = {"1"};
        String sel = Reviews.RESTAURANT_ID + " = ? AND " + Reviews.TYPE_ID + " = ? AND "
                + Reviews.CONTACT_ID + " IS NULL AND "
                + Reviews.WRITTEN_ON + " >= datetime('now', '-4 hours')";
        String[] args = Elements.toStrings(restaurantId, PRIVATE.id);
        Cursor c = cr().query(Uris.limit(Reviews.CONTENT_URI, 1), proj, sel, args, null);
        int count = c.getCount();
        c.close();
        return count > 0;
    }
}
