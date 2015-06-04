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
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CursorAdapter;
import android.widget.GridView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import net.sf.diningout.R;
import net.sf.diningout.data.Review;
import net.sf.diningout.data.Sync;
import net.sf.diningout.picasso.Placeholders;
import net.sf.diningout.provider.Contract.Columns;
import net.sf.diningout.provider.Contract.Contacts;
import net.sf.diningout.provider.Contract.RestaurantPhotos;
import net.sf.diningout.provider.Contract.Syncs;
import net.sf.diningout.provider.Contract.SyncsJoinAll;
import net.sf.sprockets.app.ui.SprocketsFragment;
import net.sf.sprockets.content.EasyCursorLoader;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.net.Uris;
import net.sf.sprockets.preference.Prefs;
import net.sf.sprockets.util.StringArrays;
import net.sf.sprockets.view.ViewHolder;
import net.sf.sprockets.widget.ResourceEasyCursorAdapter;
import net.wujingchao.android.view.SimpleTagImageView;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import butterknife.Optional;

import static android.provider.BaseColumns._ID;
import static android.text.format.DateUtils.FORMAT_ABBREV_ALL;
import static android.text.format.DateUtils.MINUTE_IN_MILLIS;
import static java.util.Collections.EMPTY_SET;
import static net.sf.diningout.data.Status.ACTIVE;
import static net.sf.diningout.data.Sync.Type.REVIEW;
import static net.sf.diningout.data.Sync.Type.USER;
import static net.sf.diningout.picasso.Transformations.BR;
import static net.sf.diningout.preference.Keys.App.APP;
import static net.sf.diningout.preference.Keys.App.NEW_SYNC_IDS;
import static net.sf.sprockets.app.SprocketsApplication.res;
import static net.sf.sprockets.sql.SQLite.alias_;
import static net.sf.sprockets.sql.SQLite.aliased_;
import static net.sf.sprockets.sql.SQLite.max;
import static net.sf.sprockets.sql.SQLite.millis;

/**
 * Displays a list of notifications. Activities that attach this must implement {@link Listener}.
 */
public class NotificationsFragment extends SprocketsFragment
        implements LoaderCallbacks<EasyCursor>, OnItemClickListener {
    @InjectView(R.id.list)
    GridView mGrid;
    private Listener mListener;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mListener = (Listener) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        return inflater.inflate(R.layout.notifications_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mGrid.setAdapter(new NotificationsAdapter());
        mGrid.setOnItemClickListener(this);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public Loader<EasyCursor> onCreateLoader(int id, Bundle args) {
        String[] proj = {max(SyncsJoinAll.SYNC__ID), alias_(SyncsJoinAll.SYNC_TYPE_ID),
                millis("max", Syncs.ACTION_ON), alias_(SyncsJoinAll.REVIEW_TYPE_ID),
                alias_(SyncsJoinAll.RESTAURANT__ID), alias_(SyncsJoinAll.RESTAURANT_NAME),
                alias_(SyncsJoinAll.CONTACT__ID), Contacts.ANDROID_LOOKUP_KEY, Contacts.ANDROID_ID,
                alias_(SyncsJoinAll.CONTACT_NAME), "coalesce(" + SyncsJoinAll.RESTAURANT_COLOR + ","
                + SyncsJoinAll.CONTACT_COLOR + ") AS " + Columns.COLOR};
        String sel = SyncsJoinAll.SYNC_TYPE_ID + " = ? AND " + SyncsJoinAll.REVIEW_STATUS_ID
                + " = ? AND " + SyncsJoinAll.RESTAURANT_STATUS_ID + " = ? OR "
                + SyncsJoinAll.SYNC_TYPE_ID + " = ? AND " + SyncsJoinAll.CONTACT_STATUS_ID + " = ?";
        String[] selArgs = StringArrays.from(REVIEW.id, ACTIVE.id, ACTIVE.id, USER.id, ACTIVE.id);
        Uri uri = Uris.groupBy(SyncsJoinAll.CONTENT_URI,
                SyncsJoinAll.RESTAURANT__ID + ", " + SyncsJoinAll.CONTACT__ID);
        String order = Syncs.ACTION_ON + " DESC";
        return new EasyCursorLoader(a, uri, proj, sel, selArgs, order);
    }

    @Override
    public void onLoadFinished(Loader<EasyCursor> loader, EasyCursor c) {
        if (mGrid != null) {
            if (c.getCount() == 0 && mGrid.getEmptyView() == null) {
                View view = getView();
                mGrid.setEmptyView(((ViewStub) view.findViewById(R.id.empty)).inflate());
                ButterKnife.inject(this, view);
            }
            ((CursorAdapter) mGrid.getAdapter()).swapCursor(c);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        EasyCursor c = (EasyCursor) parent.getItemAtPosition(position);
        Sync.Type syncType = Sync.Type.get(c.getInt(aliased_(SyncsJoinAll.SYNC_TYPE_ID)));
        Review.Type reviewType = null;
        switch (syncType) {
            case USER:
                id = c.getLong(aliased_(SyncsJoinAll.CONTACT__ID));
                break;
            case REVIEW:
                id = c.getLong(aliased_(SyncsJoinAll.RESTAURANT__ID));
                reviewType = Review.Type.get(c.getInt(aliased_(SyncsJoinAll.REVIEW_TYPE_ID)));
                break;
        }
        mListener.onNotificationClick(view, syncType, id, reviewType);
    }

    /**
     * Go to {@link FriendsActivity}.
     */
    @Optional
    @OnClick(R.id.invite)
    void invite() {
        startActivity(new Intent(a, FriendsActivity.class));
        a.finish();
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
     * Receives notifications for {@link NotificationsFragment} events.
     */
    interface Listener {
        /**
         * The notification was clicked.
         *
         * @param id         restaurant ID when syncType is REVIEW
         * @param reviewType null when syncType is not REVIEW
         */
        void onNotificationClick(View view, Sync.Type syncType, long id, Review.Type reviewType);
    }

    /**
     * Translates notification rows to Views.
     */
    private class NotificationsAdapter extends ResourceEasyCursorAdapter {
        private final int mCellHeight;

        private NotificationsAdapter() {
            super(a, R.layout.notifications_adapter, null, 0);
            mCellHeight = res().getDimensionPixelSize(R.dimen.notification_card_height);
        }

        @Override
        public void bindView(View view, Context context, EasyCursor c) {
            NotificationHolder notif = ViewHolder.get(view, NotificationHolder.class);
            Uri photo = null;
            CharSequence action = null;
            switch (Sync.Type.get(c.getInt(aliased_(SyncsJoinAll.SYNC_TYPE_ID)))) {
                case USER:
                    String androidKey = c.getString(Contacts.ANDROID_LOOKUP_KEY);
                    long androidId = c.getLong(Contacts.ANDROID_ID);
                    if (androidKey != null && androidId > 0) {
                        photo = ContactsContract.Contacts.getLookupUri(androidId, androidKey);
                    }
                    action = context.getString(R.string.new_friend, contact(context, c));
                    break;
                case REVIEW:
                    photo = RestaurantPhotos.uriForRestaurant(
                            c.getLong(aliased_(SyncsJoinAll.RESTAURANT__ID)));
                    String restaurant = c.getString(aliased_(SyncsJoinAll.RESTAURANT_NAME));
                    switch (Review.Type.get(c.getInt(aliased_(SyncsJoinAll.REVIEW_TYPE_ID)))) {
                        case PRIVATE:
                            action = context.getString(R.string.new_friend_review,
                                    contact(context, c), restaurant);
                            break;
                        case GOOGLE:
                            action = context.getString(R.string.new_public_review, restaurant);
                            break;
                    }
                    break;
            }
            Picasso.with(context).load(photo).resize(mGrid.getColumnWidth(), mCellHeight)
                    .centerCrop().transform(BR).placeholder(Placeholders.rect(c))
                    .into(notif.mPhoto);
            notif.mAction.setText(action);
            long now = System.currentTimeMillis();
            long when = c.getLong(Syncs.ACTION_ON);
            notif.mTime.setText(now - when > MINUTE_IN_MILLIS
                    ? DateUtils.getRelativeTimeSpanString(when, now, 0, FORMAT_ABBREV_ALL)
                    : context.getString(R.string.recent_time));
            notif.mPhoto.setTagEnable(Prefs.getStringSet(context, APP, NEW_SYNC_IDS, EMPTY_SET)
                    .contains(String.valueOf(c.getLong(_ID))));
        }

        /**
         * Get the contact name from the cursor.
         */
        private String contact(Context context, EasyCursor c) {
            String contact = c.getString(aliased_(SyncsJoinAll.CONTACT_NAME));
            if (contact == null) {
                contact = context.getString(R.string.non_contact);
            }
            return contact;
        }
    }

    public static class NotificationHolder extends ViewHolder {
        @InjectView(R.id.photo)
        SimpleTagImageView mPhoto;

        @InjectView(R.id.action)
        TextView mAction;

        @InjectView(R.id.time)
        TextView mTime;

        @Override
        protected NotificationHolder newInstance() {
            return new NotificationHolder();
        }
    }
}
