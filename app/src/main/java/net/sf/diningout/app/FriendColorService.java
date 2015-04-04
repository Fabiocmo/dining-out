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
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.support.v7.graphics.Palette;
import android.support.v7.graphics.Palette.Swatch;
import android.util.Log;

import com.squareup.picasso.Picasso;

import net.sf.diningout.provider.Contract.Columns;
import net.sf.diningout.provider.Contract.Contacts;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.graphics.Palettes;
import net.sf.sprockets.net.Uris;

import java.io.IOException;

import static android.provider.BaseColumns._ID;
import static net.sf.diningout.data.Status.ACTIVE;
import static net.sf.sprockets.app.SprocketsApplication.cr;
import static net.sf.sprockets.gms.analytics.Trackers.exception;
import static net.sf.sprockets.graphics.Palettes.MAX_COLORS;

/**
 * Gets the most prominent {@link Columns#COLOR color} of a contact's photo. If {@link #EXTRA_ID}
 * is not provided, all contacts without a color will be updated. This is a low priority
 * operation and there will be a few seconds delay before each update.
 */
public class FriendColorService extends IntentService {
    /**
     * ID of the contact to get the photo color of.
     */
    public static final String EXTRA_ID = "intent.extra.ID";
    private static final String TAG = FriendColorService.class.getSimpleName();
    private static final long DELAY = 2500L;

    public FriendColorService() {
        super(TAG);
    }


    @Override
    protected void onHandleIntent(Intent intent) {
        long id = intent.getLongExtra(EXTRA_ID, 0L);
        Uri uri = id > 0 ? ContentUris.withAppendedId(Contacts.CONTENT_URI, id)
                : Contacts.CONTENT_URI;
        String[] proj = {_ID, Contacts.ANDROID_LOOKUP_KEY, Contacts.ANDROID_ID};
        String sel = Contacts.COLOR + " IS NULL AND " + Contacts.STATUS_ID + " = ?";
        String[] args = {String.valueOf(ACTIVE.id)};
        EasyCursor c = new EasyCursor(cr().query(uri, proj, sel, args, null));
        while (c.moveToNext()) {
            SystemClock.sleep(DELAY);
            updateColor(c.getInt(_ID), c.getString(Contacts.ANDROID_LOOKUP_KEY),
                    c.getLong(Contacts.ANDROID_ID));
        }
        c.close();
    }

    private void updateColor(long id, String lookupKey, long androidId) {
        if (lookupKey == null || androidId <= 0) {
            return;
        }
        try {
            Bitmap photo = Picasso.with(this)
                    .load(ContactsContract.Contacts.getLookupUri(androidId, lookupKey)).get();
            if (photo != null) {
                Swatch swatch = Palettes.getMostPopulousSwatch(Palette.generate(photo, MAX_COLORS));
                if (swatch != null) {
                    ContentValues vals = new ContentValues(1);
                    vals.put(Contacts.COLOR, swatch.getRgb());
                    cr().update(Uris.notifyChange(Contacts.CONTENT_URI, id, false), vals,
                            null, null);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "loading contact photo", e);
            exception(e);
        }
    }
}
