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

package net.sf.diningout.data;

public class User extends Synced {
    /**
     * Base64 encoded SHA-512 hash of the user's email address.
     */
    public String emailHash;
    /**
     * True if the app user is following this user.
     */
    public boolean isFollowing;

    /**
     * Reset the fields in this class (but not the parent) to their default values.
     */
    public User clear() {
        emailHash = null;
        isFollowing = false;
        return this;
    }
}
