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
import android.content.ComponentName;
import android.content.Intent;
import android.content.Loader;
import android.location.Location;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnActionExpandListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CursorAdapter;
import android.widget.GridView;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.ShareActionProvider;
import android.widget.ShareActionProvider.OnShareTargetSelectedListener;

import net.sf.diningout.R;
import net.sf.diningout.provider.Contract.Restaurants;
import net.sf.diningout.undobar.Undoer;
import net.sf.diningout.widget.RestaurantCursorAdapter;
import net.sf.sprockets.app.ui.SprocketsFragment;
import net.sf.sprockets.content.EasyCursorLoader;
import net.sf.sprockets.content.LocalCursorLoader;
import net.sf.sprockets.content.Managers;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.sql.SQLite;
import net.sf.sprockets.util.SparseArrays;
import net.sf.sprockets.widget.SearchViews;

import butterknife.InjectView;
import icepick.Icicle;

import static android.content.Intent.ACTION_SEND;
import static android.content.Intent.EXTRA_SUBJECT;
import static android.content.Intent.EXTRA_TEXT;
import static android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT;
import static android.provider.BaseColumns._ID;
import static net.sf.diningout.data.Status.ACTIVE;
import static net.sf.diningout.data.Status.DELETED;
import static net.sf.sprockets.gms.analytics.Trackers.event;

/**
 * Displays a list of the user's restaurants. Activities that attach this must implement
 * {@link Listener}.
 */
public class RestaurantsFragment extends SprocketsFragment
        implements LoaderCallbacks<EasyCursor>, OnItemClickListener {
    /**
     * Loader argument for the position of the selected sort option.
     */
    private static final String SORT = "sort";

    /**
     * Loader argument for restaurant name to search for.
     */
    private static final String SEARCH_QUERY = "search_query";

    private static final String[] sShareFields =
            {Restaurants.NAME, Restaurants.VICINITY, Restaurants.INTL_PHONE, Restaurants.URL};

    @InjectView(R.id.list)
    GridView mGrid;

    @Icicle
    Bundle mLoaderArgs;

    private Listener mListener;
    private SearchView mSearch;
    private ActionMode mActionMode;
    private final Intent mShare = new Intent(ACTION_SEND).setType("text/plain");

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mListener = (Listener) activity;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        a.getActionBar().setIcon(R.drawable.logo); // expanded SearchView uses icon
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        return inflater.inflate(R.layout.restaurants_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mGrid.setAdapter(new RestaurantCursorAdapter(mGrid));
        mGrid.setOnItemClickListener(this);
        mGrid.setMultiChoiceModeListener(new ChoiceListener());
        mListener.onViewCreated((AbsListView) view);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getLoaderManager().initLoader(0, mLoaderArgs, this);
    }

    @Override
    public Loader<EasyCursor> onCreateLoader(int id, Bundle args) {
        mLoaderArgs = args;
        EasyCursorLoader loader = null;
        final String[] proj = {_ID, Restaurants.NAME, Restaurants.VICINITY,
                Restaurants.INTL_PHONE, Restaurants.URL, Restaurants.COLOR, Restaurants.RATING};
        StringBuilder sel = new StringBuilder(Restaurants.STATUS_ID).append(" = ?");
        String[] selArgs;
        String order = null;
        String searchQuery = args != null ? args.getString(SEARCH_QUERY) : null;
        if (!TextUtils.isEmpty(searchQuery)) {
            sel.append(" AND ").append(Restaurants.NORMALISED_NAME).append(" LIKE ?");
            String filter = '%' + SQLite.normalise(searchQuery) + '%';
            selArgs = new String[]{String.valueOf(ACTIVE.id), filter, filter.substring(1)};
            order = Restaurants.NORMALISED_NAME + " LIKE ? DESC, " + Restaurants.NAME;
        } else {
            selArgs = new String[]{String.valueOf(ACTIVE.id)};
            switch (args != null ? args.getInt(SORT) : 0) {
                case 0:
                    order = Restaurants.NAME + " = '', " + Restaurants.NAME; // placeholders at end
                    break;
                case 1:
                    proj[proj.length - 1] = SQLite.millis(Restaurants.LAST_VISIT_ON);
                    order = Restaurants.LAST_VISIT_ON;
                    break;
                case 2:
                case 3:
                    order = Restaurants.DISTANCE + " IS NULL, " + Restaurants.DISTANCE;
                    loader = new LocalCursorLoader(a, Restaurants.CONTENT_URI, proj,
                            sel.toString(), selArgs, order) {
                        @Override
                        protected void onLocation(Location location) {
                            proj[proj.length - 1] = Restaurants.distance(location);
                        }
                    };
                    break;
                case 4:
                    order = Restaurants.RATING + " DESC";
                    break;
            }
        }
        return loader != null ? loader : new EasyCursorLoader(a, Restaurants.CONTENT_URI, proj,
                sel.toString(), selArgs, order);
    }

    @Override
    public void onLoadFinished(Loader<EasyCursor> loader, EasyCursor c) {
        if (mGrid != null) {
            ((CursorAdapter) mGrid.getAdapter()).swapCursor(c);
            updateActionMode();
            if (c.getCount() == 0 && (mLoaderArgs == null
                    || TextUtils.isEmpty(mLoaderArgs.getString(SEARCH_QUERY)))) {
                startActivity(new Intent(a, RestaurantAddActivity.class)
                        .addFlags(FLAG_ACTIVITY_REORDER_TO_FRONT));
            }
        }
    }

    /**
     * Update the ActionMode title and prepare the share Intent.
     */
    private void updateActionMode() {
        if (mActionMode != null) {
            int count = mGrid.getCheckedItemCount();
            if (count > 0) {
                mActionMode.setTitle(getString(R.string.n_selected, count));
                if (count > 1) {
                    mShare.putExtra(EXTRA_SUBJECT, getString(R.string.restaurants_share));
                }
                StringBuilder text = new StringBuilder(192 * count);
                for (int i : SparseArrays.trueKeys(mGrid.getCheckedItemPositions())) {
                    if (text.length() > 0) { // separate sequential restaurants
                        text.append("\n");
                    }
                    EasyCursor c = (EasyCursor) mGrid.getItemAtPosition(i);
                    for (String field : sShareFields) {
                        String detail = c.getString(field);
                        if (count == 1 && field.equals(Restaurants.NAME)) {
                            mShare.putExtra(EXTRA_SUBJECT, detail);
                        }
                        if (!TextUtils.isEmpty(detail)) {
                            if (text.length() > 0) {
                                text.append("\n");
                            }
                            text.append(detail);
                        }
                    }
                }
                mShare.putExtra(EXTRA_TEXT, text.toString());
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (mListener.onRestaurantsOptionsMenu()) {
            inflater.inflate(R.menu.restaurants, menu);
            MenuItem item = menu.findItem(R.id.search);
            mSearch = (SearchView) item.getActionView();
            mSearch.setSearchableInfo(Managers.search(a).getSearchableInfo(a.getComponentName()));
            SearchViews.setBackground(mSearch, R.drawable.textfield_searchview);
            mSearch.setOnSearchClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    event("restaurants", "search");
                }
            });
            mSearch.setOnQueryTextListener(new SearchTextListener());
            item.setOnActionExpandListener(new SearchExpandListener());
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mListener.onRestaurantsOptionsMenu()) { // don't respond unless menu items added
            switch (item.getItemId()) {
                case R.id.add:
                    startActivity(new Intent(a, RestaurantAddActivity.class));
                    a.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Set the sort option that should be used when loading the restaurants.
     */
    void setSort(int position) {
        initLoaderArgs().putInt(SORT, position);
    }

    /**
     * Sort the restaurants by the sort option.
     */
    void sort(int position) {
        setSort(position);
        mLoaderArgs.remove(SEARCH_QUERY);
        getLoaderManager().restartLoader(0, mLoaderArgs, this);
        mGrid.smoothScrollToPosition(0);
    }

    /**
     * Initialise {@link #mLoaderArgs} if it isn't already.
     */
    private Bundle initLoaderArgs() {
        if (mLoaderArgs == null) {
            mLoaderArgs = new Bundle(2);
        }
        return mLoaderArgs;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (mSearch != null) {
            mSearch.clearFocus(); // hiding input method later causes placeholder Drawable to resize
        }
        mListener.onRestaurantClick(view, id);
    }

    @Override
    public AbsListView getAbsListView() {
        return mGrid;
    }

    @Override
    public void onLoaderReset(Loader<EasyCursor> loader) {
        if (mGrid != null) {
            ((CursorAdapter) mGrid.getAdapter()).swapCursor(null);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     * Receives notifications for {@link RestaurantsFragment} events.
     */
    interface Listener {
        /**
         * The restaurants list has been created.
         */
        void onViewCreated(AbsListView view);

        /**
         * The restaurants options menu is being created. Return true to add the menu items or false
         * to skip them.
         */
        boolean onRestaurantsOptionsMenu();

        /**
         * Restaurants are being searched for names that match the query.
         */
        void onRestaurantsSearch(String query);

        /**
         * The restaurant was clicked.
         */
        void onRestaurantClick(View view, long id);
    }

    /**
     * Filters the restaurants by name as the search query changes.
     */
    private class SearchTextListener implements OnQueryTextListener {
        private String oldText = "";

        @Override
        public boolean onQueryTextChange(String newText) {
            if (!newText.equals(oldText)) {
                mListener.onRestaurantsSearch(newText);
                initLoaderArgs().putString(SEARCH_QUERY, newText);
                getLoaderManager().restartLoader(0, mLoaderArgs, RestaurantsFragment.this);
                mGrid.smoothScrollToPosition(0);
                oldText = newText;
            }
            return true;
        }

        @Override
        public boolean onQueryTextSubmit(String query) {
            mSearch.clearFocus();
            return true;
        }
    }

    /**
     * Reloads the restaurants when the SearchView is closed with an empty query. This is needed
     * after a configuration change when the SearchView has lost its query, yet the restaurants
     * are still filtered. onQueryTextChange is not called when the SearchView is closed with an
     * empty query.
     */
    private class SearchExpandListener implements OnActionExpandListener {
        @Override
        public boolean onMenuItemActionExpand(MenuItem item) {
            return true;
        }

        @Override
        public boolean onMenuItemActionCollapse(MenuItem item) {
            if (mSearch.getQuery().length() == 0) {
                getLoaderManager().restartLoader(0, mLoaderArgs, RestaurantsFragment.this);
            }
            return true;
        }
    }

    /**
     * Manages the CAB while restaurants are selected.
     */
    private class ChoiceListener implements MultiChoiceModeListener {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mActionMode = mode;
            mode.getMenuInflater().inflate(R.menu.restaurants_cab, menu);
            ShareActionProvider share =
                    (ShareActionProvider) menu.findItem(R.id.share).getActionProvider();
            share.setShareHistoryFileName("restaurant_share_history.xml");
            share.setShareIntent(mShare);
            share.setOnShareTargetSelectedListener(new OnShareTargetSelectedListener() {
                @Override
                public boolean onShareTargetSelected(ShareActionProvider source, Intent intent) {
                    ComponentName component = intent.getComponent();
                    event("restaurants", "share", component != null ? component.getClassName()
                            : null, mGrid.getCheckedItemCount());
                    return false;
                }
            });
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int pos, long id, boolean checked) {
            updateActionMode();
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.delete:
                    long[] ids = mGrid.getCheckedItemIds();
                    new Undoer(a, getString(R.string.n_deleted, ids.length),
                            Restaurants.CONTENT_URI, ids, DELETED, ACTIVE);
                    mode.finish(); // ensure mActionMode is null before updateActionMode is called
                    return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mActionMode = null;
        }
    }
}
