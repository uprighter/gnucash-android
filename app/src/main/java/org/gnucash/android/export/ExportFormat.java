/*
 * Copyright (c) 2013 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.export;

import androidx.annotation.NonNull;

/**
 * Enumeration of the different export formats supported by the application
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public enum ExportFormat {
    QIF("Quicken Interchange Format"),
    OFX("Open Financial eXchange"),
    XML("GnuCash XML"),
    CSVA("GnuCash accounts CSV"),
    CSVT("GnuCash transactions CSV");

    /**
     * Full name of the export format acronym
     */
    private final String mDescription;

    ExportFormat(String description) {
        this.mDescription = description;
    }

    /**
     * Returns the file extension for this export format including the period e.g. ".qif"
     *
     * @return String file extension for the export format
     */
    public String getExtension() {
        return switch (this) {
            case QIF -> ".qif";
            case OFX -> ".ofx";
            case XML -> ".gnca";
            case CSVA, CSVT -> ".csv";
        };
    }

    @NonNull
    @Override
    public String toString() {
        return mDescription;
    }
}
