/*
 * Copyright (c) 2012 - 2014 Ngewi Fet <ngewif@gmail.com>
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

import static org.gnucash.android.ui.util.TextViewExtKt.displayBalance;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.SQLException;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.app.MenuFragment;
import org.gnucash.android.databinding.CardviewTransactionBinding;
import org.gnucash.android.databinding.FragmentTransactionsListBinding;
import org.gnucash.android.db.DatabaseCursorLoader;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity;
import org.gnucash.android.ui.transaction.dialog.BulkMoveDialogFragment;
import org.gnucash.android.ui.util.CursorRecyclerAdapter;
import org.gnucash.android.util.BackupManager;

import java.util.List;

import timber.log.Timber;

/**
 * List Fragment for displaying list of transactions for an account
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class TransactionsListFragment extends MenuFragment implements
    Refreshable, LoaderManager.LoaderCallbacks<Cursor>, FragmentResultListener {

    private TransactionsDbAdapter mTransactionsDbAdapter;
    private String mAccountUID;

    private boolean mUseCompactView = false;
    private boolean mUseDoubleEntry = true;

    private TransactionRecyclerAdapter mTransactionRecyclerAdapter;

    private FragmentTransactionsListBinding mBinding;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context context = requireContext();
        Bundle args = getArguments();
        mAccountUID = args.getString(UxArgument.SELECTED_ACCOUNT_UID);

        mUseDoubleEntry = GnuCashApplication.isDoubleEntryEnabled(context);
        mUseCompactView = GnuCashApplication.getBookPreferences(context).getBoolean(getString(R.string.key_use_compact_list), mUseCompactView);
        //if there was a local override of the global setting, respect it
        if (savedInstanceState != null) {
            mUseCompactView = savedInstanceState.getBoolean(getString(R.string.key_use_compact_list), mUseCompactView);
        }

        mTransactionsDbAdapter = TransactionsDbAdapter.getInstance();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(getString(R.string.key_use_compact_list), mUseCompactView);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mBinding = FragmentTransactionsListBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        assert actionBar != null;
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);

        final FragmentTransactionsListBinding binding = mBinding;
        binding.list.setHasFixedSize(true);
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            GridLayoutManager gridLayoutManager = new GridLayoutManager(getActivity(), 2);
            binding.list.setLayoutManager(gridLayoutManager);
        } else {
            LinearLayoutManager mLayoutManager = new LinearLayoutManager(getActivity());
            binding.list.setLayoutManager(mLayoutManager);
        }
        binding.list.setEmptyView(binding.emptyView);
        binding.list.setTag("transactions");

        mTransactionRecyclerAdapter = new TransactionRecyclerAdapter(null);
        binding.list.setAdapter(mTransactionRecyclerAdapter);
    }

    /**
     * Refresh the list with transactions from account with ID <code>accountId</code>
     *
     * @param accountUID GUID of account to load transactions from
     */
    @Override
    public void refresh(String accountUID) {
        mAccountUID = accountUID;
        refresh();
    }

    /**
     * Reload the list of transactions and recompute account balances
     */
    @Override
    public void refresh() {
        if (isDetached() || getFragmentManager() == null) return;
        getLoaderManager().restartLoader(0, null, this);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    public void onListItemClick(String transactionUID) {
        Intent intent = new Intent(getActivity(), TransactionDetailActivity.class)
            .putExtra(UxArgument.SELECTED_TRANSACTION_UID, transactionUID)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, mAccountUID);
        startActivity(intent);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.transactions_list_actions, menu);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem item = menu.findItem(R.id.menu_toggle_compact);
        item.setChecked(mUseCompactView);
        item.setEnabled(mUseDoubleEntry); //always compact for single-entry
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_toggle_compact:
                item.setChecked(!item.isChecked());
                mUseCompactView = item.isChecked();
                refresh();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Timber.d("Creating transactions loader");
        return new TransactionsCursorLoader(getActivity(), mAccountUID);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
        Timber.d("Transactions loader finished. Swapping in cursor");
        mTransactionRecyclerAdapter.changeCursor(cursor);
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        Timber.d("Resetting transactions loader");
        mTransactionRecyclerAdapter.changeCursor(null);
    }

    @Override
    public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle result) {
        if (BulkMoveDialogFragment.TAG.equals(requestKey)) {
            boolean refresh = result.getBoolean(Refreshable.EXTRA_REFRESH);
            if (refresh) refresh();
        }
    }

    /**
     * {@link DatabaseCursorLoader} for loading transactions asynchronously from the database
     *
     * @author Ngewi Fet <ngewif@gmail.com>
     */
    protected static class TransactionsCursorLoader extends DatabaseCursorLoader<TransactionsDbAdapter> {
        private final String accountUID;

        public TransactionsCursorLoader(Context context, String accountUID) {
            super(context);
            this.accountUID = accountUID;
        }

        @Override
        public Cursor loadInBackground() {
            databaseAdapter = TransactionsDbAdapter.getInstance();
            if (databaseAdapter == null) return null;
            Cursor c = databaseAdapter.fetchAllTransactionsForAccount(accountUID);
            if (c != null)
                registerContentObserver(c);
            return c;
        }
    }

    public class TransactionRecyclerAdapter extends CursorRecyclerAdapter<TransactionRecyclerAdapter.TransactionViewHolder> {

        public TransactionRecyclerAdapter(Cursor cursor) {
            super(cursor);
        }

        @Override
        public TransactionViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            CardviewTransactionBinding binding = CardviewTransactionBinding.inflate(inflater, parent, false);
            return new TransactionViewHolder(binding);
        }

        @Override
        public void onBindViewHolderCursor(TransactionViewHolder holder, Cursor cursor) {
            holder.bind(cursor);
        }

        public class TransactionViewHolder extends RecyclerView.ViewHolder implements PopupMenu.OnMenuItemClickListener {
            private final TextView primaryText;
            private final TextView secondaryText;
            private final TextView transactionAmount;
            private final ImageView optionsMenu;

            //these views are not used in the compact view, hence the nullability
            private final TextView transactionDate;
            private final ImageView editTransaction;

            @Nullable
            private Transaction transaction;
            @ColorInt
            private final int colorBalanceZero;

            public TransactionViewHolder(CardviewTransactionBinding binding) {
                super(binding.getRoot());
                primaryText = binding.listItem2Lines.primaryText;
                secondaryText = binding.listItem2Lines.secondaryText;
                transactionAmount = binding.transactionAmount;
                optionsMenu = binding.optionsMenu;
                transactionDate = binding.transactionDate;
                editTransaction = binding.editTransaction;
                colorBalanceZero = transactionAmount.getCurrentTextColor();
                setup();
            }

            private void setup() {
                optionsMenu.setOnClickListener(v -> {
                    PopupMenu popup = new PopupMenu(v.getContext(), v);
                    popup.setOnMenuItemClickListener(TransactionViewHolder.this);
                    MenuInflater inflater = popup.getMenuInflater();
                    Menu menu = popup.getMenu();
                    inflater.inflate(R.menu.transactions_context_menu, menu);
                    menu.findItem(R.id.menu_edit).setVisible(mUseCompactView);
                    popup.show();
                });

                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (transaction != null) {
                            onListItemClick(transaction.getUID());
                        }
                    }
                });
            }

            @Override
            public boolean onMenuItemClick(@NonNull MenuItem item) {
                if (transaction == null) return false;
                final String transactionUID = transaction.getUID();

                switch (item.getItemId()) {
                    case R.id.menu_delete:
                        deleteTransaction(transactionUID);
                        return true;

                    case R.id.menu_duplicate:
                        duplicateTransaction(transactionUID);
                        return true;

                    case R.id.menu_move:
                        moveTransaction(transactionUID);
                        return true;

                    case R.id.menu_edit:
                        editTransaction(transactionUID);
                        return true;

                    default:
                        return false;
                }
            }

            public void bind(@NonNull Cursor cursor) {
                final Context context = itemView.getContext();
                transaction = mTransactionsDbAdapter.buildModelInstance(cursor);
                final String transactionUID = transaction.getUID();

                primaryText.setText(transaction.getDescription());

                Money amount = transaction.getBalance(mAccountUID);
                displayBalance(transactionAmount, amount, colorBalanceZero);

                String dateText = TransactionsActivity.getPrettyDateFormat(context, transaction.getTimeMillis());
                transactionDate.setText(dateText);

                if (mUseCompactView || !mUseDoubleEntry) {
                    secondaryText.setVisibility(View.GONE);
                    editTransaction.setVisibility(View.GONE);
                } else {
                    secondaryText.setVisibility(View.VISIBLE);
                    editTransaction.setVisibility(View.VISIBLE);

                    List<Split> splits = transaction.getSplits();
                    String text = "";
                    String error = null;

                    if (splits.size() == 2) {
                        if (splits.get(0).isPairOf(splits.get(1))) {
                            for (Split split : splits) {
                                if (!split.getAccountUID().equals(mAccountUID)) {
                                    text = AccountsDbAdapter.getInstance().getFullyQualifiedAccountName(split.getAccountUID());
                                    break;
                                }
                            }
                        }
                        if (TextUtils.isEmpty(text)) {
                            text = getString(R.string.label_split_count, splits.size());
                            error = getString(R.string.imbalance_account_name);
                        }
                    } else if (splits.size() > 2) {
                        text = getString(R.string.label_split_count, splits.size());
                    }
                    secondaryText.setText(text);
                    secondaryText.setError(error);

                    editTransaction.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            editTransaction(transactionUID);
                        }
                    });
                }
            }
        }
    }

    private void deleteTransaction(final String transactionUID) {
        final Activity activity = requireActivity();
        if (GnuCashApplication.shouldBackupTransactions(activity)) {
            BackupManager.backupActiveBookAsync(activity, result -> {
                mTransactionsDbAdapter.deleteRecord(transactionUID);
                WidgetConfigurationActivity.updateAllWidgets(activity);
                refresh();
                return null;
            });
        } else {
            mTransactionsDbAdapter.deleteRecord(transactionUID);
            WidgetConfigurationActivity.updateAllWidgets(activity);
            refresh();
        }
    }

    private void duplicateTransaction(String transactionUID) {
        try {
            Transaction transaction = mTransactionsDbAdapter.getRecord(transactionUID);
            Transaction duplicate = new Transaction(transaction);
            duplicate.setTime(System.currentTimeMillis());
            mTransactionsDbAdapter.addRecord(duplicate, DatabaseAdapter.UpdateMethod.insert);
            refresh();
        } catch (SQLException e) {
            Timber.e(e);
        }
    }

    private void moveTransaction(String transactionUID) {
        String[] uids = new String[]{transactionUID};
        FragmentManager fm = getParentFragmentManager();
        fm.setFragmentResultListener(BulkMoveDialogFragment.TAG, TransactionsListFragment.this, TransactionsListFragment.this);
        BulkMoveDialogFragment fragment = BulkMoveDialogFragment.newInstance(uids, mAccountUID);
        fragment.show(fm, BulkMoveDialogFragment.TAG);
    }

    private void editTransaction(String transactionUID) {
        Intent intent = new Intent(getActivity(), FormActivity.class)
            .putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name())
            .putExtra(UxArgument.SELECTED_TRANSACTION_UID, transactionUID)
            .putExtra(UxArgument.SELECTED_ACCOUNT_UID, mAccountUID);
        startActivity(intent);
    }
}
