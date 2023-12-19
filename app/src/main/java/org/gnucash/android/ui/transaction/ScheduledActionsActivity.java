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
package org.gnucash.android.ui.transaction;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.gnucash.android.R;
import org.gnucash.android.databinding.ActivityScheduledEventsBinding;
import org.gnucash.android.model.ScheduledAction;
import org.gnucash.android.ui.common.BaseDrawerActivity;

/**
 * Activity for displaying scheduled actions
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class ScheduledActionsActivity extends BaseDrawerActivity {

    public static final String LOG_TAG = "ScheduledActionsActivity";

    public static final int INDEX_SCHEDULED_TRANSACTIONS = 0;
    public static final int INDEX_SCHEDULED_EXPORTS = 1;
    public static final int MAX_SCHEDULED_ACTIONS = 2;

    ViewPager2 mViewPager;
    TabLayout mTabLayout;

    @Override
    public ViewBinding bindViews() {
        ActivityScheduledEventsBinding viewBinding = ActivityScheduledEventsBinding.inflate(getLayoutInflater());
        mDrawerLayout = viewBinding.drawerLayout;
        mNavigationView = viewBinding.navView;
        mToolbar = viewBinding.toolbarLayout.toolbar;
        mToolbarProgress = viewBinding.toolbarLayout.actionbarProgressIndicator.toolbarProgress;

        mViewPager = viewBinding.pager;
        mTabLayout = viewBinding.tabLayout;

        return viewBinding;
    }
    @Override
    public int getTitleRes() {
        return R.string.nav_menu_scheduled_actions;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //show the simple accounts list
        FragmentStateAdapter mPagerAdapter = new ScheduledActionsViewPager(this);
        mViewPager.setAdapter(mPagerAdapter);

        mTabLayout.addTab(mTabLayout.newTab().setText(R.string.title_scheduled_transactions));
        mTabLayout.addTab(mTabLayout.newTab().setText(R.string.title_scheduled_exports));
        mTabLayout.setTabGravity(TabLayout.GRAVITY_FILL);
        new TabLayoutMediator(mTabLayout, mViewPager,
                (@NonNull TabLayout.Tab tab, int position) -> {
                    Log.d(LOG_TAG, String.format("TabLayoutMediator, position %d, tab.getText()  %s.", position, tab.getText()));
                        switch (position) {
                            case INDEX_SCHEDULED_TRANSACTIONS ->
                                    tab.setText(R.string.title_scheduled_transactions);
                            case INDEX_SCHEDULED_EXPORTS ->
                                    tab.setText(R.string.title_scheduled_exports);
                        }
                }
        ).attach();

        mTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                mViewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                //nothing to see here, move along
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                //nothing to see here, move along
            }
        });


    }

    /**
     * View pager adapter for managing the scheduled action views
     */
    private static class ScheduledActionsViewPager extends FragmentStateAdapter {

        public ScheduledActionsViewPager(FragmentActivity fa) {
            super(fa);
        }

        @Override
        @NonNull
        public Fragment createFragment(int position) {
            switch (position) {
                case INDEX_SCHEDULED_TRANSACTIONS -> {
                    return ScheduledActionsListFragment.getInstance(ScheduledAction.ActionType.TRANSACTION);
                }
                case INDEX_SCHEDULED_EXPORTS -> {
                    return ScheduledActionsListFragment.getInstance(ScheduledAction.ActionType.BACKUP);
                }
            }
            Log.e(LOG_TAG, String.format("createFragment for position %d.", position));
            return ScheduledActionsListFragment.getInstance(ScheduledAction.ActionType.BACKUP);
        }

        @Override
        public int getItemCount() {
            return MAX_SCHEDULED_ACTIONS;
        }
    }
}
