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

package net.sf.diningout.app.ui;

import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;

import net.sf.diningout.R;
import net.sf.diningout.app.ui.FriendsFragment.Listener;

import butterknife.InjectView;
import butterknife.Optional;

import static android.view.Gravity.START;

/**
 * Displays contacts to follow and invite to join.
 */
public class FriendsActivity extends BaseNavigationDrawerActivity implements Listener {
    @Optional
    @InjectView(R.id.root)
    DrawerLayout mDrawerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.friends_activity);
        if (mDrawerLayout != null) {
            setDrawerLayout(mDrawerLayout);
        }
    }

    @Override
    public boolean onFriendsOptionsMenu() {
        return mDrawerLayout == null || !mDrawerLayout.isDrawerOpen(START);
    }

    @Override
    public void onFriendClick(int total) {
    }
}
