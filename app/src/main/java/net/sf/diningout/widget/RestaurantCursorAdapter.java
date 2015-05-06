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

package net.sf.diningout.widget;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.widget.AbsListView;
import android.widget.GridView;

import net.sf.diningout.R;
import net.sf.diningout.provider.Contract.RestaurantPhotos;
import net.sf.diningout.provider.Contract.Restaurants;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.view.ViewHolder;
import net.sf.sprockets.widget.ResourceEasyCursorAdapter;

import static android.provider.BaseColumns._ID;

/**
 * Translates restaurant rows to Views.
 */
public class RestaurantCursorAdapter extends ResourceEasyCursorAdapter {
    private final GridView mView;
    private long mSelectedId;

    /**
     * True if the cursor has the rating column.
     */
    private boolean mHasRating;

    /**
     * True if the cursor has the last_visit_on column.
     */
    private boolean mHasVisit;

    /**
     * True if the cursor has the distance column.
     */
    private boolean mHasDistance;

    public RestaurantCursorAdapter(GridView view) {
        super(view.getContext(), R.layout.restaurants_adapter, null, 0);
        mView = view;
    }

    /**
     * Set the restaurant's View as {@link AbsListView#setItemChecked(int, boolean) checked} and
     * scroll to it.
     */
    public RestaurantCursorAdapter setSelectedId(long restaurantId) {
        mSelectedId = restaurantId;
        return this;
    }

    @Override
    public Cursor swapCursor(Cursor newCursor) {
        Cursor oldCursor = super.swapCursor(newCursor);
        if (newCursor != null) {
            EasyCursor c = (EasyCursor) newCursor;
            mHasRating = c.getColumnIndex(Restaurants.RATING) >= 0;
            mHasVisit = c.getColumnIndex(Restaurants.LAST_VISIT_ON) >= 0;
            mHasDistance = c.getColumnIndex(Restaurants.DISTANCE) >= 0;
            if (mSelectedId > 0) {
                int oldPosition = c.getPosition();
                c.moveToPosition(-1);
                while (c.moveToNext()) {
                    if (c.getLong(_ID) == mSelectedId) {
                        mSelectedId = 0L; // don't set checked again
                        int pos = c.getPosition();
                        mView.setItemChecked(pos, true);
                        mView.setSelection(pos);
                        break;
                    }
                }
                c.moveToPosition(oldPosition);
            }
        }
        return oldCursor;
    }

    @Override
    public void bindView(View view, Context context, EasyCursor c) {
        RestaurantHolder restaurant = ViewHolder.get(view, RestaurantHolder.class);
        restaurant.photo(RestaurantPhotos.uriForRestaurant(c.getLong(_ID)), mView, c)
                .name(c.getString(Restaurants.NAME));
        if (mHasRating) {
            restaurant.rating(c.getFloat(Restaurants.RATING));
        } else if (mHasVisit) {
            restaurant.visit(c.getLong(Restaurants.LAST_VISIT_ON));
        } else if (mHasDistance) {
            restaurant.distance(!c.isNull(Restaurants.DISTANCE)
                    ? Math.sqrt(c.getDouble(Restaurants.DISTANCE)) : -1.0);
        }
    }
}
