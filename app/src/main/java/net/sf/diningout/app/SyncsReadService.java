/*
 * Copyright 2014 pushbit <pushbit@gmail.com>
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
import android.content.ContentValues;
import android.content.Intent;
import android.os.Parcelable;

import net.sf.diningout.provider.Contract.Syncs;

import java.util.Arrays;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static net.sf.diningout.data.Status.ACTIVE;
import static net.sf.diningout.data.Status.INACTIVE;
import static net.sf.sprockets.app.SprocketsApplication.cr;

/**
 * Marks all syncs as read. {@link #EXTRA_ACTIVITIES} and
 * {@link #onStartCommand(Intent, int, int) onStartCommand} will be removed after Android issue
 * 41253 is fixed.
 */
public class SyncsReadService extends IntentService {
    /**
     * Activities to start.
     */
    public static final String EXTRA_ACTIVITIES = "intent.extra.ACTIVITIES";
    private static final String TAG = SyncsReadService.class.getSimpleName();

    public SyncsReadService() {
        super(TAG);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            Parcelable[] activities = intent.getParcelableArrayExtra(EXTRA_ACTIVITIES);
            if (activities != null) {
                Intent[] intents = Arrays.copyOf(activities, activities.length, Intent[].class);
                for (Intent i : intents) { // TaskStackBuilder in API 16 doesn't add to parents
                    i.addFlags(FLAG_ACTIVITY_NEW_TASK);
                }
                startActivities(intents);
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        ContentValues vals = new ContentValues(1);
        vals.put(Syncs.STATUS_ID, INACTIVE.id);
        String sel = Syncs.STATUS_ID + " = ?";
        String[] args = {String.valueOf(ACTIVE.id)};
        cr().update(Syncs.CONTENT_URI, vals, sel, args);
    }
}
