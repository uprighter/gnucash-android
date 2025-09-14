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

package org.gnucash.android.db;

import static android.database.DatabaseUtils.sqlEscapeString;
import static org.gnucash.android.db.DatabaseHelper.createResetBalancesTriggers;
import static org.gnucash.android.db.DatabaseHelper.hasTableColumn;
import static org.gnucash.android.db.DatabaseSchema.AccountEntry;
import static org.gnucash.android.db.DatabaseSchema.BudgetAmountEntry;
import static org.gnucash.android.db.DatabaseSchema.CommodityEntry;
import static org.gnucash.android.db.DatabaseSchema.ScheduledActionEntry;
import static org.gnucash.android.db.DatabaseSchema.SplitEntry;
import static org.gnucash.android.db.DatabaseSchema.TransactionEntry;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.gnucash.android.R;
import org.gnucash.android.importer.CommoditiesXmlHandler;
import org.gnucash.android.model.Commodity;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import timber.log.Timber;

/**
 * Collection of helper methods which are used during database migrations
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
@SuppressWarnings("unused")
public class MigrationHelper {

    /**
     * Imports commodities into the database from XML resource file
     */
    @VisibleForTesting
    public static void importCommodities(@NonNull DatabaseHolder holder) throws SAXException, ParserConfigurationException, IOException {
        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        SAXParser parser = parserFactory.newSAXParser();
        XMLReader reader = parser.getXMLReader();

        InputStream inputStream = holder.context.getResources()
            .openRawResource(R.raw.iso_4217_currencies);

        /* Create handler to handle XML Tags ( extends DefaultHandler ) */
        CommoditiesXmlHandler handler = new CommoditiesXmlHandler(holder);

        reader.setContentHandler(handler);
        reader.parse(new InputSource(inputStream));
    }

    public static void migrate(@NonNull Context context, @NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 16) {
            migrateTo16(db);
        }
        if (oldVersion < 17) {
            migrateTo17(db);
        }
        if (oldVersion < 18) {
            migrateTo18(db);
        }
        if (oldVersion < 19) {
            migrateTo19(db);
        }
        if ((oldVersion >= 19) && (oldVersion < 21)) {
            migrateTo21(db);
        }
        if (oldVersion < 23) {
            migrateTo23(context, db);
        }
        if (oldVersion < 24) {
            migrateTo24(db);
        }
    }

    /**
     * Upgrade the database to version 16.
     *
     * @param db the database.
     */
    private static void migrateTo16(SQLiteDatabase db) {
        Timber.i("Upgrading database to version 16");

        String sqlAddQuoteSource = "ALTER TABLE " + CommodityEntry.TABLE_NAME +
            " ADD COLUMN " + CommodityEntry.COLUMN_QUOTE_SOURCE + " varchar(255)";
        String sqlAddQuoteTZ = "ALTER TABLE " + CommodityEntry.TABLE_NAME +
            " ADD COLUMN " + CommodityEntry.COLUMN_QUOTE_TZ + " varchar(100)";

        db.execSQL(sqlAddQuoteSource);
        db.execSQL(sqlAddQuoteTZ);
    }

    /**
     * Upgrade the database to version 17.
     *
     * @param db the database.
     */
    private static void migrateTo17(SQLiteDatabase db) {
        Timber.i("Upgrading database to version 17");

        String sqlAddBudgetNotes = "ALTER TABLE " + BudgetAmountEntry.TABLE_NAME +
            " ADD COLUMN " + BudgetAmountEntry.COLUMN_NOTES + " text";

        db.execSQL(sqlAddBudgetNotes);
    }

    /**
     * Upgrade the database to version 18.
     *
     * @param db the database.
     */
    private static void migrateTo18(SQLiteDatabase db) {
        Timber.i("Upgrading database to version 18");

        String sqlAddNotes = "ALTER TABLE " + AccountEntry.TABLE_NAME
            + " ADD COLUMN " + AccountEntry.COLUMN_NOTES + " text";
        String sqlAddBalance = "ALTER TABLE " + AccountEntry.TABLE_NAME
            + " ADD COLUMN " + AccountEntry.COLUMN_BALANCE + " varchar(255)";
        String sqlAddClearedBalance = "ALTER TABLE " + AccountEntry.TABLE_NAME
            + " ADD COLUMN " + AccountEntry.COLUMN_CLEARED_BALANCE + " varchar(255)";
        String sqlAddNoClosingBalance = "ALTER TABLE " + AccountEntry.TABLE_NAME
            + " ADD COLUMN " + AccountEntry.COLUMN_NOCLOSING_BALANCE + " varchar(255)";
        String sqlAddReconciledBalance = "ALTER TABLE " + AccountEntry.TABLE_NAME
            + " ADD COLUMN " + AccountEntry.COLUMN_RECONCILED_BALANCE + " varchar(255)";

        db.execSQL(sqlAddNotes);
        db.execSQL(sqlAddBalance);
        db.execSQL(sqlAddClearedBalance);
        db.execSQL(sqlAddNoClosingBalance);
        db.execSQL(sqlAddReconciledBalance);
        createResetBalancesTriggers(db);
    }

    /**
     * Upgrade the database to version 19.
     *
     * @param db the database.
     */
    private static void migrateTo19(SQLiteDatabase db) {
        Timber.i("Upgrading database to version 19");

        // Fetch list of accounts with mismatched currencies.
        String sqlAccountCurrencyWrong = "SELECT DISTINCT a." + AccountEntry.COLUMN_CURRENCY + ", a." + AccountEntry.COLUMN_COMMODITY_UID + ", c." + CommodityEntry.COLUMN_UID
            + " FROM " + AccountEntry.TABLE_NAME + " a, " + CommodityEntry.TABLE_NAME + " c"
            + " WHERE a." + AccountEntry.COLUMN_CURRENCY + " = c." + CommodityEntry.COLUMN_MNEMONIC
            + " AND (c." + CommodityEntry.COLUMN_NAMESPACE + " = " + sqlEscapeString(Commodity.COMMODITY_CURRENCY)
            + " OR c." + CommodityEntry.COLUMN_NAMESPACE + " = " + sqlEscapeString(Commodity.COMMODITY_ISO4217) + ")"
            + " AND a." + AccountEntry.COLUMN_COMMODITY_UID + " != c." + CommodityEntry.COLUMN_UID;
        Cursor cursor = db.rawQuery(sqlAccountCurrencyWrong, null);
        List<AccountCurrency> accountsWrong = new ArrayList<>();
        if (cursor.moveToFirst()) {
            do {
                String currencyCode = cursor.getString(0);
                String commodityUIDOld = cursor.getString(1);
                String commodityUIDNew = cursor.getString(2);
                accountsWrong.add(new AccountCurrency(currencyCode, commodityUIDOld, commodityUIDNew));
            } while (cursor.moveToNext());
        }
        cursor.close();

        // Update with correct commodities.
        for (AccountCurrency accountWrong : accountsWrong) {
            String sql = "UPDATE " + AccountEntry.TABLE_NAME
                + " SET " + AccountEntry.COLUMN_COMMODITY_UID + " = " + sqlEscapeString(accountWrong.commodityUIDNew)
                + " WHERE " + AccountEntry.COLUMN_CURRENCY + " = " + sqlEscapeString(accountWrong.currencyCode)
                + " AND " + AccountEntry.COLUMN_COMMODITY_UID + " = " + sqlEscapeString(accountWrong.commodityUIDOld);
            db.execSQL(sql);
        }
    }

    private static class AccountCurrency {
        @NonNull
        public final String currencyCode;
        @NonNull
        public final String commodityUIDOld;
        @NonNull
        public final String commodityUIDNew;

        private AccountCurrency(
            @NonNull String currencyCode,
            @NonNull String commodityUIDOld,
            @NonNull String commodityUIDNew) {
            this.currencyCode = currencyCode;
            this.commodityUIDOld = commodityUIDOld;
            this.commodityUIDNew = commodityUIDNew;
        }
    }

    /**
     * Upgrade the database to version 21.
     *
     * @param db the database.
     */
    private static void migrateTo21(SQLiteDatabase db) {
        Timber.i("Upgrading database to version 21");

        boolean hasColumnCurrency = hasTableColumn(db, AccountEntry.TABLE_NAME, AccountEntry.COLUMN_CURRENCY);
        if (hasColumnCurrency) {
            return;
        }

        // Restore the currency code column that was deleted in v19.
        String sqlAccountCurrency = "ALTER TABLE " + AccountEntry.TABLE_NAME
            + " ADD COLUMN " + AccountEntry.COLUMN_CURRENCY + " varchar(255)";
        try {
            db.execSQL(sqlAccountCurrency);
        } catch (SQLException e) {
            Timber.e(e);
        }

        // Restore the currency code column.
        String sqlTransactionCurrency = "ALTER TABLE " + TransactionEntry.TABLE_NAME
            + " ADD COLUMN " + TransactionEntry.COLUMN_CURRENCY + " varchar(255)";
        try {
            db.execSQL(sqlTransactionCurrency);
        } catch (SQLException e) {
            Timber.e(e);
        }
    }

    /**
     * Upgrade the database to version 23.
     *
     * @param context the context.
     * @param db      the database.
     */
    private static void migrateTo23(@NonNull Context context, @NonNull SQLiteDatabase db) {
        Timber.i("Upgrading database to version 23");

        boolean hasColumnQuoteFlag = hasTableColumn(db, CommodityEntry.TABLE_NAME, CommodityEntry.COLUMN_QUOTE_FLAG);
        if (!hasColumnQuoteFlag) {
            // Restore the currency code column that was deleted in v19.
            String sqlCommodityFlag = "ALTER TABLE " + CommodityEntry.TABLE_NAME
                + " ADD COLUMN " + CommodityEntry.COLUMN_QUOTE_FLAG + " tinyint default 0";
            try {
                db.execSQL(sqlCommodityFlag);
            } catch (SQLException e) {
                Timber.e(e);
            }
        }

        try {
            DatabaseHolder holder = new DatabaseHolder(context, db);
            importCommodities(holder);
        } catch (SAXException | ParserConfigurationException | IOException e) {
            String msg = "Error loading currencies into the database";
            Timber.e(e, msg);
            throw new SQLiteException(msg, e);
        }
    }

    /**
     * Upgrade the database to version 24.
     *
     * @param db the database.
     */
    private static void migrateTo24(@NonNull SQLiteDatabase db) {
        Timber.i("Upgrading database to version 24");

        if (!hasTableColumn(db, AccountEntry.TABLE_NAME, AccountEntry.COLUMN_TEMPLATE)) {
            String sqlAccountTemplate = "ALTER TABLE " + AccountEntry.TABLE_NAME +
                " ADD COLUMN " + AccountEntry.COLUMN_TEMPLATE + " tinyint default 0";
            db.execSQL(sqlAccountTemplate);
        }

        if (!hasTableColumn(db, SplitEntry.TABLE_NAME, SplitEntry.COLUMN_SCHEDX_ACTION_ACCOUNT_UID)) {
            String sqlAddSchedxActionAccount = "ALTER TABLE " + SplitEntry.TABLE_NAME +
                " ADD COLUMN " + SplitEntry.COLUMN_SCHEDX_ACTION_ACCOUNT_UID + " varchar(255)";
            db.execSQL(sqlAddSchedxActionAccount);
        }
    }
}