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
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.databinding.ActivityTransactionsBinding;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.Money;
import org.gnucash.android.ui.account.AccountsActivity;
import org.gnucash.android.ui.account.AccountsListFragment;
import org.gnucash.android.ui.account.OnAccountClickedListener;
import org.gnucash.android.ui.common.BaseDrawerActivity;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.util.AccountBalanceTask;
import org.gnucash.android.util.QualifiedAccountNameCursorAdapter;
import org.joda.time.LocalDate;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Activity for displaying, creating and editing transactions
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class TransactionsActivity extends BaseDrawerActivity implements
        Refreshable, OnAccountClickedListener, OnTransactionClickedListener {

    /**
     * Logging tag
     */
    protected static final String LOG_TAG = "TransactionsActivity";

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

    ViewPager2 mViewPager;
    Spinner mToolbarSpinner;
    TabLayout mTabLayout;
    TextView mSumTextView;
    FloatingActionButton mCreateFloatingButton;

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Log.d(LOG_TAG, "launch intent: result = " + result);
//                if (result.getResultCode() == Activity.RESULT_CANCELED) {
//                    return;
//                }
//                refresh();
            }
    );

    /**
     * Flag for determining is the currently displayed account is a placeholder account or not.
     * This will determine if the transactions tab is displayed or not
     */
    private boolean mIsPlaceholderAccount;

    private final AdapterView.OnItemSelectedListener mTransactionListNavigationListener = new AdapterView.OnItemSelectedListener() {

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            mAccountUID = mAccountsDbAdapter.getUID(id);
            getIntent().putExtra(UxArgument.SELECTED_ACCOUNT_UID, mAccountUID); //update the intent in case the account gets rotated
            mIsPlaceholderAccount = mAccountsDbAdapter.isPlaceholderAccount(mAccountUID);
            if (mIsPlaceholderAccount) {
                if (mTabLayout.getTabCount() > 1) {
                    mPagerAdapter.notifyDataSetChanged();
                    mTabLayout.removeTabAt(1);
                }
            } else {
                if (mTabLayout.getTabCount() < 2) {
                    mPagerAdapter.notifyDataSetChanged();
                    mTabLayout.addTab(mTabLayout.newTab().setText(R.string.section_header_transactions));
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

    private FragmentStateAdapter mPagerAdapter;


    /**
     * Adapter for managing the sub-account and transaction fragment pages in the accounts view
     */
    private class AccountViewPagerAdapter extends FragmentStateAdapter {

        public AccountViewPagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        @Override
        @NonNull
        public Fragment createFragment(int position) {
            if (mIsPlaceholderAccount) {
                return prepareSubAccountsListFragment();
            } else {
                if (position == INDEX_SUB_ACCOUNTS_FRAGMENT) {
                    return prepareSubAccountsListFragment();
                } else { // INDEX_TRANSACTIONS_FRAGMENT:
                    return prepareTransactionsListFragment();
                }
            }
        }

        @Override
        public int getItemCount() {
            if (mIsPlaceholderAccount) {
                return 1;
            }
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
            Log.i(LOG_TAG, "Opening transactions for account:  " + mAccountUID);
            return transactionsListFragment;
        }
    }

    /**
     * Refreshes the fragments currently in the transactions activity
     */
    @Override
    public void refresh(String accountUID) {

        if (mPagerAdapter != null) {
            mPagerAdapter.notifyDataSetChanged();
        }

        new AccountBalanceTask(mSumTextView).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mAccountUID);
    }

    @Override
    public void refresh() {
        refresh(mAccountUID);
        setTitleIndicatorColor();
    }

    @Override
    public ViewBinding bindViews() {
        ActivityTransactionsBinding viewBinding = ActivityTransactionsBinding.inflate(getLayoutInflater());
        mDrawerLayout = viewBinding.drawerLayout;
        mNavigationView = viewBinding.navView;
        mToolbar = viewBinding.toolbarLayout.toolbar;
        mToolbarProgress = viewBinding.toolbarLayout.actionbarProgressIndicator.toolbarProgress;

        mViewPager = viewBinding.pager;
        mToolbarSpinner = viewBinding.toolbarLayout.toolbarSpinner;
        mTabLayout = viewBinding.tabLayout;
        mSumTextView = viewBinding.accountBalanceToolbar.transactionsSum;
        mCreateFloatingButton = viewBinding.fabCreateTransaction;

        return viewBinding;
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

        mTabLayout.addTab(mTabLayout.newTab().setText(R.string.section_header_subaccounts));
        if (!mIsPlaceholderAccount) {
            mTabLayout.addTab(mTabLayout.newTab().setText(R.string.section_header_transactions));
        }

        setupActionBarNavigation();

        mPagerAdapter = new AccountViewPagerAdapter(this);
        mViewPager.setAdapter(mPagerAdapter);
//        mViewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(mTabLayout));

        new TabLayoutMediator(mTabLayout, mViewPager,
                (@NonNull TabLayout.Tab tab, int position) -> {
                    if (mIsPlaceholderAccount) {
                        tab.setText(R.string.section_header_subaccounts);
                    } else {
                        switch (position) {
                            case INDEX_SUB_ACCOUNTS_FRAGMENT:
                                tab.setText(R.string.section_header_subaccounts);

                            case INDEX_TRANSACTIONS_FRAGMENT:
                            default:
                                tab.setText(R.string.section_header_transactions);
                        }
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

        //if there are no transactions, and there are sub-accounts, show the sub-accounts
        if (TransactionsDbAdapter.getInstance().getTransactionsCount(mAccountUID) == 0
                && mAccountsDbAdapter.getSubAccountCount(mAccountUID) > 0) {
            mViewPager.setCurrentItem(INDEX_SUB_ACCOUNTS_FRAGMENT);
        } else {
            mViewPager.setCurrentItem(INDEX_TRANSACTIONS_FRAGMENT);
        }

        mCreateFloatingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (mViewPager.getCurrentItem()) {
                    case INDEX_SUB_ACCOUNTS_FRAGMENT:
                        Intent addAccountIntent = new Intent(TransactionsActivity.this, FormActivity.class);
                        addAccountIntent.setAction(Intent.ACTION_INSERT_OR_EDIT);
                        addAccountIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.ACCOUNT.name());
                        addAccountIntent.putExtra(UxArgument.PARENT_ACCOUNT_UID, mAccountUID);
                        launcher.launch(addAccountIntent);
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

        mTabLayout.setBackgroundColor(iColor);

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

        mToolbarSpinner.setAdapter(mSpinnerAdapter);
        mToolbarSpinner.setOnItemSelectedListener(mTransactionListNavigationListener);
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
                mToolbarSpinner.setSelection(i);
                break;
            }
            ++i;
        }
        accountsCursor.close();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem favoriteAccountMenuItem = menu.findItem(R.id.menu_favorite_account);

        if (favoriteAccountMenuItem == null) //when the activity is used to edit a transaction
            return super.onPrepareOptionsMenu(menu);

        boolean isFavoriteAccount = AccountsDbAdapter.getInstance().isFavoriteAccount(mAccountUID);

        int favoriteIcon = isFavoriteAccount ? R.drawable.ic_star_white_24dp : R.drawable.ic_star_border_white_24dp;
        favoriteAccountMenuItem.setIcon(favoriteIcon);
        return super.onPrepareOptionsMenu(menu);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                return super.onOptionsItemSelected(item);

            case R.id.menu_favorite_account:
                AccountsDbAdapter accountsDbAdapter = AccountsDbAdapter.getInstance();
                long accountId = accountsDbAdapter.getID(mAccountUID);
                boolean isFavorite = accountsDbAdapter.isFavoriteAccount(mAccountUID);
                //toggle favorite preference
                accountsDbAdapter.updateAccount(accountId, DatabaseSchema.AccountEntry.COLUMN_FAVORITE, isFavorite ? "0" : "1");
                supportInvalidateOptionsMenu();
                return true;

            case R.id.menu_edit_account:
                Intent editAccountIntent = new Intent(this, FormActivity.class);
                editAccountIntent.setAction(Intent.ACTION_INSERT_OR_EDIT);
                editAccountIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, mAccountUID);
                editAccountIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.ACCOUNT.name());
                launcher.launch(editAccountIntent);
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
        mAccountsCursor.close();
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
     * Display the balance of a transaction in a text view and format the text color to match the sign of the amount
     *
     * @param balanceTextView {@link android.widget.TextView} where balance is to be displayed
     * @param balance         {@link org.gnucash.android.model.Money} balance to display
     */
    public static void displayBalance(TextView balanceTextView, Money balance) {
        balanceTextView.setText(balance.formattedString());
        Context context = GnuCashApplication.getAppContext();
        int fontColor = balance.isNegative() ?
                context.getResources().getColor(R.color.debit_red, context.getTheme()) :
                context.getResources().getColor(R.color.credit_green, context.getTheme());
        if (balance.asBigDecimal().compareTo(BigDecimal.ZERO) == 0) {
            fontColor = context.getResources().getColor(android.R.color.black, context.getTheme());
        }
        balanceTextView.setTextColor(fontColor);
    }

    /**
     * Formats the date to show the the day of the week if the {@code dateMillis} is within 7 days
     * of today. Else it shows the actual date formatted as short string. <br>
     * It also shows "today", "yesterday" or "tomorrow" if the date is on any of those days
     *
     * @param dateMillis date in milliseconds.
     * @return pretty date format.
     */
    @NonNull
    public static String getPrettyDateFormat(Context context, long dateMillis) {
        LocalDate transactionTime = new LocalDate(dateMillis);
        LocalDate today = new LocalDate();
        String prettyDateText;
        if (transactionTime.compareTo(today.minusDays(1)) >= 0 && transactionTime.compareTo(today.plusDays(1)) <= 0) {
            prettyDateText = DateUtils.getRelativeTimeSpanString(dateMillis, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString();
        } else if (transactionTime.getYear() == today.getYear()) {
            SimpleDateFormat dayMonthDateFormat = new SimpleDateFormat("EEE, d MMM", Locale.getDefault());
            prettyDateText = dayMonthDateFormat.format(new Date(dateMillis));
        } else {
            prettyDateText = DateUtils.formatDateTime(context, dateMillis, DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_SHOW_YEAR);
        }

        return prettyDateText;
    }

    @Override
    public void createNewTransaction(String accountUID) {
        Intent createTransactionIntent = new Intent(this.getApplicationContext(), FormActivity.class);
        createTransactionIntent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        createTransactionIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID);
        createTransactionIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name());
        launcher.launch(createTransactionIntent);
    }

    @Override
    public void editTransaction(String transactionUID) {
        Intent createTransactionIntent = new Intent(this.getApplicationContext(), FormActivity.class);
        createTransactionIntent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        createTransactionIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, mAccountUID);
        createTransactionIntent.putExtra(UxArgument.SELECTED_TRANSACTION_UID, transactionUID);
        createTransactionIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name());
        launcher.launch(createTransactionIntent);
    }

    @Override
    public void accountSelected(String accountUID) {
        Intent restartIntent = new Intent(this.getApplicationContext(), TransactionsActivity.class);
        restartIntent.setAction(Intent.ACTION_VIEW);
        restartIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID);
        launcher.launch(restartIntent);
    }
}
