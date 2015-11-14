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

import android.app.ActionBar;
import android.app.ActionBar.OnNavigationListener;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Intent;
import android.content.Loader;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.util.SimpleArrayMap;
import android.support.v4.widget.DrawerLayout;
import android.text.TextUtils;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.common.collect.ImmutableList;

import net.sf.diningout.R;
import net.sf.diningout.app.ui.RestaurantsFragment.Listener;
import net.sf.diningout.preference.Keys;
import net.sf.diningout.provider.Contract.Restaurants;
import net.sf.diningout.undobar.Undoer;
import net.sf.diningout.widget.RestaurantHolder;
import net.sf.sprockets.app.Fragments;
import net.sf.sprockets.app.MoreActivityOptions;
import net.sf.sprockets.content.EasyCursorLoader;
import net.sf.sprockets.content.LocalCursorLoader;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.gms.maps.GoogleMaps;
import net.sf.sprockets.view.ActionModePresenter;
import net.sf.sprockets.view.ViewHolder;

import java.util.List;

import butterknife.Bind;
import icepick.Icicle;

import static android.app.ActionBar.NAVIGATION_MODE_LIST;
import static android.provider.BaseColumns._ID;
import static android.support.v4.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED;
import static android.support.v4.widget.DrawerLayout.LOCK_MODE_UNLOCKED;
import static android.view.Gravity.START;
import static net.sf.diningout.app.ui.RestaurantActivity.EXTRA_ID;
import static net.sf.diningout.app.ui.RestaurantActivity.EXTRA_SORT;
import static net.sf.diningout.data.Status.ACTIVE;
import static net.sf.diningout.data.Status.DELETED;
import static net.sf.sprockets.app.SprocketsApplication.res;
import static net.sf.sprockets.gms.analytics.Trackers.event;
import static net.sf.sprockets.util.MeasureUnit.MILE;

/**
 * Displays a list of the user's restaurants.
 */
public class RestaurantsActivity extends BaseNavigationDrawerActivity
        implements OnNavigationListener, OnMapReadyCallback, LoaderCallbacks<EasyCursor>, Listener {
    /**
     * ID of a restaurant to delete.
     */
    static final String EXTRA_DELETE_ID = "intent.extra.DELETE_ID";

    /**
     * Tag for the map fragment.
     */
    private static final String MAP = "map";

    private static final int MAP_NAVIGATION_ITEM_POSITION = 3;

    @Nullable
    @Bind(R.id.root)
    DrawerLayout mDrawerLayout;

    /**
     * Position of selected sort option.
     */
    @Icicle
    int mSort;

    private GoogleMap mMap;

    /**
     * Maps to restaurant ID.
     */
    private SimpleArrayMap<Marker, Long> mMarkers;

    /**
     * True if the next map load should move the camera to the user's location.
     */
    private boolean mMoveToMyLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar ab = getActionBar();
        ab.setDisplayShowTitleEnabled(false);
        ab.setNavigationMode(NAVIGATION_MODE_LIST);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.restaurants_navigation,
                R.id.sort, res().getStringArray(R.array.restaurants_sort));
        adapter.setDropDownViewResource(R.layout.restaurants_navigation_item);
        ab.setListNavigationCallbacks(adapter, this);
        ab.setSelectedNavigationItem(mSort); // restore when rotating with navigation drawer open
        setContentView(R.layout.restaurants_activity);
        if (mDrawerLayout != null) {
            setDrawerLayout(mDrawerLayout);
        }
        /* set up the map if it was previously showing */
        MapFragment map = map();
        if (map != null) {
            map.getMapAsync(this);
        }
        getLoaderManager().initLoader(0, null, this); // need already to support config changes
    }

    private static final String[] sSortLabels = {"by name", "by last visit", "by distance",
            "on a map", "by rating"};

    @Override
    public boolean onNavigationItemSelected(int pos, long id) {
        if (mSort != pos) { // option changed
            mSort = pos;
            if (pos != MAP_NAVIGATION_ITEM_POSITION) { // remove map if showing and sort by option
                MapFragment map = map();
                RestaurantsFragment restaurants = restaurants();
                if (map != null) {
                    Fragments.close(this).remove(map).commit();
                    mMap = null;
                    if (mMarkers != null) {
                        mMarkers.clear();
                    }
                    getLoaderManager().destroyLoader(0);
                    Fragments.open(this).show(restaurants).commit();
                }
                restaurants.sort(pos);
            } else {
                Fragments.close(this).hide(restaurants()).commit();
                MapFragment map = MapFragment.newInstance();
                Fragments.open(this).add(R.id.map, map, MAP).commit();
                mMoveToMyLocation = true;
                map.getMapAsync(this);
            }
            event("restaurants", "sort", sSortLabels[pos]);
        }
        return true;
    }

    @Override
    public void onMapReady(GoogleMap map) {
        mMap = map;
        map.setMyLocationEnabled(true);
        if (mMoveToMyLocation) {
            GoogleMaps.moveCameraToMyLocation(this, map);
        }
        getLoaderManager().restartLoader(0, null, this);
        map.setOnInfoWindowClickListener(new OnInfoWindowClickListener() {
            @Override
            public void onInfoWindowClick(Marker marker) {
                Long id = mMarkers.get(marker);
                if (id != null) {
                    onRestaurantClick(null, id);
                }
            }
        });
    }

    @Override
    public Loader<EasyCursor> onCreateLoader(int id, Bundle args) {
        if (mMap != null) {
            final String[] proj = {_ID, Restaurants.NAME, Restaurants.LATITUDE,
                    Restaurants.LONGITUDE, Restaurants.RATING, null};
            String sel = Restaurants.DISTANCE + " IS NOT NULL AND "
                    + Restaurants.STATUS_ID + " = ?";
            String[] selArgs = {String.valueOf(ACTIVE.id)};
            return new LocalCursorLoader(this, Restaurants.CONTENT_URI, proj, sel, selArgs, null) {
                @Override
                protected void onLocation(Location location) {
                    proj[proj.length - 1] = Restaurants.distance(location);
                }
            };
        } else { // ignore dummy init in onCreate
            return new EasyCursorLoader(this, Restaurants.CONTENT_URI, null, "0 = 1", null, null);
        }
    }

    @Override
    public void onLoadFinished(Loader<EasyCursor> loader, EasyCursor c) {
        if (mMap == null) {
            return;
        }
        /* (re-)add restaurant markers */
        mMap.clear();
        if (mMarkers != null) {
            mMarkers.clear();
        }
        MarkerOptions options = null;
        double nearestDistance = Double.MAX_VALUE; // first marker will be closer
        LatLng nearestPosition = null;
        while (c.moveToNext()) {
            if (options == null) {
                options = new MarkerOptions();
            }
            LatLng position = new LatLng(
                    c.getDouble(Restaurants.LATITUDE), c.getDouble(Restaurants.LONGITUDE));
            float rating = c.getFloat(Restaurants.RATING);
            double distance = Math.sqrt(c.getDouble(Restaurants.DISTANCE));
            boolean miles = Keys.isDistanceUnit(MILE);
            String snippet = rating > 0.0f
                    ? getString(miles ? R.string.rating_mi : R.string.rating_km, rating, distance)
                    : getString(miles ? R.string.mi : R.string.km, distance);
            Marker marker = mMap.addMarker(options.position(position)
                    .title(c.getString(Restaurants.NAME)).snippet(snippet));
            if (mMarkers == null) {
                mMarkers = new SimpleArrayMap<>(c.getCount());
            }
            mMarkers.put(marker, c.getLong(_ID));
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestPosition = position;
            }
        }
        /* move the camera to include at least one marker, if necessary */
        if (mMoveToMyLocation) {
            if (nearestPosition != null) {
                GoogleMaps.animateCameraToIncludePosition(this, mMap, nearestPosition, 1000L);
            }
            mMoveToMyLocation = false;
        }
    }

    @Override
    public void onViewCreated(AbsListView view) {
        if (mSort == MAP_NAVIGATION_ITEM_POSITION) { // avoid restaurants flash before map is shown
            getFragmentManager().beginTransaction().hide(restaurants()).commit();
        }
    }

    @Override
    public boolean onRestaurantsOptionsMenu() {
        return mDrawerLayout == null || !mDrawerLayout.isDrawerOpen(START);
    }

    @Override
    public void onRestaurantsSearch(String query) {
        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerLockMode(!TextUtils.isEmpty(query)
                    ? LOCK_MODE_LOCKED_CLOSED : LOCK_MODE_UNLOCKED, START);
        }
    }

    @Override
    public void onRestaurantClick(View view, long id) {
        Bundle options = null;
        if (view != null) {
            RestaurantActivity.sPlaceholder =
                    ((RestaurantHolder) ViewHolder.get(view)).photo.getDrawable();
            options = MoreActivityOptions.makeScaleUpAnimation(view).toBundle();
        }
        startActivity(new Intent(this, RestaurantActivity.class)
                .putExtra(EXTRA_ID, id).putExtra(EXTRA_SORT, mSort), options);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        long id = intent.getLongExtra(EXTRA_DELETE_ID, 0L);
        if (id > 0) {
            new Undoer(this, getString(R.string.n_deleted, 1),
                    Restaurants.CONTENT_URI, new long[]{id}, DELETED, ACTIVE);
        }
    }

    @Override
    public List<ActionModePresenter> getActionModePresenters() {
        return ImmutableList.of((ActionModePresenter) restaurants());
    }

    private RestaurantsFragment restaurants() {
        return (RestaurantsFragment) getFragmentManager().findFragmentById(R.id.restaurants);
    }

    private MapFragment map() {
        return (MapFragment) getFragmentManager().findFragmentByTag(MAP);
    }

    @Override
    public void onLoaderReset(Loader<EasyCursor> loader) {
    }
}
