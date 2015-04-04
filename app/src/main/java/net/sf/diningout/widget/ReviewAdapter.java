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

package net.sf.diningout.widget;

import android.content.Context;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.Html;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Callback.EmptyCallback;
import com.squareup.picasso.Picasso;

import net.sf.diningout.R;
import net.sf.diningout.picasso.Placeholders;
import net.sf.diningout.provider.Contract.Contacts;
import net.sf.diningout.provider.Contract.Reviews;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.view.ViewHolder;
import net.sf.sprockets.view.Views;
import net.sf.sprockets.widget.ResourceEasyCursorAdapter;

import butterknife.InjectView;

import static android.text.format.DateUtils.FORMAT_ABBREV_ALL;
import static android.text.format.DateUtils.MINUTE_IN_MILLIS;
import static net.sf.diningout.picasso.Transformations.CIRCLE;

/**
 * Translates review rows to Views.
 */
public class ReviewAdapter extends ResourceEasyCursorAdapter {
    public ReviewAdapter(Context context) {
        super(context, R.layout.reviews_adapter, null, 0);
    }

    @Override
    public void bindView(View view, Context context, EasyCursor c) {
        ReviewHolder review = ViewHolder.get(view, ReviewHolder.class);
        photo(context, review.mPhoto, c);
        review.mName.setText(name(context, c));
        review.mTime.setText(time(context, c));
        review.mRating.setText(c.getString(Reviews.RATING));
        review.mComments.setText(comments(c));
    }

    /**
     * Load the contact's photo into the View.
     */
    private void photo(Context context, final ImageView view, EasyCursor c) {
        if (c.getColumnIndex(Reviews.CONTACT_ID) >= 0 && !c.isNull(Reviews.CONTACT_ID)) {
            String key = c.getString(Contacts.ANDROID_LOOKUP_KEY);
            long id = c.getLong(Contacts.ANDROID_ID);
            Uri uri = key != null && id > 0 ? ContactsContract.Contacts.getLookupUri(id, key)
                    : null;
            final String name = c.getString(Contacts.NAME);
            Picasso.with(context).load(uri).resizeDimen(R.dimen.review_photo, R.dimen.review_photo)
                    .centerCrop().transform(CIRCLE).placeholder(Placeholders.round(c))
                    .into(view, new EmptyCallback() {
                        @Override
                        public void onError() {
                            Placeholders.round(view, name);
                        }
                    });
            Views.visible(view);
        } else {
            Views.gone(view);
        }
    }

    /**
     * Get the name of the reviewer.
     */
    public static String name(Context context, EasyCursor c) {
        String name;
        if (c.getColumnIndex(Reviews.CONTACT_ID) >= 0) { // private review
            if (!c.isNull(Reviews.CONTACT_ID)) {
                name = !c.isNull(Contacts.NAME) ? c.getString(Contacts.NAME)
                        : context.getString(R.string.non_contact);
            } else {
                name = context.getString(R.string.me);
            }
        } else { // public review
            name = c.getString(Reviews.AUTHOR_NAME);
        }
        return name;
    }

    /**
     * Get when the review was written.
     */
    public static CharSequence time(Context context, EasyCursor c) {
        long now = System.currentTimeMillis();
        long when = c.getLong(Reviews.WRITTEN_ON);
        return now - when > MINUTE_IN_MILLIS
                ? DateUtils.getRelativeTimeSpanString(when, now, 0, FORMAT_ABBREV_ALL)
                : context.getString(R.string.recent_time);
    }

    /**
     * Get the formatted review.
     */
    public static CharSequence comments(EasyCursor c) {
        return comments(c.getString(Reviews.COMMENTS));
    }

    /**
     * Get the formatted review.
     */
    public static CharSequence comments(String comments) {
        return Html.fromHtml(comments.replace("\n", "<br />"));
    }

    public static class ReviewHolder extends ViewHolder {
        @InjectView(R.id.photo)
        ImageView mPhoto;
        @InjectView(R.id.name)
        TextView mName;
        @InjectView(R.id.time)
        TextView mTime;
        @InjectView(R.id.rating)
        TextView mRating;
        @InjectView(R.id.comments)
        TextView mComments;

        @Override
        protected ReviewHolder newInstance() {
            return new ReviewHolder();
        }
    }
}
