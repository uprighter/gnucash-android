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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.gnucash.android.R;
import org.gnucash.android.databinding.CardviewBudgetBinding;
import org.gnucash.android.databinding.FragmentBudgetListBinding;
import org.gnucash.android.db.DatabaseCursorLoader;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BudgetsDbAdapter;
import org.gnucash.android.model.Budget;
import org.gnucash.android.model.BudgetAmount;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.util.CursorRecyclerAdapter;
import org.gnucash.android.ui.util.widget.EmptyRecyclerView;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

/**
 * Budget list fragment
 */
public class BudgetListFragment extends Fragment implements Refreshable,
        LoaderManager.LoaderCallbacks<Cursor> {

    private static final String LOG_TAG = BudgetListFragment.class.getName();

    private BudgetRecyclerAdapter mBudgetRecyclerAdapter;

    private BudgetsDbAdapter mBudgetsDbAdapter;

    EmptyRecyclerView mRecyclerView;
    Button mProposeBudgets;

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Log.d(LOG_TAG, "launch intent: result = " + result);
                if (result.getResultCode() == Activity.RESULT_OK) {
                    refresh();
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        FragmentBudgetListBinding binding = FragmentBudgetListBinding.inflate(inflater, container, false);
        mRecyclerView = binding.budgetRecyclerView;
        mProposeBudgets = binding.emptyView;

        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setEmptyView(mProposeBudgets);

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            GridLayoutManager gridLayoutManager = new GridLayoutManager(getActivity(), 2);
            mRecyclerView.setLayoutManager(gridLayoutManager);
        } else {
            LinearLayoutManager mLayoutManager = new LinearLayoutManager(getActivity());
            mRecyclerView.setLayoutManager(mLayoutManager);
        }
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mBudgetsDbAdapter = BudgetsDbAdapter.getInstance();
        mBudgetRecyclerAdapter = new BudgetRecyclerAdapter(null);

        mRecyclerView.setAdapter(mBudgetRecyclerAdapter);

        LoaderManager.getInstance(this).initLoader(0, null, this);
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Log.d(LOG_TAG, "Creating the accounts loader");
        return new BudgetsCursorLoader(requireActivity());
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loaderCursor, Cursor cursor) {
        Log.d(LOG_TAG, "Budget loader finished. Swapping in cursor");
        mBudgetRecyclerAdapter.swapCursor(cursor);
        mBudgetRecyclerAdapter.notifyDataSetChanged();
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> arg0) {
        Log.d(LOG_TAG, "Resetting the accounts loader");
        mBudgetRecyclerAdapter.swapCursor(null);
        mBudgetRecyclerAdapter.notifyDataSetChanged();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
        requireActivity().findViewById(R.id.fab_create_budget).setVisibility(View.VISIBLE);
        Objects.requireNonNull(((AppCompatActivity) requireActivity()).getSupportActionBar()).setTitle("Budgets");
    }

    @Override
    public void refresh() {
        LoaderManager.getInstance(this).restartLoader(0, null, this);
    }

    /**
     * This method does nothing with the GUID.
     * Is equivalent to calling {@link #refresh()}
     *
     * @param uid GUID of relevant item to be refreshed
     */
    @Override
    public void refresh(String uid) {
        refresh();
    }

    /**
     * Opens the budget detail fragment
     *
     * @param budgetUID GUID of budget
     */
    public void onClickBudget(String budgetUID) {
        FragmentManager fragmentManager = requireActivity().getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager
                .beginTransaction();

        fragmentTransaction.replace(R.id.fragment_container, BudgetDetailFragment.newInstance(budgetUID));
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();
    }

    /**
     * Launches the FormActivity for editing the budget
     *
     * @param budgetId Db record Id of the budget
     */
    private void editBudget(long budgetId) {
        Intent addAccountIntent = new Intent(getActivity(), FormActivity.class);
        addAccountIntent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        addAccountIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.BUDGET.name());
        addAccountIntent.putExtra(UxArgument.BUDGET_UID, mBudgetsDbAdapter.getUID(budgetId));
        launcher.launch(addAccountIntent);
    }

    /**
     * Delete the budget from the database
     *
     * @param budgetId Database record ID
     */
    private void deleteBudget(long budgetId) {
        BudgetsDbAdapter.getInstance().deleteRecord(budgetId);
        refresh();
    }

    class BudgetRecyclerAdapter extends CursorRecyclerAdapter<BudgetRecyclerAdapter.BudgetViewHolder> {

        public BudgetRecyclerAdapter(Cursor cursor) {
            super(cursor);
        }

        @Override
        public void onBindViewHolderCursor(BudgetViewHolder holder, Cursor cursor) {
            final Budget budget = mBudgetsDbAdapter.buildModelInstance(cursor);
            holder.budgetId = mBudgetsDbAdapter.getID(Objects.requireNonNull(budget.getUID()));

            holder.budgetName.setText(budget.getName());

            AccountsDbAdapter accountsDbAdapter = AccountsDbAdapter.getInstance();
            String accountString;
            int numberOfAccounts = budget.getNumberOfAccounts();
            if (numberOfAccounts == 1) {
                accountString = accountsDbAdapter.getAccountFullName(budget.getBudgetAmounts().get(0).getAccountUID());
            } else {
                accountString = numberOfAccounts + " budgeted accounts";
            }
            holder.accountName.setText(accountString);

            holder.budgetRecurrence.setText(
                    String.format(Locale.getDefault(), "%s - x days left",
                            Objects.requireNonNull(budget.getRecurrence()).getRrule()));

            BigDecimal spentAmountValue = BigDecimal.ZERO;
            for (BudgetAmount budgetAmount : budget.getCompactedBudgetAmounts()) {
                Money balance = accountsDbAdapter.getAccountBalance(budgetAmount.getAccountUID(),
                        budget.getStartofCurrentPeriod(), budget.getEndOfCurrentPeriod());
                spentAmountValue = spentAmountValue.add(balance.asBigDecimal());
            }

            Money budgetTotal = budget.getAmountSum();
            assert budgetTotal != null;
            Commodity commodity = budgetTotal.getCommodity();
            assert commodity != null;
            String usedAmount = commodity.getSymbol() + spentAmountValue + " of "
                    + budgetTotal.formattedString();
            holder.budgetAmount.setText(usedAmount);

            double budgetProgress = spentAmountValue.divide(budgetTotal.asBigDecimal(),
                            commodity.getSmallestFractionDigits(), RoundingMode.HALF_EVEN)
                    .doubleValue();
            holder.budgetIndicator.setProgress((int) (budgetProgress * 100));

            holder.budgetAmount.setTextColor(BudgetsActivity.getBudgetProgressColor(1 - budgetProgress));

            holder.itemView.setOnClickListener(v -> onClickBudget(budget.getUID()));
        }

        @NonNull
        @Override
        public BudgetViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.cardview_budget, parent, false);

            return new BudgetViewHolder(v);
        }

        class BudgetViewHolder extends RecyclerView.ViewHolder implements PopupMenu.OnMenuItemClickListener {
            TextView budgetName;
            TextView accountName;
            TextView budgetAmount;
            ImageView optionsMenu;
            ProgressBar budgetIndicator;
            TextView budgetRecurrence;
            long budgetId;

            public BudgetViewHolder(View itemView) {
                super(itemView);

                CardviewBudgetBinding binding = CardviewBudgetBinding.bind(itemView);
                budgetName = binding.listItemTwoLines.primaryText;
                accountName = binding.listItemTwoLines.secondaryText;
                budgetAmount = binding.budgetAmount;
                optionsMenu = binding.optionsMenu;
                budgetIndicator = binding.budgetIndicator;
                budgetRecurrence = binding.budgetRecurrence;


                optionsMenu.setOnClickListener(v -> {
                    PopupMenu popup = new PopupMenu(requireActivity(), v);
                    popup.setOnMenuItemClickListener(BudgetViewHolder.this);
                    MenuInflater inflater = popup.getMenuInflater();
                    inflater.inflate(R.menu.budget_context_menu, popup.getMenu());
                    popup.show();
                });
            }

            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.context_menu_edit_budget) {
                    editBudget(budgetId);
                    return true;
                } else if (item.getItemId() == R.id.context_menu_delete) {
                    deleteBudget(budgetId);
                    return true;
                } else {
                    return false;
                }
            }
        }
    }


    /**
     * Loads Budgets asynchronously from the database
     */
    private static class BudgetsCursorLoader extends DatabaseCursorLoader {

        /**
         * Constructor
         * Initializes the content observer
         *
         * @param context Application context
         */
        public BudgetsCursorLoader(Context context) {
            super(context);
        }

        @Override
        public Cursor loadInBackground() {
            mDatabaseAdapter = BudgetsDbAdapter.getInstance();
            return mDatabaseAdapter.fetchAllRecords(null, null, DatabaseSchema.BudgetEntry.COLUMN_NAME + " ASC");
        }
    }
}