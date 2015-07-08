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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.SimpleOnPageChangeListener;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import net.sf.diningout.R;
import net.sf.diningout.accounts.Accounts;
import net.sf.diningout.app.InitService;
import net.sf.diningout.provider.Contract;
import net.sf.diningout.provider.Contract.Restaurants;
import net.sf.sprockets.app.Fragments;
import net.sf.sprockets.app.ui.PanesActivity;
import net.sf.sprockets.app.ui.ProgressBarFragment;
import net.sf.sprockets.content.Content;
import net.sf.sprockets.google.Place;
import net.sf.sprockets.net.Network;
import net.sf.sprockets.preference.Prefs;

import java.util.ArrayList;

import butterknife.Bind;
import icepick.Icicle;

import static android.accounts.AccountManager.KEY_ACCOUNT_NAME;
import static com.google.android.gms.auth.GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE;
import static net.sf.diningout.app.InitService.EXTRA_FOLLOW_IDS;
import static net.sf.diningout.app.InitService.EXTRA_RESTAURANTS;
import static net.sf.diningout.preference.Keys.App.ACCOUNT_INITIALISED;
import static net.sf.diningout.preference.Keys.App.ACCOUNT_NAME;
import static net.sf.diningout.preference.Keys.App.APP;
import static net.sf.diningout.preference.Keys.App.ONBOARDED;
import static net.sf.diningout.provider.Contract.ACTION_USER_LOGGED_IN;
import static net.sf.diningout.provider.Contract.AUTHORITY;
import static net.sf.diningout.provider.Contract.EXTRA_HAS_RESTAURANTS;
import static net.sf.sprockets.app.SprocketsApplication.res;
import static net.sf.sprockets.gms.analytics.Trackers.event;
import static net.sf.sprockets.view.animation.Interpolators.ANTICIPATE;

/**
 * Logs the user into the server and helps them get started or restores their existing data.
 */
public class InitActivity extends PanesActivity
        implements InitRestaurantsFragment.Listener, FriendsFragment.Listener {
    /**
     * Tag for the progress bar fragment.
     */
    private static final String PROGRESS = "progress";

    @Nullable
    @Bind(R.id.panes)
    ViewPager mPager;

    /**
     * True if the Receiver is listening for a broadcast.
     */
    @Icicle
    boolean mListening;

    /**
     * Listens for the user to be logged in to the server.
     */
    private final Receiver mReceiver = new Receiver();

    /**
     * Number of restaurants that have been selected.
     */
    private int mRestaurantsSel;

    /**
     * Number of friends that have been selected.
     */
    private int mFriendsSel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (mListening) {
            mReceiver.start(this);
        }
        if (findFragmentByPane(1) != null) { // panes already populated
            setDefaultContentView();
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (savedInstanceState == null) {
            if (!Prefs.getBoolean(this, APP, ACCOUNT_INITIALISED)) {
                startActivityForResult(AccountManager.newChooseAccountIntent(null, null,
                        new String[]{GOOGLE_ACCOUNT_TYPE}, false, null, null, null, null), 0);
            } else {
                setDefaultContentView();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) { // initialise the selected account
            Prefs.putString(this, APP, ACCOUNT_NAME, data.getStringExtra(KEY_ACCOUNT_NAME));
            Account account = Accounts.selected();
            ContentResolver.setIsSyncable(account, AUTHORITY, 1);
            ContentResolver.setSyncAutomatically(account, AUTHORITY, true);
            if (Network.isConnected(this)) {
                Fragments.open(this).add(android.R.id.content, new ProgressBarFragment(), PROGRESS)
                        .commit();
                mReceiver.start(this);
                Content.requestSyncNow(account, AUTHORITY);
                return;
            }
        } else {
            event("account", "not chosen", "result code " + resultCode);
        }
        /* proceed without account and/or network connection */
        invalidateOptionsMenu(); // API 16 overwrites 'done' with 'add' without this
        setDefaultContentView();
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        if (mPager != null) {
            mPager.setPageMargin(res().getDimensionPixelOffset(R.dimen.cards_parent_margin));
            mPager.setAlpha(0.0f);
            mPager.animate().alpha(1.0f).withLayer();
            mPager.setOnPageChangeListener(new SimpleOnPageChangeListener() {
                @Override
                public void onPageSelected(int position) {
                    super.onPageSelected(position);
                    updateTitle();
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.init, menu); // API 16 needs items or none will ever show
        if (findFragmentByPane(1) == null) { // nothing to be 'done' with yet
            menu.findItem(R.id.done).setEnabled(false); // keep visible for API 16
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.done:
                if (mPager != null && mPager.getCurrentItem() == 0) { // continue to friends
                    slideToItem(1);
                } else { // add restaurants, follow and invite friends, send to restaurants Activity
                    Intent intent = new Intent(this, InitService.class);
                    /* send selected restaurants */
                    Place[] places = ((InitRestaurantsFragment) findFragmentByPane(1)).getChecked();
                    if (places != null) {
                        ArrayList<ContentValues> vals = new ArrayList<>(places.length);
                        for (Place place : places) {
                            vals.add(Restaurants.values(place));
                        }
                        intent.putParcelableArrayListExtra(EXTRA_RESTAURANTS, vals);
                    }
                    /* send followed friends */
                    FriendsFragment friends = findFragmentByPane(2);
                    long[] followed = friends.getFollowed();
                    startService(intent.putExtra(EXTRA_FOLLOW_IDS, followed));
                    Prefs.putBoolean(this, APP, ONBOARDED, true);
                    startActivity(new Intent(this, RestaurantsActivity.class));
                    friends.invite();
                    finish(); // last so invite returns to restaurants
                    event("restaurants", "init", places != null ? places.length : 0L);
                    event("friends", "init", followed != null ? followed.length : 0L);
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Slide the current item off screen and switch to the new item.
     */
    private void slideToItem(final int item) {
        final View view = findFragmentByPane(item == 1 ? 1 : 2).getView();
        int x = item == 1 ? -view.getWidth() : view.getWidth();
        view.animate().translationX(x).setInterpolator(ANTICIPATE).withLayer()
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (mPager != null) {
                            mPager.setCurrentItem(item);
                            mPager.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    view.setTranslationX(0.0f);
                                }
                            }, 300L); // reset after pager animation completes
                        }
                    }
                });
    }

    @Override
    public Fragment getFragment(int pane) {
        return pane == 1 ? new InitRestaurantsFragment() : FriendsFragment.newInstance(true);
    }

    @Override
    public void onRestaurantClick(int total) {
        mRestaurantsSel = total;
        updateTitle();
    }

    @Override
    public boolean onFriendsOptionsMenu() {
        return true;
    }

    @Override
    public void onFriendClick(int total) {
        mFriendsSel = total;
        updateTitle();
    }

    /**
     * Display the number of items selected in the title.
     */
    private void updateTitle() {
        if (mRestaurantsSel == 0 && mFriendsSel == 0) {
            setTitle(R.string.init_title);
        } else if (mPager != null) { // restaurants or friends
            int page = mPager.getCurrentItem();
            if (page == 0 && mRestaurantsSel > 0) {
                setTitle(getString(R.string.n_selected, mRestaurantsSel));
            } else if (page == 1 && mFriendsSel > 0) {
                setTitle(getString(R.string.n_selected, mFriendsSel));
            } else {
                setTitle(R.string.init_title);
            }
        } else { // restaurants and friends
            String restaurants = mRestaurantsSel > 0 ? res().getQuantityString(
                    R.plurals.n_restaurants, mRestaurantsSel, mRestaurantsSel) : null;
            String friends = mFriendsSel > 0
                    ? res().getQuantityString(R.plurals.n_friends, mFriendsSel, mFriendsSel) : null;
            if (restaurants != null && friends != null) {
                setTitle(getString(R.string.s_s_selected, restaurants, friends));
            } else {
                setTitle(getString(R.string.s_selected,
                        restaurants != null ? restaurants : friends));
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mPager != null && mPager.getCurrentItem() == 1) { // back to restaurants
            slideToItem(0);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mListening) {
            mReceiver.stop(this);
        }
    }

    /**
     * Loads the content panes for new users or sends existing users to {@link RestaurantsActivity}.
     */
    private class Receiver extends BroadcastReceiver {
        /**
         * Start listening for the {@link Contract#ACTION_USER_LOGGED_IN ACTION_USER_LOGGED_IN}
         * broadcast.
         */
        private Receiver start(Context context) {
            LocalBroadcastManager.getInstance(context).registerReceiver(this,
                    new IntentFilter(ACTION_USER_LOGGED_IN));
            mListening = true;
            return this;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            stop(context);
            FragmentManager fm = getFragmentManager();
            Fragment frag = fm.findFragmentByTag(PROGRESS);
            if (frag != null) {
                Fragments.close(fm).remove(frag).commitAllowingStateLoss();
                fm.executePendingTransactions(); // don't let close transition get skipped
            }
            if (!intent.getBooleanExtra(EXTRA_HAS_RESTAURANTS, false)) { // onboarding
                invalidateOptionsMenu(); // API 16 overwrites 'done' with 'add' without this
                setDefaultContentView();
            } else { // restoring
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        finish(); // first so RestaurantsActivity is task root (affects transition)
                        startActivity(new Intent(InitActivity.this, RestaurantsActivity.class));
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    }
                }, 1000L); // wait for restaurant add to avoid 'add restaurant' Activity launch
                event("user", "restore");
            }
        }

        /**
         * Stop listening for the broadcast.
         */
        private void stop(Context context) {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
            mListening = false;
        }
    }
}
