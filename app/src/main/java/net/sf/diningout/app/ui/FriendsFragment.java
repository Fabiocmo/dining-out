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
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.Fragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Intents.Insert;
import android.provider.ContactsContract.RawContacts;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnActionExpandListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CursorAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import net.sf.diningout.R;
import net.sf.diningout.accounts.Accounts;
import net.sf.diningout.app.ReviewsService;
import net.sf.diningout.picasso.Placeholders;
import net.sf.diningout.provider.Contract.Contacts;
import net.sf.sprockets.app.ContentService;
import net.sf.sprockets.app.ui.SprocketsDialogFragment;
import net.sf.sprockets.app.ui.SprocketsFragment;
import net.sf.sprockets.content.Content;
import net.sf.sprockets.content.Intents;
import net.sf.sprockets.content.Managers;
import net.sf.sprockets.content.ReadCursorLoader;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.database.ReadCursor;
import net.sf.sprockets.net.Uris;
import net.sf.sprockets.sql.SQLite;
import net.sf.sprockets.util.Elements;
import net.sf.sprockets.util.SparseArrays;
import net.sf.sprockets.view.ViewHolder;
import net.sf.sprockets.widget.ResourceReadCursorAdapter;
import net.sf.sprockets.widget.SearchViews;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.primitives.ArrayLongList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import butterknife.Bind;
import icepick.Icicle;

import static android.content.Intent.ACTION_EDIT;
import static android.content.Intent.ACTION_SENDTO;
import static android.provider.BaseColumns._ID;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static net.sf.diningout.app.ReviewsService.EXTRA_GLOBAL_IDS;
import static net.sf.diningout.data.Status.ACTIVE;
import static net.sf.diningout.picasso.Transformations.TL;
import static net.sf.diningout.provider.Contract.ACTION_CONTACTS_SYNCED;
import static net.sf.diningout.provider.Contract.ACTION_CONTACTS_SYNCING;
import static net.sf.diningout.provider.Contract.AUTHORITY;
import static net.sf.diningout.provider.Contract.SYNC_EXTRAS_CONTACTS_ONLY;
import static net.sf.sprockets.app.ContentService.EXTRA_SELECTION;
import static net.sf.sprockets.app.ContentService.EXTRA_VALUES;
import static net.sf.sprockets.app.SprocketsApplication.res;
import static net.sf.sprockets.gms.analytics.Trackers.event;
import static net.sf.sprockets.sql.SQLite.groupConcat;
import static net.sf.sprockets.sql.SQLite.in;
import static net.sf.sprockets.sql.SQLite.max;
import static net.sf.sprockets.sql.SQLite.min;
import static net.sf.sprockets.view.animation.Interpolators.ANTICIPATE;
import static net.sf.sprockets.view.animation.Interpolators.OVERSHOOT;

/**
 * Displays contacts to follow and invite to join. Activities that attach this must implement
 * {@link Listener}.
 */
public class FriendsFragment extends SprocketsFragment
        implements LoaderCallbacks<ReadCursor>, OnItemClickListener {
    /**
     * Loader argument for contact name to search for.
     */
    private static final String SEARCH_QUERY = "search_query";

    /**
     * True if the user is initialising the app.
     */
    @Icicle
    boolean mInit;

    @Bind(R.id.header)
    ViewStub mHeader;

    @Bind(R.id.progress)
    View mProgress;

    @Bind(R.id.list)
    GridView mGrid;

    private Listener mListener;
    private Receiver mReceiver;
    private SearchView mSearch;

    /**
     * Create an instance that runs in app initialisation mode.
     */
    public static FriendsFragment newInstance(boolean init) {
        FriendsFragment frag = new FriendsFragment();
        frag.mInit = init;
        return frag;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mListener = (Listener) activity;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mReceiver = new Receiver();
        IntentFilter filter = new IntentFilter(ACTION_CONTACTS_SYNCING);
        filter.addAction(ACTION_CONTACTS_SYNCED);
        LocalBroadcastManager.getInstance(a).registerReceiver(mReceiver, filter);
        a.getActionBar().setIcon(R.drawable.logo); // expanded SearchView uses icon
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
        return inflater.inflate(R.layout.friends_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (mInit) {
            mHeader.inflate();
        } else {
            mGrid.setPadding(0, 0, 0, 0); // remove padding for header
        }
        mGrid.setAdapter(new FriendsAdapter());
        mGrid.setOnItemClickListener(this);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public void onResume() {
        super.onResume();
        Bundle extras = new Bundle();
        extras.putBoolean(SYNC_EXTRAS_CONTACTS_ONLY, true);
        Content.requestSyncNow(Accounts.selected(), AUTHORITY, extras);
    }

    @Override
    public Loader<ReadCursor> onCreateLoader(int id, Bundle args) {
        String[] proj = {min(_ID), groupConcat(Contacts.GLOBAL_ID),
                Contacts.ANDROID_LOOKUP_KEY, Contacts.ANDROID_ID, Contacts.NAME,
                groupConcat(Contacts.EMAIL, "\t"), max(Contacts.FOLLOWING), Contacts.COLOR};
        StringBuilder sel = new StringBuilder(Contacts.STATUS_ID).append(" = ?");
        String[] selArgs;
        Uri uri = Uris.groupBy(Contacts.CONTENT_URI, Contacts.ANDROID_ID);
        StringBuilder order = new StringBuilder(
                Contacts.GLOBAL_ID + " IS NOT NULL DESC, " + Contacts.NAME + ", " + _ID);
        String searchQuery = args != null ? args.getString(SEARCH_QUERY) : null;
        if (!TextUtils.isEmpty(searchQuery)) {
            sel.append(" AND ").append(Contacts.NORMALISED_NAME).append(" LIKE ?");
            String filter = '%' + SQLite.normalise(searchQuery) + '%';
            selArgs = new String[]{String.valueOf(ACTIVE.id), filter, filter.substring(1)};
            order.insert(0, " LIKE ? DESC, ").insert(0, Contacts.NORMALISED_NAME);
        } else {
            selArgs = new String[]{String.valueOf(ACTIVE.id)};
        }
        return new ReadCursorLoader(a, uri, proj, sel.toString(), selArgs, order.toString());
    }

    @Override
    public void onLoadFinished(Loader<ReadCursor> loader, ReadCursor data) {
        if (mGrid != null) {
            ((CursorAdapter) mGrid.getAdapter()).swapCursor(data);
            mListener.onFriendClick(mGrid.getCheckedItemCount());
        }
    }

    /**
     * Add a new contact.
     */
    private static final Intent sAddIntent = new Intent(Insert.ACTION)
            .setType(RawContacts.CONTENT_TYPE).putExtra("finishActivityOnSaveCompleted", true);

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (!mListener.onFriendsOptionsMenu()) {
            return;
        }
        inflater.inflate(R.menu.friends, menu);
        if (mInit) {
            menu.removeItem(R.id.search);
        } else {
            MenuItem item = menu.findItem(R.id.search);
            mSearch = (SearchView) item.getActionView();
            mSearch.setSearchableInfo(Managers.search(a).getSearchableInfo(a.getComponentName()));
            SearchViews.setBackground(mSearch, R.drawable.textfield_searchview);
            mSearch.setOnSearchClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    event("friends", "search");
                }
            });
            mSearch.setOnQueryTextListener(new SearchTextListener());
            item.setOnActionExpandListener(new SearchExpandListener());
        }
        if (!Intents.hasActivity(a, sAddIntent)) {
            menu.removeItem(R.id.add);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.add:
                if (Intents.hasActivity(a, sAddIntent)) {
                    startActivity(sAddIntent);
                    event("friend", "add");
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        /* slide out action, update its value, slide it back in */
        final FriendHolder friend = ViewHolder.get(view);
        EasyCursor c = (EasyCursor) mGrid.getItemAtPosition(position);
        final boolean isUser = !c.isNull(Contacts.GLOBAL_ID);
        final String globalId = c.getString(Contacts.GLOBAL_ID);
        final String email = c.getString(Contacts.EMAIL);
        final boolean isChecked = mGrid.isItemChecked(position);
        friend.mAction.animate().withEndAction(new Runnable() {
            @Override
            public void run() {
                updateAction(friend.mAction, isChecked, isUser);
                friend.mAction.animate().withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (mInit) {
                            return;
                        }
                        if (isUser) { // follow
                            String[] globalIds = globalId.split(",");
                            ContentValues vals = new ContentValues(2);
                            vals.put(Contacts.FOLLOWING, isChecked);
                            vals.put(Contacts.DIRTY, 1);
                            a.startService(new Intent(ACTION_EDIT, Contacts.CONTENT_URI,
                                    a, ContentService.class).putExtra(EXTRA_VALUES, vals)
                                    .putExtra(EXTRA_SELECTION,
                                            in(Contacts.GLOBAL_ID, globalIds).toString()));
                            if (isChecked) {
                                a.startService(new Intent(a, ReviewsService.class)
                                        .putExtra(EXTRA_GLOBAL_IDS, Elements.toLongs(globalIds)));
                            }
                            event("friend", isChecked ? "follow" : "unfollow");
                        } else if (isChecked) { // invite
                            String[] addrs = email.split("\t");
                            if (addrs.length == 1) {
                                sendInvite(addrs[0], FriendsFragment.this);
                                event("friend", "invite");
                            } else if (addrs.length > 1) {
                                InviteFragment.newInstance(addrs).show(getFragmentManager(), null);
                                event("friend", "invite [choose email address]");
                            }
                        }
                    }
                }).translationX(0.0f).setInterpolator(OVERSHOOT);
            }
        }).translationX(friend.mAction.getWidth()).setInterpolator(ANTICIPATE);
        mListener.onFriendClick(mGrid.getCheckedItemCount());
    }

    /**
     * Update the action View text and icon based on its state.
     */
    private void updateAction(TextView view, boolean isChecked, boolean isUser) {
        if (isChecked) {
            view.setText(isUser ? R.string.following : R.string.inviting);
            view.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_action_accept_small, 0, 0,
                    0);
        } else {
            view.setText(isUser ? R.string.follow : R.string.invite);
            view.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_action_new_small, 0, 0, 0);
        }
    }

    /**
     * Get the global IDs of users that are chosen to be followed.
     *
     * @return null if none are checked
     */
    long[] getFollowed() {
        if (mGrid.getCheckedItemCount() <= 0) {
            return null;
        }
        ArrayLongList globalIds = null;
        int[] keys = SparseArrays.trueKeys(mGrid.getCheckedItemPositions());
        for (int pos : keys) {
            EasyCursor c = (EasyCursor) mGrid.getItemAtPosition(pos);
            if (!c.isNull(Contacts.GLOBAL_ID)) {
                if (globalIds == null) {
                    globalIds = new ArrayLongList();
                }
                String globalId = c.getString(Contacts.GLOBAL_ID);
                if (globalId.indexOf(',') < 0) {
                    globalIds.add(Long.parseLong(globalId));
                } else {
                    Elements.addAll(globalIds, Elements.toLongs(globalId.split(",")));
                }
            }
        }
        return globalIds != null ? globalIds.toArray() : null;
    }

    /**
     * Start an email app to send an invitation to selected contacts.
     */
    void invite() {
        if (mGrid.getCheckedItemCount() <= 0) {
            return;
        }
        List<String> to = null;
        int[] keys = SparseArrays.trueKeys(mGrid.getCheckedItemPositions());
        for (int pos : keys) {
            EasyCursor c = (EasyCursor) mGrid.getItemAtPosition(pos);
            if (c.isNull(Contacts.GLOBAL_ID) && !c.isNull(Contacts.EMAIL)) {
                if (to == null) {
                    to = new ArrayList<>(keys.length);
                }
                String email = c.getString(Contacts.EMAIL);
                if (!email.contains("\t")) {
                    to.add(email);
                } else {
                    CollectionUtils.addAll(to, email.split("\t"));
                }
            }
        }
        if (to != null) {
            sendInvite(to, this);
            event("friends", "invite", to.size());
        }
    }

    private static void sendInvite(String to, Fragment frag) {
        sendInvite(Collections.singletonList(to), frag);
    }

    private static void sendInvite(List<String> to, Fragment frag) {
        Intent intent = new Intent(ACTION_SENDTO, Uris.mailto(to, null, null,
                frag.getString(R.string.invite_subject), frag.getString(R.string.invite_body)));
        if (Intents.hasActivity(frag.getActivity(), intent)) {
            frag.startActivity(intent);
        }
    }

    @Override
    public void onLoaderReset(Loader<ReadCursor> loader) {
        if (mGrid != null) {
            ((CursorAdapter) mGrid.getAdapter()).swapCursor(null);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(a).unregisterReceiver(mReceiver);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     * Receives notifications for {@link FriendsFragment} events.
     */
    interface Listener {
        /**
         * The friends options menu is being created. Return true to add the menu items or false
         * to skip them.
         */
        boolean onFriendsOptionsMenu();

        /**
         * A friend has been clicked and the new total number of friends selected is provided.
         */
        void onFriendClick(int total);
    }

    /**
     * Translates contact rows to Views.
     */
    private class FriendsAdapter extends ResourceReadCursorAdapter {
        private final int mCellHeight;

        private FriendsAdapter() {
            super(a, R.layout.friends_adapter, null, 0);
            mCellHeight = res().getDimensionPixelSize(R.dimen.grid_row_height);
        }

        @Override
        public void bindView(View view, Context context, ReadCursor c) {
            FriendHolder friend = ViewHolder.get(view, FriendHolder.class);
            /* load contact photo */
            String key = c.getString(Contacts.ANDROID_LOOKUP_KEY);
            long id = c.getLong(Contacts.ANDROID_ID);
            Uri uri = key != null && id > 0 ? ContactsContract.Contacts.getLookupUri(id, key)
                    : null;
            String name = c.getString(Contacts.NAME);
            Picasso.with(context).load(uri).resize(mGrid.getColumnWidth(), mCellHeight).centerCrop()
                    .transform(TL).placeholder(Placeholders.rect(c, name)).into(friend.mPhoto);
            /* select if user already following or deselect if unfollowed remotely */
            boolean isUser = !c.isNull(Contacts.GLOBAL_ID);
            if (isUser && !c.wasRead()) {
                mGrid.setItemChecked(c.getPosition(), c.getInt(Contacts.FOLLOWING) == 1);
            }
            boolean isChecked = mGrid.isItemChecked(c.getPosition());
            friend.mName.setText(name != null ? name : getString(R.string.non_contact));
            updateAction(friend.mAction, isChecked, isUser);
        }
    }

    public static class FriendHolder extends ViewHolder {
        @Bind(R.id.photo)
        ImageView mPhoto;

        @Bind(R.id.name)
        TextView mName;

        @Bind(R.id.action)
        TextView mAction;

        @Override
        protected FriendHolder newInstance() {
            return new FriendHolder();
        }
    }

    /**
     * Prompts the user to select one of their contact's email addresses and then sends the invite.
     */
    public static class InviteFragment extends SprocketsDialogFragment {
        @Icicle
        String[] mAddresses;

        private static InviteFragment newInstance(String[] addresses) {
            InviteFragment frag = new InviteFragment();
            frag.mAddresses = addresses;
            return frag;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new Builder(a).setTitle(R.string.send_invite_to)
                    .setItems(mAddresses, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            sendInvite(mAddresses[which], InviteFragment.this);
                            event("friend", "invite");
                        }
                    }).create();
        }
    }

    /**
     * Filters the contacts by name as the search query changes.
     */
    private class SearchTextListener implements OnQueryTextListener {
        private String oldText = "";
        private Bundle mLoaderArgs;

        @Override
        public boolean onQueryTextChange(String newText) {
            if (!newText.equals(oldText)) {
                if (mLoaderArgs == null) {
                    mLoaderArgs = new Bundle(1);
                }
                mLoaderArgs.putString(SEARCH_QUERY, newText);
                getLoaderManager().restartLoader(0, mLoaderArgs, FriendsFragment.this);
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
     * Reloads the contacts when the SearchView is closed with an empty query. This is needed after
     * a configuration change when the SearchView has lost its query, yet the contacts are still
     * filtered. onQueryTextChange is not called when the SearchView is closed with an empty query.
     */
    private class SearchExpandListener implements OnActionExpandListener {
        @Override
        public boolean onMenuItemActionExpand(MenuItem item) {
            return true;
        }

        @Override
        public boolean onMenuItemActionCollapse(MenuItem item) {
            if (mSearch.getQuery().length() == 0) {
                getLoaderManager().restartLoader(0, null, FriendsFragment.this);
            }
            return true;
        }
    }

    /**
     * Shows the progress bar when contacts are synchronising with the server and hides it when
     * finished.
     */
    private class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mProgress != null) {
                if (ACTION_CONTACTS_SYNCING.equals(intent.getAction())) { // show progress bar
                    mProgress.setVisibility(VISIBLE);
                    mProgress.animate().alpha(1.0f);
                } else { // hide progress bar
                    mProgress.animate().alpha(0.0f).withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            if (mProgress != null) {
                                mProgress.setVisibility(GONE);
                            }
                        }
                    });
                }
            }
        }
    }
}
