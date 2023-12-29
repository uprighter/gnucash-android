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
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;

import org.gnucash.android.R;
import org.gnucash.android.databinding.CardviewBudgetAmountBinding;
import org.gnucash.android.databinding.FragmentBudgetDetailBinding;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BudgetsDbAdapter;
import org.gnucash.android.model.Budget;
import org.gnucash.android.model.BudgetAmount;
import org.gnucash.android.model.Money;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.transaction.TransactionsActivity;
import org.gnucash.android.ui.util.widget.EmptyRecyclerView;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fragment for displaying budget details
 */
public class BudgetDetailFragment extends Fragment implements Refreshable {

    public static final String LOG_TAG = BudgetDetailFragment.class.getName();

    TextView mBudgetNameTextView;
    TextView mBudgetDescriptionTextView;
    TextView mBudgetRecurrence;
    EmptyRecyclerView mRecyclerView;

    private String mBudgetUID;
    private BudgetsDbAdapter mBudgetsDbAdapter;

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Log.d(LOG_TAG, "launch intent: result = " + result);
                if (result.getResultCode() == Activity.RESULT_OK) {
                    refresh();
                }
            }
    );

    public static BudgetDetailFragment newInstance(String budgetUID) {
        BudgetDetailFragment fragment = new BudgetDetailFragment();
        Bundle args = new Bundle();
        args.putString(UxArgument.BUDGET_UID, budgetUID);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        FragmentBudgetDetailBinding binding = FragmentBudgetDetailBinding.inflate(inflater, container, false);
        mBudgetNameTextView = binding.listItemTwoLines.primaryText;
        mBudgetDescriptionTextView = binding.listItemTwoLines.secondaryText;
        mBudgetRecurrence = binding.budgetRecurrence;
        EmptyRecyclerView mRecyclerView = binding.budgetAmountRecycler;

        mBudgetDescriptionTextView.setMaxLines(3);

        mRecyclerView.setHasFixedSize(true);

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
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mBudgetsDbAdapter = BudgetsDbAdapter.getInstance();
        mBudgetUID = requireArguments().getString(UxArgument.BUDGET_UID);
        bindViews();

        setHasOptionsMenu(true);
    }

    private void bindViews() {
        Budget budget = mBudgetsDbAdapter.getRecord(mBudgetUID);
        mBudgetNameTextView.setText(budget.getName());

        String description = budget.getDescription();
        if (description != null && !description.isEmpty())
            mBudgetDescriptionTextView.setText(description);
        else {
            mBudgetDescriptionTextView.setVisibility(View.GONE);
        }
        mBudgetRecurrence.setText(Objects.requireNonNull(budget.getRecurrence()).getRepeatString());

        mRecyclerView.setAdapter(new BudgetAmountAdapter());
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();

        View view = requireActivity().findViewById(R.id.fab_create_budget);
        if (view != null) {
            view.setVisibility(View.GONE);
        }
    }

    @Override
    public void refresh() {
        bindViews();
        String budgetName = mBudgetsDbAdapter.getAttribute(mBudgetUID, DatabaseSchema.BudgetEntry.COLUMN_NAME);
        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        assert actionBar != null;
        actionBar.setTitle("Budget: " + budgetName);
    }

    @Override
    public void refresh(String budgetUID) {
        mBudgetUID = budgetUID;
        refresh();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.budget_actions, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_edit_budget) {
            Intent addAccountIntent = new Intent(getActivity(), FormActivity.class);
            addAccountIntent.setAction(Intent.ACTION_INSERT_OR_EDIT);
            addAccountIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.BUDGET.name());
            addAccountIntent.putExtra(UxArgument.BUDGET_UID, mBudgetUID);
            launcher.launch(addAccountIntent);
            return true;
        }
        return false;
    }

    public class BudgetAmountAdapter extends RecyclerView.Adapter<BudgetAmountAdapter.BudgetAmountViewHolder> {
        private final List<BudgetAmount> mBudgetAmounts;
        private final Budget mBudget;

        public BudgetAmountAdapter() {
            mBudget = mBudgetsDbAdapter.getRecord(mBudgetUID);
            mBudgetAmounts = mBudget.getCompactedBudgetAmounts();
        }

        @NonNull
        @Override
        public BudgetAmountViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(getActivity()).inflate(R.layout.cardview_budget_amount, parent, false);
            return new BudgetAmountViewHolder(view);
        }

        @Override
        public void onBindViewHolder(BudgetAmountViewHolder holder, final int position) {
            BudgetAmount budgetAmount = mBudgetAmounts.get(position);
            Money projectedAmount = budgetAmount.getAmount();
            assert projectedAmount != null;
            AccountsDbAdapter accountsDbAdapter = AccountsDbAdapter.getInstance();

            holder.budgetAccount.setText(accountsDbAdapter.getAccountFullName(budgetAmount.getAccountUID()));
            holder.budgetAmount.setText(projectedAmount.formattedString());

            Money spentAmount = accountsDbAdapter.getAccountBalance(budgetAmount.getAccountUID(),
                    mBudget.getStartofCurrentPeriod(), mBudget.getEndOfCurrentPeriod());

            holder.budgetSpent.setText(spentAmount.abs().formattedString());
            holder.budgetLeft.setText(projectedAmount.subtract(spentAmount.abs()).formattedString());

            double budgetProgress = 0;
            if (projectedAmount.asDouble() != 0) {
                budgetProgress = spentAmount.asBigDecimal().divide(projectedAmount.asBigDecimal(),
                        Objects.requireNonNull(spentAmount.getCommodity()).getSmallestFractionDigits(),
                        RoundingMode.HALF_EVEN).doubleValue();
            }

            holder.budgetIndicator.setProgress((int) (budgetProgress * 100));
            holder.budgetSpent.setTextColor(BudgetsActivity.getBudgetProgressColor(1 - budgetProgress));
            holder.budgetLeft.setTextColor(BudgetsActivity.getBudgetProgressColor(1 - budgetProgress));

            generateChartData(holder.budgetChart, budgetAmount);

            holder.itemView.setOnClickListener(v -> {
                Intent transactionActivityIntent = new Intent(getActivity(), TransactionsActivity.class);
                transactionActivityIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, mBudgetAmounts.get(holder.getBindingAdapterPosition()).getAccountUID());
                launcher.launch(transactionActivityIntent);
            });
        }

        /**
         * Generate the chart data for the chart
         *
         * @param barChart     View where to display the chart
         * @param budgetAmount BudgetAmount to visualize
         */
        public void generateChartData(BarChart barChart, BudgetAmount budgetAmount) {
            // FIXME: 25.10.15 chart is broken

            AccountsDbAdapter accountsDbAdapter = AccountsDbAdapter.getInstance();

            List<BarEntry> barEntries = new ArrayList<>();
            List<String> xVals = new ArrayList<>();

            //todo: refactor getNumberOfPeriods into budget
            int budgetPeriods = (int) mBudget.getNumberOfPeriods();
            budgetPeriods = budgetPeriods == 0 ? 12 : budgetPeriods;
            int periods = Objects.requireNonNull(mBudget.getRecurrence()).getNumberOfPeriods(budgetPeriods); //// FIXME: 15.08.2016 why do we need number of periods

            for (int periodNum = 1; periodNum <= periods; periodNum++) {
                BigDecimal amount = accountsDbAdapter.getAccountBalance(budgetAmount.getAccountUID(),
                                mBudget.getStartOfPeriod(periodNum), mBudget.getEndOfPeriod(periodNum))
                        .asBigDecimal();

                if (amount.equals(BigDecimal.ZERO)) {
                    continue;
                }

                barEntries.add(new BarEntry(amount.floatValue(), periodNum));
                xVals.add(mBudget.getRecurrence().getTextOfCurrentPeriod(periodNum));
            }

            String label = accountsDbAdapter.getAccountName(budgetAmount.getAccountUID());
            BarDataSet barDataSet = new BarDataSet(barEntries, label);

            BarData barData = new BarData(xVals, barDataSet);
            LimitLine limitLine = new LimitLine(Objects.requireNonNull(budgetAmount.getAmount()).asBigDecimal().floatValue());
            limitLine.setLineWidth(2f);
            limitLine.setLineColor(Color.RED);


            barChart.setData(barData);
            barChart.getAxisLeft().addLimitLine(limitLine);
            BigDecimal maxValue = budgetAmount.getAmount().add(budgetAmount.getAmount().multiply(new BigDecimal("0.2"))).asBigDecimal();
            barChart.getAxisLeft().setAxisMaxValue(maxValue.floatValue());
            barChart.animateX(1000);
            barChart.setAutoScaleMinMaxEnabled(true);
            barChart.setDrawValueAboveBar(true);
            barChart.invalidate();
        }

        @Override
        public int getItemCount() {
            return mBudgetAmounts.size();
        }

        class BudgetAmountViewHolder extends RecyclerView.ViewHolder {
            TextView budgetAccount;
            TextView budgetAmount;
            TextView budgetSpent;
            TextView budgetLeft;
            ProgressBar budgetIndicator;
            BarChart budgetChart;

            public BudgetAmountViewHolder(View itemView) {
                super(itemView);

                CardviewBudgetAmountBinding binding = CardviewBudgetAmountBinding.bind(itemView);
                budgetAccount = binding.budgetAccount;
                budgetAmount = binding.budgetAmount;
                budgetSpent = binding.budgetSpent;
                budgetLeft = binding.budgetLeft;
                budgetIndicator = binding.budgetIndicator;
                budgetChart = binding.budgetChart;
            }
        }
    }
}
