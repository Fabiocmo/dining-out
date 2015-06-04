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

package net.sf.diningout.picasso;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.widget.ImageView;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.TextDrawable.IShapeBuilder;

import net.sf.diningout.R;
import net.sf.diningout.provider.Contract.Columns;
import net.sf.sprockets.database.EasyCursor;
import net.sf.sprockets.graphics.drawable.Drawables;

import static net.sf.sprockets.app.SprocketsApplication.res;

/**
 * Provides Drawables for placeholders.
 */
public class Placeholders {
    private static final IShapeBuilder sRect = TextDrawable.builder()
            .beginConfig().textColor(res().getColor(R.color.placeholder_text)).endConfig();
    private static final IShapeBuilder sRound = TextDrawable.builder();

    private Placeholders() {
    }

    /**
     * Get a default placeholder.
     */
    public static Drawable rect() {
        return Drawables.darkColor();
    }

    /**
     * Get a placeholder with the cursor's {@link Columns#COLOR color}.
     *
     * @return default placeholder if the cursor is null or does not have a color value
     */
    public static Drawable rect(EasyCursor c) {
        return c != null && !c.isNull(Columns.COLOR) ? new ColorDrawable(c.getInt(Columns.COLOR))
                : rect();
    }

    /**
     * Get a placeholder with the cursor's {@link Columns#COLOR color} and the first letter of the
     * text.
     */
    public static Drawable rect(EasyCursor c, String text) {
        return text.length() > 0 ? sRect.buildRect(text.substring(0, 1), c.getInt(Columns.COLOR))
                : rect(c);
    }

    /**
     * Get a default placeholder.
     */
    public static Drawable round() {
        return Drawables.darkOval();
    }

    /**
     * Get a placeholder with the cursor's {@link Columns#COLOR color}.
     *
     * @return default placeholder if the cursor is null or does not have a color value
     */
    public static Drawable round(EasyCursor c) {
        return c != null && !c.isNull(Columns.COLOR) ? Drawables.oval(c.getInt(Columns.COLOR))
                : round();
    }

    /**
     * Write the first letter of the text in the centre of the view's ShapeDrawable.
     */
    public static void round(ImageView view, String text) {
        if (text.length() > 0) {
            view.setImageDrawable(sRound.buildRound(text.substring(0, 1),
                    ((ShapeDrawable) view.getDrawable()).getPaint().getColor()));
        }
    }
}
