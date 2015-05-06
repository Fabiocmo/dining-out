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

package net.sf.diningout.app.ui;

import android.support.v4.widget.DrawerLayout;

import net.sf.sprockets.app.ui.NavigationDrawerActivity;
import net.sf.sprockets.preference.Prefs;

import static android.view.Gravity.START;
import static net.sf.diningout.preference.Keys.App.APP;
import static net.sf.diningout.preference.Keys.App.NAVIGATION_DRAWER_OPENED;

/**
 * Opens the navigation drawer on first launch and fades out when finishing if not the task root.
 */
public class BaseNavigationDrawerActivity extends NavigationDrawerActivity {
    @Override
    public NavigationDrawerActivity setDrawerLayout(DrawerLayout layout) {
        super.setDrawerLayout(layout);
        if (!Prefs.getBoolean(this, APP, NAVIGATION_DRAWER_OPENED)) {
            layout.openDrawer(START);
            Prefs.putBoolean(this, APP, NAVIGATION_DRAWER_OPENED, true);
        }
        return this;
    }

    @Override
    public void finish() {
        super.finish();
        if (!isTaskRoot()) {
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }
}
