/*
 * Copyright (c) 2015 Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
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

package org.gnucash.android.ui.report.barchart;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.LargeValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;

import org.gnucash.android.R;
import org.gnucash.android.databinding.FragmentBarChartBinding;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.ui.report.BaseReportFragment;
import org.gnucash.android.ui.report.ReportType;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * Activity used for drawing a bar chart
 *
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class StackedBarChartFragment extends BaseReportFragment {

    private static final String X_AXIS_MONTH_PATTERN = "MMM YY";
    private static final String X_AXIS_QUARTER_PATTERN = "Q%d %s";
    private static final String X_AXIS_YEAR_PATTERN = "YYYY";

    private static final int ANIMATION_DURATION = 2000;
    private static final int NO_DATA_BAR_COUNTS = 3;

    private boolean mTotalPercentageMode = true;
    private boolean mChartDataPresent = true;

    private FragmentBarChartBinding mBinding;

    @Override
    public View inflateView(LayoutInflater inflater, ViewGroup container) {
        mBinding = FragmentBarChartBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public ReportType getReportType() {
        return ReportType.BAR_CHART;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final Context context = mBinding.barChart.getContext();

        @ColorInt int textColorPrimary = getTextColor(context);

        mBinding.barChart.setOnChartValueSelectedListener(this);
        mBinding.barChart.setDescription("");
        mBinding.barChart.getXAxis().setDrawGridLines(false);
        mBinding.barChart.getXAxis().setTextColor(textColorPrimary);
        mBinding.barChart.getAxisRight().setEnabled(false);
        mBinding.barChart.getAxisLeft().setStartAtZero(false);
        mBinding.barChart.getAxisLeft().enableGridDashedLine(4.0f, 4.0f, 0);
        mBinding.barChart.getAxisLeft().setValueFormatter(new LargeValueFormatter(mCommodity.getSymbol()));
        mBinding.barChart.getAxisLeft().setTextColor(textColorPrimary);
        Legend legend = mBinding.barChart.getLegend();
        legend.setTextColor(textColorPrimary);
        legend.setWordWrapEnabled(true);
    }


    /**
     * Returns a data object that represents a user data of the specified account types
     *
     * @return a {@code BarData} instance that represents a user data
     */
    protected BarData getData(@NonNull Context context) {
        List<BarEntry> values = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        Map<String, Integer> accountToColorMap = new LinkedHashMap<>();
        List<String> xValues = new ArrayList<>();
        LocalDateTime tmpDate = new LocalDateTime(getStartDate(mAccountType).toDate().getTime());
        int count = getDateDiff(new LocalDateTime(getStartDate(mAccountType).toDate().getTime()),
                new LocalDateTime(getEndDate(mAccountType).toDate().getTime()));
        for (int i = 0; i <= count; i++) {
            long start = 0;
            long end = 0;
            switch (mGroupInterval) {
                case MONTH:
                    start = tmpDate.dayOfMonth().withMinimumValue().millisOfDay().withMinimumValue().toDateTime().getMillis();
                    end = tmpDate.dayOfMonth().withMaximumValue().millisOfDay().withMaximumValue().toDateTime().getMillis();

                    xValues.add(tmpDate.toString(X_AXIS_MONTH_PATTERN));
                    tmpDate = tmpDate.plusMonths(1);
                    break;
                case QUARTER:
                    int quarter = getQuarter(tmpDate);
                    start = tmpDate.withMonthOfYear(quarter * 3 - 2).dayOfMonth().withMinimumValue().millisOfDay().withMinimumValue().toDateTime().getMillis();
                    end = tmpDate.withMonthOfYear(quarter * 3).dayOfMonth().withMaximumValue().millisOfDay().withMaximumValue().toDateTime().getMillis();

                    xValues.add(String.format(X_AXIS_QUARTER_PATTERN, quarter, tmpDate.toString(" YY")));
                    tmpDate = tmpDate.plusMonths(3);
                    break;
                case YEAR:
                    start = tmpDate.dayOfYear().withMinimumValue().millisOfDay().withMinimumValue().toDateTime().getMillis();
                    end = tmpDate.dayOfYear().withMaximumValue().millisOfDay().withMaximumValue().toDateTime().getMillis();

                    xValues.add(tmpDate.toString(X_AXIS_YEAR_PATTERN));
                    tmpDate = tmpDate.plusYears(1);
                    break;
            }
            List<Float> stack = new ArrayList<>();
            for (Account account : mAccountsDbAdapter.getSimpleAccountList()) {
                if (account.getAccountType() == mAccountType
                        && !account.isPlaceholderAccount()
                        && account.getCommodity().equals(mCommodity)) {

                    float balance = mAccountsDbAdapter.getAccountBalance(account.getUID(), start, end).toFloat();
                    if (balance != 0) {
                        stack.add(balance);

                        String accountName = account.getName();
                        while (labels.contains(accountName)) {
                            if (!accountToColorMap.containsKey(account.getUID())) {
                                for (String label : labels) {
                                    if (label.equals(accountName)) {
                                        accountName += " ";
                                    }
                                }
                            } else {
                                break;
                            }
                        }
                        labels.add(accountName);

                        if (!accountToColorMap.containsKey(account.getUID())) {
                            @ColorInt int color;
                            if (mUseAccountColor) {
                                color = (account.getColor() != Account.DEFAULT_COLOR)
                                        ? account.getColor()
                                        : COLORS[accountToColorMap.size() % COLORS.length];
                            } else {
                                color = COLORS[accountToColorMap.size() % COLORS.length];
                            }
                            accountToColorMap.put(account.getUID(), color);
                        }
                        colors.add(accountToColorMap.get(account.getUID()));

                        Timber.d(mAccountType + tmpDate.toString(" MMMM yyyy ") + account.getName() + " = " + stack.get(stack.size() - 1));
                    }
                }
            }

            String stackLabels = labels.subList(labels.size() - stack.size(), labels.size()).toString();
            values.add(new BarEntry(floatListToArray(stack), i, stackLabels));
        }

        BarDataSet set = new BarDataSet(values, "");
        set.setDrawValues(false);
        set.setStackLabels(labels.toArray(new String[0]));
        set.setColors(colors);

        if (getYValueSum(set) == 0) {
            mChartDataPresent = false;
            return getEmptyData(context);
        }
        mChartDataPresent = true;
        return new BarData(xValues, set);
    }

    /**
     * Returns a data object that represents situation when no user data available
     *
     * @return a {@code BarData} instance for situation when no user data available
     */
    private BarData getEmptyData(@NonNull Context context) {
        List<String> xValues = new ArrayList<>();
        List<BarEntry> yValues = new ArrayList<>();
        for (int i = 0; i < NO_DATA_BAR_COUNTS; i++) {
            xValues.add("");
            yValues.add(new BarEntry(i + 1, i));
        }
        BarDataSet set = new BarDataSet(yValues, context.getString(R.string.label_chart_no_data));
        set.setDrawValues(false);
        set.setColor(NO_DATA_COLOR);

        return new BarData(xValues, set);
    }

    /**
     * Returns the start data of x-axis for the specified account type
     *
     * @param accountType account type
     * @return the start data
     */
    private LocalDate getStartDate(AccountType accountType) {
        LocalDate startDate;
        if (mReportPeriodStart == -1) {
            TransactionsDbAdapter adapter = TransactionsDbAdapter.getInstance();
            String currencyCode = mCommodity.getCurrencyCode();
            startDate = new LocalDate(adapter.getTimestampOfEarliestTransaction(accountType, currencyCode));
        } else {
            startDate = new LocalDate(mReportPeriodStart);
        }
        startDate = startDate.withDayOfMonth(1);
        Timber.d(accountType + " X-axis start date: " + startDate.toString("dd MM yyyy"));
        return startDate;
    }

    /**
     * Returns the end data of x-axis for the specified account type
     *
     * @param accountType account type
     * @return the end data
     */
    private LocalDate getEndDate(AccountType accountType) {
        LocalDate endDate;
        if (mReportPeriodEnd == -1) {
            TransactionsDbAdapter adapter = TransactionsDbAdapter.getInstance();
            String currencyCode = mCommodity.getCurrencyCode();
            endDate = new LocalDate(adapter.getTimestampOfLatestTransaction(accountType, currencyCode));
        } else {
            endDate = new LocalDate(mReportPeriodEnd);
        }
        endDate = endDate.withDayOfMonth(1);
        Timber.d(accountType + " X-axis end date: " + endDate.toString("dd MM yyyy"));
        return endDate;
    }

    /**
     * Converts the specified list of floats to an array
     *
     * @param list a list of floats
     * @return a float array
     */
    private float[] floatListToArray(List<Float> list) {
        final int size = list.size();
        float[] array = new float[size];
        for (int i = 0; i < size; i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    @Override
    public void generateReport(@NonNull Context context) {
        mBinding.barChart.setData(getData(context));
        setCustomLegend();

        mBinding.barChart.getAxisLeft().setDrawLabels(mChartDataPresent);
        mBinding.barChart.getXAxis().setDrawLabels(mChartDataPresent);
        mBinding.barChart.setTouchEnabled(mChartDataPresent);
    }

    @Override
    protected void displayReport() {
        mBinding.barChart.notifyDataSetChanged();
        mBinding.barChart.highlightValues(null);
        if (mChartDataPresent) {
            mBinding.barChart.animateY(ANIMATION_DURATION);
        } else {
            mBinding.barChart.clearAnimation();
            mSelectedValueTextView.setText(R.string.label_chart_no_data);
        }

        mBinding.barChart.invalidate();
    }

    /**
     * Sets custom legend. Disable legend if its items count greater than {@code COLORS} array size.
     */
    private void setCustomLegend() {
        Legend legend = mBinding.barChart.getLegend();
        IBarDataSet dataSet = mBinding.barChart.getData().getDataSetByIndex(0);

        List<Integer> colors = dataSet.getColors();
        List<String> labels = Arrays.asList(dataSet.getStackLabels());

        if (colors.size() == labels.size()) {
            legend.setCustom(colors, labels);
            return;
        }
        legend.setEnabled(false);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.menu_percentage_mode).setVisible(mChartDataPresent);
        // hide pie/line chart specific menu items
        menu.findItem(R.id.menu_order_by_size).setVisible(false);
        menu.findItem(R.id.menu_toggle_labels).setVisible(false);
        menu.findItem(R.id.menu_toggle_average_lines).setVisible(false);
        menu.findItem(R.id.menu_group_other_slice).setVisible(false);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.isCheckable()) {
            item.setChecked(!item.isChecked());
        }
        final Context context = mBinding.barChart.getContext();
        switch (item.getItemId()) {
            case R.id.menu_toggle_legend:
                Legend legend = mBinding.barChart.getLegend();
                if (!legend.isLegendCustom()) {
                    Toast.makeText(context, R.string.toast_legend_too_long, Toast.LENGTH_LONG).show();
                    item.setChecked(false);
                } else {
                    item.setChecked(!mBinding.barChart.getLegend().isEnabled());
                    legend.setEnabled(!mBinding.barChart.getLegend().isEnabled());
                    mBinding.barChart.invalidate();
                }
                return true;

            case R.id.menu_percentage_mode:
                mTotalPercentageMode = !mTotalPercentageMode;
                @StringRes int msgId = mTotalPercentageMode ? R.string.toast_chart_percentage_mode_total
                        : R.string.toast_chart_percentage_mode_current_bar;
                Toast.makeText(context, msgId, Toast.LENGTH_LONG).show();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onValueSelected(Entry e, int dataSetIndex, Highlight h) {
        if (e == null || ((BarEntry) e).getVals().length == 0) return;
        BarEntry entry = (BarEntry) e;
        int index = h.getStackIndex() == -1 ? 0 : h.getStackIndex();
        String stackLabels = entry.getData().toString();
        String label = mBinding.barChart.getData().getXVals().get(entry.getXIndex()) + ", "
                + stackLabels.substring(1, stackLabels.length() - 1).split(",")[index];
        double value = Math.abs(entry.getVals()[index]);
        double sum = 0;
        if (mTotalPercentageMode) {
            for (BarEntry barEntry : getYVals(mBinding.barChart.getData().getDataSetByIndex(dataSetIndex))) {
                sum += barEntry.getNegativeSum() + barEntry.getPositiveSum();
            }
        } else {
            sum = entry.getNegativeSum() + entry.getPositiveSum();
        }
        mSelectedValueTextView.setText(String.format(SELECTED_VALUE_PATTERN, label.trim(), value, (value * 100) / sum));
    }
}
