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

import android.accounts.Account;
import android.os.Bundle;
import android.preference.ListPreference;
import android.support.v4.widget.DrawerLayout;

import net.sf.diningout.R;
import net.sf.diningout.accounts.Accounts;
import net.sf.sprockets.app.ui.SprocketsPreferenceFragment;

import butterknife.InjectView;
import butterknife.Optional;

import static net.sf.diningout.preference.Keys.App.ACCOUNT_NAME;
import static net.sf.diningout.preference.Keys.DISTANCE_UNIT;
import static net.sf.sprockets.util.MeasureUnit.KILOMETER;
import static net.sf.sprockets.util.MeasureUnit.MILE;

/**
 * Displays app settings.
 */
public class SettingsActivity extends BaseNavigationDrawerActivity {
    @Optional
    @InjectView(R.id.root)
    DrawerLayout mDrawerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (mDrawerLayout != null) {
            setDrawerLayout(mDrawerLayout);
        }
    }

    /**
     * Adds dynamic values that aren't defined in the preferences resource.
     */
    public static class SettingsFragment extends SprocketsPreferenceFragment {
        @Override
        public void addPreferencesFromResource(int preferencesResId) {
            super.addPreferencesFromResource(preferencesResId);
            Account account = Accounts.selected();
            if (account != null) {
                findPreference(ACCOUNT_NAME).setSummary(account.name);
            }
            ((ListPreference) findPreference(DISTANCE_UNIT))
                    .setEntryValues(new String[]{"", KILOMETER.getSubtype(), MILE.getSubtype()});
        }
    }
}
