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
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ShareActionProvider;
import android.widget.ShareActionProvider.OnShareTargetSelectedListener;
import android.widget.Spinner;
import android.widget.TextView;

import com.squareup.picasso.Callback.EmptyCallback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import net.sf.diningout.R;
import net.sf.diningout.app.RestaurantGeocodeService;
import net.sf.diningout.app.RestaurantService;
import net.sf.diningout.data.OpenHour.Type;
import net.sf.diningout.picasso.Placeholders;
import net.sf.diningout.provider.Contract.OpenDays;
import net.sf.diningout.provider.Contract.OpenHours;
import net.sf.diningout.provider.Contract.RestaurantPhotos;
import net.sf.diningout.provider.Contract.Restaurants;
import net.sf.sprockets.app.ui.SprocketsFragment;
import net.sf.sprockets.content.Content.Query;
import net.sf.sprockets.content.EasyCursorLoader;
import net.sf.sprockets.content.Intents;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.google.Place.Prediction;
import net.sf.sprockets.lang.Maths;
import net.sf.sprockets.lang.Substring;
import net.sf.sprockets.net.Uris;
import net.sf.sprockets.net.Urls;
import net.sf.sprockets.view.TextViewHolder;
import net.sf.sprockets.view.ViewHolder;
import net.sf.sprockets.view.inputmethod.InputMethods;
import net.sf.sprockets.widget.GooglePlaceAutoComplete;
import net.sf.sprockets.widget.GooglePlaceAutoComplete.OnPlaceClickListener;
import net.sf.sprockets.widget.ResourceEasyCursorAdapter;

import java.util.Calendar;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import butterknife.Optional;
import icepick.Icicle;

import static android.content.Intent.ACTION_DIAL;
import static android.content.Intent.ACTION_SEND;
import static android.content.Intent.ACTION_VIEW;
import static android.content.Intent.EXTRA_SUBJECT;
import static android.content.Intent.EXTRA_TEXT;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.provider.BaseColumns._ID;
import static android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE;
import static butterknife.ButterKnife.findById;
import static java.util.Calendar.DAY_OF_WEEK;
import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.MINUTE;
import static net.sf.diningout.app.ui.RestaurantActivity.EXTRA_ID;
import static net.sf.diningout.app.ui.RestaurantsActivity.EXTRA_DELETE_ID;
import static net.sf.diningout.data.OpenHour.Type.OPEN;
import static net.sf.diningout.picasso.Transformations.LEFT;
import static net.sf.sprockets.app.SprocketsApplication.cr;
import static net.sf.sprockets.app.SprocketsApplication.res;
import static net.sf.sprockets.gms.analytics.Trackers.event;
import static net.sf.sprockets.view.animation.Interpolators.ANTICIPATE;
import static net.sf.sprockets.view.animation.Interpolators.OVERSHOOT;

/**
 * Displays a restaurant's details. The attaching Activity must have
 * {@link RestaurantActivity#EXTRA_ID} in its Intent extras.
 */
public class RestaurantFragment extends SprocketsFragment implements LoaderCallbacks<EasyCursor> {
    private static final int LOAD_DETAILS = 0;
    private static final int LOAD_HOURS = 1;
    private static final int LOAD_DAYS = 2;

    @InjectView(R.id.photo)
    ImageView mPhoto;

    @InjectView(R.id.name)
    TextView mNameView;

    @InjectView(R.id.address)
    TextView mVicinity;

    @Optional
    @InjectView(R.id.edit_address_stub)
    ViewStub mEditAddressStub;

    @InjectView(R.id.hours)
    Spinner mHours;

    @InjectView(R.id.phone)
    TextView mLocalPhone;

    @Optional
    @InjectView(R.id.edit_phone_stub)
    ViewStub mEditPhoneStub;

    @InjectView(R.id.website)
    TextView mWebsite;

    @Optional
    @InjectView(R.id.edit_website_stub)
    ViewStub mEditWebsiteStub;

    @Icicle
    int mDayOfWeek = -1;
    private long mId;
    private String mPlaceId;
    private String mGoogleUrl;
    private String mName;
    private String mAddress;
    private View mEditAddressGroup;
    private GooglePlaceAutoComplete mEditAddress;
    private double mLat;
    private double mLong;
    private String mIntlPhone;
    private View mEditPhoneGroup;
    private EditText mEditPhone;
    private String mUrl;
    private View mEditWebsiteGroup;
    private EditText mEditWebsite;
    private final Intent mShare = new Intent(ACTION_SEND).setType("text/plain");
    private boolean mPhotoLoaded;
    private boolean mIsOpen;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mId = activity.getIntent().getLongExtra(EXTRA_ID, 0L);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        return inflater.inflate(R.layout.restaurant_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mPhoto.setImageDrawable(RestaurantActivity.sPlaceholder != null
                ? RestaurantActivity.sPlaceholder : Placeholders.rect());
        mHours.setAdapter(new HoursAdapter());
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        LoaderManager manager = getLoaderManager();
        manager.initLoader(LOAD_DETAILS, null, this);
        manager.initLoader(LOAD_HOURS, null, this);
        manager.initLoader(LOAD_DAYS, null, this);
    }

    @Override
    public Loader<EasyCursor> onCreateLoader(int id, Bundle args) {
        Query q = new Query();
        switch (id) {
            case LOAD_DETAILS:
                q.uri(ContentUris.withAppendedId(Restaurants.CONTENT_URI, mId));
                q.proj(Restaurants.PLACE_ID, Restaurants.GOOGLE_URL, Restaurants.NAME,
                        Restaurants.ADDRESS, Restaurants.VICINITY, Restaurants.LATITUDE,
                        Restaurants.LONGITUDE, Restaurants.INTL_PHONE, Restaurants.LOCAL_PHONE,
                        Restaurants.URL, Restaurants.COLOR);
                break;
            case LOAD_HOURS:
                Calendar cal = Calendar.getInstance();
                mDayOfWeek = Maths.rollover(cal.get(DAY_OF_WEEK) - 2, 0, 6); // from Sun=1 to Mon=0
                int time = cal.get(HOUR_OF_DAY) * 100 + cal.get(MINUTE);
                q.uri(Uris.limit(OpenHours.CONTENT_URI, "1")).proj(OpenHours.TYPE_ID)
                        .sel(OpenHours.RESTAURANT_ID + " = ? AND ("
                                + OpenHours.DAY + " = ? AND " + OpenHours.TIME + " <= ? OR "
                                + OpenHours.DAY + " < ?)").args(mId, mDayOfWeek, time, mDayOfWeek)
                        .order(OpenHours.DAY + " DESC, " + OpenHours.TIME + " DESC");
                break;
            case LOAD_DAYS:
                q.uri(OpenDays.CONTENT_URI).proj(OpenDays.DAY + " AS " + _ID, OpenDays.HOURS)
                        .sel(OpenDays.RESTAURANT_ID + " = ?").args(mId).order(OpenDays.DAY);
                break;
        }
        return new EasyCursorLoader(a, q);
    }

    @Override
    public void onLoadFinished(Loader<EasyCursor> loader, EasyCursor c) {
        switch (loader.getId()) {
            case LOAD_DETAILS:
                if (mNameView != null && c.moveToFirst()) {
                    mPlaceId = c.getString(Restaurants.PLACE_ID);
                    mGoogleUrl = c.getString(Restaurants.GOOGLE_URL);
                    mName = c.getString(Restaurants.NAME);
                    mNameView.setText(mName);
                    mAddress = c.getString(Restaurants.ADDRESS);
                    String vicinity = c.getString(Restaurants.VICINITY);
                    mVicinity.setText(vicinity);
                    mLat = c.getDouble(Restaurants.LATITUDE);
                    mLong = c.getDouble(Restaurants.LONGITUDE);
                    mIntlPhone = c.getString(Restaurants.INTL_PHONE);
                    mLocalPhone.setText(c.getString(Restaurants.LOCAL_PHONE));
                    mUrl = c.getString(Restaurants.URL);
                    mWebsite.setText(!TextUtils.isEmpty(mUrl) ? Uri.parse(mUrl).getHost() : null);
                    /* prompt to add details for own restaurant */
                    if (mPlaceId == null) {
                        if (TextUtils.isEmpty(mAddress)) {
                            mVicinity.setText(R.string.add_address);
                        }
                        if (TextUtils.isEmpty(mIntlPhone)) {
                            mLocalPhone.setText(R.string.add_phone);
                        }
                        if (TextUtils.isEmpty(mUrl)) {
                            mWebsite.setText(R.string.add_website);
                        }
                    }
                    /* set Activity title */
                    CharSequence title = a.getTitle();
                    if (TextUtils.isEmpty(title)) { // set transparent while tab fragments load
                        SpannableString name = new SpannableString(mName);
                        name.setSpan(new ForegroundColorSpan(0), 0, mName.length(),
                                SPAN_INCLUSIVE_INCLUSIVE);
                        a.setTitle(name);
                    } else if (title instanceof SpannableStringBuilder) { // update (and keep alpha)
                        SpannableStringBuilder name = (SpannableStringBuilder) title;
                        a.setTitle(name.replace(0, name.length(), mName));
                    }
                    /* set share subject and text */
                    mShare.putExtra(EXTRA_SUBJECT, mName);
                    StringBuilder text = new StringBuilder(192);
                    text.append(mName);
                    if (!TextUtils.isEmpty(vicinity)) {
                        text.append("\n").append(vicinity);
                    }
                    if (!TextUtils.isEmpty(mIntlPhone)) {
                        text.append("\n").append(mIntlPhone);
                    }
                    if (!TextUtils.isEmpty(mUrl)) {
                        text.append("\n").append(mUrl);
                    }
                    mShare.putExtra(EXTRA_TEXT, text.toString());
                }
                if (mPhoto != null && !mPhotoLoaded) {
                    RequestCreator req = Picasso.with(a)
                            .load(RestaurantPhotos.uriForRestaurant(mId)).transform(LEFT);
                    if (RestaurantActivity.sPlaceholder != null) {
                        req.placeholder(RestaurantActivity.sPlaceholder);
                        RestaurantActivity.sPlaceholder = null; // only use once, bounds can reset
                    } else {
                        req.placeholder(Placeholders.rect(c));
                    }
                    req.into(mPhoto, new EmptyCallback() {
                        @Override
                        public void onSuccess() {
                            mPhotoLoaded = true;
                        }
                    });
                }
                break;
            case LOAD_HOURS:
                if (!hoursLoaded(c)) { // get last open or close event from last week
                    new AsyncTask<Void, Void, Cursor>() {
                        @Override
                        protected Cursor doInBackground(Void... params) {
                            String[] proj = {OpenHours.TYPE_ID};
                            String sel = OpenHours.RESTAURANT_ID + " = ?";
                            String[] args = {String.valueOf(mId)};
                            String order = OpenHours.DAY + " DESC, " + OpenHours.TIME + " DESC";
                            return cr().query(Uris.limit(OpenHours.CONTENT_URI, "1"), proj,
                                    sel, args, order);
                        }

                        @Override
                        protected void onPostExecute(Cursor c) {
                            super.onPostExecute(c);
                            hoursLoaded(new EasyCursor(c));
                            c.close();
                        }
                    }.execute();
                }
                break;
            case LOAD_DAYS:
                if (mHours != null) {
                    ((CursorAdapter) mHours.getAdapter()).swapCursor(c);
                    int count = c.getCount();
                    float alpha = mHours.getAlpha();
                    if (count > 0 && alpha < 1.0f) {
                        mHours.animate().alpha(1.0f);
                    } else if (count <= 0 && alpha > 0.0f) {
                        mHours.animate().alpha(0.0f);
                    }
                }
                break;
        }
    }

    /**
     * True if the last open or close event has been loaded.
     */
    private boolean hoursLoaded(EasyCursor c) {
        if (c.moveToFirst()) {
            mIsOpen = Type.get(c.getInt(OpenHours.TYPE_ID)) == OPEN;
            if (mHours != null) {
                ((BaseAdapter) mHours.getAdapter()).notifyDataSetChanged();
            }
            return true;
        }
        return false;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.restaurant, menu);
        ShareActionProvider share =
                (ShareActionProvider) menu.findItem(R.id.share).getActionProvider();
        share.setShareHistoryFileName("restaurant_share_history.xml");
        share.setShareIntent(mShare);
        share.setOnShareTargetSelectedListener(new OnShareTargetSelectedListener() {
            @Override
            public boolean onShareTargetSelected(ShareActionProvider source, Intent intent) {
                ComponentName component = intent.getComponent();
                event("restaurant", "share", component != null ? component.getClassName() : null);
                return false;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.refresh:
                a.startService(new Intent(a, RestaurantService.class)
                        .putExtra(RestaurantService.EXTRA_ID, mId));
                event("restaurant", "refresh");
                return true;
            case R.id.delete:
                startActivity(new Intent(a, RestaurantsActivity.class)
                        .putExtra(EXTRA_DELETE_ID, mId).addFlags(FLAG_ACTIVITY_CLEAR_TOP));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @OnClick({R.id.name, R.id.address, R.id.phone, R.id.website})
    public void onClick(TextView view) {
        Intent intent = null;
        String eventLabel = null;
        switch (view.getId()) {
            case R.id.name:
                intent = new Intent(ACTION_VIEW, Uri.parse(mGoogleUrl != null ? mGoogleUrl
                        : "https://plus.google.com/s/" + Uri.encode(mName))); // search
                eventLabel = "name";
                break;
            case R.id.address:
                if (!TextUtils.isEmpty(mAddress)) { // go to maps
                    intent = new Intent(ACTION_VIEW,
                            Uri.parse("geo:" + mLat + ',' + mLong + "?q=" + Uri.encode(mAddress)));
                    eventLabel = "address";
                } else if (mPlaceId == null) { // edit address
                    view.animate().translationX(view.getWidth()).setInterpolator(ANTICIPATE)
                            .withEndAction(new ShowEditAddress());
                }
                break;
            case R.id.phone:
                if (!TextUtils.isEmpty(mIntlPhone)) {
                    intent = new Intent(ACTION_DIAL, Uri.parse("tel:" + Uri.encode(mIntlPhone)));
                    eventLabel = "phone";
                } else if (mPlaceId == null) { // edit phone
                    view.animate().translationX(view.getWidth()).setInterpolator(ANTICIPATE)
                            .withEndAction(new ShowEditPhone());
                }
                break;
            case R.id.website:
                if (!TextUtils.isEmpty(mUrl)) {
                    intent = new Intent(ACTION_VIEW, Uri.parse(mUrl));
                    eventLabel = "website";
                } else if (mPlaceId == null) { // edit website
                    view.animate().translationX(view.getWidth()).setInterpolator(ANTICIPATE)
                            .withEndAction(new ShowEditWebsite());
                }
                break;
        }
        if (intent != null) {
            if (Intents.hasActivity(a, intent)) {
                startActivity(intent);
                event("restaurant", "click", eventLabel);
            } else {
                event("restaurant", "click [fail]", eventLabel);
            }
        }
    }

    @Optional
    @OnClick({R.id.save_address, R.id.save_phone, R.id.save_website})
    public void onSave(ImageButton button) {
        dismissEditGroup(button.getId(), true);
    }

    private void dismissEditGroup(int buttonId, final boolean save) {
        switch (buttonId) {
            case R.id.save_address:
                mEditAddressGroup.animate().translationX(mEditAddressGroup.getWidth())
                        .setInterpolator(ANTICIPATE).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (save) {
                            String address = mEditAddress.getText().toString().trim();
                            if (address.length() > 0) {
                                ContentValues vals = new ContentValues(3);
                                vals.put(Restaurants.ADDRESS, address);
                                vals.put(Restaurants.VICINITY, address);
                                vals.put(Restaurants.DIRTY, 1);
                                cr().update(ContentUris.withAppendedId(
                                        Restaurants.CONTENT_URI, mId), vals, null, null);
                                a.startService(new Intent(a, RestaurantGeocodeService.class)
                                        .putExtra(RestaurantGeocodeService.EXTRA_ID, mId));
                            } else {
                                mEditAddress.setText(null); // remove any whitespace
                            }
                        }
                        mVicinity.animate().translationX(0.0f).setInterpolator(OVERSHOOT);
                    }
                });
                break;
            case R.id.save_phone:
                mEditPhoneGroup.animate().translationX(mEditPhoneGroup.getWidth())
                        .setInterpolator(ANTICIPATE).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (save) {
                            String phone = mEditPhone.getText().toString().trim();
                            if (phone.length() > 0) {
                                ContentValues vals = new ContentValues(3);
                                vals.put(Restaurants.INTL_PHONE, phone);
                                vals.put(Restaurants.LOCAL_PHONE, phone);
                                vals.put(Restaurants.DIRTY, 1);
                                cr().update(ContentUris.withAppendedId(
                                        Restaurants.CONTENT_URI, mId), vals, null, null);
                            } else {
                                mEditPhone.setText(null);
                            }
                        }
                        mLocalPhone.animate().translationX(0.0f).setInterpolator(OVERSHOOT);
                    }
                });
                break;
            case R.id.save_website:
                mEditWebsiteGroup.animate().translationX(mEditWebsiteGroup.getWidth())
                        .setInterpolator(ANTICIPATE).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (save) {
                            String url = mEditWebsite.getText().toString().trim();
                            if (url.length() > 0) {
                                ContentValues vals = new ContentValues(2);
                                vals.put(Restaurants.URL, Urls.addHttp(url));
                                vals.put(Restaurants.DIRTY, 1);
                                cr().update(ContentUris.withAppendedId(
                                        Restaurants.CONTENT_URI, mId), vals, null, null);
                            } else {
                                mEditWebsite.setText(null);
                            }
                        }
                        mWebsite.animate().translationX(0.0f).setInterpolator(OVERSHOOT);
                    }
                });
                break;
        }
        mPhoto.postDelayed(new Runnable() {
            @Override
            public void run() {
                InputMethods.hide(mPhoto);
            }
        }, 750L); // after detail slides out and back in
    }

    void show(long restaurantId) {
        mId = restaurantId;
        mPhotoLoaded = false;
        /* reload details */
        LoaderManager manager = getLoaderManager();
        manager.restartLoader(LOAD_DETAILS, null, this);
        manager.restartLoader(LOAD_HOURS, null, this);
        manager.restartLoader(LOAD_DAYS, null, this);
        /* dismiss edit fields if showing */
        if (mEditAddressGroup != null && mEditAddressGroup.getTranslationX() == 0.0f) {
            dismissEditGroup(R.id.save_address, false);
        }
        if (mEditPhoneGroup != null && mEditPhoneGroup.getTranslationX() == 0.0f) {
            dismissEditGroup(R.id.save_phone, false);
        }
        if (mEditWebsiteGroup != null && mEditWebsiteGroup.getTranslationX() == 0.0f) {
            dismissEditGroup(R.id.save_website, false);
        }
    }

    @Override
    public void onLoaderReset(Loader<EasyCursor> loader) {
        if (loader.getId() == LOAD_DAYS && mHours != null) {
            ((CursorAdapter) mHours.getAdapter()).swapCursor(null);
        }
    }

    @Override
    public void onDestroyView() {
        Picasso.with(a).cancelRequest(mPhoto);
        super.onDestroyView();
    }

    /**
     * Translates open day rows to Views.
     */
    private class HoursAdapter extends ResourceEasyCursorAdapter {
        private final ClickListener mListener = new ClickListener();

        private HoursAdapter() {
            super(a, R.layout.hours_item, null, 0);
            setViewResource(R.layout.hours);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view = (TextView) super.getView(position, convertView, parent);
            view.setText(mIsOpen ? R.string.open : R.string.closed);
            view.setTextAppearance(a, R.style.Cell_Title);
            view.setOnClickListener(mListener);
            return view;
        }

        @Override
        public void bindView(View view, Context context, EasyCursor c) {
            TextViewHolder hours = ViewHolder.get(view, TextViewHolder.class);
            hours.text1.setText(c.getString(OpenDays.HOURS));
            hours.text1.setTextAppearance(context,
                    c.getInt(_ID) == mDayOfWeek ? R.style.Highlight : R.style.Plain);
        }

        /**
         * Scrolls to the current day.
         */
        private class ClickListener implements OnClickListener {
            @Override
            public void onClick(View v) {
                if (mDayOfWeek >= 0 && mDayOfWeek < mHours.getCount()) {
                    mHours.setSelection(mDayOfWeek);
                }
                mHours.performClick(); // needs forwarding
                event("restaurant", "click", "hours");
            }
        }
    }

    /**
     * Inflates the edit address layout, animates it into place, and prepares it for editing.
     */
    private class ShowEditAddress implements Runnable {
        @Override
        public void run() {
            if (mEditAddressStub != null) {
                mEditAddressGroup = mEditAddressStub.inflate();
                mEditAddress = findById(mEditAddressGroup, R.id.edit_address);
                mEditAddress.setOnPlaceClickListener(new OnPlaceClickListener() {
                    @Override
                    public void onPlaceClick(AdapterView<?> parent, Prediction place, int pos) {
                        /* only use street address and district/city */
                        List<Substring> terms = place.getTerms();
                        if (terms.size() > 2) {
                            Substring term = terms.get(1);
                            mEditAddress.setText(place.getName()
                                    .substring(0, term.getOffset() + term.getLength()));
                        }
                    }
                });
                ButterKnife.inject(RestaurantFragment.this, getView()); // done button click
                mEditAddressStub = null;
            }
            mEditAddressGroup.setTranslationX(
                    res().getDimensionPixelSize(R.dimen.restaurant_detail_edit_width));
            mEditAddressGroup.animate().translationX(0.0f).setInterpolator(OVERSHOOT);
            mEditAddress.requestFocus();
            InputMethods.show(mEditAddress);
        }
    }

    /**
     * Inflates the edit phone layout, animates it into place, and prepares it for editing.
     */
    private class ShowEditPhone implements Runnable {
        @Override
        public void run() {
            if (mEditPhoneStub != null) {
                mEditPhoneGroup = mEditPhoneStub.inflate();
                mEditPhone = findById(mEditPhoneGroup, R.id.edit_phone);
                ButterKnife.inject(RestaurantFragment.this, getView());
                mEditPhoneStub = null;
            }
            mEditPhoneGroup.setTranslationX(
                    res().getDimensionPixelSize(R.dimen.restaurant_detail_edit_width));
            mEditPhoneGroup.animate().translationX(0.0f).setInterpolator(OVERSHOOT);
            mEditPhone.requestFocus();
            InputMethods.show(mEditPhone);
        }
    }

    /**
     * Inflates the edit website layout, animates it into place, and prepares it for editing.
     */
    private class ShowEditWebsite implements Runnable {
        @Override
        public void run() {
            if (mEditWebsiteStub != null) {
                mEditWebsiteGroup = mEditWebsiteStub.inflate();
                mEditWebsite = findById(mEditWebsiteGroup, R.id.edit_website);
                ButterKnife.inject(RestaurantFragment.this, getView());
                mEditWebsiteStub = null;
            }
            mEditWebsiteGroup.setTranslationX(
                    res().getDimensionPixelSize(R.dimen.restaurant_detail_edit_width));
            mEditWebsiteGroup.animate().translationX(0.0f).setInterpolator(OVERSHOOT);
            mEditWebsite.requestFocus();
            InputMethods.show(mEditWebsite);
        }
    }
}
