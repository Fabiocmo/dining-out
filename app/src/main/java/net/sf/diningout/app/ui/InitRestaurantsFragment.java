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

package net.sf.diningout.app.ui;

import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Loader;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import net.sf.diningout.R;
import net.sf.diningout.provider.Contract.Restaurants;
import net.sf.diningout.widget.PoweredByGoogle;
import net.sf.diningout.widget.RestaurantPlacesAdapter;
import net.sf.sprockets.app.ui.SprocketsFragment;
import net.sf.sprockets.content.GooglePlacesLoader;
import net.sf.sprockets.google.LocalPlacesParams;
import net.sf.sprockets.google.Place;
import net.sf.sprockets.google.Places.Params;
import net.sf.sprockets.google.Places.Response;
import net.sf.sprockets.util.SparseArrays;
import net.sf.sprockets.widget.GooglePlacesAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import butterknife.InjectView;
import in.srain.cube.views.GridViewWithHeaderAndFooter;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.google.common.base.Predicates.notNull;
import static net.sf.diningout.provider.Contract.Restaurants.SEARCH_RADIUS;
import static net.sf.diningout.provider.Contract.Restaurants.SEARCH_TYPES;
import static net.sf.sprockets.gms.analytics.Trackers.event;
import static net.sf.sprockets.google.Places.Request.NEARBY_SEARCH;
import static net.sf.sprockets.google.Places.Response.Status.OK;

/**
 * Displays a list of local restaurants for the user to choose from. Activities that attach this
 * must implement {@link Listener}.
 */
public class InitRestaurantsFragment extends SprocketsFragment
        implements LoaderCallbacks<Response<List<Place>>>, OnItemClickListener, OnScrollListener {
    @InjectView(R.id.progress)
    View mProgress;
    @InjectView(R.id.list)
    GridViewWithHeaderAndFooter mGrid;
    private GooglePlacesAdapter mAdapter;
    private PoweredByGoogle mPowered;
    private Listener mListener;
    /**
     * Three lists of restaurants that are loaded on demand.
     */
    private final List<List<Place>> mPlaces = new ArrayList<>(3);
    /**
     * Next page tokens that can be used to load more restaurants when not null.
     */
    private final String[] mTokens = new String[2];

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mListener = (Listener) activity;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Collections.addAll(mPlaces, null, null, null); // make room for the restaurant lists
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        return inflater.inflate(R.layout.init_restaurants_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mPowered = new PoweredByGoogle(a);
        mGrid.addFooterView(mPowered, null, false);
        mAdapter = new RestaurantPlacesAdapter(mGrid);
        mGrid.setAdapter(mAdapter);
        mGrid.setOnItemClickListener(this);
        mGrid.setOnScrollListener(this);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getLoaderManager().initLoader(0, null, this); // load first batch on Activity creation
        getLoaderManager().initLoader(1, null, this); // possibly load batches on recreation
        getLoaderManager().initLoader(2, null, this);
    }

    @Override
    public Loader<Response<List<Place>>> onCreateLoader(int id, Bundle args) {
        Params params = null;
        if (id == 0) { // get the first batch of restaurants
            mProgress.setVisibility(VISIBLE);
            params = new LocalPlacesParams(a).radius(SEARCH_RADIUS).types(SEARCH_TYPES);
        } else { // get another batch if available
            String token = mTokens[id - 1];
            if (!TextUtils.isEmpty(token)) {
                params = new Params().pageToken(token);
                mTokens[id - 1] = null; // reset so not used again
            }
        }
        return new GooglePlacesLoader<>(a, NEARBY_SEARCH, params, Restaurants.searchFields());
    }

    @Override
    public void onLoadFinished(Loader<Response<List<Place>>> loader, Response<List<Place>> resp) {
        if (mGrid == null) {
            return;
        }
        if (resp != null && resp.getStatus() == OK) {
            int id = loader.getId();
            mPlaces.set(id, resp.getResult());
            mAdapter.swapPlaces(
                    Lists.newArrayList(Iterables.concat(Iterables.filter(mPlaces, notNull()))));
            mListener.onRestaurantClick(mGrid.getCheckedItemCount());
            mPowered.setHtmlAttributions(resp.getHtmlAttributions());
            if (id < mTokens.length) { // save any token for future batch load
                mTokens[id] = resp.getNextPageToken();
            }
            if (id == 0) { // animate views into place
                if (mProgress.getVisibility() == VISIBLE) { // swap progress bar and grid
                    mProgress.animate().alpha(0.0f).withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            if (mProgress != null) {
                                mProgress.setVisibility(GONE);
                                mGrid.animate().alpha(1.0f).withLayer();
                            }
                        }
                    });
                } else if (mGrid.getAlpha() < 1.0f) {
                    mGrid.animate().alpha(1.0f).withLayer();
                }
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        mListener.onRestaurantClick(mGrid.getCheckedItemCount());
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
    }

    @Override
    public void onScroll(AbsListView view, int first, int visible, int total) {
        if (total - first - visible <= 8) { // load more when only 8 left
            if (!TextUtils.isEmpty(mTokens[0]) && mPlaces.get(1) == null) {
                getLoaderManager().restartLoader(1, null, this);
                event("restaurants", "init scroll", "page 2");
            } else if (!TextUtils.isEmpty(mTokens[1]) && mPlaces.get(2) == null) {
                getLoaderManager().restartLoader(2, null, this);
                event("restaurants", "init scroll", "page 3");
            }
        }
    }

    /**
     * Get the restaurants that are checked.
     *
     * @return null if none are checked
     */
    Place[] getCheckedRestaurants() {
        if (mGrid.getCheckedItemCount() > 0) {
            int[] keys = SparseArrays.trueKeys(mGrid.getCheckedItemPositions());
            Place[] places = new Place[keys.length];
            for (int i = 0; i < keys.length; i++) {
                places[i] = mAdapter.getItem(keys[i]);
            }
            return places;
        }
        return null;
    }

    @Override
    public void onLoaderReset(Loader<Response<List<Place>>> loader) {
        mAdapter.swapPlaces(null);
        mPowered.setHtmlAttributions(null);
        int id = loader.getId();
        mPlaces.set(id, null);
        if (id < mTokens.length) {
            mTokens[id] = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mAdapter = null;
        mPowered = null;
        mPlaces.clear();
        Arrays.fill(mTokens, null);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     * Receives notifications for {@link InitRestaurantsFragment} events.
     */
    interface Listener {
        /**
         * A restaurant has been chosen and the new total number of restaurants selected is
         * provided.
         */
        void onRestaurantClick(int total);
    }
}
