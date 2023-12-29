/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gnucash.android.ui.colorpicker;


import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.core.content.res.ResourcesCompat;

import org.gnucash.android.R;
import org.gnucash.android.databinding.ColorPickerSwatchBinding;

/**
 * Creates a circular swatch of a specified color.  Adds a checkmark if marked as checked.
 */
public class ColorPickerSwatch extends FrameLayout implements View.OnClickListener {

    private final int mColor;
    private final ImageView mSwatchImage;
    private final ImageView mCheckmarkImage;
    private final OnColorSelectedListener mOnColorSelectedListener;

    /**
     * Interface for a callback when a color square is selected.
     */
    public interface OnColorSelectedListener {

        /**
         * Called when a specific color square has been selected.
         */
        void onColorSelected(int color);
    }

    public ColorPickerSwatch(Context context) {
        this(context, 0, true, null);
    }

    public ColorPickerSwatch(Context context, int color, boolean checked,
                             OnColorSelectedListener listener) {
        super(context);
        mColor = color;
        mOnColorSelectedListener = listener;

        ColorPickerSwatchBinding binding = ColorPickerSwatchBinding.inflate(LayoutInflater.from(context), this);
        mSwatchImage = binding.colorPickerSwatch;
        mCheckmarkImage = binding.colorPickerCheckmark;

        setColor(color);
        setChecked(checked);
        setOnClickListener(this);
    }

    protected void setColor(int color) {
        Drawable[] colorDrawable = new Drawable[]
                {ResourcesCompat.getDrawable(getContext().getResources(), R.drawable.color_picker_swatch, getContext().getTheme())};
        mSwatchImage.setImageDrawable(new ColorStateDrawable(colorDrawable, color));
    }

    private void setChecked(boolean checked) {
        if (checked) {
            mCheckmarkImage.setVisibility(View.VISIBLE);
        } else {
            mCheckmarkImage.setVisibility(View.GONE);
        }
    }

    @Override
    public void onClick(View v) {
        if (mOnColorSelectedListener != null) {
            mOnColorSelectedListener.onColorSelected(mColor);
        }
    }
}