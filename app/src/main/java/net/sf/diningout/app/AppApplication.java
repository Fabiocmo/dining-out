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

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.google.android.gms.analytics.GoogleAnalytics;

import net.sf.diningout.R;
import net.sf.sprockets.app.VersionedApplication;
import net.sf.sprockets.gms.analytics.Trackers;
import net.sf.sprockets.preference.Prefs;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.HashSet;

import static net.sf.diningout.BuildConfig.TRACKING_ID;
import static net.sf.diningout.preference.Keys.ALLOW_ANALYTICS;
import static net.sf.diningout.preference.Keys.App.ACCOUNT_INITIALISED;
import static net.sf.diningout.preference.Keys.App.ACCOUNT_NAME;
import static net.sf.diningout.preference.Keys.App.APP;
import static net.sf.diningout.preference.Keys.App.CLOUD_ID;
import static net.sf.diningout.preference.Keys.App.INSTALL_ID;
import static net.sf.diningout.preference.Keys.App.LAST_SYNC;
import static net.sf.diningout.preference.Keys.App.MIGRATE_TO_PLACE_ID;
import static net.sf.diningout.preference.Keys.App.NAVIGATION_DRAWER_OPENED;
import static net.sf.diningout.preference.Keys.App.ONBOARDED;
import static net.sf.diningout.preference.Keys.DISTANCE_UNIT;
import static net.sf.diningout.preference.Keys.SHOW_NOTIFICATIONS;

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
        Trackers.use(this, ga.newTracker(R.xml.tracker)).set("&tid", TRACKING_ID);
        /* start migration tasks */
        if (Prefs.getBoolean(this, APP, MIGRATE_TO_PLACE_ID)) {
            startService(new Intent(this, RestaurantsPlaceIdService.class));
        }
    }

    @Override
    public void onVersionChanged(int oldCode, String oldName, int newCode, String newName) {
        PreferenceManager.setDefaultValues(this, R.xml.preferences, true);
        if (oldCode > 0 && oldCode < 110) { // first so that prefs are moved to right location
            HashSet<String> notifs = new HashSet<>(Prefs.getStringSet(this, SHOW_NOTIFICATIONS));
            notifs.add(getString(R.string.at_restaurant_notifications_value));
            Prefs.putStringSet(this, SHOW_NOTIFICATIONS, notifs);
            migrateAppPrefs();
        }
        TokenService.refreshTokens(this);
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
            Prefs.putBoolean(this, APP, MIGRATE_TO_PLACE_ID, true);
        }
        if (oldCode < 109) {
            startService(new Intent(this, RestaurantsRefreshService.class));
        }
        if (oldCode < 112) {
            startService(new Intent(this, ContactNormalisedNameService.class));
        }
        if (oldCode < 113 && TextUtils.isEmpty(Prefs.getString(this, DISTANCE_UNIT))) {
            Prefs.putString(this, DISTANCE_UNIT, getString(R.string.automatic_value));
        }
    }

    /**
     * Move non-user setting preferences from default to app preferences.
     */
    private void migrateAppPrefs() {
        SharedPreferences def = Prefs.get(this);
        Editor editDef = def.edit();
        SharedPreferences app = Prefs.get(this, APP);
        Editor editApp = app.edit();
        if (def.contains(ACCOUNT_INITIALISED)) {
            editApp.putBoolean(ACCOUNT_INITIALISED, def.getBoolean(ACCOUNT_INITIALISED, false));
            editDef.remove(ACCOUNT_INITIALISED);
        }
        if (def.contains(ACCOUNT_NAME)) {
            editApp.putString(ACCOUNT_NAME, def.getString(ACCOUNT_NAME, null));
            editDef.remove(ACCOUNT_NAME);
        }
        if (def.contains(CLOUD_ID)) {
            editApp.putString(CLOUD_ID, def.getString(CLOUD_ID, null));
            editDef.remove(CLOUD_ID);
        }
        if (def.contains(INSTALL_ID)) {
            editApp.putLong(INSTALL_ID, def.getLong(INSTALL_ID, 0L));
            editDef.remove(INSTALL_ID);
        }
        if (def.contains(LAST_SYNC)) {
            editApp.putLong(LAST_SYNC, def.getLong(LAST_SYNC, 0L));
            editDef.remove(LAST_SYNC);
        }
        if (def.contains(MIGRATE_TO_PLACE_ID)) {
            editApp.putBoolean(MIGRATE_TO_PLACE_ID, def.getBoolean(MIGRATE_TO_PLACE_ID, false));
            editDef.remove(MIGRATE_TO_PLACE_ID);
        }
        if (def.contains(NAVIGATION_DRAWER_OPENED)) {
            editApp.putBoolean(NAVIGATION_DRAWER_OPENED,
                    def.getBoolean(NAVIGATION_DRAWER_OPENED, false));
            editDef.remove(NAVIGATION_DRAWER_OPENED);
        }
        if (def.contains(ONBOARDED)) {
            editApp.putBoolean(ONBOARDED, def.getBoolean(ONBOARDED, false));
            editDef.remove(ONBOARDED);
        }
        editDef.apply();
        editApp.apply();
    }
}
