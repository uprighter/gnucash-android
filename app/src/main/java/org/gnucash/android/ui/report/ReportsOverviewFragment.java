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
package org.gnucash.android.ui.report;

import static com.github.mikephil.charting.components.Legend.LegendPosition;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.FragmentManager;
import androidx.viewbinding.ViewBinding;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.Legend.LegendForm;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;

import org.gnucash.android.R;
import org.gnucash.android.databinding.FragmentReportSummaryBinding;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Money;
import org.gnucash.android.ui.report.barchart.StackedBarChartFragment;
import org.gnucash.android.ui.report.linechart.CashFlowLineChartFragment;
import org.gnucash.android.ui.report.piechart.PieChartFragment;
import org.gnucash.android.ui.report.sheet.BalanceSheetFragment;
import org.gnucash.android.ui.transaction.TransactionsActivity;
import org.joda.time.LocalDate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Shows a summary of reports
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class ReportsOverviewFragment extends BaseReportFragment {

    public static final int LEGEND_TEXT_SIZE = 14;

    Button mPieChartButton;
    Button mBarChartButton;
    Button mLineChartButton;
    Button mBalanceSheetButton;

    PieChart mChart;
    TextView mTotalAssets;
    TextView mTotalLiabilities;
    TextView mNetWorth;

    private AccountsDbAdapter mAccountsDbAdapter;
    private Money mAssetsBalance;
    private Money mLiabilitiesBalance;

    private boolean mChartHasData = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAccountsDbAdapter = AccountsDbAdapter.getInstance();
    }

    @Override
    public ViewBinding bindViews() {
        FragmentReportSummaryBinding viewBinding = FragmentReportSummaryBinding.inflate(getLayoutInflater());

        mPieChartButton = viewBinding.btnPieChart;
        mPieChartButton.setOnClickListener(this::onClickChartTypeButton);
        mBarChartButton = viewBinding.btnBarChart;
        mBarChartButton.setOnClickListener(this::onClickChartTypeButton);
        mLineChartButton = viewBinding.btnLineChart;
        mLineChartButton.setOnClickListener(this::onClickChartTypeButton);
        mBalanceSheetButton = viewBinding.btnBalanceSheet;
        mBalanceSheetButton.setOnClickListener(this::onClickChartTypeButton);

        mChart = viewBinding.pieChart;
        mTotalAssets = viewBinding.totalAssets;
        mTotalLiabilities = viewBinding.totalLiabilities;
        mNetWorth = viewBinding.netWorth;
        return viewBinding;
    }

    @Override
    public int getTitle() {
        return R.string.title_reports;
    }

    @Override
    public ReportType getReportType() {
        return ReportType.NONE;
    }

    @Override
    public boolean requiresAccountTypeOptions() {
        return false;
    }

    @Override
    public boolean requiresTimeRangeOptions() {
        return false;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setHasOptionsMenu(false);

        mChart.setCenterTextSize(PieChartFragment.CENTER_TEXT_SIZE);
        mChart.setDescription("");
        mChart.setDrawSliceText(false);
        Legend legend = mChart.getLegend();
        legend.setEnabled(true);
        legend.setWordWrapEnabled(true);
        legend.setForm(LegendForm.CIRCLE);
        legend.setPosition(LegendPosition.RIGHT_OF_CHART_CENTER);
        legend.setTextSize(LEGEND_TEXT_SIZE);

        ColorStateList csl = new ColorStateList(new int[][]{new int[0]}, new int[]{ContextCompat.getColor(getContext(), R.color.account_green)});
        setButtonTint(mPieChartButton, csl);
        csl = new ColorStateList(new int[][]{new int[0]}, new int[]{ContextCompat.getColor(getContext(), R.color.account_red)});
        setButtonTint(mBarChartButton, csl);
        csl = new ColorStateList(new int[][]{new int[0]}, new int[]{ContextCompat.getColor(getContext(), R.color.account_blue)});
        setButtonTint(mLineChartButton, csl);
        csl = new ColorStateList(new int[][]{new int[0]}, new int[]{ContextCompat.getColor(getContext(), R.color.account_purple)});
        setButtonTint(mBalanceSheetButton, csl);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.menu_group_reports_by).setVisible(false);
    }

    @Override
    protected void generateReport() {
        PieData pieData = PieChartFragment.groupSmallerSlices(getData(), getActivity());
        if (pieData.getYValCount() != 0) {
            mChart.setData(pieData);
            float sum = mChart.getData().getYValueSum();
            String total = getResources().getString(R.string.label_chart_total);
            String currencySymbol = mCommodity.getSymbol();
            mChart.setCenterText(String.format(Locale.getDefault(), PieChartFragment.TOTAL_VALUE_LABEL_PATTERN, total, sum, currencySymbol));
            mChartHasData = true;
        } else {
            mChart.setData(getEmptyData());
            mChart.setCenterText(getResources().getString(R.string.label_chart_no_data));
            mChart.getLegend().setEnabled(false);
            mChartHasData = false;
        }

        List<AccountType> accountTypes = new ArrayList<>();
        accountTypes.add(AccountType.ASSET);
        accountTypes.add(AccountType.CASH);
        accountTypes.add(AccountType.BANK);
        mAssetsBalance = mAccountsDbAdapter.getAccountBalance(accountTypes, -1, System.currentTimeMillis());

        accountTypes.clear();
        accountTypes.add(AccountType.LIABILITY);
        accountTypes.add(AccountType.CREDIT);
        mLiabilitiesBalance = mAccountsDbAdapter.getAccountBalance(accountTypes, -1, System.currentTimeMillis());
    }

    /**
     * Returns {@code PieData} instance with data entries, colors and labels
     *
     * @return {@code PieData} instance
     */
    private PieData getData() {
        PieDataSet dataSet = new PieDataSet(null, "");
        List<String> labels = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (Account account : mAccountsDbAdapter.getSimpleAccountList()) {
            if (account.getAccountType() == AccountType.EXPENSE
                    && !account.isPlaceholderAccount()
                    && account.getCommodity().equals(mCommodity)) {

                long start = new LocalDate().minusMonths(2).dayOfMonth().withMinimumValue().toDate().getTime();
                long end = new LocalDate().plusDays(1).toDate().getTime();
                double balance = mAccountsDbAdapter.getAccountsBalance(
                        Collections.singletonList(account.getUID()), start, end).asDouble();
                if (balance > 0) {
                    dataSet.addEntry(new Entry((float) balance, dataSet.getEntryCount()));
                    colors.add(account.getColor() != Account.DEFAULT_COLOR
                            ? account.getColor()
                            : ReportsActivity.COLORS[(dataSet.getEntryCount() - 1) % ReportsActivity.COLORS.length]);
                    labels.add(account.getName());
                }
            }
        }
        dataSet.setColors(colors);
        dataSet.setSliceSpace(PieChartFragment.SPACE_BETWEEN_SLICES);
        return new PieData(labels, dataSet);
    }

    @Override
    protected void displayReport() {
        if (mChartHasData) {
            mChart.animateXY(1800, 1800);
            mChart.setTouchEnabled(true);
        } else {
            mChart.setTouchEnabled(false);
        }
        mChart.highlightValues(null);
        mChart.invalidate();

        TransactionsActivity.displayBalance(mTotalAssets, mAssetsBalance);
        TransactionsActivity.displayBalance(mTotalLiabilities, mLiabilitiesBalance);
        TransactionsActivity.displayBalance(mNetWorth, mAssetsBalance.subtract(mLiabilitiesBalance));
    }

    /**
     * Returns a data object that represents situation when no user data available
     *
     * @return a {@code PieData} instance for situation when no user data available
     */
    private PieData getEmptyData() {
        PieDataSet dataSet = new PieDataSet(null, getResources().getString(R.string.label_chart_no_data));
        dataSet.addEntry(new Entry(1, 0));
        dataSet.setColor(PieChartFragment.NO_DATA_COLOR);
        dataSet.setDrawValues(false);
        return new PieData(Collections.singletonList(""), dataSet);
    }

    public void onClickChartTypeButton(View view) {
        BaseReportFragment fragment;
        if (view.getId() == R.id.btn_pie_chart) {
            fragment = new PieChartFragment();
        } else if (view.getId() == R.id.btn_bar_chart) {
            fragment = new StackedBarChartFragment();
        } else if (view.getId() == R.id.btn_line_chart) {
            fragment = new CashFlowLineChartFragment();
        } else if (view.getId() == R.id.btn_balance_sheet) {
            fragment = new BalanceSheetFragment();
        } else {
            fragment = this;
        }
        FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    public void setButtonTint(Button button, ColorStateList tint) {
        ViewCompat.setBackgroundTintList(button, tint);
        button.setTextColor(ContextCompat.getColor(getContext(), android.R.color.white));
    }

}
