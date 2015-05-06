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
import android.net.Uri;
import android.text.format.DateUtils;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import net.sf.diningout.R;
import net.sf.diningout.picasso.Placeholders;
import net.sf.diningout.preference.Keys;
import net.sf.diningout.provider.Contract.Columns;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.view.ViewHolder;

import butterknife.InjectView;

import static android.text.format.DateUtils.FORMAT_ABBREV_ALL;
import static android.text.format.DateUtils.MINUTE_IN_MILLIS;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static net.sf.diningout.picasso.Transformations.BL;
import static net.sf.sprockets.app.SprocketsApplication.context;
import static net.sf.sprockets.util.MeasureUnit.MILE;

/**
 * Views for a restaurant in a list and methods to update their contents.
 */
public class RestaurantHolder extends ViewHolder {
    @InjectView(R.id.photo)
    public ImageView photo;

    @InjectView(R.id.name)
    public TextView name;

    @InjectView(R.id.detail)
    public TextView detail;

    private final Context mContext;
    private final int mCellHeight;

    public RestaurantHolder() {
        mContext = context();
        mCellHeight = mContext.getResources().getDimensionPixelSize(R.dimen.grid_row_height);
    }

    @Override
    protected RestaurantHolder newInstance() {
        return new RestaurantHolder();
    }

    /**
     * Load the URI, resize it according to the GridView measurements, and set it as the
     * restaurant's photo. Use the cursor's {@link Columns#COLOR color} for the placeholder.
     */
    RestaurantHolder photo(Uri uri, GridView view, EasyCursor c) {
        photo(Picasso.with(mContext).load(uri), view, c);
        return this;
    }

    /**
     * Download the image at the URL, resize it according to the GridView measurements, and set it
     * as the restaurant's photo.
     */
    RestaurantHolder photo(String url, GridView view) {
        photo(Picasso.with(mContext).load(url), view, null);
        return this;
    }

    private void photo(RequestCreator req, GridView view, EasyCursor c) {
        req.resize(view.getColumnWidth(), mCellHeight).centerCrop().transform(BL)
                .placeholder(Placeholders.rect(c)).into(photo);
    }

    /**
     * Set the restaurant's name.
     */
    RestaurantHolder name(String name) {
        this.name.setText(name);
        return this;
    }

    /**
     * Set the restaurant's rating, hiding it when rating <= 0.
     */
    RestaurantHolder rating(float rating) {
        if (rating > 0.0f) {
            detail.setText(mContext.getString(R.string.rating, rating));
            detail.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_action_important_small, 0,
                    0, 0);
            detail.setVisibility(VISIBLE);
        } else {
            detail.setVisibility(GONE);
        }
        return this;
    }

    /**
     * Set the last time the restaurant was visited.
     *
     * @param millis 0 if the restaurant has never been visited
     */
    RestaurantHolder visit(long millis) {
        if (millis > 0) {
            long now = System.currentTimeMillis();
            detail.setText(now - millis > MINUTE_IN_MILLIS
                    ? DateUtils.getRelativeTimeSpanString(millis, now, 0, FORMAT_ABBREV_ALL)
                    : mContext.getString(R.string.recent_time));
        } else {
            detail.setText(R.string.never);
        }
        detail.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_action_time_small, 0, 0, 0);
        detail.setVisibility(VISIBLE);
        return this;
    }

    /**
     * Set the distance to the restaurant, hiding it when distance < 0.
     */
    RestaurantHolder distance(double distance) {
        if (distance >= 0.0) {
            detail.setText(mContext.getString(Keys.isDistanceUnit(MILE) ? R.string.distance_mi
                    : R.string.distance_km, distance));
            detail.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_action_location_found_small, 0, 0, 0);
            detail.setVisibility(VISIBLE);
        } else {
            detail.setVisibility(GONE);
        }
        return this;
    }
}
