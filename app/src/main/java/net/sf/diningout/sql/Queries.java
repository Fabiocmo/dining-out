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

package net.sf.diningout.sql;

import org.apache.commons.dbutils.QueryLoader;

import java.io.IOException;
import java.util.Map;

import static net.sf.sprockets.gms.analytics.Trackers.exception;

/**
 * Get queries defined in the XML files in this package.
 */
public class Queries {
    private static final String DIR =
            '/' + Queries.class.getPackage().getName().replace('.', '/') + '/';
    public static final Queries RESTAURANT = new Queries("restaurant");

    private final Map<String, String> mQueries;

    private Queries(String file) {
        file = DIR + file + ".xml";
        try {
            mQueries = QueryLoader.instance().load(file);
        } catch (IOException e) {
            exception(e);
            throw new IllegalArgumentException(file + " is not accessible", e);
        }
    }

    /**
     * Get the query identified by the key.
     */
    public String query(String key) {
        return mQueries.get(key);
    }
}
