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
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import net.sf.diningout.R;
import net.sf.sprockets.app.ui.SprocketsFragment;
import net.sf.sprockets.google.Place.Prediction;
import net.sf.sprockets.text.TextWatcherAdapter;
import net.sf.sprockets.view.inputmethod.InputMethods;
import net.sf.sprockets.widget.GooglePlaceAutoComplete;
import net.sf.sprockets.widget.GooglePlaceAutoComplete.OnPlaceClickListener;

import butterknife.Bind;
import butterknife.OnClick;

/**
 * Displays an autocomplete field for restaurants and provides search requests. Activities that
 * attach this must implement {@link Listener}.
 */
public class RestaurantAutocompleteFragment extends SprocketsFragment {
    @Bind(R.id.name)
    GooglePlaceAutoComplete mName;

    private Listener mListener;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mListener = (Listener) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        return inflater.inflate(R.layout.restaurant_autocomplete, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mName.addTextChangedListener(new TextWatcherAdapter() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mListener.onRestaurantNameChange(s);
            }
        });
        mName.setOnPlaceClickListener(new OnPlaceClickListener() {
            @Override
            public void onPlaceClick(AdapterView<?> parent, Prediction place, int position) {
                mListener.onRestaurantAutocomplete(place);
            }
        });
    }

    @OnClick(R.id.search)
    void search() {
        CharSequence name = mName.getText();
        if (TextUtils.getTrimmedLength(name) > 0) {
            InputMethods.hide(mName);
            mListener.onRestaurantSearch(name);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     * Receives notifications for {@link RestaurantAutocompleteFragment} events.
     */
    interface Listener {
        /**
         * The entered restaurant name changed.
         */
        void onRestaurantNameChange(CharSequence name);

        /**
         * The restaurant in autocomplete suggestions was clicked.
         */
        void onRestaurantAutocomplete(Prediction place);

        /**
         * A request to search for the restaurant was made.
         */
        void onRestaurantSearch(CharSequence name);
    }
}
