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

package net.sf.diningout.app;

import android.content.ContentResolver;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;

import net.sf.diningout.R;
import net.sf.diningout.accounts.Accounts;
import net.sf.sprockets.app.VersionedApplication;
import net.sf.sprockets.gms.analytics.Trackers;
import net.sf.sprockets.preference.Prefs;

import org.apache.commons.io.FileUtils;

import java.io.File;

import static net.sf.diningout.BuildConfig.TRACKING_ID;
import static net.sf.diningout.preference.Keys.ALLOW_ANALYTICS;
import static net.sf.diningout.preference.Keys.CLOUD_ID;
import static net.sf.diningout.preference.Keys.MIGRATE_TO_PLACE_ID;
import static net.sf.diningout.provider.Contract.AUTHORITY;

/**
 * Performs application update tasks.
 */
public class AppApplication extends VersionedApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        /* prepare the analytics tracker */
        GoogleAnalytics ga = GoogleAnalytics.getInstance(this);
        ga.enableAutoActivityReports(this);
        if (!Prefs.getBoolean(this, ALLOW_ANALYTICS)) {
            ga.setAppOptOut(true);
        }
        Tracker tracker = ga.newTracker(R.xml.tracker);
        tracker.set("&tid", TRACKING_ID);
        Trackers.use(this, tracker);
        /* start migration tasks */
        if (Prefs.getBoolean(this, MIGRATE_TO_PLACE_ID)) {
            startService(new Intent(this, RestaurantsPlaceIdService.class));
        }
    }

    @Override
    public void onVersionChanged(int oldCode, String oldName, int newCode, String newName) {
        PreferenceManager.setDefaultValues(this, R.xml.preferences, true);
        if (Prefs.contains(this, CLOUD_ID)) { // need to re-register
            Prefs.remove(this, CLOUD_ID);
            ContentResolver.requestSync(Accounts.selected(), AUTHORITY, new Bundle());
        }
        if (oldCode < 100) { // delete pre-1.0.0 restaurant images
            File file = getExternalFilesDir(null);
            if (file != null) {
                FileUtils.deleteQuietly(new File(file, "images"));
            }
        }
        if (oldCode == 0) { // nothing else to do if run for the first time
            return;
        }
        if (oldCode < 107) { // get colors of existing photos
            startService(new Intent(this, RestaurantColorService.class));
            startService(new Intent(this, FriendColorService.class));
        }
        if (oldCode < 108) {
            Prefs.putBoolean(this, MIGRATE_TO_PLACE_ID, true);
        }
        if (oldCode < 109) {
            startService(new Intent(this, RestaurantsRefreshService.class));
        }
    }
}
