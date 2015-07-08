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
import android.os.Bundle;

import com.google.android.gms.gcm.GcmListenerService;

import net.sf.diningout.accounts.Accounts;

import static net.sf.diningout.data.CloudMessage.ACTION_KEY;
import static net.sf.diningout.data.CloudMessage.ACTION_REQUEST_SYNC;
import static net.sf.diningout.provider.Contract.AUTHORITY;
import static net.sf.sprockets.content.Content.SYNC_EXTRAS_DOWNLOAD;
import static net.sf.sprockets.gms.analytics.Trackers.event;

/**
 * Reacts to messages received from the cloud.
 */
public class CloudService extends GcmListenerService {
    @Override
    public void onMessageReceived(String from, Bundle data) {
        super.onMessageReceived(from, data);
        if (ACTION_REQUEST_SYNC.equals(data.getString(ACTION_KEY))) {
            Bundle extras = new Bundle();
            extras.putBoolean(SYNC_EXTRAS_DOWNLOAD, true);
            ContentResolver.requestSync(Accounts.selected(), AUTHORITY, extras);
            event("cloud", "receive sync request");
        }
    }
}
