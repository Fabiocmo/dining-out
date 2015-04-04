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

import android.content.ContentUris;
import android.content.UriMatcher;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import net.sf.diningout.provider.Contract;
import net.sf.diningout.provider.Contract.Contacts;
import net.sf.diningout.provider.Contract.OpenDays;
import net.sf.diningout.provider.Contract.OpenHours;
import net.sf.diningout.provider.Contract.RestaurantPhotos;
import net.sf.diningout.provider.Contract.Restaurants;
import net.sf.diningout.provider.Contract.ReviewDrafts;
import net.sf.diningout.provider.Contract.ReviewDraftsJoinRestaurants;
import net.sf.diningout.provider.Contract.Reviews;
import net.sf.diningout.provider.Contract.ReviewsJoinAll;
import net.sf.diningout.provider.Contract.ReviewsJoinContacts;
import net.sf.diningout.provider.Contract.ReviewsJoinRestaurants;
import net.sf.diningout.provider.Contract.Syncs;
import net.sf.diningout.provider.Contract.SyncsJoinAll;
import net.sf.sprockets.content.DbContentProvider;
import net.sf.sprockets.database.sqlite.DbOpenHelper;

import java.io.File;
import java.io.FileNotFoundException;

import static android.content.UriMatcher.NO_MATCH;
import static android.os.ParcelFileDescriptor.MODE_READ_ONLY;
import static net.sf.diningout.provider.Contract.AUTHORITY;
import static net.sf.diningout.sql.Queries.RESTAURANT;
import static net.sf.sprockets.app.SprocketsApplication.cr;

/**
 * See {@link Contract} for the interface to this ContentProvider.
 */
public class AppContentProvider extends DbContentProvider {
    private static final String TAG = AppContentProvider.class.getSimpleName();
    private static final int CONTACTS = 100;
    private static final int CONTACT_ID = 110;
    private static final int RESTAURANTS = 200;
    private static final int RESTAURANT_ID = 210;
    private static final int OPEN_HOURS = 300;
    private static final int OPEN_DAYS = 400;
    private static final int RESTAURANT_PHOTOS = 500;
    private static final int RESTAURANT_PHOTO_ID = 510;
    private static final int REVIEWS = 600;
    private static final int REVIEW_ID = 610;
    private static final int REVIEWS_JOIN_RESTAURANTS = 700;
    private static final int REVIEW_JOIN_RESTAURANT_ID = 710;
    private static final int REVIEWS_JOIN_CONTACTS = 800;
    private static final int REVIEW_JOIN_CONTACT_ID = 810;
    private static final int REVIEWS_JOIN_ALL = 900;
    private static final int REVIEW_JOIN_ALL_ID = 910;
    private static final int REVIEW_DRAFTS = 1000;
    private static final int REVIEW_DRAFT_ID = 1010;
    private static final int REVIEW_DRAFTS_JOIN_RESTAURANTS = 1100;
    private static final int REVIEW_DRAFT_JOIN_RESTAURANT_ID = 1110;
    private static final int SYNCS = 1200;
    private static final int SYNC_ID = 1210;
    private static final int SYNCS_JOIN_ALL = 1300;
    private static final int SYNC_JOIN_ALL_ID = 1310;
    private static final UriMatcher sMatcher = new UriMatcher(NO_MATCH);

    static {
        sMatcher.addURI(AUTHORITY, "contact", CONTACTS);
        sMatcher.addURI(AUTHORITY, "contact/#", CONTACT_ID);
        sMatcher.addURI(AUTHORITY, "restaurant", RESTAURANTS);
        sMatcher.addURI(AUTHORITY, "restaurant/#", RESTAURANT_ID);
        sMatcher.addURI(AUTHORITY, "open_hour", OPEN_HOURS);
        sMatcher.addURI(AUTHORITY, "open_day", OPEN_DAYS);
        sMatcher.addURI(AUTHORITY, "restaurant_photo", RESTAURANT_PHOTOS);
        sMatcher.addURI(AUTHORITY, "restaurant_photo/#", RESTAURANT_PHOTO_ID);
        sMatcher.addURI(AUTHORITY, "review", REVIEWS);
        sMatcher.addURI(AUTHORITY, "review/#", REVIEW_ID);
        sMatcher.addURI(AUTHORITY, "review_join_restaurant", REVIEWS_JOIN_RESTAURANTS);
        sMatcher.addURI(AUTHORITY, "review_join_restaurant/#", REVIEW_JOIN_RESTAURANT_ID);
        sMatcher.addURI(AUTHORITY, "review_join_contact", REVIEWS_JOIN_CONTACTS);
        sMatcher.addURI(AUTHORITY, "review_join_contact/#", REVIEW_JOIN_CONTACT_ID);
        sMatcher.addURI(AUTHORITY, "review_join_all", REVIEWS_JOIN_ALL);
        sMatcher.addURI(AUTHORITY, "review_join_all/#", REVIEW_JOIN_ALL_ID);
        sMatcher.addURI(AUTHORITY, "review_draft", REVIEW_DRAFTS);
        sMatcher.addURI(AUTHORITY, "review_draft/#", REVIEW_DRAFT_ID);
        sMatcher.addURI(AUTHORITY, "review_draft_join_restaurant", REVIEW_DRAFTS_JOIN_RESTAURANTS);
        sMatcher.addURI(AUTHORITY, "review_draft_join_restaurant/#",
                REVIEW_DRAFT_JOIN_RESTAURANT_ID);
        sMatcher.addURI(AUTHORITY, "sync", SYNCS);
        sMatcher.addURI(AUTHORITY, "sync/#", SYNC_ID);
        sMatcher.addURI(AUTHORITY, "sync_join_all", SYNCS_JOIN_ALL);
        sMatcher.addURI(AUTHORITY, "sync_join_all/#", SYNC_JOIN_ALL_ID);
    }

    private SQLiteOpenHelper mHelper;

    @Override
    protected SQLiteOpenHelper getOpenHelper() {
        mHelper = new DbOpenHelper(getContext(), "dining-out-v100.db", 4);
        return mHelper;
    }

    @Override
    protected Sql translate(Uri uri) {
        int code = sMatcher.match(uri);
        if (code >= 0) {
            Sql sql = new Sql();
            switch (code) {
                case REVIEW_JOIN_RESTAURANT_ID:
                    sql.sel(ReviewsJoinRestaurants.REVIEW__ID + " = ?");
                case REVIEWS_JOIN_RESTAURANTS:
                    sql.table("review AS w").join("JOIN restaurant AS r ON w.restaurant_id = r._id")
                            .notify(Reviews.CONTENT_URI);
                    break;
                case REVIEW_JOIN_CONTACT_ID:
                    sql.sel(ReviewsJoinContacts.REVIEW__ID + " = ?");
                case REVIEWS_JOIN_CONTACTS:
                    sql.table("review AS w").join("LEFT JOIN contact AS c ON w.contact_id = c._id")
                            .notify(Reviews.CONTENT_URI);
                    break;
                case REVIEW_JOIN_ALL_ID:
                    sql.sel(ReviewsJoinAll.REVIEW__ID + " = ?");
                case REVIEWS_JOIN_ALL:
                    sql.table("review AS w")
                            .join("JOIN restaurant AS r ON w.restaurant_id = r._id "
                                    + "LEFT JOIN contact AS c ON w.contact_id = c._id")
                            .notify(Reviews.CONTENT_URI);
                    break;
                case REVIEW_DRAFT_JOIN_RESTAURANT_ID:
                    sql.sel(ReviewDrafts.RESTAURANT_ID + " = ?");
                case REVIEW_DRAFTS_JOIN_RESTAURANTS:
                    sql.table("review_draft AS d")
                            .join("JOIN restaurant AS r ON d.restaurant_id = r._id")
                            .notify(ReviewDrafts.CONTENT_URI);
                    break;
                case SYNC_JOIN_ALL_ID:
                    sql.sel(SyncsJoinAll.SYNC__ID + " = ?");
                case SYNCS_JOIN_ALL:
                    sql.table("sync AS s");
                    sql.join("LEFT JOIN review AS w ON s.type_id = 4 AND s.object_id = w._id "
                            + "LEFT JOIN restaurant AS r ON w.restaurant_id = r._id "
                            + "LEFT JOIN contact AS c ON s.type_id = 1 AND s.object_id = c._id "
                            + "OR s.type_id = 4 AND w.contact_id = c._id");
                    sql.notify(Syncs.CONTENT_URI);
                    break;
            }
            return sql;
        }
        return super.translate(uri);
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        mHelper.getWritableDatabase().execSQL(RESTAURANT.query(method), new Object[]{arg, arg});
        cr().notifyChange(ContentUris.withAppendedId(Restaurants.CONTENT_URI, Long.parseLong(arg)),
                null, false);
        return null;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        switch (sMatcher.match(uri)) {
            case RESTAURANT_PHOTOS:
                File file = RestaurantPhotos.file(
                        Long.parseLong(uri.getQueryParameter(RestaurantPhotos.RESTAURANT_ID)));
                if (file != null) {
                    return ParcelFileDescriptor.open(file, MODE_READ_ONLY);
                }
                break;
        }
        return super.openFile(uri, mode);
    }

    @Override
    public String getType(Uri uri) {
        switch (sMatcher.match(uri)) {
            case CONTACTS:
                return Contacts.CONTENT_TYPE;
            case CONTACT_ID:
                return Contacts.CONTENT_ITEM_TYPE;
            case RESTAURANTS:
                return Restaurants.CONTENT_TYPE;
            case RESTAURANT_ID:
                return Restaurants.CONTENT_ITEM_TYPE;
            case OPEN_HOURS:
                return OpenHours.CONTENT_TYPE;
            case OPEN_DAYS:
                return OpenDays.CONTENT_TYPE;
            case RESTAURANT_PHOTOS:
                return RestaurantPhotos.CONTENT_TYPE;
            case RESTAURANT_PHOTO_ID:
                return RestaurantPhotos.CONTENT_ITEM_TYPE;
            case REVIEWS:
                return Reviews.CONTENT_TYPE;
            case REVIEW_ID:
                return Reviews.CONTENT_ITEM_TYPE;
            case REVIEWS_JOIN_RESTAURANTS:
                return ReviewsJoinRestaurants.CONTENT_TYPE;
            case REVIEW_JOIN_RESTAURANT_ID:
                return ReviewsJoinRestaurants.CONTENT_ITEM_TYPE;
            case REVIEWS_JOIN_CONTACTS:
                return ReviewsJoinContacts.CONTENT_TYPE;
            case REVIEW_JOIN_CONTACT_ID:
                return ReviewsJoinContacts.CONTENT_ITEM_TYPE;
            case REVIEWS_JOIN_ALL:
                return ReviewsJoinAll.CONTENT_TYPE;
            case REVIEW_JOIN_ALL_ID:
                return ReviewsJoinAll.CONTENT_ITEM_TYPE;
            case REVIEW_DRAFTS:
                return ReviewDrafts.CONTENT_TYPE;
            case REVIEW_DRAFT_ID:
                return ReviewDrafts.CONTENT_ITEM_TYPE;
            case REVIEW_DRAFTS_JOIN_RESTAURANTS:
                return ReviewDraftsJoinRestaurants.CONTENT_TYPE;
            case REVIEW_DRAFT_JOIN_RESTAURANT_ID:
                return ReviewDraftsJoinRestaurants.CONTENT_ITEM_TYPE;
            case SYNCS:
                return Syncs.CONTENT_TYPE;
            case SYNC_ID:
                return Syncs.CONTENT_ITEM_TYPE;
            case SYNCS_JOIN_ALL:
                return SyncsJoinAll.CONTENT_TYPE;
            case SYNC_JOIN_ALL_ID:
                return SyncsJoinAll.CONTENT_ITEM_TYPE;
        }
        return null;
    }
}
