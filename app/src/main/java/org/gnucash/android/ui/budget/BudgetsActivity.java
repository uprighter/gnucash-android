/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.ui.budget;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewbinding.ViewBinding;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.databinding.ActivityBudgetsBinding;
import org.gnucash.android.ui.common.BaseDrawerActivity;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.UxArgument;

/**
 * Activity for managing display and editing of budgets
 */
public class BudgetsActivity extends BaseDrawerActivity implements View.OnClickListener {

    public static final int REQUEST_CREATE_BUDGET = 0xA;

    @Override
    public ViewBinding bindViews() {
        ActivityBudgetsBinding viewBinding = ActivityBudgetsBinding.inflate(getLayoutInflater());
        mDrawerLayout = viewBinding.drawerLayout;
        mNavigationView = viewBinding.navView;
        mToolbar = viewBinding.toolbarLayout.toolbar;
        mToolbarProgress = viewBinding.toolbarLayout.actionbarProgressIndicator.toolbarProgress;

        return viewBinding;
    }

    @Override
    public int getTitleRes() {
        return R.string.title_budgets;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FloatingActionButton createBudgetButton = findViewById(R.id.fab_create_budget);
        createBudgetButton.setOnClickListener(this);

        if (savedInstanceState == null) {
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction fragmentTransaction = fragmentManager
                    .beginTransaction();

            fragmentTransaction.replace(R.id.fragment_container, new BudgetListFragment());
            fragmentTransaction.commit();
        }
    }

    /**
     * Callback when create budget floating action button is clicked
     *
     * @param view View which was clicked
     */
    @Override
    public void onClick(View view) {
        Intent addAccountIntent = new Intent(BudgetsActivity.this, FormActivity.class);
        addAccountIntent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        addAccountIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.BUDGET.name());
        startActivityForResult(addAccountIntent, REQUEST_CREATE_BUDGET);
    }

    /**
     * Returns a color between red and green depending on the value parameter
     *
     * @param value Value between 0 and 1 indicating the red to green ratio
     * @return Color between red and green
     */
    public static int getBudgetProgressColor(double value) {
        return GnuCashApplication.darken(android.graphics.Color.HSVToColor(new float[]{(float) value * 120f, 1f, 1f}));
    }
}
