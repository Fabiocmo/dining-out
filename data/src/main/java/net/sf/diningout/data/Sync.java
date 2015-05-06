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

package net.sf.diningout.data;

import net.sf.sprockets.util.Elements;

/**
 * Action taken by someone (or the system) on an object that should be synchronised across users or
 * devices.
 *
 * @param <T> type of object that was acted on
 */
public class Sync<T extends Synced> {
    /**
     * ID on the server.
     */
    public long globalId;

    /**
     * Global value for user that performed this action or 0 for own or system actions.
     */
    public long userId;
    public T object;
    public Action action;

    /**
     * UTC datetime in ISO format.
     */
    public String actionOn;

    /**
     * Objects that can be synchronised.
     */
    public enum Type {
        USER(1), RESTAURANT(2), VISIT(3), REVIEW(4), RESTAURANT_PHOTO(5), REVIEW_DRAFT(6);

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

    /**
     * Changes to objects that can be synchronised.
     */
    public enum Action {
        INSERT(1), UPDATE(2), DELETE(3), MERGE(4);

        public final int id;

        Action(int id) {
            this.id = id;
        }

        /**
         * Get the action with the ID.
         *
         * @return null if the ID is invalid
         */
        public static Action get(int id) {
            return Elements.get(values(), id - 1);
        }
    }
}
