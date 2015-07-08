/*
 * Copyright 2015 pushbit <pushbit@gmail.com>
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
import android.text.Html;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import net.sf.diningout.R;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;

import static net.sf.sprockets.app.SprocketsApplication.res;

/**
 * Displays the 'Powered by Google' logo and any content attributions.
 */
public class PoweredByGoogle extends RelativeLayout {
    @Bind(R.id.attributions)
    TextView mAttribs;

    public PoweredByGoogle(Context context) {
        this(context, null);
    }

    public PoweredByGoogle(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PoweredByGoogle(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        int tb = res().getDimensionPixelSize(R.dimen.padding);
        int lr = res().getDimensionPixelSize(R.dimen.padding_large);
        setPadding(lr, tb, lr, tb);
        View.inflate(context, R.layout.powered_by_google, this);
        ButterKnife.bind(this);
    }

    // todo Added in API level 21
    // public PoweredByGoogle(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    //     super(context, attrs, defStyleAttr, defStyleRes);
    // }

    /**
     * @param attribs may be null to clear
     */
    public PoweredByGoogle setHtmlAttributions(List<String> attribs) {
        mAttribs.setText(attribs != null ? Html.fromHtml(attribs.get(0)).toString() : null);
        return this;
    }
}
