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

package org.gnucash.android.ui.util;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.maltaisn.recurpicker.Recurrence;
import com.maltaisn.recurpicker.RecurrencePickerSettings;
import com.maltaisn.recurpicker.format.RRuleFormatter;
import com.maltaisn.recurpicker.list.RecurrenceListCallback;
import com.maltaisn.recurpicker.list.RecurrenceListDialog;
import com.maltaisn.recurpicker.picker.RecurrencePickerCallback;
import com.maltaisn.recurpicker.picker.RecurrencePickerFragment;

import org.gnucash.android.R;

public class DateTimePicker {
    private static final String LOG_TAG = DateTimePicker.class.getName();

    public static class DatePickerFragment extends DialogFragment {
        final DatePickerDialog.OnDateSetListener listener;
        final int defaultYear, defaultMonth, defaultDay;

        // Pass the default date into the picker.
        public DatePickerFragment(DatePickerDialog.OnDateSetListener listener,
                                  int year, int month, int day) {
            this.listener = listener;
            this.defaultYear = year;
            this.defaultMonth = month;
            this.defaultDay = day;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Create a new instance of DatePickerDialog and return it.
            return new DatePickerDialog(requireContext(), listener, defaultYear, defaultMonth, defaultDay);
        }
    }

    public static class TimePickerFragment extends DialogFragment {
        final TimePickerDialog.OnTimeSetListener listener;
        final int defaultHour, defaultMinute;

        public TimePickerFragment(TimePickerDialog.OnTimeSetListener listener, int hour, int minute) {
            this.listener = listener;
            this.defaultHour = hour;
            this.defaultMinute = minute;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Create a new instance of TimePickerDialog and return it.
            return new TimePickerDialog(getActivity(), listener, defaultHour, defaultMinute,
                    android.text.format.DateFormat.is24HourFormat(getActivity()));
        }
    }

    public interface RRulePickerListener {
        void onRecurrenceSet(String rRule);
    }

    public static class RRulePickerFragment extends Fragment implements
            RecurrenceListCallback, RecurrencePickerCallback {
        // Because RecurrenceListDialog could only use its parentFragment/targetFragment or
        // activity as listener, this wrapper is necessary to implement listeners here.
        RecurrencePickerSettings settings = new RecurrencePickerSettings.Builder().build();
        long now = System.currentTimeMillis();

        RecurrenceListDialog listDialog;
        RecurrencePickerFragment pickerFragment;
        FragmentManager fm;
        RRulePickerListener listener;
        private static final RRuleFormatter mRRuleFormatter = new RRuleFormatter();

        String initialRRule;
        Recurrence initialRecurrence;

        public RRulePickerFragment(RRulePickerListener listener, String initialRRule) {
            super(R.layout.fragment_rrule_picker);

            this.listener = listener;
            this.initialRRule = initialRRule;
            if (initialRRule == null) {
                this.initialRecurrence = Recurrence.DOES_NOT_REPEAT;
            } else {
                this.initialRecurrence = mRRuleFormatter.parse(initialRRule);
            }
            listDialog = RecurrenceListDialog.newInstance(settings);
            pickerFragment = RecurrencePickerFragment.newInstance(settings);
        }

        public void show(FragmentManager fm) {
            fm.beginTransaction()
                    .add(R.id.picker_fragment_container, this, "recurrence-picker-fragment")
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .addToBackStack(null)
                    .commit();
        }

        @MainThread
        @Override
        public void onAttach(@NonNull Context context) {
            super.onAttach(context);

            listDialog.setSelectedRecurrence(initialRecurrence);
            listDialog.setStartDate(now);
            listDialog.show(getChildFragmentManager(), "recurrence-list-dialog");
        }

        @Override
        public void onRecurrenceCustomClicked() {
            // The "Custom..." item in the recurrence list dialog was clicked. Show the picker fragment.
            Log.d(LOG_TAG, "onRecurrenceCustomClicked.");
            pickerFragment.setSelectedRecurrence(initialRecurrence);
            pickerFragment.setStartDate(now);
            getChildFragmentManager().beginTransaction()
                    .add(R.id.custom_picker_fragment_container, pickerFragment, "recurrence-picker-fragment")
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .addToBackStack(null)
                    .commit();
        }

        @Override
        public void onRecurrenceListDialogCancelled() {
            Log.d(LOG_TAG, "onRecurrenceListDialogCancelled.");
        }

        @Override
        public void onRecurrencePresetSelected(@NonNull Recurrence recurrence) {
            // A recurrence preset item in the recurrence list dialog was selected.
            Log.d(LOG_TAG, String.format("onRecurrencePresetSelected(%s).", recurrence));
            if (recurrence == Recurrence.DOES_NOT_REPEAT) {
                listener.onRecurrenceSet(null);
            } else {
                listener.onRecurrenceSet(mRRuleFormatter.format(recurrence));
            }
        }

        @Override
        public void onRecurrenceCreated(@NonNull Recurrence recurrence) {
            // A custom recurrence was created with the recurrence picker fragment.
            Log.d(LOG_TAG, String.format("onRecurrenceCreated(%s).", recurrence));
            if (recurrence == Recurrence.DOES_NOT_REPEAT) {
                listener.onRecurrenceSet(null);
            } else {
                listener.onRecurrenceSet(mRRuleFormatter.format(recurrence));
            }
        }

        @Override
        public void onRecurrencePickerCancelled() {
            Log.d(LOG_TAG, "onRecurrencePickerCancelled.");
        }
    }
}