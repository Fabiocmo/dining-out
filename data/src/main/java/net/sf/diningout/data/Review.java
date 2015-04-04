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

import net.sf.sprockets.util.Elements;

public class Review extends Synced {
    /**
     * Global value for the restaurant this review was written for.
     */
    public long restaurantId;
    public Type type;

    /**
     * Global value for user that wrote this review or 0 for own review.
     */
    public long userId;
    public String comments;
    public int rating;

    /**
     * UTC datetime in ISO format.
     */
    public String writtenOn;

    /**
     * Reset the fields in this class (but not the parent) to their default values.
     */
    public Review clear() {
        restaurantId = 0L;
        type = null;
        userId = 0L;
        comments = null;
        rating = 0;
        writtenOn = null;
        return this;
    }

    public enum Type {
        PRIVATE(1), GOOGLE(2);

        public final int id;

        Type(int id) {
            this.id = id;
        }

        /**
         * Get the type with the ID.
         *
         * @return null if the ID is invalid
         */
        public static Type get(int id) {
            return Elements.get(values(), id - 1);
        }
    }
}
