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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.Loader;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ShareActionProvider;
import android.widget.ShareActionProvider.OnShareTargetSelectedListener;
import android.widget.Spinner;
import android.widget.TextView;

import net.sf.diningout.R;
import net.sf.diningout.app.ui.RestaurantActivity.TabListFragment;
import net.sf.diningout.data.Review.Type;
import net.sf.diningout.data.Status;
import net.sf.diningout.provider.Contract.Contacts;
import net.sf.diningout.provider.Contract.ReviewDrafts;
import net.sf.diningout.provider.Contract.Reviews;
import net.sf.diningout.provider.Contract.ReviewsJoinContacts;
import net.sf.diningout.undobar.Undoer;
import net.sf.diningout.widget.ReviewAdapter;
import net.sf.sprockets.app.ui.SprocketsDialogFragment;
import net.sf.sprockets.content.EasyCursorLoader;
import net.sf.sprockets.content.Intents;
import net.sf.sprockets.content.res.Themes;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.util.Elements;
import net.sf.sprockets.util.SparseArrays;
import net.sf.sprockets.view.ViewHolder;
import net.sf.sprockets.view.inputmethod.InputMethods;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import icepick.Icicle;

import static android.app.SearchManager.QUERY;
import static android.content.Intent.ACTION_SEND;
import static android.content.Intent.ACTION_WEB_SEARCH;
import static android.content.Intent.EXTRA_SUBJECT;
import static android.content.Intent.EXTRA_TEXT;
import static android.provider.BaseColumns._ID;
import static android.widget.AbsListView.CHOICE_MODE_MULTIPLE_MODAL;
import static net.sf.diningout.data.Review.Type.PRIVATE;
import static net.sf.diningout.data.Status.ACTIVE;
import static net.sf.diningout.data.Status.DELETED;
import static net.sf.diningout.provider.Contract.AUTHORITY_URI;
import static net.sf.diningout.provider.Contract.CALL_UPDATE_RESTAURANT_LAST_VISIT;
import static net.sf.diningout.provider.Contract.CALL_UPDATE_RESTAURANT_RATING;
import static net.sf.sprockets.app.SprocketsApplication.cr;
import static net.sf.sprockets.app.SprocketsApplication.res;
import static net.sf.sprockets.gms.analytics.Trackers.event;
import static net.sf.sprockets.sql.SQLite.millis;

/**
 * Displays a list of reviews for a restaurant.
 */
public class ReviewsFragment extends TabListFragment implements LoaderCallbacks<EasyCursor> {
    private static final int LOADER_REVIEWS = 0;
    private static final int LOADER_REVIEW_DRAFT = 1;
    private static final int DEFAULT_RATING_POS = 2;

    @Icicle
    long mRestaurantId;

    @Icicle
    int mTypeId;

    /**
     * True if a review is being added or edited.
     */
    @Icicle
    boolean mEditing;

    /**
     * Review being edited or 0.
     */
    @Icicle
    long mReviewId;

    @Icicle
    int mRatingPos = -1;

    @Icicle
    CharSequence mComments;

    @Icicle
    long mDraftVersion = -1L;

    private ActionMode mActionMode;
    private final Intent mShare = new Intent(ACTION_SEND).setType("text/plain");

    /**
     * Display the type of reviews for the restaurant and, optionally, prompt to add a new review.
     */
    static ReviewsFragment newInstance(long restaurantId, Type type, boolean addReview) {
        ReviewsFragment frag = new ReviewsFragment();
        frag.mRestaurantId = restaurantId;
        frag.mTypeId = type.id;
        frag.mEditing = addReview;
        return frag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (mTypeId == PRIVATE.id) {
            setHasOptionsMenu(true);
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ListView list = getListView();
        list.setDrawSelectorOnTop(true);
        list.setChoiceMode(CHOICE_MODE_MULTIPLE_MODAL);
        list.setMultiChoiceModeListener(new ChoiceListener());
        setListAdapter(new ReviewAdapter(a));
        if (mEditing) {
            editReview(false);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        LoaderManager lm = getLoaderManager();
        lm.initLoader(LOADER_REVIEWS, null, this);
        if (mTypeId == PRIVATE.id) {
            lm.initLoader(LOADER_REVIEW_DRAFT, null, this);
        }
    }

    @Override
    public Loader<EasyCursor> onCreateLoader(int id, Bundle args) {
        Uri uri = null;
        String[] proj = null;
        String sel = null;
        String[] selArgs = null;
        String order = null;
        switch (id) {
            case LOADER_REVIEWS:
                switch (Type.get(mTypeId)) {
                    case PRIVATE:
                        uri = ReviewsJoinContacts.CONTENT_URI;
                        proj = new String[]{ReviewsJoinContacts.REVIEW__ID, Reviews.TYPE_ID,
                                Reviews.CONTACT_ID, Contacts.ANDROID_LOOKUP_KEY,
                                Contacts.ANDROID_ID, Contacts.NAME, Contacts.COLOR,
                                Reviews.COMMENTS, Reviews.RATING, millis(Reviews.WRITTEN_ON)};
                        sel = Reviews.RESTAURANT_ID + " = ? AND " + Reviews.TYPE_ID + " = ? AND "
                                + ReviewsJoinContacts.REVIEW_STATUS_ID + " = ?";
                        break;
                    case GOOGLE:
                        uri = Reviews.CONTENT_URI;
                        proj = new String[]{_ID, Reviews.TYPE_ID, Reviews.CONTACT_ID,
                                Reviews.AUTHOR_NAME, Reviews.COMMENTS, Reviews.RATING,
                                millis(Reviews.WRITTEN_ON)};
                        sel = Reviews.RESTAURANT_ID + " = ? AND " + Reviews.TYPE_ID + " = ? AND "
                                + Reviews.STATUS_ID + " = ?";
                        break;
                }
                selArgs = Elements.toStrings(mRestaurantId, mTypeId, ACTIVE.id);
                order = Reviews.WRITTEN_ON + " DESC";
                break;
            case LOADER_REVIEW_DRAFT:
                uri = ContentUris.withAppendedId(ReviewDrafts.CONTENT_URI, mRestaurantId);
                proj = new String[]{ReviewDrafts.COMMENTS, ReviewDrafts.RATING,
                        ReviewDrafts.STATUS_ID, ReviewDrafts.VERSION};
                break;
        }
        return new EasyCursorLoader(a, uri, proj, sel, selArgs, order);
    }

    @Override
    public void onLoadFinished(Loader<EasyCursor> loader, EasyCursor c) {
        switch (loader.getId()) {
            case LOADER_REVIEWS:
                ((CursorAdapter) getListAdapter()).swapCursor(c);
                updateActionMode();
                switch (Type.get(mTypeId)) {
                    case PRIVATE:
                        if (!mEditing && c.getCount() == 0) {
                            editReview(false);
                        } else {
                            a.invalidateOptionsMenu(); // show add/share now
                        }
                        break;
                    case GOOGLE:
                        ListView list = getListView();
                        if (c.getCount() == 0) {
                            if (list.getHeaderViewsCount() == 1) { // add header View
                                View view = a.getLayoutInflater()
                                        .inflate(R.layout.reviews_public_empty, list, false);
                                list.addHeaderView(view);
                                ButterKnife.bind(this, view);
                            }
                        } else if (list.getHeaderViewsCount() > 1) { // no longer need header View
                            list.removeHeaderView(list.getAdapter().getView(1, null, list));
                        }
                        break;
                }
                break;
            case LOADER_REVIEW_DRAFT:
                if (c.moveToFirst()) {
                    long version = c.getLong(ReviewDrafts.VERSION);
                    if (mReviewId == 0) {
                        Status status = Status.get(c.getInt(ReviewDrafts.STATUS_ID));
                        if (status == ACTIVE && version > mDraftVersion) {
                            EditReviewHolder review = !mEditing ? editReview(false) : getReview();
                            review.mRatings.setSelection(c.getInt(ReviewDrafts.RATING) - 1);
                            review.mComments.setText(c.getString(ReviewDrafts.COMMENTS));
                        } else if (status == DELETED && mEditing && getReview() != null
                                && mDraftVersion >= 0 && version > mDraftVersion) {
                            discardReview(false);
                        }
                    }
                    mDraftVersion = version;
                }
                break;
        }
    }

    /**
     * Update the ActionMode title, refresh the menu, and prepare the share Intent.
     */
    private void updateActionMode() {
        if (mActionMode != null) {
            ListView list = getListView();
            int count = list.getCheckedItemCount();
            if (count > 0) {
                mActionMode.setTitle(getString(R.string.n_selected, count));
                mActionMode.invalidate();
                String subject = res().getQuantityString(R.plurals.reviews_share, count,
                        a.getTitle());
                mShare.putExtra(EXTRA_SUBJECT, subject);
                StringBuilder text = new StringBuilder(512 * count);
                for (int i : SparseArrays.trueKeys(list.getCheckedItemPositions())) {
                    if (text.length() > 0) { // separate sequential reviews
                        text.append("\n\n");
                    }
                    EasyCursor c = (EasyCursor) list.getItemAtPosition(i);
                    text.append(getString(R.string.review_metadata, ReviewAdapter.name(a, c),
                            ReviewAdapter.time(a, c), c.getInt(Reviews.RATING)));
                    CharSequence comments = ReviewAdapter.comments(c);
                    if (comments.length() > 0) {
                        text.append("\n").append(comments);
                    }
                }
                mShare.putExtra(EXTRA_TEXT, text.toString());
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.reviews, menu);
        if (mEditing || getListAdapter().getCount() == 0) {
            menu.findItem(R.id.add).setEnabled(false).setVisible(false);
        }
        if (!mEditing) {
            menu.findItem(R.id.done).setEnabled(false).setVisible(false);
            menu.findItem(R.id.discard).setEnabled(false).setVisible(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.add:
                editReview(true);
                return true;
            case R.id.done:
                EditReviewHolder review = getReview();
                if (review == null) { // somehow can click done without editing a review
                    event("review", "try to add/update but holder is null");
                    return true;
                }
                if (TextUtils.getTrimmedLength(review.mComments.getText()) == 0) { // confirm
                    DialogFragment dialog = new EmptyReviewDialog();
                    dialog.setTargetFragment(this, 0);
                    dialog.show(getFragmentManager(), null);
                    event("review", "empty, prompt to confirm");
                    return true;
                }
                item.setEnabled(false); // prevent double tap due to delayed menu invalidate
                saveReview(); // and then fall through to discard draft
            case R.id.discard:
                discard();
                if (id == R.id.discard) {
                    event("review", "discard");
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Cancel the adding or editing of a review.
     */
    private void discard() {
        if (mReviewId == 0) {
            deleteDraft();
        }
        discardReview(false);
    }

    /**
     * Confirms that an empty review should be added.
     */
    public static class EmptyReviewDialog extends SprocketsDialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(a).setTitle(R.string.add_empty_review)
                    .setPositiveButton(android.R.string.yes, new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ReviewsFragment frag = (ReviewsFragment) getTargetFragment();
                            frag.saveReview();
                            frag.discard();
                            event("review", "add empty");
                        }
                    }).setNegativeButton(android.R.string.no, null).create();
        }
    }

    /**
     * Add a header View for adding or editing a review and, optionally, scroll to it.
     */
    private EditReviewHolder editReview(boolean scroll) {
        ListView list = getListView();
        View view = a.getLayoutInflater().inflate(R.layout.reviews_edit, list, false);
        EditReviewHolder r = ViewHolder.get(view, EditReviewHolder.class);
        r.mTitle.setText(mReviewId == 0 ? R.string.add_review_title : R.string.edit_review_title);
        r.mRatings.setAdapter(ArrayAdapter.createFromResource(a, R.array.ratings, R.layout.rating));
        r.mRatings.setSelection(mRatingPos >= 0 ? mRatingPos : DEFAULT_RATING_POS);
        r.mComments.setText(mComments);
        list.addHeaderView(view);
        ((BaseAdapter) getListAdapter()).notifyDataSetChanged(); // ListView only tells own Observer
        if (scroll) { // to the new header View (below ActionBar and tabs)
            list.smoothScrollToPositionFromTop(1, Themes.getActionBarSize(a) * 2 + mDividerHeight);
            r.mComments.requestFocus();
            if (a instanceof RestaurantActivity
                    && ((RestaurantActivity) a).getCurrentTabFragment() == this) {
                InputMethods.show(r.mComments);
            }
        }
        mEditing = true;
        a.invalidateOptionsMenu();
        return r;
    }

    /**
     * Add a new review or update an existing one.
     */
    private void saveReview() {
        EditReviewHolder review = getReview();
        String comments = review.mComments.getText().toString().trim();
        ContentValues vals = new ContentValues(4);
        vals.put(Reviews.COMMENTS, comments);
        vals.put(Reviews.RATING, review.mRatings.getSelectedItemPosition() + 1);
        String restaurantId = String.valueOf(mRestaurantId);
        if (mReviewId == 0) {
            vals.put(Reviews.RESTAURANT_ID, mRestaurantId);
            vals.put(Reviews.TYPE_ID, mTypeId);
            cr().insert(Reviews.CONTENT_URI, vals);
            cr().call(AUTHORITY_URI, CALL_UPDATE_RESTAURANT_LAST_VISIT, restaurantId, null);
            event("review", "add", "length", comments.length());
        } else {
            vals.put(Reviews.DIRTY, 1);
            cr().update(ContentUris.withAppendedId(Reviews.CONTENT_URI, mReviewId), vals, null,
                    null);
            event("review", "update", "length", comments.length());
        }
        cr().call(AUTHORITY_URI, CALL_UPDATE_RESTAURANT_RATING, restaurantId, null);
    }

    /**
     * Remove or reset the header View for adding or editing a review.
     *
     * @param remove true if the header View should be removed even if there are no reviews
     */
    private void discardReview(final boolean remove) {
        final ListView view = getListView();
        InputMethods.hide(view);
        view.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isResumed()) {
                    return;
                }
                BaseAdapter adapter = (BaseAdapter) getListAdapter();
                if (remove || adapter.getCount() > 0) {
                    view.removeHeaderView(view.getAdapter().getView(1, null, view));
                    adapter.notifyDataSetChanged(); // ListView only tells own Observer
                    mEditing = false;
                    mReviewId = 0L;
                    a.invalidateOptionsMenu();
                } else { // just reset it
                    EditReviewHolder review = getReview();
                    review.mRatings.setSelection(DEFAULT_RATING_POS);
                    review.mComments.setText(null);
                }
            }
        }, !remove ? 500L : 0L); // after input method hidden and cursor reloaded on slower devices
        mRatingPos = DEFAULT_RATING_POS;
        mComments = null;
    }

    /**
     * Get the ViewHolder of the header View for adding or editing a review.
     *
     * @return null if the header View is not added
     */
    private EditReviewHolder getReview() {
        ListView view = getListView();
        return mEditing && view.getHeaderViewsCount() > 1
                ? (EditReviewHolder) ViewHolder.get(view.getAdapter().getView(1, null, view))
                : null;
    }

    /**
     * Search the web for reviews of the restaurant.
     */
    @Nullable
    @OnClick(R.id.search)
    void searchWeb() {
        Intent intent = new Intent(ACTION_WEB_SEARCH)
                .putExtra(QUERY, getString(R.string.reviews_public_search, a.getTitle()));
        if (Intents.hasActivity(a, intent)) {
            startActivity(intent);
            event("reviews", "search web");
        } else {
            event("reviews", "search web [fail]");
        }
    }

    @Override
    void setRestaurant(long id) {
        checkDraft();
        discardReview(true);
        mRestaurantId = id;
        mEditing = false;
        mReviewId = 0L;
        mRatingPos = -1;
        mComments = null;
        mDraftVersion = -1L;
        if (mActionMode != null) {
            mActionMode.finish();
        }
        LoaderManager lm = getLoaderManager();
        lm.restartLoader(LOADER_REVIEWS, null, this);
        if (mTypeId == PRIVATE.id) {
            lm.restartLoader(LOADER_REVIEW_DRAFT, null, this);
        }
    }

    @Override
    public void onLoaderReset(Loader<EasyCursor> loader) {
        ((CursorAdapter) getListAdapter()).swapCursor(null);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (!a.isChangingConfigurations()) {
            checkDraft();
        }
    }

    /**
     * Save any new review draft.
     */
    private void checkDraft() {
        if (mReviewId == 0) {
            EditReviewHolder review = getReview();
            if (review != null) {
                if (TextUtils.getTrimmedLength(review.mComments.getText()) > 0) {
                    saveDraft(review);
                } else {
                    deleteDraft();
                }
            }
        }
    }

    private void saveDraft(EditReviewHolder review) {
        /* get existing values */
        String oldComments = null;
        int oldRating = 0;
        Uri uri = ContentUris.withAppendedId(ReviewDrafts.CONTENT_URI, mRestaurantId);
        String[] proj = {ReviewDrafts.COMMENTS, ReviewDrafts.RATING};
        EasyCursor c = new EasyCursor(cr().query(uri, proj, null, null, null));
        int count = c.getCount();
        if (c.moveToNext()) {
            oldComments = c.getString(ReviewDrafts.COMMENTS);
            oldRating = c.getInt(ReviewDrafts.RATING);
        }
        c.close();
        /* update if changed or insert if none yet */
        String newComments = review.mComments.getText().toString();
        int newRating = review.mRatings.getSelectedItemPosition() + 1;
        if (count > 0) {
            if (newRating != oldRating || !newComments.equals(oldComments)) {
                cr().update(uri, draftValues(newComments, newRating, ACTIVE), null, null);
                event("review", "save draft");
            }
        } else {
            ContentValues vals = draftValues(newComments, newRating, ACTIVE);
            vals.put(ReviewDrafts.RESTAURANT_ID, mRestaurantId);
            cr().insert(ReviewDrafts.CONTENT_URI, vals);
            event("review", "save draft");
        }
    }

    /**
     * @return number of rows updated
     */
    private int deleteDraft() { //
        String sel = ReviewDrafts.STATUS_ID + "<> ?";
        String[] args = {String.valueOf(Status.DELETED.id)};
        return cr().update(ContentUris.withAppendedId(ReviewDrafts.CONTENT_URI, mRestaurantId),
                draftValues(null, null, DELETED), sel, args);
    }

    private ContentValues draftValues(String comments, Integer rating, Status status) {
        ContentValues vals = new ContentValues(5);
        vals.put(ReviewDrafts.COMMENTS, comments);
        vals.put(ReviewDrafts.RATING, rating);
        vals.put(ReviewDrafts.STATUS_ID, status.id);
        vals.put(ReviewDrafts.DIRTY, 1);
        mDraftVersion++;
        return vals;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        EditReviewHolder review = getReview();
        if (review != null) { // adapter Views not saved/restored by framework
            mRatingPos = review.mRatings.getSelectedItemPosition();
            mComments = review.mComments.getText();
        }
        super.onSaveInstanceState(outState);
    }

    /**
     * Manages the CAB while reviews are selected.
     */
    private class ChoiceListener implements MultiChoiceModeListener {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mActionMode = mode;
            mode.getMenuInflater().inflate(R.menu.reviews_cab, menu);
            menu.findItem(R.id.edit).setEnabled(false).setVisible(false);
            ShareActionProvider share =
                    (ShareActionProvider) menu.findItem(R.id.share).getActionProvider();
            share.setShareHistoryFileName("review_share_history.xml");
            share.setShareIntent(mShare);
            share.setOnShareTargetSelectedListener(new OnShareTargetSelectedListener() {
                @Override
                public boolean onShareTargetSelected(ShareActionProvider source, Intent intent) {
                    ComponentName component = intent.getComponent();
                    event("reviews", "share", component != null ? component.getClassName() : null,
                            getListView().getCheckedItemCount());
                    return false;
                }
            });
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            boolean updated = false;
            if (mTypeId == PRIVATE.id) {
                ListView list = getListView();
                switch (list.getCheckedItemCount()) {
                    case 1:
                        if (getReview() != null) { // can't edit when already add/editing
                            break;
                        }
                        int pos = SparseArrays.firstTrueKey(list.getCheckedItemPositions());
                        if (pos < 0 || pos >= list.getCount()) { // guard against rare NPE/IOOBE
                            break;
                        }
                        EasyCursor c = (EasyCursor) list.getItemAtPosition(pos);
                        if (c != null && c.isNull(Reviews.CONTACT_ID)) {
                            menu.findItem(R.id.edit).setEnabled(true).setVisible(true);
                            updated = true;
                        }
                        break;
                    case 2:
                        menu.findItem(R.id.edit).setEnabled(false).setVisible(false);
                        updated = true;
                        break;
                }
            }
            return updated;
        }

        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int pos, long id, boolean checked) {
            updateActionMode();
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.edit:
                    ListView list = getListView();
                    EasyCursor c = (EasyCursor) list.getItemAtPosition(
                            SparseArrays.firstTrueKey(list.getCheckedItemPositions()));
                    mReviewId = c.getLong(_ID);
                    mRatingPos = c.getInt(Reviews.RATING) - 1;
                    mComments = c.getString(Reviews.COMMENTS);
                    editReview(true);
                    mode.finish();
                    return true;
                case R.id.delete:
                    long[] ids = getListView().getCheckedItemIds();
                    new Undoer(a, getString(R.string.n_deleted, ids.length),
                            Reviews.CONTENT_URI, ids, DELETED, ACTIVE, mTypeId == PRIVATE.id) {
                        @Override
                        protected void onUpdate(Uri contentUri, long[] ids, Status status) {
                            super.onUpdate(contentUri, ids, status);
                            String id = String.valueOf(mRestaurantId);
                            cr().call(AUTHORITY_URI, CALL_UPDATE_RESTAURANT_RATING, id, null);
                            if (mTypeId == PRIVATE.id) {
                                cr().call(AUTHORITY_URI, CALL_UPDATE_RESTAURANT_LAST_VISIT, id,
                                        null);
                            }
                        }
                    };
                    mode.finish(); // ensure mActionMode is null before updateActionMode is called
                    event("reviews", "delete", ids.length);
                    return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mActionMode = null;
        }
    }

    public static class EditReviewHolder extends ViewHolder {
        @Bind(R.id.title)
        TextView mTitle;

        @Bind(R.id.ratings)
        Spinner mRatings;

        @Bind(R.id.comments)
        EditText mComments;

        @Override
        protected EditReviewHolder newInstance() {
            return new EditReviewHolder();
        }
    }
}
