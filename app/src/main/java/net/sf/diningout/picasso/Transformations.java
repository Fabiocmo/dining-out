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

package net.sf.diningout.picasso;

import net.sf.diningout.R;
import net.sf.sprockets.picasso.GradientTransformation;

import jp.wasabeef.picasso.transformations.CropCircleTransformation;

import static android.graphics.drawable.GradientDrawable.Orientation.BR_TL;
import static android.graphics.drawable.GradientDrawable.Orientation.RIGHT_LEFT;
import static android.graphics.drawable.GradientDrawable.Orientation.TL_BR;
import static android.graphics.drawable.GradientDrawable.Orientation.TR_BL;
import static net.sf.sprockets.app.SprocketsApplication.context;

/**
 * Instances of Transformations.
 */
public class Transformations {
    public static final GradientTransformation BL = new GradientTransformation(context(), TR_BL);
    public static final GradientTransformation TL = new GradientTransformation(context(), BR_TL);
    public static final GradientTransformation BR = new GradientTransformation(context(), TL_BR);
    public static final GradientTransformation LEFT =
            new GradientTransformation(context(), RIGHT_LEFT, R.color.overlay_dark);
    public static final CropCircleTransformation CIRCLE = new CropCircleTransformation();
}
