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

package net.sf.diningout.data;

/**
 * Object that is synchronised with the server.
 */
public abstract class Synced {
    /**
     * ID on a device or 0 if not available.
     */
    public long localId;

    /**
     * ID on the server or 0 if not available.
     */
    public long globalId;
    public Status status;

    /**
     * True if this needs to be synchronised with the server.
     */
    public boolean dirty;

    /**
     * Number of the change that requires synchronising this with the server.
     */
    public long version;
}
