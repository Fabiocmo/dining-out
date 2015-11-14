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

import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Loader;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.TextView;

import com.google.common.base.Predicate;

import net.sf.diningout.R;
import net.sf.diningout.widget.PoweredByGoogle;
import net.sf.diningout.widget.RestaurantHolder;
import net.sf.diningout.widget.RestaurantPlacesAdapter;
import net.sf.sprockets.app.ui.SprocketsFragment;
import net.sf.sprockets.content.GooglePlacesLoader;
import net.sf.sprockets.google.LocalPlacesParams;
import net.sf.sprockets.google.Place;
import net.sf.sprockets.google.Places.Response;
import net.sf.sprockets.google.PlacesParams;
import net.sf.sprockets.view.Views;

import java.util.List;

import butterknife.Bind;
import icepick.Icicle;
import in.srain.cube.views.GridViewWithHeaderAndFooter;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.AdapterView.INVALID_POSITION;
import static net.sf.diningout.provider.Contract.Restaurants.SEARCH_FIELDS;
import static net.sf.diningout.provider.Contract.Restaurants.SEARCH_TYPES;
import static net.sf.sprockets.google.Places.Params.RANK_BY_DISTANCE;
import static net.sf.sprockets.google.Places.Response.STATUS_OK;
import static net.sf.sprockets.google.Places.Response.STATUS_UNKNOWN_ERROR;
import static net.sf.sprockets.google.Places.Response.STATUS_ZERO_RESULTS;
import static net.sf.sprockets.google.Places.URL_NEARBY_SEARCH;
import static net.sf.sprockets.view.animation.Interpolators.ANTI_OVER;

/**
 * Displays a list of nearby restaurants after {@link #filter(Predicate)} is called.
 */
public class RestaurantsNearbyFragment extends SprocketsFragment
        implements LoaderCallbacks<Response<List<Place>>>, OnItemClickListener {
    @Nullable
    @Bind(R.id.header)
    TextView mHeader;

    @Bind(R.id.progress)
    View mProgress;

    @Bind(R.id.empty)
    ViewStub mEmptyStub;

    @Bind(R.id.list)
    GridViewWithHeaderAndFooter mGrid;

    @Icicle
    String mSearch;

    private TextView mEmpty;
    private RestaurantPlacesAdapter mAdapter;
    private Predicate<Place> mFilter;
    private Listener mListener;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (activity instanceof Listener) {
            mListener = (Listener) activity;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        return inflater.inflate(R.layout.restaurants_nearby, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (mHeader != null) {
            if (!TextUtils.isEmpty(mSearch)) { // loader data is search results
                mHeader.setText(R.string.search_results_title);
            }
        }
        mGrid.addFooterView(new PoweredByGoogle(a));
        mAdapter = new RestaurantPlacesAdapter(mGrid);
        mGrid.setAdapter(mAdapter);
        mGrid.setOnItemClickListener(this);
    }

    /**
     * Only display restaurants for which the filter returns true.
     *
     * @param filter may be null to not apply a filter
     */
    void filter(Predicate<Place> filter) {
        mFilter = filter;
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public Loader<Response<List<Place>>> onCreateLoader(int id, Bundle args) {
        mProgress.setVisibility(VISIBLE);
        mProgress.animate().alpha(1.0f);
        PlacesParams params = new LocalPlacesParams(a).addTypes(SEARCH_TYPES);
        if (TextUtils.isEmpty(mSearch)) {
            params.rankBy(RANK_BY_DISTANCE);
        } else {
            params.keyword(mSearch);
        }
        if (mFilter != null) {
            params.placeFilter(mFilter);
        }
        return new GooglePlacesLoader<>(a, URL_NEARBY_SEARCH, params, SEARCH_FIELDS);
    }

    @Override
    public void onLoadFinished(Loader<Response<List<Place>>> loader, Response<List<Place>> resp) {
        if (mGrid == null) {
            return;
        }
        final String status = resp != null ? resp.getStatus() : STATUS_UNKNOWN_ERROR;
        switch (status) {
            case STATUS_OK:
                mAdapter.swapPlaces(resp.getResult());
                if (mListener != null) {
                    int pos = mGrid.getCheckedItemPosition();
                    if (pos != INVALID_POSITION) { // restore state from when it was first clicked
                        mListener.onRestaurantClick(mAdapter.getItem(pos));
                    }
                }
                break;
            case STATUS_ZERO_RESULTS:
                if (mEmpty == null) {
                    mEmpty = (TextView) mEmptyStub.inflate();
                }
                mEmpty.setText(mSearch != null ? Html.fromHtml(
                        getString(R.string.search_results_empty, TextUtils.htmlEncode(mSearch)))
                        : getString(R.string.none_found));
                break;
        }
        if (mProgress.getVisibility() == VISIBLE) { // swap progress bar with grid or empty card
            mProgress.animate().alpha(0.0f).withEndAction(new Runnable() {
                @Override
                public void run() {
                    if (mProgress != null) {
                        mProgress.setVisibility(GONE);
                        results(status);
                    }
                }
            });
        } else if (mGrid.getAlpha() < 1.0f) {
            results(status);
        }
    }

    /**
     * Fade in the GridView or show the empty card if there weren't any results.
     */
    private void results(String status) {
        switch (status) {
            case STATUS_OK:
                Views.gone(mEmpty); // in case previously shown
                mGrid.animate().alpha(1.0f).withLayer();
                break;
            case STATUS_ZERO_RESULTS:
                Views.visible(mEmpty);
                break;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Place place = mAdapter.getItem(position);
        ((RestaurantHolder) view.getTag()).animateAddress(place.getVicinity());
        if (mListener != null) {
            mListener.onRestaurantClick(place);
        }
    }

    /**
     * Search for nearby restaurants with the name.
     */
    void search(String name) {
        if (mHeader != null && TextUtils.isEmpty(mSearch)) { // flip to search results first time
            mHeader.animate().rotationXBy(360.0f).setInterpolator(ANTI_OVER).setDuration(1200L)
                    .setStartDelay(300L);
            mHeader.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mHeader != null) {
                        mHeader.setText(R.string.search_results_title);
                    }
                }
            }, 900L); // swap text when flipped over (half way through rotation)
        }
        mSearch = name;
        mGrid.animate().alpha(0.0f).withLayer().withEndAction(new Runnable() {
            @Override
            public void run() {
                if (mGrid != null) {
                    mGrid.smoothScrollToPosition(0);
                    Views.gone(mEmpty);
                    getLoaderManager().restartLoader(0, null, RestaurantsNearbyFragment.this);
                }
            }
        });
    }

    @Override
    public void onLoaderReset(Loader<Response<List<Place>>> loader) {
        mAdapter.swapPlaces(null);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     * Receives notifications for {@link RestaurantsNearbyFragment} events.
     */
    interface Listener {
        /**
         * The restaurant was clicked.
         */
        void onRestaurantClick(Place place);
    }
}
