/*
 * Copyright (c) 2012 - 2015 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 Yongxin Wang <fefe.wyx@gmail.com>
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

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.fragment.app.FragmentStatePagerAdapter;

import com.google.android.material.tabs.TabLayout;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.databinding.ActivityTransactionsBinding;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.ui.account.AccountsListFragment;
import org.gnucash.android.ui.account.DeleteAccountDialogFragment;
import org.gnucash.android.ui.account.OnAccountClickedListener;
import org.gnucash.android.ui.common.BaseDrawerActivity;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.util.BackupManager;
import org.gnucash.android.util.QualifiedAccountNameCursorAdapter;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import timber.log.Timber;

/**
 * Activity for displaying, creating and editing transactions
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class TransactionsActivity extends BaseDrawerActivity implements
    Refreshable, OnAccountClickedListener, OnTransactionClickedListener, FragmentResultListener {

    /**
     * ViewPager index for sub-accounts fragment
     */
    private static final int INDEX_SUB_ACCOUNTS_FRAGMENT = 0;

    /**
     * ViewPager index for transactions fragment
     */
    private static final int INDEX_TRANSACTIONS_FRAGMENT = 1;

    /**
     * Number of pages to show
     */
    private static final int DEFAULT_NUM_PAGES = 2;
    private static DateTimeFormatter dayMonthFormatter = DateTimeFormat.forPattern("EEE, d MMM");

    /**
     * GUID of {@link Account} whose transactions are displayed
     */
    private String mAccountUID = null;

    /**
     * Account database adapter for manipulating the accounts list in navigation
     */
    private AccountsDbAdapter mAccountsDbAdapter;

    /**
     * Hold the accounts cursor that will be used in the Navigation
     */
    private Cursor mAccountsCursor = null;

    private SparseArray<Refreshable> mFragmentPageReferenceMap = new SparseArray<>();

    /**
     * Flag for determining is the currently displayed account is a placeholder account or not.
     * This will determine if the transactions tab is displayed or not
     */
    private boolean mIsPlaceholderAccount;

    private ActivityTransactionsBinding mBinding;

    private AdapterView.OnItemSelectedListener mTransactionListNavigationListener = new AdapterView.OnItemSelectedListener() {

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            mAccountUID = mAccountsDbAdapter.getUID(id);
            getIntent().putExtra(UxArgument.SELECTED_ACCOUNT_UID, mAccountUID); //update the intent in case the account gets rotated
            mIsPlaceholderAccount = mAccountsDbAdapter.isPlaceholderAccount(mAccountUID);
            if (mIsPlaceholderAccount) {
                if (mBinding.tabLayout.getTabCount() > 1) {
                    mPagerAdapter.notifyDataSetChanged();
                    mBinding.tabLayout.removeTabAt(1);
                }
            } else {
                if (mBinding.tabLayout.getTabCount() < 2) {
                    mPagerAdapter.notifyDataSetChanged();
                    mBinding.tabLayout.addTab(mBinding.tabLayout.newTab().setText(R.string.section_header_transactions));
                }
            }
            if (view != null) {
                // Hide the favorite icon of the selected account to avoid clutter
                ((TextView) view).setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            }
            //refresh any fragments in the tab with the new account UID
            refresh();
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            //nothing to see here, move along
        }
    };

    private AccountViewPagerAdapter mPagerAdapter;

    @Override
    public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle result) {
        if (DeleteAccountDialogFragment.TAG.equals(requestKey)) {
            boolean refresh = result.getBoolean(Refreshable.EXTRA_REFRESH);
            if (refresh) {
                finish();
            }
        }
    }

    /**
     * Adapter for managing the sub-account and transaction fragment pages in the accounts view
     */
    private class AccountViewPagerAdapter extends FragmentStatePagerAdapter {

        public AccountViewPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            if (mIsPlaceholderAccount) {
                Fragment transactionsListFragment = prepareSubAccountsListFragment();
                mFragmentPageReferenceMap.put(position, (Refreshable) transactionsListFragment);
                return transactionsListFragment;
            }

            Fragment currentFragment;
            switch (position) {
                case INDEX_SUB_ACCOUNTS_FRAGMENT:
                    currentFragment = prepareSubAccountsListFragment();
                    break;

                case INDEX_TRANSACTIONS_FRAGMENT:
                default:
                    currentFragment = prepareTransactionsListFragment();
                    break;
            }

            mFragmentPageReferenceMap.put(position, (Refreshable) currentFragment);
            return currentFragment;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            super.destroyItem(container, position, object);
            mFragmentPageReferenceMap.remove(position);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if (mIsPlaceholderAccount)
                return getString(R.string.section_header_subaccounts);

            switch (position) {
                case INDEX_SUB_ACCOUNTS_FRAGMENT:
                    return getString(R.string.section_header_subaccounts);

                case INDEX_TRANSACTIONS_FRAGMENT:
                default:
                    return getString(R.string.section_header_transactions);
            }
        }

        @Override
        public int getCount() {
            if (mIsPlaceholderAccount)
                return 1;
            else
                return DEFAULT_NUM_PAGES;
        }

        /**
         * Creates and initializes the fragment for displaying sub-account list
         *
         * @return {@link AccountsListFragment} initialized with the sub-accounts
         */
        private AccountsListFragment prepareSubAccountsListFragment() {
            AccountsListFragment subAccountsListFragment = new AccountsListFragment();
            Bundle args = new Bundle();
            args.putString(UxArgument.PARENT_ACCOUNT_UID, mAccountUID);
            subAccountsListFragment.setArguments(args);
            return subAccountsListFragment;
        }

        /**
         * Creates and initializes fragment for displaying transactions
         *
         * @return {@link TransactionsListFragment} initialized with the current account transactions
         */
        private TransactionsListFragment prepareTransactionsListFragment() {
            TransactionsListFragment transactionsListFragment = new TransactionsListFragment();
            Bundle args = new Bundle();
            args.putString(UxArgument.SELECTED_ACCOUNT_UID, mAccountUID);
            transactionsListFragment.setArguments(args);
            Timber.i("Opening transactions for account: %s", mAccountUID);
            return transactionsListFragment;
        }
    }

    /**
     * Refreshes the fragments currently in the transactions activity
     */
    @Override
    public void refresh(String accountUID) {
        for (int i = 0; i < mFragmentPageReferenceMap.size(); i++) {
            mFragmentPageReferenceMap.valueAt(i).refresh(accountUID);
        }

        if (mPagerAdapter != null)
            mPagerAdapter.notifyDataSetChanged();
    }

    @Override
    public void refresh() {
        refresh(mAccountUID);
        setTitleIndicatorColor();
    }

    @Override
    public void inflateView() {
        mBinding = ActivityTransactionsBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        mDrawerLayout = mBinding.drawerLayout;
        mNavigationView = mBinding.navView;
        mToolbar = mBinding.toolbarLayout.toolbar;
        mToolbarProgress = mBinding.toolbarLayout.toolbarProgress.progress;
    }

    @Override
    public int getTitleRes() {
        return R.string.title_transactions;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportActionBar().setDisplayShowTitleEnabled(false);

        mAccountUID = getIntent().getStringExtra(UxArgument.SELECTED_ACCOUNT_UID);
        mAccountsDbAdapter = AccountsDbAdapter.getInstance();

        mIsPlaceholderAccount = mAccountsDbAdapter.isPlaceholderAccount(mAccountUID);

        mBinding.tabLayout.addTab(mBinding.tabLayout.newTab().setText(R.string.section_header_subaccounts));
        if (!mIsPlaceholderAccount) {
            mBinding.tabLayout.addTab(mBinding.tabLayout.newTab().setText(R.string.section_header_transactions));
        }

        setupActionBarNavigation();

        mPagerAdapter = new AccountViewPagerAdapter(getSupportFragmentManager());
        mBinding.pager.setAdapter(mPagerAdapter);
        mBinding.pager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(mBinding.tabLayout));

        mBinding.tabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                mBinding.pager.setCurrentItem(tab.getPosition());
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

        //if there are no transactions, and there are sub-accounts, show the sub-accounts
        if (TransactionsDbAdapter.getInstance().getTransactionsCount(mAccountUID) == 0
                && mAccountsDbAdapter.getSubAccountCount(mAccountUID) > 0) {
            mBinding.pager.setCurrentItem(INDEX_SUB_ACCOUNTS_FRAGMENT);
        } else {
            mBinding.pager.setCurrentItem(INDEX_TRANSACTIONS_FRAGMENT);
        }

        mBinding.fabCreateTransaction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (mBinding.pager.getCurrentItem()) {
                    case INDEX_SUB_ACCOUNTS_FRAGMENT:
                        Intent addAccountIntent = new Intent(TransactionsActivity.this, FormActivity.class);
                        addAccountIntent.setAction(Intent.ACTION_INSERT_OR_EDIT);
                        addAccountIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.ACCOUNT.name());
                        addAccountIntent.putExtra(UxArgument.PARENT_ACCOUNT_UID, mAccountUID);
                        startActivity(addAccountIntent);
                        break;

                    case INDEX_TRANSACTIONS_FRAGMENT:
                        createNewTransaction(mAccountUID);
                        break;

                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        setTitleIndicatorColor();
    }

    /**
     * Sets the color for the ViewPager title indicator to match the account color
     */
    private void setTitleIndicatorColor() {
        int iColor = AccountsDbAdapter.getActiveAccountColorResource(mAccountUID);

        mBinding.tabLayout.setBackgroundColor(iColor);

        if (getSupportActionBar() != null)
            getSupportActionBar().setBackgroundDrawable(new ColorDrawable(iColor));

        getWindow().setStatusBarColor(GnuCashApplication.darken(iColor));
    }

    /**
     * Set up action bar navigation list and listener callbacks
     */
    private void setupActionBarNavigation() {
        // set up spinner adapter for navigation list
        if (mAccountsCursor != null) {
            mAccountsCursor.close();
        }
        mAccountsCursor = mAccountsDbAdapter.fetchAllRecordsOrderedByFullName();

        SpinnerAdapter mSpinnerAdapter = new QualifiedAccountNameCursorAdapter(
                getSupportActionBar().getThemedContext(), mAccountsCursor, R.layout.account_spinner_item);

        mBinding.toolbarLayout.toolbarSpinner.setAdapter(mSpinnerAdapter);
        mBinding.toolbarLayout.toolbarSpinner.setOnItemSelectedListener(mTransactionListNavigationListener);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        updateNavigationSelection();
    }

    /**
     * Updates the action bar navigation list selection to that of the current account
     * whose transactions are being displayed/manipulated
     */
    public void updateNavigationSelection() {
        // set the selected item in the spinner
        int i = 0;
        Cursor accountsCursor = mAccountsDbAdapter.fetchAllRecordsOrderedByFullName();
        while (accountsCursor.moveToNext()) {
            String uid = accountsCursor.getString(accountsCursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_UID));
            if (mAccountUID.equals(uid)) {
                mBinding.toolbarLayout.toolbarSpinner.setSelection(i);
                break;
            }
            ++i;
        }
        accountsCursor.close();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem favoriteAccountMenuItem = menu.findItem(R.id.menu_favorite);

        if (favoriteAccountMenuItem == null) //when the activity is used to edit a transaction
            return super.onPrepareOptionsMenu(menu);

        boolean isFavoriteAccount = AccountsDbAdapter.getInstance().isFavoriteAccount(mAccountUID);

        @DrawableRes int favoriteIcon = isFavoriteAccount ? R.drawable.ic_favorite : R.drawable.ic_favorite_border;
        favoriteAccountMenuItem.setIcon(favoriteIcon);
        return super.onPrepareOptionsMenu(menu);

    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                return super.onOptionsItemSelected(item);

            case R.id.menu_favorite:
                AccountsDbAdapter accountsDbAdapter = AccountsDbAdapter.getInstance();
                long accountId = accountsDbAdapter.getID(mAccountUID);
                boolean isFavorite = accountsDbAdapter.isFavoriteAccount(mAccountUID);
                //toggle favorite preference
                accountsDbAdapter.updateAccount(accountId, DatabaseSchema.AccountEntry.COLUMN_FAVORITE, isFavorite ? "0" : "1");
                supportInvalidateOptionsMenu();
                return true;

            case R.id.menu_edit:
                Intent editAccountIntent = new Intent(this, FormActivity.class);
                editAccountIntent.setAction(Intent.ACTION_INSERT_OR_EDIT);
                editAccountIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, mAccountUID);
                editAccountIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.ACCOUNT.name());
                startActivity(editAccountIntent);
                return true;

            case R.id.menu_delete:
                tryDeleteAccount(mAccountUID);
                return true;

            default:
                return false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_CANCELED)
            return;

        refresh();
        setupActionBarNavigation();
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        close();
    }

    private void close() {
        if (mAccountsCursor != null) {
            mAccountsCursor.close();
            mAccountsCursor = null;
        }
    }

    /**
     * Returns the global unique ID of the current account
     *
     * @return GUID of the current account
     */
    public String getCurrentAccountUID() {
        return mAccountUID;
    }

    /**
     * Formats the date to show the the day of the week if the {@code dateMillis} is within 7 days
     * of today. Else it shows the actual date formatted as short string. <br>
     * It also shows "today", "yesterday" or "tomorrow" if the date is on any of those days
     *
     * @param dateMillis
     * @return
     */
    @NonNull
    public static String getPrettyDateFormat(Context context, long dateMillis) {
        LocalDate transactionTime = new LocalDate(dateMillis);
        LocalDate today = new LocalDate();
        final String prettyDateText;
        if (transactionTime.compareTo(today.minusDays(1)) >= 0 && transactionTime.compareTo(today.plusDays(1)) <= 0) {
            prettyDateText = DateUtils.getRelativeTimeSpanString(dateMillis, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString();
        } else if (transactionTime.getYear() == today.getYear()) {
            prettyDateText = dayMonthFormatter.print(dateMillis);
        } else {
            prettyDateText = DateUtils.formatDateTime(context, dateMillis, DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_SHOW_YEAR);
        }

        return prettyDateText;
    }

    @Override
    public void createNewTransaction(String accountUID) {
        Intent createTransactionIntent = new Intent(this, FormActivity.class);
        createTransactionIntent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        createTransactionIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID);
        createTransactionIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name());
        startActivity(createTransactionIntent);
    }

    @Override
    public void editTransaction(String transactionUID) {
        Intent createTransactionIntent = new Intent(this, FormActivity.class);
        createTransactionIntent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        createTransactionIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, mAccountUID);
        createTransactionIntent.putExtra(UxArgument.SELECTED_TRANSACTION_UID, transactionUID);
        createTransactionIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name());
        startActivity(createTransactionIntent);
    }

    @Override
    public void accountSelected(String accountUID) {
        Intent restartIntent = new Intent(this, TransactionsActivity.class);
        restartIntent.setAction(Intent.ACTION_VIEW);
        restartIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID);
        startActivity(restartIntent);
    }

    /**
     * Delete the account with UID.
     * It shows the delete confirmation dialog if the account has transactions,
     * else deletes the account immediately
     *
     * @param accountUID The UID of the account
     */
    private void tryDeleteAccount(final String accountUID) {
        if (mAccountsDbAdapter.getTransactionCount(accountUID) > 0 || mAccountsDbAdapter.getSubAccountCount(accountUID) > 0) {
            showConfirmationDialog(accountUID);
        } else {
            BackupManager.backupActiveBookAsync(this, result -> {
                // Avoid calling AccountsDbAdapter.deleteRecord(long). See #654
                if (mAccountsDbAdapter.deleteRecord(accountUID)) {
                    finish();
                }
                return null;
            });
        }
    }

    /**
     * Shows the delete confirmation dialog
     *
     * @param accountUID Unique ID of account to be deleted after confirmation
     */
    private void showConfirmationDialog(String accountUID) {
        FragmentManager fm = getSupportFragmentManager();
        DeleteAccountDialogFragment alertFragment =
            DeleteAccountDialogFragment.newInstance(accountUID);
        fm.setFragmentResultListener(DeleteAccountDialogFragment.TAG, this, this);
        alertFragment.show(fm, DeleteAccountDialogFragment.TAG);
    }
}
