/*
 * Copyright 2014 pushbit <pushbit@gmail.com>
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

import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Loader;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;

import com.google.common.base.Objects;
import com.google.common.base.Strings;

import net.sf.diningout.R;
import net.sf.diningout.app.ui.RestaurantActivity.TabListFragment;
import net.sf.diningout.provider.Contract.Restaurants;
import net.sf.sprockets.content.EasyCursorLoader;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.view.ViewHolder;

import butterknife.InjectView;
import icepick.Icicle;

import static net.sf.sprockets.app.SprocketsApplication.cr;

/**
 * Displays editable notes for a restaurant.
 */
public class NotesFragment extends TabListFragment implements LoaderCallbacks<EasyCursor> {
    @Icicle
    long mRestaurantId;
    @Icicle
    CharSequence mNotes;
    @Icicle
    String mStoredNotes;

    /**
     * Display notes for the restaurant.
     */
    static NotesFragment newInstance(long restaurantId) {
        NotesFragment frag = new NotesFragment();
        frag.mRestaurantId = restaurantId;
        return frag;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        NotesAdapter adapter = new NotesAdapter();
        setListAdapter(adapter);
        final ListView list = getListView();
        view = a.getLayoutInflater().inflate(R.layout.notes_fragment, list, false);
        NotesHolder notes = ViewHolder.get(view, NotesHolder.class);
        notes.mNotes.setText(mNotes); // restore after config change
        list.addHeaderView(view);
        adapter.notifyDataSetChanged(); // ListView only tells own Observer
        list.postDelayed(new Runnable() {
            @Override
            public void run() {
                list.smoothScrollBy(1, 300);
            }
        }, 600L); // fix detail and tabs scroll after rotation with input method shown
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public Loader<EasyCursor> onCreateLoader(int id, Bundle args) {
        Uri uri = ContentUris.withAppendedId(Restaurants.CONTENT_URI, mRestaurantId);
        return new EasyCursorLoader(a, uri, new String[]{Restaurants.NOTES}, null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<EasyCursor> loader, EasyCursor c) {
        if (c.moveToFirst()) {
            String notes = c.getString(Restaurants.NOTES);
            if (!Objects.equal(notes, mStoredNotes)) { // don't overwrite unless updated elsewhere
                getNotes().mNotes.setText(notes);
                mStoredNotes = notes;
            }
        }
    }

    @Override
    void setRestaurant(long id) {
        saveNotes();
        mRestaurantId = id;
        mNotes = null;
        mStoredNotes = null;
        getNotes().mNotes.setText(null);
        getLoaderManager().restartLoader(0, null, this);
    }

    @Override
    public void onLoaderReset(Loader<EasyCursor> loader) {
    }

    @Override
    public void onPause() {
        super.onPause();
        if (!a.isChangingConfigurations()) {
            saveNotes();
        }
    }

    /**
     * Get the ViewHolder of the header View for editing notes.
     */
    private NotesHolder getNotes() {
        ListView view = getListView();
        return ViewHolder.get(view.getAdapter().getView(1, null, view));
    }

    private void saveNotes() {
        String notes = getNotes().mNotes.getText().toString();
        if (!notes.equals(Strings.nullToEmpty(mStoredNotes))) {
            ContentValues vals = new ContentValues(2);
            vals.put(Restaurants.NOTES, notes);
            vals.put(Restaurants.DIRTY, 1);
            cr().update(ContentUris.withAppendedId(Restaurants.CONTENT_URI, mRestaurantId),
                    vals, null, null);
            mStoredNotes = notes;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        mNotes = getNotes().mNotes.getText(); // adapter Views not saved/restored by framework
        super.onSaveInstanceState(outState);
    }

    /**
     * Dummy adapter that only exists so ListView can wrap it when adding a header View.
     */
    private class NotesAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return 0;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return null;
        }
    }

    public static class NotesHolder extends ViewHolder {
        @InjectView(R.id.notes)
        EditText mNotes;

        @Override
        protected NotesHolder newInstance() {
            return new NotesHolder();
        }
    }
}
