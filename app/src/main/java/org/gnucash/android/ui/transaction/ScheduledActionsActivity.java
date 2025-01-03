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

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

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

    private static final int INDEX_SCHEDULED_TRANSACTIONS = 0;
    private static final int INDEX_SCHEDULED_EXPORTS = 1;

    private ActivityScheduledEventsBinding binding;

    @Override
    public void inflateView() {
        binding = ActivityScheduledEventsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        mDrawerLayout = binding.drawerLayout;
        mNavigationView = binding.navView;
        mToolbar = binding.toolbarLayout.toolbar;
        mToolbarProgress = binding.toolbarLayout.toolbarProgress.progress;
    }

    @Override
    public int getTitleRes() {
        return R.string.nav_menu_scheduled_actions;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TabLayout tabLayout = binding.tabLayout;
        tabLayout.addTab(tabLayout.newTab().setText(R.string.title_scheduled_transactions));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.title_scheduled_exports));
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);

        ViewPager viewPager = binding.pager;

        //show the simple accounts list
        PagerAdapter mPagerAdapter = new ScheduledActionsViewPager(getSupportFragmentManager());
        viewPager.setAdapter(mPagerAdapter);

        viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
        tabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
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
    private class ScheduledActionsViewPager extends FragmentStatePagerAdapter {

        public ScheduledActionsViewPager(FragmentManager fm) {
            super(fm);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case INDEX_SCHEDULED_TRANSACTIONS:
                    return getString(R.string.title_scheduled_transactions);
                case INDEX_SCHEDULED_EXPORTS:
                    return getString(R.string.title_scheduled_exports);
                default:
                    return super.getPageTitle(position);
            }
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case INDEX_SCHEDULED_TRANSACTIONS:
                    return new ScheduledTransactionsListFragment();
                case INDEX_SCHEDULED_EXPORTS:
                    return new ScheduledExportsListFragment();
            }
            return null;
        }

        @Override
        public int getCount() {
            return 2;
        }
    }
}
