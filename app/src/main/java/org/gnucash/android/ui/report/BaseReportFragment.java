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

import static org.gnucash.android.util.ColorExtKt.getTextColorPrimary;
import static org.gnucash.android.util.ColorExtKt.parseColor;
import static java.lang.Math.max;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.data.ChartData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.IDataSet;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import org.gnucash.android.R;
import org.gnucash.android.app.MenuFragment;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.PricesDbAdapter;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.ui.common.BaseDrawerActivity;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.util.DateExtKt;
import org.joda.time.LocalDateTime;
import org.joda.time.Months;
import org.joda.time.Years;

import java.lang.ref.WeakReference;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Base class for report fragments.
 * <p>All report fragments should extend this class. At the minimum, reports must implement
 * {@link #getReportType()}, {@link #generateReport(Context)}, {@link #displayReport()} and {@link #getTitle()}</p>
 * <p>Implementing classes should create their own XML layouts and inflate it in {@link #inflateView(LayoutInflater, ViewGroup)}.
 * </p>
 * <p>Any custom information to be initialized for the report should be done in {@link #onActivityCreated(Bundle)} in implementing classes.
 * The report is then generated in {@link #onStart()}
 * </p>
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public abstract class BaseReportFragment extends MenuFragment implements
    OnChartValueSelectedListener, ReportOptionsListener, Refreshable {

    /**
     * Color for chart with no data
     */
    public static final int NO_DATA_COLOR = Color.LTGRAY;

    protected static final int[] COLORS = {
        parseColor("#17ee4e"), parseColor("#cc1f09"), parseColor("#3940f7"),
        parseColor("#f9cd04"), parseColor("#5f33a8"), parseColor("#e005b6"),
        parseColor("#17d6ed"), parseColor("#e4a9a2"), parseColor("#8fe6cd"),
        parseColor("#8b48fb"), parseColor("#343a36"), parseColor("#6decb1"),
        parseColor("#f0f8ff"), parseColor("#5c3378"), parseColor("#a6dcfd"),
        parseColor("#ba037c"), parseColor("#708809"), parseColor("#32072c"),
        parseColor("#fddef8"), parseColor("#fa0e6e"), parseColor("#d9e7b5")
    };

    /**
     * Reporting period start time
     */
    @Nullable
    protected LocalDateTime mReportPeriodStart = null;
    /**
     * Reporting period end time
     */
    @Nullable
    protected LocalDateTime mReportPeriodEnd = null;

    /**
     * Account type for which to display reports
     */
    protected AccountType mAccountType = AccountType.EXPENSE;
    protected AccountsDbAdapter mAccountsDbAdapter;
    protected PricesDbAdapter pricesDbAdapter = PricesDbAdapter.getInstance();
    protected boolean mUseAccountColor = true;

    /**
     * Commodity for which to display reports
     */
    protected Commodity mCommodity = Commodity.DEFAULT_COMMODITY;

    /**
     * Intervals in which to group reports
     */
    protected ReportsActivity.GroupInterval mGroupInterval = ReportsActivity.GroupInterval.MONTH;

    /**
     * Pattern to use to display selected chart values
     */
    private static final String SELECTED_VALUE_PATTERN = "%s — %s %s (%.2f%%)";
    private static final String TOTAL_VALUE_LABEL_PATTERN = "%s\n%s %s";

    protected ReportsActivity mReportsActivity;

    protected TextView mSelectedValueTextView;

    @Nullable
    private GeneratorTask mReportGenerator;

    /**
     * Return the title of this report
     *
     * @return Title string identifier
     */
    @StringRes
    public int getTitle() {
        return getReportType().titleId;
    }

    /**
     * Returns what kind of report this is
     *
     * @return Type of report
     */
    public abstract ReportType getReportType();

    /**
     * Return {@code true} if this report fragment requires account type options.
     * <p>Sub-classes should implement this method. The base implementation returns {@code true}</p>
     *
     * @return {@code true} if the fragment makes use of account type options, {@code false} otherwise
     */
    public boolean requiresAccountTypeOptions() {
        return true;
    }

    /**
     * Return {@code true} if this report fragment requires time range options.
     * <p>Base implementation returns true</p>
     *
     * @return {@code true} if the report fragment requires time range options, {@code false} otherwise
     */
    public boolean requiresTimeRangeOptions() {
        return true;
    }

    /**
     * Generates the data for the report
     * <p>This method should not call any methods which modify the UI as it will be run in a background thread
     * <br>Put any code to update the UI in {@link #displayReport()}
     * </p>
     */
    protected abstract void generateReport(@NonNull Context context);

    /**
     * Update the view after the report chart has been generated <br/>
     * Sub-classes should call to the base method
     */
    protected abstract void displayReport();

    protected abstract View inflateView(LayoutInflater inflater, ViewGroup container);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflateView(inflater, container);
        mSelectedValueTextView = view.findViewById(R.id.selected_chart_slice);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        assert actionBar != null;
        actionBar.setTitle(getTitle());
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAccountsDbAdapter = AccountsDbAdapter.getInstance();
        mUseAccountColor = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getBoolean(getString(R.string.key_use_account_color), false);
    }

    @Override
    public void onStart() {
        super.onStart();
        pricesDbAdapter = PricesDbAdapter.getInstance();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        ReportsActivity reportsActivity = (ReportsActivity) requireActivity();
        mReportPeriodStart = reportsActivity.getReportPeriodStart();
        mReportPeriodEnd = reportsActivity.getReportPeriodEnd();
        mAccountType = reportsActivity.getAccountType();
    }

    @Override
    public void onResume() {
        super.onResume();

        Activity activity = getActivity();
        if (activity instanceof ReportsActivity) {
            mReportsActivity = (ReportsActivity) activity;
        } else {
            throw new RuntimeException("Report fragments can only be used with the ReportsActivity");
        }
        mReportsActivity.onFragmentResumed(this);
        toggleBaseReportingOptionsVisibility(mReportsActivity);
        mCommodity = Commodity.DEFAULT_COMMODITY;
        refresh();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (mReportGenerator != null)
            mReportGenerator.cancel(true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mReportGenerator != null) {
            mReportGenerator.cancel(true);
            mReportGenerator = null;
        }
    }

    private void toggleBaseReportingOptionsVisibility(ReportsActivity activity) {
        View timeRangeLayout = activity.findViewById(R.id.time_range_layout);
        if (timeRangeLayout != null) {
            int visibility = requiresTimeRangeOptions() ? View.VISIBLE : View.GONE;
            timeRangeLayout.setVisibility(visibility);
        }
    }

    /**
     * Calculates difference between two date values accordingly to {@code mGroupInterval}
     *
     * @param start start date
     * @param end   end date
     * @return difference between two dates or {@code -1}
     */
    protected int getDateDiff(
        @NonNull ReportsActivity.GroupInterval groupInterval,
        @NonNull LocalDateTime start,
        @NonNull LocalDateTime end
    ) {
        start = start.withMillisOfDay(0);
        end = end.withMillisOfDay(0);
        switch (groupInterval) {
            case MONTH:
                return max(1, Months.monthsBetween(start, end).getMonths());
            case QUARTER:
                start = start.withMonthOfYear(DateExtKt.getFirstQuarterMonth(start)).dayOfMonth().withMinimumValue();
                int m = Months.monthsBetween(start, end).getMonths();
                int q = m / 3;
                if (m % 3 > 0) q++;
                return max(1, q);
            case YEAR:
                return max(1, Years.yearsBetween(start, end).getYears());
            default:
                return -1;
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.chart_actions, menu);
    }

    @Override
    public void refresh() {
        if (mReportGenerator != null) {
            mReportGenerator.cancel(true);
        }
        mReportGenerator = new GeneratorTask(mReportsActivity);
        mReportGenerator.execute();
    }

    /**
     * Charts do not support account specific refreshes in general.
     * So we provide a base implementation which just calls {@link #refresh()}
     *
     * @param uid GUID of relevant item to be refreshed
     */
    @Override
    public void refresh(String uid) {
        refresh();
    }

    @Override
    public void onGroupingUpdated(@NonNull ReportsActivity.GroupInterval groupInterval) {
        if (mGroupInterval != groupInterval) {
            mGroupInterval = groupInterval;
            refresh();
        }
    }

    @Override
    public void onTimeRangeUpdated(@Nullable LocalDateTime start, @Nullable LocalDateTime end) {
        mReportPeriodStart = start;
        mReportPeriodEnd = end;
        refresh();
    }

    @Override
    public void onAccountTypeUpdated(@NonNull AccountType accountType) {
        if (mAccountType != accountType) {
            mAccountType = accountType;
            refresh();
        }
    }

    @Override
    public void onValueSelected(Entry e, Highlight h) {
        //nothing to see here, move along
    }

    @Override
    public void onNothingSelected() {
        if (mSelectedValueTextView != null)
            mSelectedValueTextView.setText(R.string.select_chart_to_view_details);
    }

    protected String formatSelectedValue(String label, float value, float percentage) {
        return formatSelectedValue(Locale.getDefault(), label.trim(), value, mCommodity, percentage);
    }

    @VisibleForTesting
    public static String formatSelectedValue(Locale locale, String label, float value, Commodity commodity, float percentage) {
        NumberFormat formatter = NumberFormat.getNumberInstance(locale);
        formatter.setMinimumFractionDigits(0);
        formatter.setMaximumFractionDigits(commodity.getSmallestFractionDigits());
        String currencySymbol = commodity.getSymbol();
        return String.format(locale, SELECTED_VALUE_PATTERN, label.trim(), formatter.format(value), currencySymbol, percentage);
    }

    protected String formatTotalValue(float value) {
        return formatTotalValue(requireContext(), Locale.getDefault(), value, mCommodity);
    }

    protected String getLabel(@NonNull Context context, @NonNull AccountType accountType) {
        String[] labels = context.getResources().getStringArray(R.array.account_type_entry_values);
        return labels[accountType.labelIndex];
    }

    @VisibleForTesting
    //TODO get locale from context.
    public static String formatTotalValue(Context context, Locale locale, float value, Commodity commodity) {
        String label = context.getString(R.string.label_chart_total);
        NumberFormat formatter = NumberFormat.getNumberInstance(locale);
        formatter.setMinimumFractionDigits(0);
        formatter.setMaximumFractionDigits(commodity.getSmallestFractionDigits());
        String currencySymbol = commodity.getSymbol();
        return String.format(locale, TOTAL_VALUE_LABEL_PATTERN, label, formatter.format(value), currencySymbol);
    }

    protected static <E extends Entry, T extends IDataSet<E>> float getYValueSum(ChartData<T> data) {
        return data.getYMax() - data.getYMin();
    }

    protected static <E extends Entry> float getYValueSum(IDataSet<E> dataSet) {
        return dataSet.getYMax() - dataSet.getYMin();
    }

    @ColorInt
    protected int getTextColor(@NonNull Context context) {
        return getTextColorPrimary(context);
    }

    @ColorInt
    protected int getAccountColor(@NonNull Account account, int count) {
        @ColorInt int color;
        if (mUseAccountColor) {
            color = (account.getColor() != Account.DEFAULT_COLOR)
                ? account.getColor()
                : COLORS[count % COLORS.length];
        } else {
            color = COLORS[count % COLORS.length];
        }
        return color;
    }

    private class GeneratorTask extends AsyncTask<Void, Void, Void> {

        private final WeakReference<ReportsActivity> activityRef;

        private GeneratorTask(ReportsActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }

        @Override
        protected void onPreExecute() {
            BaseDrawerActivity activity = this.activityRef.get();
            assert activity != null;
            activity.showProgressBar(true);
        }

        @Override
        protected Void doInBackground(Void... params) {
            BaseDrawerActivity activity = this.activityRef.get();
            if (activity != null) {
                // FIXME return data to be displayed.
                generateReport(activity);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            BaseDrawerActivity activity = this.activityRef.get();
            if (activity != null) {
                // FIXME display the result data that was generated.
                displayReport();
                activity.showProgressBar(false);
            }
        }
    }
}
