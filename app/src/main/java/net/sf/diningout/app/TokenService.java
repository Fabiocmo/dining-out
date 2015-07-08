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

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.gms.iid.InstanceIDListenerService;

import net.sf.diningout.accounts.Accounts;
import net.sf.sprockets.preference.Prefs;

import static android.os.Bundle.EMPTY;
import static net.sf.diningout.preference.Keys.App.APP;
import static net.sf.diningout.preference.Keys.App.CLOUD_ID;
import static net.sf.diningout.provider.Contract.AUTHORITY;
import static net.sf.sprockets.gms.analytics.Trackers.event;

/**
 * Removes any existing cloud ID and requests a new one.
 */
public class TokenService extends InstanceIDListenerService {
    @Override
    public void onTokenRefresh() {
        super.onTokenRefresh();
        refreshTokens(this);
        event("gms", "token refresh");
    }

    static void refreshTokens(Context context) {
        SharedPreferences prefs = Prefs.get(context, APP);
        if (prefs.contains(CLOUD_ID)) {
            prefs.edit().remove(CLOUD_ID).apply();
            ContentResolver.requestSync(Accounts.selected(), AUTHORITY, EMPTY);
        }
    }
}
