/*
 * Copyright 2014-2015 pushbit <pushbit@gmail.com>
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

package net.sf.diningout.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.util.SparseArray;
import android.view.View;
import android.widget.ListView;

import net.sf.diningout.R;
import net.sf.sprockets.app.ui.BaseNavigationDrawerFragment;
import net.sf.sprockets.app.ui.NavigationDrawerActivity;
import net.sf.sprockets.content.Intents;
import net.sf.sprockets.net.Uris;

import java.util.Collections;

import static android.content.Intent.ACTION_SENDTO;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.view.Gravity.START;

/**
 * Displays the navigation drawer items and starts the associated Activities when clicked.
 */
public class NavigationDrawerFragment extends BaseNavigationDrawerFragment {
    private static final SparseArray<Class<? extends Activity>> sItems = new SparseArray<>(4);

    static {
        sItems.put(R.string.restaurants_title, RestaurantsActivity.class);
        sItems.put(R.string.friends_title, FriendsActivity.class);
        sItems.put(R.string.notifications_title, NotificationsActivity.class);
        sItems.put(R.id.settings, SettingsActivity.class);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            setItems(R.array.navigation_drawer_items);
            int i = sItems.indexOfValue(a.getClass());
            if (i >= 0) { // Activity is in navigation
                setSelectedItemResId(sItems.keyAt(i));
            }
            showSettings(true).showFeedback(true).showRate(true);
        }
    }

    @Override
    public void onListItemClick(ListView list, View view, int position, long id, int resId) {
        super.onListItemClick(list, view, position, id, resId);
        final NavigationDrawerActivity a = a();
        DrawerLayout layout = a.getDrawerLayout();
        if (layout != null) {
            layout.closeDrawer(START);
        }
        if (resId == R.id.feedback) { // special external case
            final Intent intent = new Intent(ACTION_SENDTO,
                    Uris.mailto(Collections.singletonList(getString(R.string.feedback_destination)),
                            null, null, getString(R.string.feedback_subject), null));
            if (Intents.hasActivity(a, intent)) {
                view.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (isAdded()) {
                            startActivity(intent);
                        }
                    }
                }, layout != null ? 300L : 0L); // start Activity after drawer closes
            }
            return;
        } else if (resId == R.id.rate) { // parent takes care of it
            return;
        }
        /* start associated Activity */
        final Class<? extends Activity> src = a.getClass();
        final Class<? extends Activity> dest = sItems.get(resId);
        if (dest != src) {
            a.setOneTimeDrawerActionDelay(300L); // finish fade to dest before ActionBar restored
            view.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (isAdded()) {
                        startActivity(new Intent(a, dest).addFlags(FLAG_ACTIVITY_CLEAR_TOP));
                        if (src != RestaurantsActivity.class) {
                            a.finish();
                        }
                        a.overridePendingTransition(android.R.anim.fade_in,
                                android.R.anim.fade_out);
                    }
                }
            }, layout != null ? 300L : 0L);
        }
    }
}
