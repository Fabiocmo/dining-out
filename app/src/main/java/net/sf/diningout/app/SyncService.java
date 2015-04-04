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

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import net.sf.diningout.content.SyncAdapter;

/**
 * Provides a binder to the sync adapter.
 */
public class SyncService extends Service {
    private static SyncAdapter sAdapter;
    private static final Object LOCK = new Object();

    @Override
    public void onCreate() {
        super.onCreate();
        synchronized (LOCK) {
            if (sAdapter == null) {
                sAdapter = new SyncAdapter(getApplicationContext());
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return sAdapter.getSyncAdapterBinder();
    }
}
