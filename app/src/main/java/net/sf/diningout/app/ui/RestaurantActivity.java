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

import android.app.Fragment;
import android.app.ListFragment;
import android.content.Intent;
import android.content.res.Resources;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.AbsListView;
import android.widget.AbsListView.LayoutParams;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Space;

import com.astuetz.PagerSlidingTabStrip;

import net.sf.diningout.R;
import net.sf.diningout.app.ui.RestaurantsFragment.Listener;
import net.sf.diningout.widget.RestaurantCursorAdapter;
import net.sf.sprockets.app.ui.SprocketsActivity;
import net.sf.sprockets.app.ui.SprocketsListFragment;
import net.sf.sprockets.content.res.Themes;
import net.sf.sprockets.view.Animators;
import net.sf.sprockets.view.Displays;
import net.sf.sprockets.view.TranslateImagePageChangeListener;
import net.sf.sprockets.view.Windows;
import net.sf.sprockets.view.inputmethod.InputMethods;
import net.sf.sprockets.widget.FadingActionBarScrollListener;
import net.sf.sprockets.widget.FloatingHeaderScrollListener;
import net.sf.sprockets.widget.ListScrollListeners;
import net.sf.sprockets.widget.ListScrollListeners.OnScrollApprover;
import net.sf.sprockets.widget.ListViews;
import net.sf.sprockets.widget.ParallaxViewScrollListener;

import butterknife.InjectView;

import static android.support.v4.view.ViewPager.SCROLL_STATE_IDLE;
import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_UP;
import static android.widget.AbsListView.CHOICE_MODE_SINGLE;
import static android.widget.AdapterView.ITEM_VIEW_TYPE_HEADER_OR_FOOTER;
import static net.sf.diningout.data.Review.Type.GOOGLE;
import static net.sf.diningout.data.Review.Type.PRIVATE;
import static net.sf.sprockets.app.SprocketsApplication.res;
import static net.sf.sprockets.gms.analytics.Trackers.event;

/**
 * Displays a restaurant's details and reviews. Callers must include {@link #EXTRA_ID} in their
 * Intent extras.
 */
public class RestaurantActivity extends SprocketsActivity implements OnScrollApprover, Listener {
    /**
     * ID of the restaurant.
     */
    public static final String EXTRA_ID = "intent.extra.ID";

    /**
     * Tab to start on when the Activity is created. Must be one of {@link #TAB_PRIVATE},
     * {@link #TAB_PUBLIC}, or {@link #TAB_NOTES}.
     */
    public static final String EXTRA_TAB = "intent.extra.TAB";
    public static final int TAB_PRIVATE = 0;
    public static final int TAB_PUBLIC = 1;
    public static final int TAB_NOTES = 2;

    /**
     * Position of the selected sort option.
     */
    public static final String EXTRA_SORT = "intent.extra.SORT";

    /**
     * Image to display while the restaurant photo is loading.
     */
    static Drawable sPlaceholder;
    private static final int[] sTabTitles = {R.string.tab_private, R.string.tab_public,
            R.string.tab_notes};
    private static final String[] sTabEventLabels = {"private", "public", "notes"};

    @InjectView(R.id.detail)
    View mDetail;
    @InjectView(R.id.tabs)
    PagerSlidingTabStrip mTabs;
    @InjectView(R.id.pager)
    ViewPager mPager;
    private long mId;
    private int mActionBarSize;

    /**
     * True if the current touch event started on a tab.
     */
    private boolean mTabsActionDown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        mId = intent.getLongExtra(EXTRA_ID, 0L);
        int tab = intent.getIntExtra(EXTRA_TAB, 0);
        mActionBarSize = Themes.getActionBarSize(this);
        setContentView(R.layout.restaurant_activity);
        mPager.setOffscreenPageLimit(sTabTitles.length - 1); // keep all Fragments alive
        mPager.setAdapter(new PagerAdapter());
        mTabs.setViewPager(mPager);
        mTabs.setOnPageChangeListener(new PageChangeListener());
        mTabs.setOnTouchListener(new TabsTouchListener());
        if (savedInstanceState == null && tab > 0) {
            mPager.setCurrentItem(tab);
        }
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);
        if (fragment instanceof RestaurantsFragment) {
            int sort = getIntent().getIntExtra(EXTRA_SORT, -1);
            if (sort >= 0) {
                ((RestaurantsFragment) fragment).setSort(sort);
            }
        }
    }

    @Override
    public boolean onScroll(OnScrollListener listener, AbsListView view, int first, int visible,
                            int total) {
        /* only when list is from current page */
        TabListFragment item = getCurrentTabFragment();
        return item != null && item.getView() != null && view == item.getListView();
    }

    TabListFragment getCurrentTabFragment() {
        return ((PagerAdapter) mPager.getAdapter()).mItems[mPager.getCurrentItem()];
    }

    @Override
    public void onViewCreated(AbsListView view) {
        int padding = view.getPaddingTop();
        view.setPadding(padding, Themes.getActionBarSize(this) + padding, padding, padding);
        view.setChoiceMode(CHOICE_MODE_SINGLE);
        ((RestaurantCursorAdapter) view.getAdapter()).setSelectedId(mId);
    }

    @Override
    public boolean onRestaurantsOptionsMenu() {
        return false;
    }

    @Override
    public void onRestaurantsSearch(String query) {
    }

    @Override
    public void onRestaurantClick(View view, long id) {
        if (mId != id) {
            mId = id;
            getIntent().putExtra(EXTRA_ID, id);
            restaurant().show(id);
            for (TabListFragment frag : ((PagerAdapter) mPager.getAdapter()).mItems) {
                frag.setRestaurant(id);
            }
            getCurrentTabFragment().getListView().smoothScrollToPosition(0);
            event("restaurant", "change");
        }
    }

    /**
     * Get the restaurant fragment.
     */
    private RestaurantFragment restaurant() {
        return (RestaurantFragment) getFragmentManager().findFragmentById(R.id.detail);
    }

    /**
     * Get the restaurants fragment if it is added.
     */
    private RestaurantsFragment restaurants() {
        return (RestaurantsFragment) getFragmentManager().findFragmentById(R.id.restaurants);
    }

    @Override
    public void finish() {
        ViewPropertyAnimator anim = Animators.makeScaleDownAnimation(this);
        if (anim != null) {
            anim.alpha(0.0f).withEndAction(new Runnable() {
                @Override
                public void run() {
                    RestaurantActivity.super.finish();
                    overridePendingTransition(0, 0);
                }
            });
        } else {
            super.finish();
        }
    }

    /**
     * Provides Fragments for the tabs.
     */
    private class PagerAdapter extends FragmentPagerAdapter {
        /**
         * Cached items that are populated as they are created.
         */
        private final TabListFragment[] mItems = new TabListFragment[getCount()];

        private PagerAdapter() {
            super(getFragmentManager());
        }

        @Override
        public int getCount() {
            return sTabTitles.length;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            /* save items from getItem(int) or when restored after configuration change */
            mItems[position] = (TabListFragment) super.instantiateItem(container, position);
            return mItems[position];
        }

        @Override
        public TabListFragment getItem(int position) {
            switch (position) {
                case 0:
                case 1:
                    return ReviewsFragment.newInstance(mId, position == 0 ? PRIVATE : GOOGLE);
                case 2:
                    return NotesFragment.newInstance(mId);
            }
            return null;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return getString(sTabTitles[position]);
        }
    }

    /**
     * Synchronises the list scrolls to maintain tabs translation.
     */
    private class PageChangeListener extends TranslateImagePageChangeListener {
        /**
         * Previous pager item from which the list scrolls are synchronised.
         */
        private int mOldItem = mPager.getCurrentItem();
        /**
         * True if the lists have been synchronised for the paging session.
         */
        private boolean mSynced;

        private PageChangeListener() {
            super(mPager, ((ImageView) mDetail.findViewById(R.id.photo)));
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            super.onPageScrollStateChanged(state);
            if (state == SCROLL_STATE_IDLE) { // can scroll tabs up and down now
                mSynced = false;
            }
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            super.onPageScrolled(position, positionOffset, positionOffsetPixels);
            if (!mSynced && positionOffsetPixels > 0) {
                sync();
            }
        }

        @Override
        public void onPageSelected(int position) {
            super.onPageSelected(position);
            if (!mSynced) {
                sync();
            }
            if (mOldItem == 0 || mOldItem == 2) { // hide input method when selecting other tab
                mPager.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        InputMethods.hide(mPager);
                    }
                }, 300L); // after page swipe animation
            }
            PagerAdapter adapter = (PagerAdapter) mPager.getAdapter();
            final TabListFragment oldItem = adapter.mItems[mOldItem];
            final TabListFragment newItem = adapter.mItems[position];
            if (oldItem != null && newItem != null) { // can be after configuration change
                mPager.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        oldItem.hideActionMode();
                        newItem.restoreActionMode();
                    }
                }, 500L); // after ActionBar menu updates (so add review icon doesn't flicker)
            }
            mOldItem = position;
            event("restaurant", "view tab", sTabEventLabels[position]);
        }

        /**
         * Synchronise scroll of other lists.
         */
        private void sync() {
            PagerAdapter adapter = (PagerAdapter) mPager.getAdapter();
            TabListFragment item = adapter.mItems[mOldItem];
            if (item == null) {
                return; // after configuration change, before items are restored
            }
            ListView oldList = item.getListView();
            boolean oldHeaderVisible = oldList.getFirstVisiblePosition() == 0;
            int count = adapter.getCount();
            for (int i = 0; i < count; i++) { // sync all lists in case of continuous page scroll
                if (i != mOldItem) {
                    item = adapter.mItems[i];
                    ListView newList = item.getListView();
                    if (oldHeaderVisible) {
                        newList.setSelectionFromTop(0, oldList.getChildAt(0).getTop());
                    } else {
                        newList.setSelectionFromTop(1, mActionBarSize * 2 + item.mDividerHeight);
                    }
                }
            }
            mSynced = true;
        }
    }

    /**
     * Forwards touch events to the current ListFragment.
     */
    private class TabsTouchListener implements OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            ListFragment frag = getCurrentTabFragment();
            if (frag == null || frag.getView() == null) {
                return false; // list not ready yet
            }
            ListView list = frag.getListView();
            event.offsetLocation(0.0f, mTabs.getTranslationY()); // match tabs actual location
            switch (event.getActionMasked()) {
                case ACTION_DOWN:
                    mTabsActionDown = true;
                    break;
                case ACTION_UP:
                case ACTION_CANCEL:
                    mTabsActionDown = false;
                    break;
                default:
                    if (!mTabsActionDown) { // ACTION_DOWN was eaten, manually send it to the list
                        mTabsActionDown = true;
                        MotionEvent down = MotionEvent.obtain(event);
                        down.setAction(ACTION_DOWN);
                        list.dispatchTouchEvent(down);
                        down.recycle();
                    }
            }
            list.dispatchTouchEvent(event);
            return false;
        }
    }

    /**
     * Adds a transparent header View over the restaurant details and tabs, and a footer View sized
     * so that a short list can still scroll to the top of the screen. Sets an OnScrollListener to
     * manage the ActionBar transparency, details parallax scrolling, and tabs floating.
     */
    static abstract class TabListFragment extends SprocketsListFragment {
        int mDividerHeight;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mDividerHeight = res().getDimensionPixelOffset(R.dimen.cards_sibling_margin);
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            RestaurantActivity a = a();
            Resources res = res();
            ListView list = getListView();
            list.setFocusable(false); // don't steal focus from EditText when keyboard appears
            list.setSelector(R.drawable.cards_list_selector);
            list.setDivider(res.getDrawable(R.color.cards_window_background));
            list.setDividerHeight(mDividerHeight);
            list.setOnTouchListener(new TouchListener());
            FadingActionBarScrollListener fade = new FadingActionBarScrollListener(a, true, true, 1)
                    .setOpaqueOffset(a.mActionBarSize * 2).setOnScrollApprover(a);
            if (Displays.getSize(a).x > res.getDimensionPixelSize(R.dimen.restaurant_photo_width)) {
                fade.setMinBackgroundOpacity(Color.alpha(res.getColor(R.color.overlay)));
            }
            OnScrollListener parallax = new ParallaxViewScrollListener(a.mDetail, 0.5f)
                    .setOnScrollApprover(a);
            OnScrollListener floating = new FloatingHeaderScrollListener(a.mTabs)
                    .setActionBarOverlay(true).setOnScrollApprover(a);
            list.setOnScrollListener(new ListScrollListeners(fade, parallax, floating));
            Space header = new Space(a);
            header.setLayoutParams(new LayoutParams(1,
                    res.getDimensionPixelSize(R.dimen.restaurant_photo_height) + a.mActionBarSize,
                    ITEM_VIEW_TYPE_HEADER_OR_FOOTER));
            list.addHeaderView(header); // must be selectable to draw following divider
        }

        @Override
        public void setListAdapter(ListAdapter adapter) {
            super.setListAdapter(adapter);
            adapter.registerDataSetObserver(new Observer());
        }

        /**
         * Update the list for the restaurant.
         */
        abstract void setRestaurant(long id);

        /**
         * Forwards touch events to the overlaid detail View.
         */
        private class TouchListener implements OnTouchListener {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                RestaurantActivity a = a();
                /* only if didn't start from tab (touch slop) and the detail View is visible */
                if (!a.mTabsActionDown && getListView().getFirstVisiblePosition() == 0) {
                    if (event.getY() < a.mTabs.getTranslationY() + a.mTabs.getHeight()) {
                        float y = a.mDetail.getTranslationY();
                        if (y == 0.0f) {
                            a.mDetail.dispatchTouchEvent(event);
                        } else { // offset event to match detail translation
                            MotionEvent offsetEvent = MotionEvent.obtain(event);
                            offsetEvent.offsetLocation(0.0f, -y);
                            a.mDetail.dispatchTouchEvent(offsetEvent);
                            offsetEvent.recycle();
                        }
                    }
                }
                return false;
            }
        }

        /**
         * Updates the footer View height when the adapter Views change so that short lists (and the
         * tabs above them) can still be scrolled to the top of the screen.
         */
        private class Observer extends DataSetObserver {
            private final Space mFooter = new Space(a);

            private Observer() {
                mFooter.setLayoutParams(new LayoutParams(1, 0, ITEM_VIEW_TYPE_HEADER_OR_FOOTER));
                getListView().addFooterView(mFooter, null, false);
            }

            @Override
            public void onChanged() {
                super.onChanged();
                final ListView view = getListView();
                /* when the Views are laid out, update footer height so they can scroll to top */
                view.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        /* based on display height, windowSoftInputMode adjustResize shrinks View */
                        int listHeight = Displays.getSize(a).y - Windows.getFrame(a).top
                                - ((RestaurantActivity) a).mActionBarSize * 2; // status - AB - tabs
                        int views = ListViews.getHeight(view, 1, view.getAdapter().getCount() - 1,
                                listHeight); // ignore header and footer
                        mFooter.getLayoutParams().height =
                                Math.max(listHeight - views - mDividerHeight, 0);
                        mFooter.requestLayout();
                    }
                });
            }
        }
    }
}
