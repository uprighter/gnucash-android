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

import com.maltaisn.recurpicker.RecurrenceFinder;
import com.maltaisn.recurpicker.format.RRuleFormatter;

import org.gnucash.android.model.PeriodType;
import org.gnucash.android.model.Recurrence;

import java.sql.Timestamp;
import java.util.List;

/**
 * Parses {@link com.maltaisn.recurpicker.Recurrence }s to generate
 * {@link org.gnucash.android.model.ScheduledAction}s
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class RecurrenceParser {

    private static final RRuleFormatter mRRuleFormatter = new RRuleFormatter();
    private static final RecurrenceFinder mRecurrenceFinder = new RecurrenceFinder();

    public static String format(Recurrence recurrence) {
        return recurrence.getRrule();
    }

    /**
     * Parse an RFC5545 string into a {@link Recurrence} object
     *
     * @param rRule String
     * @return Recurrence object
     */
    public static Recurrence parse(String rRule) {
        return parse(System.currentTimeMillis(), rRule);
    }

    /**
     * Parse an RFC5545 string into a {@link Recurrence} object
     *
     * @param rRule String
     * @return Recurrence object
     */
    public static Recurrence parse(long startTime, String rRule) {
        if (rRule == null) {
            return null;
        }
        com.maltaisn.recurpicker.Recurrence pickerRecurrence = mRRuleFormatter.parse(rRule);
        PeriodType periodType = switch (pickerRecurrence.getPeriod()) {
            case DAILY -> PeriodType.DAY;
            case WEEKLY -> PeriodType.WEEK;
            case YEARLY -> PeriodType.YEAR;
//            case MONTHLY -> PeriodType.MONTH;
            default -> PeriodType.MONTH;
        };

        return new Recurrence(periodType, rRule, new Timestamp(startTime));
    }
    public static long getEndTime(String rRule, long startTime) {
        com.maltaisn.recurpicker.Recurrence pickerRecurrence = mRRuleFormatter.parse(rRule);
        if (pickerRecurrence.getEndType() == com.maltaisn.recurpicker.Recurrence.EndType.BY_DATE) {
            return pickerRecurrence.getEndDate();
        } else if (pickerRecurrence.getEndType() == com.maltaisn.recurpicker.Recurrence.EndType.BY_COUNT) {
            List<Long> events = mRecurrenceFinder.find(pickerRecurrence, startTime, pickerRecurrence.getEndCount());
            if (events.size() == 0) {
                return startTime - 1;
            } else {
                return events.get(events.size()-1);
            }
        } else {
            // Return any future time. Here use 1 day later.
            return System.currentTimeMillis() + 86400000;
        }
    }

    public static long getNextExecutionTime(String rRule, long startTime, long lastTime, int baseCount) {
        com.maltaisn.recurpicker.Recurrence pickerRecurrence = mRRuleFormatter.parse(rRule);
        List<Long> events = mRecurrenceFinder.findBasedOn(pickerRecurrence,
                startTime, lastTime, baseCount, 1, lastTime, false);
        if (events.size() == 0) {
            return startTime - 1;
        } else {
            return events.get(events.size()-1);
        }
    }
}
