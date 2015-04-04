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

package net.sf.diningout.net;

import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;

import net.sf.sprockets.preference.Prefs;

import java.io.IOException;

import static net.sf.diningout.net.Server.BACKOFF_RETRIES;
import static net.sf.diningout.preference.Keys.ACCOUNT_NAME;
import static net.sf.sprockets.app.SprocketsApplication.context;
import static net.sf.sprockets.gms.analytics.Trackers.event;
import static net.sf.sprockets.gms.analytics.Trackers.exception;

/**
 * Obtains Google authentication tokens. Always verify that a token {@link #isAvailable()} before
 * attempting to {@link #get()} it.
 */
public class AuthToken {
    private static final String TAG = AuthToken.class.getSimpleName();
    private static final String SCOPE =
            "audience:server:client_id:77419503291.apps.googleusercontent.com";
    private static String sToken;

    private AuthToken() {
    }

    /**
     * True if the current auth token is available to {@link #get()}.
     */
    public static boolean isAvailable() {
        Context context = context();
        String account = Prefs.getString(context, ACCOUNT_NAME);
        if (!TextUtils.isEmpty(account)) {
            for (int i = 0; i < BACKOFF_RETRIES; i++) {
                try {
                    sToken = GoogleAuthUtil.getTokenWithNotification(context, account, SCOPE, null);
                    return true;
                } catch (GoogleAuthException | IOException e) {
                    Log.e(TAG, "getting auth token", e);
                    exception(e);
                    if (e instanceof GoogleAuthException) {
                        return false; // user needs to fix authentication, don't retry
                    }
                }
                if (i + 1 < BACKOFF_RETRIES) {
                    SystemClock.sleep((1 << i) * 1000); // wait and retry, occasional network error
                    event("gms", "auth token retry", i + 1);
                } else {
                    event("gms", "no auth token after retries", BACKOFF_RETRIES);
                }
            }
        }
        return false;
    }

    /**
     * Get the current auth token if it {@link #isAvailable()}.
     */
    public static String get() {
        return sToken;
    }
}
