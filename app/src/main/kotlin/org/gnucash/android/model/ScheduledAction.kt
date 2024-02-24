/*
 * Copyright (c) 2014 - 2015 Ngewi Fet <ngewif@gmail.com>
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
 * Represents a scheduled event which is stored in the database and run at regular mPeriod
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
class ScheduledAction    //all actions are enabled by default
    (
    /**
     * Type of event being scheduled
     */
    var actionType: ActionType
) : BaseModel() {

    private var _startDate: Long = 0

    private var _endDate: Long = -1

    /**
     * The tag saves additional information about the scheduled action,
     * e.g. such as export parameters for scheduled backups
     */
    var tag: String? = null

    /**
     * Recurrence of this scheduled action
     */
    var recurrence: Recurrence? = null
        private set

    /**
     * Types of events which can be scheduled
     */
    enum class ActionType {
        TRANSACTION, BACKUP
    }

    /**
     * Next scheduled run of Event
     */
    var lastRunTime: Long = 0
        private set

    /**
     * Unique ID of the template from which the recurring event will be executed.
     * For example, transaction UID
     */
    var actionUID: String? = null

    /**
     * Flag indicating if this event is enabled or not
     */
    var isEnabled = true

    /**
     * Number of times this event is planned to be executed
     */
    var totalPlannedExecutionCount = 0

    /**
     * How many times this action has already been executed
     */
    var executionCount = 0

    /**
     * Flag for whether the scheduled transaction should be auto-created
     */
    private var _autoCreate = true
    private var _autoNotify = false

    /**
     * Number of days in advance to create the transaction
     *
     * This flag is currently unused in the app. It is only included here for compatibility with GnuCash desktop XML
     */
    var advanceCreateDays = 0

    /**
     * The number of days in advance to notify of scheduled transactions
     *
     * This flag is currently unused in the app. It is only included here for compatibility with GnuCash desktop XML
     */
    var advanceNotifyDays = 0

    /**
     * Set time of last execution of the scheduled action
     *
     * @param nextRun Timestamp in milliseconds since Epoch
     */
    fun setLastRun(nextRun: Long) {
        lastRunTime = nextRun
    }

    /**
     * The time of first execution of the scheduled action, represented as a timestamp in
     * milliseconds since Epoch
     */
    var startTime: Long
        get() = _startDate
        set(startDate) {
            _startDate = startDate
            if (recurrence != null) {
                recurrence!!.periodStart = Timestamp(startDate)
            }
        }

    /**
     * The end time of the scheduled action, represented as a timestamp in milliseconds since Epoch.
     */
    var endTime: Long
        get() = _endDate
        set(endDate) {
            _endDate = endDate
        }

    /**
     * Returns flag if transactions should be automatically created or not
     *
     * This flag is currently unused in the app. It is only included here for compatibility with GnuCash desktop XML
     *
     * @return `true` if the transaction should be auto-created, `false` otherwise
     */
    fun shouldAutoCreate(): Boolean {
        return _autoCreate
    }

    /**
     * Set flag for automatically creating transaction based on this scheduled action
     *
     * This flag is currently unused in the app. It is only included here for compatibility with GnuCash desktop XML
     *
     * @param autoCreate Flag for auto creating transactions
     */
    fun setAutoCreate(autoCreate: Boolean) {
        _autoCreate = autoCreate
    }

    /**
     * Check if user will be notified of creation of scheduled transactions
     *
     * This flag is currently unused in the app. It is only included here for compatibility with GnuCash desktop XML
     *
     * @return `true` if user will be notified, `false` otherwise
     */
    fun shouldAutoNotify(): Boolean {
        return _autoNotify
    }

    /**
     * Sets whether to notify the user that scheduled transactions have been created
     *
     * This flag is currently unused in the app. It is only included here for compatibility with GnuCash desktop XML
     *
     * @param autoNotify Boolean flag
     */
    fun setAutoNotify(autoNotify: Boolean) {
        _autoNotify = autoNotify
    }

    /** Backing field for @{link ScheduledAction#templateAccountUID} */
    private var _templateAccountUID: String? = null
    var templateAccountUID: String?
        /**
         * Return the template account GUID for this scheduled action
         *
         * If no GUID was set, a new one is going to be generated and returned.
         *
         * @return String GUID of template account
         */
        get() = if (_templateAccountUID == null) generateUID().also {
            _templateAccountUID = it
        } else _templateAccountUID
        /**
         * Set the template account GUID
         *
         * @param templateAccountUID String GUID of template account
         */
        set(templateAccountUID) {
            _templateAccountUID = templateAccountUID
        }

    /**
     * Sets the recurrence pattern of this scheduled action
     *
     * This also sets the start period of the recurrence object, if there is one
     *
     * @param recurrence [Recurrence] object
     */
    fun setRecurrence(recurrence: Recurrence) {
        this.recurrence = recurrence
        //if we were parsing XML and parsed the start and end date from the scheduled action first,
        //then use those over the values which might be gotten from the recurrence
        if (_startDate > 0) {
            recurrence.periodStart = Timestamp(_startDate)
        } else {
            _startDate = recurrence.periodStart.time
        }
    }

    override fun toString(): String {
        return actionType.name + " - " + recurrence.toString()
    }
}