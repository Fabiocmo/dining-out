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

import android.app.Notification.BigTextStyle;
import android.app.Notification.Builder;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
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
import net.sf.diningout.data.Sync;
import net.sf.diningout.provider.Contract.Contacts;
import net.sf.diningout.provider.Contract.RestaurantPhotos;
import net.sf.diningout.provider.Contract.Reviews;
import net.sf.diningout.provider.Contract.ReviewsJoinAll;
import net.sf.diningout.provider.Contract.Syncs;
import net.sf.diningout.widget.ReviewAdapter;
import net.sf.sprockets.content.Managers;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.preference.Prefs;
import net.sf.sprockets.util.StringArrays;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import static android.app.Notification.DEFAULT_LIGHTS;
import static android.app.Notification.DEFAULT_VIBRATE;
import static android.app.PendingIntent.FLAG_CANCEL_CURRENT;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.net.Uri.EMPTY;
import static net.sf.diningout.app.SyncsReadService.EXTRA_ACTIVITIES;
import static net.sf.diningout.app.ui.RestaurantActivity.EXTRA_ID;
import static net.sf.diningout.app.ui.RestaurantActivity.EXTRA_TAB;
import static net.sf.diningout.app.ui.RestaurantActivity.TAB_PUBLIC;
import static net.sf.diningout.data.Review.Type.GOOGLE;
import static net.sf.diningout.data.Status.ACTIVE;
import static net.sf.diningout.preference.Keys.RINGTONE;
import static net.sf.diningout.preference.Keys.VIBRATE;
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
    private static final int ID_SYNC = 0;

    private Notifications() {
    }

    /**
     * Post a notification for any unread server changes.
     */
    public static void sync(Context context) {
        ContentResolver cr = cr();
        String[] proj = {Syncs.TYPE_ID, Syncs.OBJECT_ID, millis(Syncs.ACTION_ON)};
        String sel = Syncs.STATUS_ID + " = ?";
        String[] args = {String.valueOf(ACTIVE.id)};
        String order = Syncs.ACTION_ON + " DESC";
        EasyCursor c = new EasyCursor(cr.query(Syncs.CONTENT_URI, proj, sel, args, order));
        if (c.getCount() > 0) {
            int users = 0;
            int reviews = 0;
            Review review = null; // newest, headlines notification
            Set<CharSequence> lines = new LinkedHashSet<>(); // ignore dupes
            long when = 0L;
            Bitmap icon = null;
            /* get the change details */
            while (c.moveToNext()) {
                Uri photo = null;
                switch (Sync.Type.get(c.getInt(Syncs.TYPE_ID))) {
                    case USER:
                        photo = user(context, cr, c.getLong(Syncs.OBJECT_ID), lines, icon);
                        if (photo != null) {
                            users++;
                        }
                        break;
                    case REVIEW:
                        Pair<Uri, Review> pair =
                                review(context, cr, c.getLong(Syncs.OBJECT_ID), lines, icon);
                        photo = pair.first;
                        if (pair.second != null) {
                            reviews++;
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
                    try {
                        icon = Picasso.with(context).load(photo).resizeDimen(
                                android.R.dimen.notification_large_icon_width,
                                android.R.dimen.notification_large_icon_height).centerCrop().get();
                    } catch (IOException e) { // contact or own restaurant may not have photo
                        Log.w(TAG, "loading contact or restaurant photo", e);
                    }
                }
            }
            if (!lines.isEmpty()) { // have something to notify about
                CharSequence bigText = null;
                CharSequence summary = null;
                int items = users + reviews;
                Intent activity;
                if (users > 0 && reviews == 0) {
                    activity = new Intent(context, FriendsActivity.class);
                } else if (users == 0 && (reviews == 1 || lines.size() == 1)) {
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
                bigText(context, ID_SYNC, lines, bigText, summary, when, icon, items, activity);
                event("notification", "notify", "items", items);
            } else { // sync object was deleted
                Managers.notification(context).cancel(ID_SYNC);
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
        String[] args = StringArrays.from(ACTIVE.id, ACTIVE.id);
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

    /**
     * Show a {@link BigTextStyle} notification that starts the Activity.
     *
     * @param id     one of the ID_* constants in this class
     * @param number total number of items, may be more than number of lines
     */
    private static void bigText(Context context, int id, Set<CharSequence> lines,
                                CharSequence bigText, CharSequence summary, long when, Bitmap icon,
                                int number, Intent activity) {
        int defaults = DEFAULT_LIGHTS;
        if (Prefs.getBoolean(context, VIBRATE)) {
            defaults |= DEFAULT_VIBRATE;
        }
        CharSequence title = lines.iterator().next();
        BigTextStyle style = new BigTextStyle();
        if (lines.size() == 1) {
            if (bigText != null) {
                style.bigText(bigText);
            }
            if (summary != null) {
                style.setSummaryText(summary);
            }
        } else { // add lines after title
            lines.remove(title);
            StringBuilder text = new StringBuilder(lines.size() * 48);
            for (CharSequence line : lines) {
                text.append(context.getString(R.string.sync_item, line));
            }
            style.bigText(text);
        }
        TaskStackBuilder task = TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(activity.addFlags(FLAG_ACTIVITY_NEW_TASK));
        PendingIntent content;
        if (id == ID_SYNC) { // remove after Android issue 41253 is fixed
            Intent read = new Intent(context, SyncsReadService.class)
                    .putExtra(EXTRA_ACTIVITIES, task.getIntents());
            content = PendingIntent.getService(context, 1, read, FLAG_CANCEL_CURRENT);
        } else {
            content = task.getPendingIntent(0, FLAG_CANCEL_CURRENT); // flag for Android issue 61850
        }
        Builder notif = new Builder(context).setDefaults(defaults).setOnlyAlertOnce(true)
                .setTicker(title).setContentTitle(title).setStyle(style).setWhen(when)
                .setLargeIcon(icon).setSmallIcon(R.drawable.stat_logo).setContentIntent(content)
                .setAutoCancel(true);
        String ringtone = Prefs.getString(context, RINGTONE);
        if (!TextUtils.isEmpty(ringtone)) {
            notif.setSound(Uri.parse(ringtone));
        }
        if (number > 1) {
            notif.setNumber(number);
        }
        if (id == ID_SYNC) {
            Intent read = new Intent(context, SyncsReadService.class);
            notif.setDeleteIntent(PendingIntent.getService(context, 0, read, FLAG_CANCEL_CURRENT));
        }
        Managers.notification(context).notify(id, notif.build());
    }
}
