/*
 * Copyright (c) 2014 Ngewi Fet <ngewif@gmail.com>
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

import android.text.format.Time;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.codetroopers.betterpickers.recurrencepicker.EventRecurrence;

import org.gnucash.android.model.PeriodType;
import org.gnucash.android.model.Recurrence;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Parses {@link com.maltaisn.recurpicker.Recurrence }s to generate
 * {@link org.gnucash.android.model.ScheduledAction}s
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class RecurrenceParser {
    //these are time millisecond constants which are used for scheduled actions.
    //they may not be calendar accurate, but they serve the purpose for scheduling approximate time for background service execution
    public static final long SECOND_MILLIS = 1000;
    public static final long MINUTE_MILLIS = 60 * SECOND_MILLIS;
    public static final long HOUR_MILLIS = 60 * MINUTE_MILLIS;
    public static final long DAY_MILLIS = 24 * HOUR_MILLIS;
    public static final long WEEK_MILLIS = 7 * DAY_MILLIS;
    public static final long MONTH_MILLIS = 30 * DAY_MILLIS;
    public static final long YEAR_MILLIS = 12 * MONTH_MILLIS;


    /**
     * Parse an {@link EventRecurrence} into a {@link Recurrence} object
     *
     * @param eventRecurrence EventRecurrence object
     * @return Recurrence object
     */
    public static Recurrence parse(EventRecurrence eventRecurrence) {
        if (eventRecurrence == null)
            return null;

        PeriodType periodType = switch (eventRecurrence.freq) {
            case EventRecurrence.HOURLY -> PeriodType.HOUR;
            case EventRecurrence.DAILY -> PeriodType.DAY;
            case EventRecurrence.WEEKLY -> PeriodType.WEEK;
            case EventRecurrence.MONTHLY -> PeriodType.MONTH;
            case EventRecurrence.YEARLY -> PeriodType.YEAR;
            default -> PeriodType.MONTH;
        };

        int interval = eventRecurrence.interval == 0 ? 1 : eventRecurrence.interval; //bug from betterpickers library sometimes returns 0 as the interval
        Recurrence recurrence = new Recurrence(periodType);
        recurrence.setMultiplier(interval);
        parseEndTime(eventRecurrence, recurrence);
        recurrence.setByDays(parseByDay(eventRecurrence.byday));
        if (eventRecurrence.startDate != null)
            recurrence.setPeriodStart(new Timestamp(eventRecurrence.startDate.toMillis(false)));

        return recurrence;
    }

    /**
     * Parses the end time from an EventRecurrence object and sets it to the <code>scheduledEvent</code>.
     * The end time is specified in the dialog either by number of occurrences or a date.
     *
     * @param eventRecurrence Event recurrence pattern obtained from dialog
     * @param recurrence      Recurrence event to set the end period to
     */
    private static void parseEndTime(EventRecurrence eventRecurrence, Recurrence recurrence) {
        if (eventRecurrence.until != null && eventRecurrence.until.length() > 0) {
            Time endTime = new Time();
            endTime.parse(eventRecurrence.until);
            recurrence.setPeriodEnd(new Timestamp(endTime.toMillis(false)));
        } else if (eventRecurrence.count > 0) {
            recurrence.setPeriodEnd(eventRecurrence.count);
        }
    }

    /**
     * Parses an array of byDay values to return a list of days of week
     * constants from {@link Calendar}.
     *
     * <p>Currently only supports byDay values for weeks.</p>
     *
     * @param byDay Array of byDay values
     * @return list of days of week constants from Calendar.
     */
    private static @NonNull List<Integer> parseByDay(@Nullable int[] byDay) {
        if (byDay == null) {
            return Collections.emptyList();
        }

        List<Integer> byDaysList = new ArrayList<>(byDay.length);
        for (int day : byDay) {
            byDaysList.add(EventRecurrence.day2CalendarDay(day));
        }

        return byDaysList;
    }

    /**
     * Parse an {@link com.maltaisn.recurpicker.Recurrence } into a {@link Recurrence} object
     *
     * @param eventRecurrence EventRecurrence object
     * @return Recurrence object
     */
    public static Recurrence parse(long startTime, com.maltaisn.recurpicker.Recurrence eventRecurrence) {
        if (eventRecurrence == null) {
            return null;
        }
        PeriodType periodType = switch (eventRecurrence.getPeriod()) {
            case DAILY -> PeriodType.DAY;
            case WEEKLY -> PeriodType.WEEK;
            case YEARLY -> PeriodType.YEAR;
//            case MONTHLY -> PeriodType.MONTH;
            default -> PeriodType.MONTH;
        };

        Recurrence recurrence = new Recurrence(periodType);
        recurrence.setMultiplier(eventRecurrence.getFrequency());
        recurrence.setByDays(parseByDay(eventRecurrence.getByDay()));
        if (startTime != 0 ) {
            recurrence.setPeriodStart(new Timestamp(startTime));
        }
        parseEndTime(eventRecurrence, recurrence);

        return recurrence;
    }

    /**
     * Parses the end time from an EventRecurrence object and sets it to the <code>scheduledEvent</code>.
     * The end time is specified in the dialog either by number of occurrences or a date.
     *
     * @param eventRecurrence Event recurrence pattern obtained from dialog
     * @param recurrence      Recurrence event to set the end period to
     */
    private static void parseEndTime(com.maltaisn.recurpicker.Recurrence  eventRecurrence, Recurrence recurrence) {
        if (eventRecurrence.getEndType() == com.maltaisn.recurpicker.Recurrence.EndType.BY_DATE) {
            recurrence.setPeriodEnd(new Timestamp(eventRecurrence.getEndDate()));
        } else if (eventRecurrence.getEndType() == com.maltaisn.recurpicker.Recurrence.EndType.BY_COUNT) {
            recurrence.setPeriodEnd(eventRecurrence.getEndCount());
        }
    }

    /**
     * Parses an array of byDay values to return a list of days of week
     * constants from {@link Calendar}.
     *
     * <p>Currently only supports byDay values for weeks.</p>
     *
     * @param byDay Array of byDay values
     * @return list of days of week constants from Calendar.
     */
    private static @NonNull List<Integer> parseByDay(int byDay) {
        if (byDay == 0) {
            return Collections.emptyList();
        }
        List<Integer> byDaysList = new ArrayList<>(Integer.bitCount(byDay) - 1);
        if ((byDay & com.maltaisn.recurpicker.Recurrence.SUNDAY) !=0) {
            byDaysList.add(Calendar.SUNDAY);
        }
        if ((byDay & com.maltaisn.recurpicker.Recurrence.MONDAY) !=0) {
            byDaysList.add(Calendar.MONDAY);
        }
        if ((byDay & com.maltaisn.recurpicker.Recurrence.TUESDAY) !=0) {
            byDaysList.add(Calendar.TUESDAY);
        }
        if ((byDay & com.maltaisn.recurpicker.Recurrence.WEDNESDAY) !=0) {
            byDaysList.add(Calendar.WEDNESDAY);
        }
        if ((byDay & com.maltaisn.recurpicker.Recurrence.THURSDAY) !=0) {
            byDaysList.add(Calendar.THURSDAY);
        }
        if ((byDay & com.maltaisn.recurpicker.Recurrence.FRIDAY) !=0) {
            byDaysList.add(Calendar.FRIDAY);
        }
        if ((byDay & com.maltaisn.recurpicker.Recurrence.SATURDAY) !=0) {
            byDaysList.add(Calendar.SATURDAY);
        }

        return byDaysList;
    }
}
