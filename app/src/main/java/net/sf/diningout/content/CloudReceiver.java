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

package net.sf.diningout.content;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import net.sf.diningout.accounts.Accounts;

import static com.google.android.gms.gcm.GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE;
import static net.sf.diningout.data.CloudMessage.ACTION_KEY;
import static net.sf.diningout.data.CloudMessage.ACTION_REQUEST_SYNC;
import static net.sf.diningout.provider.Contract.AUTHORITY;
import static net.sf.sprockets.content.Content.SYNC_EXTRAS_DOWNLOAD;

/**
 * Receives sync messages from the server and requests a download sync.
 */
public class CloudReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String type = GoogleCloudMessaging.getInstance(context).getMessageType(intent);
        if (MESSAGE_TYPE_MESSAGE.equals(type)
                && ACTION_REQUEST_SYNC.equals(intent.getStringExtra(ACTION_KEY))) {
            Bundle extras = new Bundle();
            extras.putBoolean(SYNC_EXTRAS_DOWNLOAD, true);
            ContentResolver.requestSync(Accounts.selected(), AUTHORITY, extras);
        }
    }
}
