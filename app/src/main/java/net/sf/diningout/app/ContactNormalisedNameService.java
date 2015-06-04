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
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.text.TextUtils;

import net.sf.diningout.provider.Contract.Contacts;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.net.Uris;
import net.sf.sprockets.sql.SQLite;

import static android.provider.BaseColumns._ID;
import static net.sf.sprockets.app.SprocketsApplication.cr;

/**
 * Updates the normalised name for all contacts. Also adds a default color if a contact is
 * colorless.
 */
public class ContactNormalisedNameService extends IntentService {
    private static final String TAG = ContactNormalisedNameService.class.getSimpleName();

    public ContactNormalisedNameService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        ContentResolver cr = cr();
        ContentValues vals = new ContentValues(2);
        String[] proj = {_ID, Contacts.NAME, Contacts.COLOR};
        EasyCursor c = new EasyCursor(cr.query(Contacts.CONTENT_URI, proj, null, null, null));
        while (c.moveToNext()) {
            String name = c.getString(Contacts.NAME);
            if (!TextUtils.isEmpty(name)) {
                vals.put(Contacts.NORMALISED_NAME, SQLite.normalise(name));
            }
            if (c.isNull(Contacts.COLOR)) {
                vals.put(Contacts.COLOR, Contacts.defaultColor());
            }
            if (vals.size() > 0) {
                cr.update(Uris.appendId(Contacts.CONTENT_URI, c), vals, null, null);
                vals.clear();
            }
        }
        c.close();
    }
}
