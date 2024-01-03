/*
 * Copyright (c) 2012 - 2015 Ngewi Fet <ngewif@gmail.com>
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

package org.gnucash.android.ui.account;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.SearchManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.databinding.CardviewAccountBinding;
import org.gnucash.android.databinding.FragmentAccountsListBinding;
import org.gnucash.android.db.DatabaseCursorLoader;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BudgetsDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.Budget;
import org.gnucash.android.model.Money;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.util.AccountBalanceTask;
import org.gnucash.android.ui.util.CursorRecyclerAdapter;
import org.gnucash.android.ui.util.widget.EmptyRecyclerView;
import org.gnucash.android.util.BackupManager;

import java.util.List;
import java.util.Objects;

/**
 * Fragment for displaying the list of accounts in the database
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class AccountsListFragment extends Fragment implements
        Refreshable,
        LoaderManager.LoaderCallbacks<Cursor>,
        SearchView.OnQueryTextListener,
        SearchView.OnCloseListener {

    private FragmentAccountsListBinding mBinding;
    private EmptyRecyclerView mRecyclerView;

    private AccountRecyclerAdapter mAccountRecyclerAdapter;

    /**
     * Describes the kinds of accounts that should be loaded in the accounts list.
     * This enhances reuse of the accounts list fragment
     */
    public enum DisplayMode {
        TOP_LEVEL, RECENT, FAVORITES
    }

    /**
     * Field indicating which kind of accounts to load.
     * Default value is {@link DisplayMode#TOP_LEVEL}
     */
    private DisplayMode mDisplayMode = DisplayMode.TOP_LEVEL;

    /**
     * Logging tag
     */
    protected static final String LOG_TAG = AccountsListFragment.class.getName();

    /**
     * Tag to save {@link AccountsListFragment#mDisplayMode} to fragment state
     */
    private static final String STATE_DISPLAY_MODE = "mDisplayMode";

    /**
     * Database adapter for loading Account records from the database
     */
    private AccountsDbAdapter mAccountsDbAdapter;
    /**
     * Listener to be notified when an account is clicked
     */
    private OnAccountClickedListener mAccountSelectedListener;

    /**
     * GUID of the account whose children will be loaded in the list fragment.
     * If no parent account is specified, then all top-level accounts are loaded.
     */
    private String mParentAccountUID = null;

    /**
     * Filter for which accounts should be displayed. Used by search interface
     */
    private String mCurrentFilter;

    /**
     * Search view for searching accounts
     */
    private SearchView mSearchView;

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Log.d(LOG_TAG, "launch intent: result = " + result);
                if (result.getResultCode() == Activity.RESULT_CANCELED) {
                    return;
                }
                refresh();
            }
    );

    public static AccountsListFragment newInstance(DisplayMode displayMode) {
        AccountsListFragment fragment = new AccountsListFragment();
        fragment.mDisplayMode = displayMode;
        return fragment;
    }

    @Override
    public @Nullable View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        mBinding = FragmentAccountsListBinding.inflate(inflater, container, false);
        mRecyclerView = mBinding.accountRecyclerView;
        TextView emptyTextView = mBinding.emptyView;

        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setEmptyView(emptyTextView);

        switch (mDisplayMode) {
            case TOP_LEVEL -> emptyTextView.setText(R.string.label_no_accounts);
            case RECENT -> emptyTextView.setText(R.string.label_no_recent_accounts);
            case FAVORITES -> emptyTextView.setText(R.string.label_no_favorite_accounts);
        }

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            GridLayoutManager gridLayoutManager = new GridLayoutManager(getActivity(), 2);
            mRecyclerView.setLayoutManager(gridLayoutManager);
        } else {
            LinearLayoutManager mLayoutManager = new LinearLayoutManager(getActivity());
            mRecyclerView.setLayoutManager(mLayoutManager);
        }
        return mBinding.getRoot();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            mParentAccountUID = args.getString(UxArgument.PARENT_ACCOUNT_UID);
        }

        if (savedInstanceState != null) {
            mDisplayMode = savedInstanceState.getSerializable(STATE_DISPLAY_MODE, DisplayMode.class);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ActionBar actionbar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        Objects.requireNonNull(actionbar).setTitle(R.string.title_accounts);
        actionbar.setDisplayHomeAsUpEnabled(true);
        setHasOptionsMenu(true);

        // specify an adapter (see also next example)
        mAccountRecyclerAdapter = new AccountRecyclerAdapter(null);
        mRecyclerView.setAdapter(mAccountRecyclerAdapter);
    }

    @Override
    public void onStart() {
        super.onStart();
        mAccountsDbAdapter = AccountsDbAdapter.getInstance();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            mAccountSelectedListener = (OnAccountClickedListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement OnAccountSelectedListener");
        }
    }

    public void onListItemClick(String accountUID) {
        mAccountSelectedListener.accountSelected(accountUID);
    }

    /**
     * Delete the account with record ID <code>rowId</code>
     * It shows the delete confirmation dialog if the account has transactions,
     * else deletes the account immediately
     *
     * @param rowId The record ID of the account
     */
    public void tryDeleteAccount(long rowId) {
        Account acc = mAccountsDbAdapter.getRecord(rowId);
        if (acc.getTransactionCount() > 0 || mAccountsDbAdapter.getSubAccountCount(acc.getUID()) > 0) {
            showDeleteConfirmationDialog(rowId);
        } else {
            BackupManager.backupActiveBook();
            // Avoid calling AccountsDbAdapter.deleteRecord(long). See #654
            String uid = mAccountsDbAdapter.getUID(rowId);
            mAccountsDbAdapter.deleteRecord(uid);
            refresh();
        }
    }

    /**
     * Duplicate the account with record ID <code>rowId</code> and all its sub-accounts.
     * It shows the confirmation dialog if the account has sub-accounts,
     * else duplicates the account immediately.
     *
     * @param rowId The record ID of the account
     */
    public void duplicateAccounts(long rowId) {
        BackupManager.backupActiveBook();

        Account account = mAccountsDbAdapter.getRecord(rowId);
        duplicateAccounts(account, account.getName() + "_2", account.getParentUID());

        refresh();
    }

    public void duplicateAccounts(Account account, String newName, String parentAccountUID) {
        Account duplicate = new Account(account, newName, parentAccountUID);
        mAccountsDbAdapter.addRecord(duplicate, DatabaseAdapter.UpdateMethod.insert);
        if (mAccountsDbAdapter.getSubAccountCount(account.getUID()) > 0) {
            // Recursively duplicates its sub-accounts.
            List<String> subAccountUIDs = mAccountsDbAdapter.getSubAccounts(account.getUID());
            for (String subAccountUID: subAccountUIDs) {
                Account subAccount = mAccountsDbAdapter.getRecord(subAccountUID);
                duplicateAccounts(subAccount, subAccount.getName(), duplicate.getUID());
            }
        }
    }

    /**
     * Shows the delete confirmation dialog
     *
     * @param id Record ID of account to be deleted after confirmation
     */
    public void showDeleteConfirmationDialog(long id) {
        String accountUID = mAccountsDbAdapter.getUID(id);
        DeleteAccountDialogFragment alertFragment =
                DeleteAccountDialogFragment.newInstance(accountUID);

        Log.d(LOG_TAG, "showConfirmationDialog delete_account_" + accountUID);
        getParentFragmentManager().setFragmentResultListener(
                alertFragment.getRequestKey(accountUID), this, (requestKey, bundle) -> {
                    Log.d(LOG_TAG, "onFragmentResult " + requestKey + ", " + bundle);
                    refresh();
                });
        alertFragment.show(getParentFragmentManager(), "delete_confirmation_dialog");
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        if (mParentAccountUID != null) {
            inflater.inflate(R.menu.sub_account_actions, menu);
        } else {
            inflater.inflate(R.menu.account_actions, menu);
            // Associate searchable configuration with the SearchView

            SearchManager searchManager =
                    (SearchManager) GnuCashApplication.getAppContext().getSystemService(Context.SEARCH_SERVICE);
            mSearchView = (SearchView) menu.findItem(R.id.menu_search).getActionView();
            if (mSearchView == null) {
                return;
            }

            mSearchView.setSearchableInfo(
                    searchManager.getSearchableInfo(requireActivity().getComponentName()));
            mSearchView.setOnQueryTextListener(this);
            mSearchView.setOnCloseListener(this);
        }
    }

    /**
     * Refresh the account list as a sublist of another account
     *
     * @param parentAccountUID GUID of the parent account
     */
    @Override
    public void refresh(String parentAccountUID) {
        requireArguments().putString(UxArgument.PARENT_ACCOUNT_UID, parentAccountUID);
        refresh();
    }

    /**
     * Refreshes the list by restarting the {@link DatabaseCursorLoader} associated
     * with the ListView
     */
    @Override
    public void refresh() {
        LoaderManager.getInstance(this).restartLoader(0, null, this);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_DISPLAY_MODE, mDisplayMode);
    }

    /**
     * Closes any open database adapters used by the list
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, String.format("onDestroy: %d items in mAccountRecyclerAdapter.", mAccountRecyclerAdapter.getItemCount()));
        mBinding = null;
        if (mAccountRecyclerAdapter != null) {
            mAccountRecyclerAdapter.swapCursor(null);
        }
    }

    /**
     * Opens a new activity for creating or editing an account.
     * If the <code>accountId</code> &lt; 1, then create else edit the account.
     *
     * @param accountId Long record ID of account to be edited. Pass 0 to create a new account.
     */
    public void openCreateOrEditActivity(long accountId) {
        Intent editAccountIntent = new Intent(AccountsListFragment.this.getActivity(), FormActivity.class);
        editAccountIntent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        editAccountIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, mAccountsDbAdapter.getUID(accountId));
        editAccountIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.ACCOUNT.name());
        launcher.launch(editAccountIntent);
    }

    @Override
    @NonNull
    public Loader<Cursor> onCreateLoader(int id, @Nullable Bundle args) {
        Log.d(LOG_TAG, "Creating the accounts loader");
        Bundle arguments = getArguments();
        String accountUID = arguments == null ? null : arguments.getString(UxArgument.PARENT_ACCOUNT_UID);

        if (mCurrentFilter != null) {
            return new AccountsCursorLoader(getActivity(), mCurrentFilter);
        } else {
            return new AccountsCursorLoader(this.getActivity(), accountUID, mDisplayMode);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loaderCursor, Cursor cursor) {
        Log.d(LOG_TAG, "Accounts loader finished. Swapping in cursor: " + cursor);
        mAccountRecyclerAdapter.swapCursor(cursor);
        mAccountRecyclerAdapter.notifyDataSetChanged();
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> arg0) {
        Log.d(LOG_TAG, "Resetting the accounts loader");
        mAccountRecyclerAdapter.swapCursor(null);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        //nothing to see here, move along
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        String newFilter = !TextUtils.isEmpty(newText) ? newText : null;

        if (mCurrentFilter == null && newFilter == null) {
            return true;
        }
        if (mCurrentFilter != null && mCurrentFilter.equals(newFilter)) {
            return true;
        }
        mCurrentFilter = newFilter;
        LoaderManager.getInstance(this).restartLoader(0, null, this);
        return true;
    }

    @Override
    public boolean onClose() {
        if (!TextUtils.isEmpty(mSearchView.getQuery())) {
            mSearchView.setQuery(null, true);
        }
        return true;
    }

    /**
     * Extends {@link DatabaseCursorLoader} for loading of {@link Account} from the
     * database asynchronously.
     * <p>By default it loads only top-level accounts (accounts which have no parent or have GnuCash ROOT account as parent.
     * By submitting a parent account ID in the constructor parameter, it will load child accounts of that parent.</p>
     * <p>Class must be static because the Android loader framework requires it to be so</p>
     *
     * @author Ngewi Fet <ngewif@gmail.com>
     */
    private static final class AccountsCursorLoader extends DatabaseCursorLoader {
        private String mParentAccountUID = null;
        private String mFilter;
        private DisplayMode mDisplayMode = DisplayMode.TOP_LEVEL;

        /**
         * Initializes the loader to load accounts from the database.
         * If the <code>parentAccountId <= 0</code> then only top-level accounts are loaded.
         * Else only the child accounts of the <code>parentAccountId</code> will be loaded
         *
         * @param context          Application context
         * @param parentAccountUID GUID of the parent account
         */
        public AccountsCursorLoader(Context context, String parentAccountUID, DisplayMode displayMode) {
            super(context);
            this.mParentAccountUID = parentAccountUID;
            this.mDisplayMode = displayMode;
        }

        /**
         * Initializes the loader with a filter for account names.
         * Only accounts whose name match the filter will be loaded.
         *
         * @param context Application context
         * @param filter  Account name filter string
         */
        public AccountsCursorLoader(Context context, String filter) {
            super(context);
            mFilter = filter;
        }

        @Override
        public Cursor loadInBackground() {
            mDatabaseAdapter = AccountsDbAdapter.getInstance();
            Cursor cursor;

            if (mFilter != null) {
                cursor = ((AccountsDbAdapter) mDatabaseAdapter)
                        .fetchAccounts(DatabaseSchema.AccountEntry.COLUMN_HIDDEN + "= 0 AND "
                                        + DatabaseSchema.AccountEntry.COLUMN_NAME + " LIKE '%" + mFilter + "%'",
                                null, null);
            } else {
                if (mParentAccountUID != null && mParentAccountUID.length() > 0)
                    cursor = ((AccountsDbAdapter) mDatabaseAdapter).fetchSubAccounts(mParentAccountUID);
                else {
                    cursor = switch (this.mDisplayMode) {
                        case RECENT ->
                                ((AccountsDbAdapter) mDatabaseAdapter).fetchRecentAccounts(10);
                        case FAVORITES ->
                                ((AccountsDbAdapter) mDatabaseAdapter).fetchFavoriteAccounts();
                        case TOP_LEVEL ->
                                ((AccountsDbAdapter) mDatabaseAdapter).fetchTopLevelAccounts();
                    };
                }

            }

            if (cursor != null)
                registerContentObserver(cursor);
            return cursor;
        }
    }


    class AccountRecyclerAdapter extends CursorRecyclerAdapter<AccountRecyclerAdapter.AccountViewHolder> {

        public AccountRecyclerAdapter(Cursor cursor) {
            super(cursor);
        }

        @Override
        @NonNull
        public AccountViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            CardviewAccountBinding binding = CardviewAccountBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
//            Log.d(LOG_TAG, "onCreateViewHolder, binding: " + binding);
            return new AccountViewHolder(binding);

        }

        @Override
        public void onBindViewHolderCursor(final AccountViewHolder holder, final Cursor cursor) {
            final String accountUID = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_UID));

            try {
                // Note: if book is switched in "Manage Books" activity, mAccountsDbAdapter will be
                // reset and the getID call would throw IllegalArgumentException. In that case,
                // refresh to reload.
                // It's weird that this bug is not triggered when books is switched in left
                // drop-down menu.
                mAccountsDbAdapter = AccountsDbAdapter.getInstance();
                holder.accountId = mAccountsDbAdapter.getID(accountUID);
            } catch (IllegalArgumentException e) {
                Log.e(LOG_TAG, String.format("onBindViewHolderCursor %s, error: %s. Reset loader.", mAccountsDbAdapter, e.getMessage()));
                refresh();
                return;
            }

            holder.accountName.setText(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_NAME)));
            int subAccountCount = mAccountsDbAdapter.getSubAccountCount(accountUID);
            if (subAccountCount > 0) {
                holder.description.setVisibility(View.VISIBLE);
                String text = getResources().getQuantityString(R.plurals.label_sub_accounts, subAccountCount, subAccountCount);
                holder.description.setText(text);
            } else
                holder.description.setVisibility(View.GONE);

            // add a summary of transactions to the account view

            new AccountBalanceTask(holder.accountBalance, accountUID).asyncExecute();

            String accountColor = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.AccountEntry.COLUMN_COLOR_CODE));
            int colorCode = accountColor == null ? Color.TRANSPARENT : Color.parseColor(accountColor);
            holder.colorStripView.setBackgroundColor(colorCode);

            boolean isPlaceholderAccount = mAccountsDbAdapter.isPlaceholderAccount(accountUID);
            if (isPlaceholderAccount) {
                holder.createTransaction.setVisibility(View.GONE);
            } else {
                holder.createTransaction.setOnClickListener((View view) -> {
                    Intent createTransactionIntent = new Intent(getActivity(), FormActivity.class);
                    createTransactionIntent.setAction(Intent.ACTION_INSERT_OR_EDIT);
                    createTransactionIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID);
                    createTransactionIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name());
                    launcher.launch(createTransactionIntent);
                });
            }

            List<Budget> budgets = BudgetsDbAdapter.getInstance().getAccountBudgets(accountUID);
            //TODO: include fetch only active budgets
            if (budgets.size() == 1) {
                Budget budget = budgets.get(0);
                Money balance = mAccountsDbAdapter.getAccountBalance(accountUID, budget.getStartofCurrentPeriod(), budget.getEndOfCurrentPeriod());
                double budgetProgress = balance.divide(Objects.requireNonNull(budget.getAmount(accountUID))).asBigDecimal().doubleValue() * 100;

                holder.budgetIndicator.setVisibility(View.VISIBLE);
                holder.budgetIndicator.setProgress((int) budgetProgress);
            } else {
                holder.budgetIndicator.setVisibility(View.GONE);
            }

            if (mAccountsDbAdapter.isFavoriteAccount(accountUID)) {
                holder.favoriteStatus.setImageResource(R.drawable.ic_star_black_24dp);
            } else {
                holder.favoriteStatus.setImageResource(R.drawable.ic_star_border_black_24dp);
            }

            holder.favoriteStatus.setOnClickListener((View v) -> {
                boolean isFavoriteAccount = mAccountsDbAdapter.isFavoriteAccount(accountUID);

                ContentValues contentValues = new ContentValues();
                contentValues.put(DatabaseSchema.AccountEntry.COLUMN_FAVORITE, !isFavoriteAccount);
                mAccountsDbAdapter.updateRecord(accountUID, contentValues);

                int drawableResource = !isFavoriteAccount ?
                        R.drawable.ic_star_black_24dp : R.drawable.ic_star_border_black_24dp;
                holder.favoriteStatus.setImageResource(drawableResource);
                if (mDisplayMode == DisplayMode.FAVORITES)
                    refresh();
            });

            holder.itemView.setOnClickListener((View v) -> onListItemClick(accountUID));
        }


        class AccountViewHolder extends RecyclerView.ViewHolder implements PopupMenu.OnMenuItemClickListener {
            TextView accountName;
            TextView description;
            TextView accountBalance;
            ImageView createTransaction;
            ImageView favoriteStatus;
            ImageView optionsMenu;
            View colorStripView;
            ProgressBar budgetIndicator;

            long accountId;

            public AccountViewHolder(CardviewAccountBinding cardviewBinding) {
                super(cardviewBinding.getRoot());
//                Log.d(LOG_TAG, "ViewHolder, CardviewAccountBinding: " + cardviewBinding);

                accountName = cardviewBinding.listItemTwoLines.primaryText;
                description = cardviewBinding.listItemTwoLines.secondaryText;
                accountBalance = cardviewBinding.accountBalance;
                createTransaction = cardviewBinding.createTransaction;
                favoriteStatus = cardviewBinding.favoriteStatus;
                optionsMenu = cardviewBinding.optionsMenu;
                colorStripView = cardviewBinding.accountColorStrip;
                budgetIndicator = cardviewBinding.budgetIndicator;

                optionsMenu.setOnClickListener((View v) -> {
                    PopupMenu popup = new PopupMenu(requireActivity(), v);
                    popup.setOnMenuItemClickListener(AccountViewHolder.this);
                    MenuInflater inflater = popup.getMenuInflater();
                    inflater.inflate(R.menu.account_context_menu, popup.getMenu());
                    popup.show();
                });
            }

            @Override
            public boolean onMenuItemClick(MenuItem item) {
                Log.d(LOG_TAG, "onMenuItemClick, item: " + item);
                if (item.getItemId() == R.id.context_menu_edit_accounts) {
                    openCreateOrEditActivity(accountId);
                    return true;
                } else if (item.getItemId() == R.id.context_menu_duplicate_accounts) {
                    duplicateAccounts(accountId);
                    return true;
                } else if (item.getItemId() == R.id.context_menu_delete) {
                    tryDeleteAccount(accountId);
                    return true;
                } else {
                    return false;
                }
            }
        }
    }
}
