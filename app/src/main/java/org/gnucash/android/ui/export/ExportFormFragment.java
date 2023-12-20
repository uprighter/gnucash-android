/*
 * Copyright (c) 2012-2013 Ngewi Fet <ngewif@gmail.com>
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

package org.gnucash.android.ui.export;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.codetroopers.betterpickers.calendardatepicker.CalendarDatePickerDialogFragment;
import com.codetroopers.betterpickers.radialtimepicker.RadialTimePickerDialogFragment;
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrence;
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrenceFormatter;
import com.codetroopers.betterpickers.recurrencepicker.RecurrencePickerDialogFragment;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.databinding.FragmentExportFormBinding;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter;
import org.gnucash.android.export.ExportAsyncTask;
import org.gnucash.android.export.ExportFormat;
import org.gnucash.android.export.ExportParams;
import org.gnucash.android.export.Exporter;
import org.gnucash.android.model.BaseModel;
import org.gnucash.android.model.ScheduledAction;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.transaction.TransactionFormFragment;
import org.gnucash.android.ui.util.RecurrenceParser;
import org.gnucash.android.ui.util.RecurrenceViewClickListener;
import org.gnucash.android.util.PreferencesHelper;
import org.gnucash.android.util.TimestampHelper;

import java.sql.Timestamp;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Objects;

/**
 * Dialog fragment for exporting accounts and transactions in various formats
 * <p>The dialog is used for collecting information on the export options and then passing them
 * to the {@link org.gnucash.android.export.Exporter} responsible for exporting</p>
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class ExportFormFragment extends Fragment implements
        RecurrencePickerDialogFragment.OnRecurrenceSetListener,
        CalendarDatePickerDialogFragment.OnDateSetListener,
        RadialTimePickerDialogFragment.OnTimeSetListener {
    private FragmentExportFormBinding mBinding;

    /**
     * Spinner for selecting destination for the exported file.
     * The destination could either be SD card, or another application which
     * accepts files, like Google Drive.
     */
    Spinner mDestinationSpinner;

    /**
     * Checkbox for deleting all transactions after exporting them
     */
    CheckBox mDeleteAllCheckBox;

    /**
     * Text view for showing warnings based on chosen export format
     */
    TextView mExportWarningTextView;

    TextView mTargetUriTextView;

    /**
     * Recurrence text view
     */
    TextView mRecurrenceTextView;

    /**
     * Text view displaying start date to export from
     */
    TextView mExportStartDate;

    TextView mExportStartTime;

    /**
     * Switch toggling whether to export all transactions or not
     */
    SwitchCompat mExportAllSwitch;

    LinearLayout mExportDateLayout;

    RadioButton mOfxRadioButton;
    RadioButton mQifRadioButton;
    RadioButton mXmlRadioButton;
    RadioButton mCsvTransactionsRadioButton;

    RadioButton mSeparatorCommaButton;
    RadioButton mSeparatorColonButton;
    RadioButton mSeparatorSemicolonButton;
    LinearLayout mCsvOptionsLayout;

    View mRecurrenceOptionsView;
    /**
     * Event recurrence options
     */
    private final EventRecurrence mEventRecurrence = new EventRecurrence();

    /**
     * Recurrence rule
     */
    private String mRecurrenceRule;

    private final Calendar mExportStartCalendar = Calendar.getInstance();

    /**
     * Tag for logging
     */
    private static final String LOG_TAG = "ExportFormFragment";

    /**
     * Export format
     */
    private ExportFormat mExportFormat = ExportFormat.QIF;

    private ExportParams.ExportTarget mExportTarget = ExportParams.ExportTarget.SD_CARD;

    /**
     * The Uri target for the export
     */
    private Uri mExportUri;

    private char mExportCsvSeparator = ',';

    /**
     * Flag to determine if export has been started.
     * Used to continue export after user has picked a destination file
     */
    private boolean mExportStarted = false;

    private final ActivityResultLauncher<Intent> exportFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Log.d(LOG_TAG, "launch exportFileIntent: result = " + result);
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null) {
                        mExportUri = data.getData();
                    }

                    final int takeFlags;
                    if ((Objects.requireNonNull(data).getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) == Intent.FLAG_GRANT_READ_URI_PERMISSION) {
                        takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                    } else if ((data.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == Intent.FLAG_GRANT_WRITE_URI_PERMISSION) {
                        takeFlags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                    } else {
                        takeFlags = 0;
                    }
                    requireActivity().getContentResolver().takePersistableUriPermission(mExportUri, takeFlags);

                    mTargetUriTextView.setText(mExportUri.toString());
                    if (mExportStarted) {
                        startExport();
                    }
                }
            }
    );

    private void onRadioButtonClicked(View view) {
        if (view.getId() == R.id.radio_ofx_format) {
            mExportFormat = ExportFormat.OFX;
            if (GnuCashApplication.isDoubleEntryEnabled()) {
                mExportWarningTextView.setText(requireActivity().getString(R.string.export_warning_ofx));
                mExportWarningTextView.setVisibility(View.VISIBLE);
            } else {
                mExportWarningTextView.setVisibility(View.GONE);
            }

            OptionsViewAnimationUtils.expand(mExportDateLayout);
            OptionsViewAnimationUtils.collapse(mCsvOptionsLayout);
        } else if (view.getId() == R.id.radio_qif_format) {
            mExportFormat = ExportFormat.QIF;
            //TODO: Also check that there exist transactions with multiple currencies before displaying warning
            if (GnuCashApplication.isDoubleEntryEnabled()) {
                mExportWarningTextView.setText(requireActivity().getString(R.string.export_warning_qif));
                mExportWarningTextView.setVisibility(View.VISIBLE);
            } else {
                mExportWarningTextView.setVisibility(View.GONE);
            }

            OptionsViewAnimationUtils.expand(mExportDateLayout);
            OptionsViewAnimationUtils.collapse(mCsvOptionsLayout);
        } else if (view.getId() == R.id.radio_xml_format) {
            mExportFormat = ExportFormat.XML;
            mExportWarningTextView.setText(R.string.export_warning_xml);
            OptionsViewAnimationUtils.collapse(mExportDateLayout);
            OptionsViewAnimationUtils.collapse(mCsvOptionsLayout);
        } else if (view.getId() == R.id.radio_csv_transactions_format) {
            mExportFormat = ExportFormat.CSVT;
            mExportWarningTextView.setText(R.string.export_notice_csv);
            OptionsViewAnimationUtils.expand(mExportDateLayout);
            OptionsViewAnimationUtils.expand(mCsvOptionsLayout);
        } else if (view.getId() == R.id.radio_separator_comma_format) {
            mExportCsvSeparator = ',';
        } else if (view.getId() == R.id.radio_separator_colon_format) {
            mExportCsvSeparator = ':';
        } else if (view.getId() == R.id.radio_separator_semicolon_format) {
            mExportCsvSeparator = ';';
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mBinding = FragmentExportFormBinding.inflate(inflater, container, false);

        bindViews();

        bindViewListeners();

        return mBinding.getRoot();
    }

    private void bindViews() {
        mDestinationSpinner = mBinding.spinnerExportDestination;
        mDeleteAllCheckBox = mBinding.checkboxPostExportDelete;
        mExportWarningTextView = mBinding.exportWarning;
        mTargetUriTextView = mBinding.targetUri;
        mRecurrenceTextView = mBinding.inputRecurrence;
        mExportStartDate = mBinding.exportStartDate;
        mExportStartTime = mBinding.exportStartTime;
        mExportAllSwitch = mBinding.switchExportAll;
        mExportDateLayout = mBinding.exportDateLayout;
        mOfxRadioButton = mBinding.radioOfxFormat;
        mQifRadioButton = mBinding.radioQifFormat;
        mXmlRadioButton = mBinding.radioXmlFormat;
        mCsvTransactionsRadioButton = mBinding.radioCsvTransactionsFormat;
        mSeparatorCommaButton = mBinding.radioSeparatorCommaFormat;
        mSeparatorColonButton = mBinding.radioSeparatorColonFormat;
        mSeparatorSemicolonButton = mBinding.radioSeparatorSemicolonFormat;
        mCsvOptionsLayout = mBinding.layoutCsvOptions;
        mRecurrenceOptionsView = mBinding.recurrenceOptions;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mBinding = null;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.default_save_actions, menu);
        MenuItem menuItem = menu.findItem(R.id.menu_save);
        menuItem.setTitle(R.string.btn_export);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_save) {
            startExport();
            return true;
        } else if (item.getItemId() == android.R.id.home) {
            requireActivity().finish();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ActionBar supportActionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        assert supportActionBar != null;
        supportActionBar.setTitle(R.string.title_export_dialog);
        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        // When the user try to export sharing to 3rd party service
        // then pausing all activities. That cause passcode screen appearing happened.
        // We use a disposable flag to skip this unnecessary passcode screen.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireActivity());
        prefs.edit().putBoolean(UxArgument.SKIP_PASSCODE_SCREEN, true).apply();
    }

    /**
     * Starts the export of transactions with the specified parameters
     */
    private void startExport() {
        if (mExportTarget == ExportParams.ExportTarget.URI && mExportUri == null) {
            mExportStarted = true;
            selectExportFile();
            return;
        }

        ExportParams exportParameters = new ExportParams(mExportFormat);

        if (mExportAllSwitch.isChecked()) {
            exportParameters.setExportStartTime(TimestampHelper.getTimestampFromEpochZero());
        } else {
            exportParameters.setExportStartTime(new Timestamp(mExportStartCalendar.getTimeInMillis()));
        }

        exportParameters.setExportTarget(mExportTarget);
        exportParameters.setExportLocation(mExportUri != null ? mExportUri.toString() : null);
        exportParameters.setDeleteTransactionsAfterExport(mDeleteAllCheckBox.isChecked());
        exportParameters.setCsvSeparator(mExportCsvSeparator);

        Log.i(LOG_TAG, "Commencing async export of transactions");
        new ExportAsyncTask(getActivity(), GnuCashApplication.getActiveDb()).execute(exportParameters);

        if (mRecurrenceRule != null) {
            ScheduledAction scheduledAction = new ScheduledAction(ScheduledAction.ActionType.BACKUP);
            scheduledAction.setRecurrence(RecurrenceParser.parse(mEventRecurrence));
            scheduledAction.setTag(exportParameters.toCsv());
            scheduledAction.setActionUID(BaseModel.generateUID());
            ScheduledActionDbAdapter.getInstance().addRecord(scheduledAction, DatabaseAdapter.UpdateMethod.insert);
        }

        int position = mDestinationSpinner.getSelectedItemPosition();
        PreferenceManager.getDefaultSharedPreferences(requireActivity())
                .edit().putInt(getString(R.string.key_last_export_destination), position)
                .apply();

        // finish the activity will cause the progress dialog to be leaked
        // which would throw an exception
        //getActivity().finish();
    }

    /**
     * Bind views to actions when initializing the export form
     */
    private void bindViewListeners() {
        // export destination bindings
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(requireActivity(),
                R.array.export_destinations, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mDestinationSpinner.setAdapter(adapter);
        mDestinationSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (view == null) //the item selection is fired twice by the Android framework. Ignore the first one
                    return;
                switch (position) {
                    case 0 -> { //Save As..
                        mExportTarget = ExportParams.ExportTarget.URI;
                        mRecurrenceOptionsView.setVisibility(View.VISIBLE);
                        if (mExportUri != null) {
                            setExportUriText(mExportUri.toString());
                        }
                    }
                    case 1 -> { //Share File
                        setExportUriText(getString(R.string.label_select_destination_after_export));
                        mExportTarget = ExportParams.ExportTarget.SHARING;
                        mRecurrenceOptionsView.setVisibility(View.GONE);
                    }
                    default -> mExportTarget = ExportParams.ExportTarget.SD_CARD;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                //nothing to see here, move along
            }
        });

        int position = PreferenceManager.getDefaultSharedPreferences(requireActivity())
                .getInt(getString(R.string.key_last_export_destination), 0);
        mDestinationSpinner.setSelection(position);

        //**************** export start time bindings ******************
        Timestamp timestamp = PreferencesHelper.getLastExportTime();
        mExportStartCalendar.setTimeInMillis(timestamp.getTime());

        final Date date = new Date(timestamp.getTime());
        mExportStartDate.setText(TransactionFormFragment.DATE_FORMATTER.format(date));
        mExportStartTime.setText(TransactionFormFragment.TIME_FORMATTER.format(date));

        mExportStartDate.setOnClickListener(v -> {
            long dateMillis = 0;
            try {
                Date date1 = TransactionFormFragment.DATE_FORMATTER.parse(mExportStartDate.getText().toString());
                dateMillis = Objects.requireNonNull(date1).getTime();
            } catch (ParseException e) {
                Log.e(getTag(), "Error converting input time to Date object");
            }
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(dateMillis);

            int year = calendar.get(Calendar.YEAR);
            int monthOfYear = calendar.get(Calendar.MONTH);
            int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
            CalendarDatePickerDialogFragment datePickerDialog = new CalendarDatePickerDialogFragment();
            datePickerDialog.setOnDateSetListener(ExportFormFragment.this);
            datePickerDialog.setPreselectedDate(year, monthOfYear, dayOfMonth);
            datePickerDialog.show(getParentFragmentManager(), "date_picker_fragment");
        });

        mExportStartTime.setOnClickListener(v -> {
            long timeMillis = 0;
            try {
                Date date12 = TransactionFormFragment.TIME_FORMATTER.parse(mExportStartTime.getText().toString());
                timeMillis = Objects.requireNonNull(date12).getTime();
            } catch (ParseException e) {
                Log.e(getTag(), "Error converting input time to Date object");
            }

            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(timeMillis);

            RadialTimePickerDialogFragment timePickerDialog = new RadialTimePickerDialogFragment();
            timePickerDialog.setOnTimeSetListener(ExportFormFragment.this);
            timePickerDialog.setStartTime(calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE));
            timePickerDialog.show(getParentFragmentManager(), "time_picker_dialog_fragment");
        });

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireActivity());
        mExportAllSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mExportStartDate.setEnabled(!isChecked);
            mExportStartTime.setEnabled(!isChecked);
            int color = isChecked ? android.R.color.darker_gray : android.R.color.black;
            mExportStartDate.setTextColor(ContextCompat.getColor(requireContext(), color));
            mExportStartTime.setTextColor(ContextCompat.getColor(requireContext(), color));
        });

        mExportAllSwitch.setChecked(sharedPrefs.getBoolean(getString(R.string.key_export_all_transactions), true));
        mDeleteAllCheckBox.setChecked(sharedPrefs.getBoolean(getString(R.string.key_delete_transactions_after_export), false));

        mRecurrenceTextView.setOnClickListener(new RecurrenceViewClickListener((AppCompatActivity) getActivity(), mRecurrenceRule, this));

        //this part (setting the export format) must come after the recurrence view bindings above
        String defaultExportFormat = sharedPrefs.getString(getString(R.string.key_default_export_format), ExportFormat.CSVT.name());
        mExportFormat = ExportFormat.valueOf(defaultExportFormat);

        View.OnClickListener radioClickListener = this::onRadioButtonClicked;

        View v = getView();
        if (v == null) return;

        mOfxRadioButton.setOnClickListener(radioClickListener);
        mQifRadioButton.setOnClickListener(radioClickListener);
        mXmlRadioButton.setOnClickListener(radioClickListener);
        mCsvTransactionsRadioButton.setOnClickListener(radioClickListener);

        mSeparatorCommaButton.setOnClickListener(radioClickListener);
        mSeparatorColonButton.setOnClickListener(radioClickListener);
        mSeparatorSemicolonButton.setOnClickListener(radioClickListener);

        ExportFormat defaultFormat = ExportFormat.valueOf(defaultExportFormat.toUpperCase());
        switch (defaultFormat) {
            case QIF -> mQifRadioButton.performClick();
            case OFX -> mOfxRadioButton.performClick();
            case XML -> mXmlRadioButton.performClick();
            case CSVT -> mCsvTransactionsRadioButton.performClick();
        }

        if (GnuCashApplication.isDoubleEntryEnabled()) {
            mOfxRadioButton.setVisibility(View.GONE);
        } else {
            mXmlRadioButton.setVisibility(View.GONE);
        }

    }

    /**
     * Display the file path of the file where the export will be saved
     *
     * @param filepath Path to export file. If {@code null}, the view will be hidden and nothing displayed
     */
    private void setExportUriText(String filepath) {
        Log.d(LOG_TAG, String.format("setExportUriText: %s.", filepath));
        if (filepath == null) {
            mTargetUriTextView.setVisibility(View.GONE);
            mTargetUriTextView.setText("");
        } else {
            mTargetUriTextView.setText(filepath);
            mTargetUriTextView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Open a chooser for user to pick a file to export to
     */
    private void selectExportFile() {
        Intent exportIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        exportIntent.setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        String bookName = BooksDbAdapter.getInstance().getActiveBookDisplayName();

        String filename = Exporter.buildExportFilename(mExportFormat, bookName);
        exportIntent.putExtra(Intent.EXTRA_TITLE, filename);
        exportFileLauncher.launch(exportIntent);
    }

    @Override
    public void onRecurrenceSet(String rrule) {
        mRecurrenceRule = rrule;
        String repeatString = getString(R.string.label_tap_to_create_schedule);

        if (mRecurrenceRule != null) {
            mEventRecurrence.parse(mRecurrenceRule);
            repeatString = EventRecurrenceFormatter.getRepeatString(getActivity(), getResources(),
                    mEventRecurrence, true);
        }
        mRecurrenceTextView.setText(repeatString);
    }

    @Override
    public void onDateSet(CalendarDatePickerDialogFragment dialog, int year, int monthOfYear, int dayOfMonth) {
        Calendar cal = new GregorianCalendar(year, monthOfYear, dayOfMonth);
        mExportStartDate.setText(TransactionFormFragment.DATE_FORMATTER.format(cal.getTime()));
        mExportStartCalendar.set(Calendar.YEAR, year);
        mExportStartCalendar.set(Calendar.MONTH, monthOfYear);
        mExportStartCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
    }

    @Override
    public void onTimeSet(RadialTimePickerDialogFragment dialog, int hourOfDay, int minute) {
        Calendar cal = new GregorianCalendar(0, 0, 0, hourOfDay, minute);
        mExportStartTime.setText(TransactionFormFragment.TIME_FORMATTER.format(cal.getTime()));
        mExportStartCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        mExportStartCalendar.set(Calendar.MINUTE, minute);
    }
}

// Gotten from: https://stackoverflow.com/a/31720191
class OptionsViewAnimationUtils {

    public static void expand(final View v) {
        v.measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        final int targetHeight = v.getMeasuredHeight();

        v.getLayoutParams().height = 0;
        v.setVisibility(View.VISIBLE);
        Animation a = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                v.getLayoutParams().height = interpolatedTime == 1
                        ? ViewGroup.LayoutParams.WRAP_CONTENT
                        : (int) (targetHeight * interpolatedTime);
                v.requestLayout();
            }

        };

        a.setDuration((int) (3 * targetHeight / v.getContext().getResources().getDisplayMetrics().density));
        v.startAnimation(a);
    }

    public static void collapse(final View v) {
        final int initialHeight = v.getMeasuredHeight();

        Animation a = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                if (interpolatedTime == 1) {
                    v.setVisibility(View.GONE);
                } else {
                    v.getLayoutParams().height = initialHeight - (int) (initialHeight * interpolatedTime);
                    v.requestLayout();
                }
            }

        };

        a.setDuration((int) (3 * initialHeight / v.getContext().getResources().getDisplayMetrics().density));
        v.startAnimation(a);
    }
}
