/*
 * Copyright 2015 pushbit <pushbit@gmail.com>
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

public class OpenHour {
    public enum Type {
        OPEN(1), CLOSE(2);

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
