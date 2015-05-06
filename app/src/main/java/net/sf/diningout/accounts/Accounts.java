/*
 * Copyright 2013-2014 pushbit <pushbit@gmail.com>
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

package net.sf.diningout.accounts;

import android.accounts.Account;
import android.text.TextUtils;

import net.sf.sprockets.preference.Prefs;

import static com.google.android.gms.auth.GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE;
import static net.sf.diningout.preference.Keys.App.ACCOUNT_NAME;
import static net.sf.diningout.preference.Keys.App.APP;
import static net.sf.sprockets.app.SprocketsApplication.context;

/**
 * Utility methods for working with Accounts.
 */
public class Accounts {
    /**
     * Selected account.
     */
    private static Account sAccount;

    private Accounts() {
    }

    /**
     * Get the selected account.
     *
     * @return null if no account is selected
     */
    public static Account selected() {
        if (sAccount == null) {
            String name = Prefs.getString(context(), APP, ACCOUNT_NAME);
            if (!TextUtils.isEmpty(name)) {
                sAccount = new Account(name, GOOGLE_ACCOUNT_TYPE);
            }
        }
        return sAccount;
    }
}
