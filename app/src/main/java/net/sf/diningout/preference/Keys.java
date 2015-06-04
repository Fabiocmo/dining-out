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

package net.sf.diningout.preference;

import android.text.TextUtils;

import net.sf.sprockets.preference.Prefs;
import net.sf.sprockets.util.Locales;
import net.sf.sprockets.util.MeasureUnit;

import static net.sf.sprockets.app.SprocketsApplication.context;

/**
 * Keys for SharedPreferences values.
 */
public class Keys {
    /**
     * True if the user allows anonymous usage statistics to be sent.
     */
    public static final String ALLOW_ANALYTICS = "allow_analytics";

    /**
     * Type of units to display distances in.
     */
    public static final String DISTANCE_UNIT = "distance_unit";

    /**
     * URI path for the selected notification ringtone or empty if no ringtone should be played.
     */
    public static final String RINGTONE = "ringtone";

    /**
     * Set of notification types that should be shown.
     */
    public static final String SHOW_NOTIFICATIONS = "show_notifications";

    private Keys() {
    }

    /**
     * True if distances should be displayed in the unit.
     */
    public static boolean isDistanceUnit(MeasureUnit unit) {
        String pref = Prefs.getString(context(), DISTANCE_UNIT);
        if (TextUtils.isEmpty(pref)) {
            pref = Locales.getDistanceUnit().getSubtype();
        }
        return pref.equals(unit.getSubtype());
    }

    /**
     * Keys for app data, not user settings.
     */
    public static class App {
        /**
         * Preferences file name.
         */
        public static final String APP = "app";

        /**
         * True if the account has been initialised.
         */
        public static final String ACCOUNT_INITIALISED = "account_initialised";

        /**
         * Name of selected account.
         */
        public static final String ACCOUNT_NAME = "account_name";

        /**
         * Used by the server to contact this device through cloud messaging.
         */
        public static final String CLOUD_ID = "cloud_id";

        /**
         * Used to contact the user's other devices through cloud messaging.
         */
        public static final String CLOUD_NOTIFICATION_KEY = "cloud_notification_key";

        /**
         * Identifier for this installation.
         */
        public static final String INSTALL_ID = "install_id";

        /**
         * Epoch milliseconds of the last full sync.
         */
        public static final String LAST_SYNC = "last_sync";

        /**
         * True if need to get missing restaurant place IDs.
         */
        public static final String MIGRATE_TO_PLACE_ID = "migrate_to_place_id";

        /**
         * True if the navigation drawer has been opened at least once.
         */
        public static final String NAVIGATION_DRAWER_OPENED = "navigation_drawer_opened";

        /**
         * Most recently posted sync notification items.
         */
        public static final String NEW_SYNC_IDS = "new_sync_ids";

        /**
         * True if the user has completed the onboarding process.
         */
        public static final String ONBOARDED = "onboarded";

        private App() {
        }
    }
}
