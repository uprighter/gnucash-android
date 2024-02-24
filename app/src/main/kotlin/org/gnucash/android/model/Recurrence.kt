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
package org.gnucash.android.model

import java.sql.Timestamp

/**
 * Model for recurrences in the database
 *
 * Basically a wrapper around [PeriodType]
 */
class Recurrence(periodType: PeriodType, rrule: String, periodStart: Timestamp) : BaseModel() {
    /**
     * Return the [PeriodType] for this recurrence
     */
    var periodType: PeriodType

    /**
     * RFC 5545 string RRule string.
     */
    var rrule: String? = null

    /**
     * Timestamp of start of recurrence
     */
    var periodStart: Timestamp

    /**
     * The multiplier for the period type. The default multiplier is 1.
     * e.g. bi-weekly actions have period type [PeriodType.WEEK] and multiplier 2.
     */
    var multiplier = 1 //multiplier for the period type

    init {
        this.periodType = periodType
        this.rrule = rrule
        this.periodStart = periodStart
    }
}
