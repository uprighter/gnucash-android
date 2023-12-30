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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.databinding.CardviewCompactTransactionBinding;
import org.gnucash.android.databinding.CardviewTransactionBinding;
import org.gnucash.android.databinding.FragmentTransactionsListBinding;
import org.gnucash.android.db.DatabaseCursorLoader;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.db.adapter.SplitsDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.homescreen.WidgetConfigurationActivity;
import org.gnucash.android.ui.settings.PreferenceActivity;
import org.gnucash.android.ui.transaction.dialog.BulkMoveDialogFragment;
import org.gnucash.android.ui.transaction.dialog.TransactionsDeleteConfirmationDialogFragment;
import org.gnucash.android.ui.util.CursorRecyclerAdapter;
import org.gnucash.android.ui.util.widget.EmptyRecyclerView;
import org.gnucash.android.util.BackupManager;

import java.util.List;
import java.util.Objects;

/**
 * List Fragment for displaying list of transactions for an account
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class TransactionsListFragment extends Fragment implements
        Refreshable, LoaderManager.LoaderCallbacks<Cursor> {

    /**
     * Logging tag
     */
    protected static final String LOG_TAG = TransactionsListFragment.class.getName();

    private FragmentTransactionsListBinding mBinding;

    private TransactionsDbAdapter mTransactionsDbAdapter;
    private String mAccountUID;

    private boolean mUseCompactView = false;

    private TransactionRecyclerAdapter mTransactionRecyclerAdapter;
    private EmptyRecyclerView mRecyclerView;

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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        Bundle args = requireArguments();
        mAccountUID = args.getString(UxArgument.SELECTED_ACCOUNT_UID);

        mUseCompactView = PreferenceActivity.getActiveBookSharedPreferences()
                .getBoolean(requireActivity().getString(R.string.key_use_compact_list), !GnuCashApplication.isDoubleEntryEnabled());
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
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        mBinding = FragmentTransactionsListBinding.inflate(inflater, container, false);
        mRecyclerView = mBinding.transactionRecyclerView;

        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setEmptyView(mBinding.emptyView);

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
    public void onDestroy() {
        super.onDestroy();
        mBinding = null;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ActionBar aBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        Objects.requireNonNull(aBar).setDisplayShowTitleEnabled(false);
        aBar.setDisplayHomeAsUpEnabled(true);

        mTransactionRecyclerAdapter = new TransactionRecyclerAdapter(null);
        mRecyclerView.setAdapter(mTransactionRecyclerAdapter);

        setHasOptionsMenu(true);
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
        LoaderManager.getInstance(this).restartLoader(0, null, this);
    }

    @Override
    public void onResume() {
        super.onResume();
        ((TransactionsActivity) requireActivity()).updateNavigationSelection();
        refresh();
    }

    public void onListItemClick(long id) {
        Intent transactionDetailActivityIntent = new Intent(getActivity(), TransactionDetailActivity.class);
        transactionDetailActivityIntent.putExtra(UxArgument.SELECTED_TRANSACTION_UID, mTransactionsDbAdapter.getUID(id));
        transactionDetailActivityIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, mAccountUID);
        launcher.launch(transactionDetailActivityIntent);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.transactions_list_actions, menu);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem item = menu.findItem(R.id.menu_compact_trn_view);
        item.setChecked(mUseCompactView);
        item.setEnabled(GnuCashApplication.isDoubleEntryEnabled()); //always compact for single-entry
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_compact_trn_view) {
            item.setChecked(!item.isChecked());
            mUseCompactView = !mUseCompactView;
            refresh();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    @NonNull
    public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
        Log.d(LOG_TAG, "Creating transactions loader");
        return new TransactionsCursorLoader(getActivity(), mAccountUID);
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
        Log.d(LOG_TAG, "Transactions loader finished. Swapping in cursor");
        mTransactionRecyclerAdapter.swapCursor(cursor);
        mTransactionRecyclerAdapter.notifyDataSetChanged();
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        Log.d(LOG_TAG, "Resetting transactions loader");
        mTransactionRecyclerAdapter.swapCursor(null);
    }

    /**
     * {@link DatabaseCursorLoader} for loading transactions asynchronously from the database
     *
     * @author Ngewi Fet <ngewif@gmail.com>
     */
    protected static class TransactionsCursorLoader extends DatabaseCursorLoader {
        private final String accountUID;

        public TransactionsCursorLoader(Context context, String accountUID) {
            super(context);
            this.accountUID = accountUID;
        }

        @Override
        public Cursor loadInBackground() {
            mDatabaseAdapter = TransactionsDbAdapter.getInstance();
            Cursor c = ((TransactionsDbAdapter) mDatabaseAdapter).fetchAllTransactionsForAccount(accountUID);
            if (c != null)
                registerContentObserver(c);
            return c;
        }
    }

    public class TransactionRecyclerAdapter extends CursorRecyclerAdapter<TransactionRecyclerAdapter.ViewHolder> {

        public static final int ITEM_TYPE_COMPACT = 0x111;
        public static final int ITEM_TYPE_FULL = 0x100;

        public TransactionRecyclerAdapter(Cursor cursor) {
            super(cursor);
        }

        @Override
        @NonNull
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Log.d(LOG_TAG, "onCreateViewHolder, viewType: " + viewType);
            if (viewType == ITEM_TYPE_COMPACT) {
                return new ViewHolder(CardviewCompactTransactionBinding.inflate(
                        LayoutInflater.from(parent.getContext()), parent, false));
            }
            return new ViewHolder(CardviewTransactionBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public int getItemViewType(int position) {
            return mUseCompactView ? ITEM_TYPE_COMPACT : ITEM_TYPE_FULL;
        }

        @Override
        public void onBindViewHolderCursor(ViewHolder holder, Cursor cursor) {
            holder.transactionId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseSchema.TransactionEntry._ID));

            String description = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.TransactionEntry.COLUMN_DESCRIPTION));
            holder.primaryText.setText(description);

            final String transactionUID = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.TransactionEntry.COLUMN_UID));
            Money amount = mTransactionsDbAdapter.getBalance(transactionUID, mAccountUID);
            TransactionsActivity.displayBalance(holder.transactionAmount, amount);

            long dateMillis = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseSchema.TransactionEntry.COLUMN_TIMESTAMP));
            String dateText = TransactionsActivity.getPrettyDateFormat(getActivity(), dateMillis);

            final long id = holder.transactionId;
            holder.itemView.setOnClickListener(v -> onListItemClick(id));

            if (mUseCompactView) {
                holder.secondaryText.setText(dateText);
            } else {

                List<Split> splits = SplitsDbAdapter.getInstance().getSplitsForTransaction(transactionUID);
                String text = "";

                if (splits.size() == 2 && splits.get(0).isPairOf(splits.get(1))) {
                    for (Split split : splits) {
                        if (!mAccountUID.equals(split.getAccountUID())) {
                            text = AccountsDbAdapter.getInstance().getFullyQualifiedAccountName(split.getAccountUID());
                            break;
                        }
                    }
                }

                if (splits.size() > 2) {
                    text = splits.size() + " splits";
                }
                holder.secondaryText.setText(text);

                if (holder.transactionDate != null) {
                    holder.transactionDate.setText(dateText);
                }

                if (holder.editTransaction != null) {
                    holder.editTransaction.setOnClickListener(v -> {
                        Intent formActivityIntent = new Intent(getActivity(), FormActivity.class);
                        formActivityIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name());
                        formActivityIntent.putExtra(UxArgument.SELECTED_TRANSACTION_UID, transactionUID);
                        formActivityIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, mAccountUID);
                        launcher.launch(formActivityIntent);
                    });
                }
            }
        }

        public class ViewHolder extends RecyclerView.ViewHolder implements PopupMenu.OnMenuItemClickListener {
            public TextView primaryText;
            public TextView secondaryText;
            public TextView transactionAmount;
            public ImageView optionsMenu;

            //these views are not used in the compact view, hence the nullability
            @Nullable
            public TextView transactionDate;
            @Nullable
            public ImageView editTransaction;

            long transactionId;

            public ViewHolder(ViewBinding viewBinding) {
                super(viewBinding.getRoot());
                Log.d(LOG_TAG, "ViewHolder, viewBinding: " + viewBinding);

                if (mUseCompactView) {
                    CardviewCompactTransactionBinding cardviewBinding = (CardviewCompactTransactionBinding) viewBinding;

                    primaryText = cardviewBinding.listItemTwoLines.primaryText;
                    secondaryText = cardviewBinding.listItemTwoLines.secondaryText;
                    transactionAmount = cardviewBinding.transactionAmount;
                    optionsMenu = cardviewBinding.optionsMenu;
                } else {
                    CardviewTransactionBinding cardviewBinding = (CardviewTransactionBinding) viewBinding;

                    primaryText = cardviewBinding.listItemTwoLines.primaryText;
                    secondaryText = cardviewBinding.listItemTwoLines.secondaryText;
                    transactionAmount = cardviewBinding.transactionAmount;
                    optionsMenu = cardviewBinding.optionsMenu;

                    //these views are not used in the compact view, hence the nullability
                    transactionDate = cardviewBinding.transactionDate;
                    editTransaction = cardviewBinding.editTransaction;
                }

                primaryText.setTextSize(18);
                optionsMenu.setOnClickListener(v -> {
                    PopupMenu popup = new PopupMenu(requireActivity(), v);
                    popup.setOnMenuItemClickListener(ViewHolder.this);
                    MenuInflater inflater = popup.getMenuInflater();
                    inflater.inflate(R.menu.transactions_context_menu, popup.getMenu());
                    popup.show();
                });
            }

            private boolean handleMenuDeleteTransaction(final long transactionId) {
                TransactionsDeleteConfirmationDialogFragment dialog = TransactionsDeleteConfirmationDialogFragment.newInstance(R.string.msg_delete_transaction_confirmation, transactionId);

                getParentFragmentManager().setFragmentResultListener(
                        dialog.getRequestKey(transactionId), TransactionsListFragment.this, (requestKey, bundle) -> {
                            Log.d(LOG_TAG, "onFragmentResult " + requestKey + ", " + bundle);
                            refresh();
                        });
                String title = "delete_transaction";
                dialog.show(getParentFragmentManager(), title);

                return true;
            }

            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.context_menu_delete) {
                    BackupManager.backupActiveBook();
                    if (transactionId > 0) {
                        return handleMenuDeleteTransaction(transactionId);
                    } else {
                        mTransactionsDbAdapter.deleteRecord(transactionId);
                        WidgetConfigurationActivity.updateAllWidgets(getActivity());
                        refresh();
                        return true;
                    }
                } else if (item.getItemId() == R.id.context_menu_duplicate_transaction) {
                    Transaction transaction = mTransactionsDbAdapter.getRecord(transactionId);
                    Transaction duplicate = new Transaction(transaction, true);
                    duplicate.setTime(System.currentTimeMillis());
                    mTransactionsDbAdapter.addRecord(duplicate, DatabaseAdapter.UpdateMethod.insert);
                    refresh();
                    return true;
                } else if (item.getItemId() == R.id.context_menu_move_transaction) {
                    long[] ids = new long[]{transactionId};
                    BulkMoveDialogFragment fragment = BulkMoveDialogFragment.newInstance(ids, mAccountUID);

                    Log.d(LOG_TAG, "context_menu_move_transaction_" + mAccountUID);
                    getParentFragmentManager().setFragmentResultListener(
                            fragment.getRequestKey(mAccountUID), TransactionsListFragment.this, (requestKey, bundle) -> {
                                Log.d(LOG_TAG, "onFragmentResult " + requestKey + ", " + bundle);
                                refresh();
                            });
                    fragment.show(getParentFragmentManager(), "bulk_move_transactions");
                    return true;

                } else {
                    return false;
                }
            }
        }
    }
}
