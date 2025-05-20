/*
 * Copyright (c) 2014 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 Yongxin Wang <fefe.wyx@gmail.com>
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

package org.gnucash.android.db.adapter;

import static org.gnucash.android.db.DatabaseSchema.AccountEntry;
import static org.gnucash.android.db.DatabaseSchema.CommonColumns;
import static org.gnucash.android.db.DatabaseSchema.SplitEntry;
import static org.gnucash.android.db.DatabaseSchema.TransactionEntry;
import static org.gnucash.android.math.MathExtKt.toBigDecimal;
import static org.gnucash.android.model.PriceKt.isNullOrEmpty;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteStatement;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Price;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.util.TimestampHelper;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import timber.log.Timber;

/**
 * Database adapter for managing transaction splits in the database
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
public class SplitsDbAdapter extends DatabaseAdapter<Split> {

    @NonNull
    final CommoditiesDbAdapter commoditiesDbAdapter;
    @NonNull
    final PricesDbAdapter pricesDbAdapter;

    private static final String credit = TransactionType.CREDIT.value;

    public SplitsDbAdapter(@NonNull SQLiteDatabase db) {
        this(new CommoditiesDbAdapter(db));
    }

    public SplitsDbAdapter(@NonNull CommoditiesDbAdapter commoditiesDbAdapter) {
        this(new PricesDbAdapter(commoditiesDbAdapter));
    }

    public SplitsDbAdapter(@NonNull PricesDbAdapter pricesDbAdapter) {
        super(pricesDbAdapter.mDb, SplitEntry.TABLE_NAME, new String[]{
            SplitEntry.COLUMN_MEMO,
            SplitEntry.COLUMN_TYPE,
            SplitEntry.COLUMN_VALUE_NUM,
            SplitEntry.COLUMN_VALUE_DENOM,
            SplitEntry.COLUMN_QUANTITY_NUM,
            SplitEntry.COLUMN_QUANTITY_DENOM,
            SplitEntry.COLUMN_CREATED_AT,
            SplitEntry.COLUMN_RECONCILE_STATE,
            SplitEntry.COLUMN_RECONCILE_DATE,
            SplitEntry.COLUMN_ACCOUNT_UID,
            SplitEntry.COLUMN_TRANSACTION_UID
        });
        this.pricesDbAdapter = pricesDbAdapter;
        this.commoditiesDbAdapter = pricesDbAdapter.commoditiesDbAdapter;
    }

    /**
     * Returns application-wide instance of the database adapter
     *
     * @return SplitsDbAdapter instance
     */
    public static SplitsDbAdapter getInstance() {
        return GnuCashApplication.getSplitsDbAdapter();
    }

    @Override
    public void close() throws IOException {
        commoditiesDbAdapter.close();
        pricesDbAdapter.close();
        super.close();
    }

    /**
     * Adds a split to the database.
     * The transactions belonging to the split are marked as exported
     *
     * @param split {@link org.gnucash.android.model.Split} to be recorded in DB
     */
    public void addRecord(@NonNull final Split split, UpdateMethod updateMethod) {
        Timber.d("Replace transaction split in db");
        super.addRecord(split, updateMethod);

        long transactionId = getTransactionID(split.getTransactionUID());
        //when a split is updated, we want mark the transaction as not exported
        updateRecord(TransactionEntry.TABLE_NAME, transactionId,
            TransactionEntry.COLUMN_EXPORTED, String.valueOf(0));

        //modifying a split means modifying the accompanying transaction as well
        updateRecord(TransactionEntry.TABLE_NAME, transactionId,
            TransactionEntry.COLUMN_MODIFIED_AT, TimestampHelper.getUtcStringFromTimestamp(TimestampHelper.getTimestampFromNow()));
    }

    @Override
    protected @NonNull SQLiteStatement bind(@NonNull SQLiteStatement stmt, @NonNull final Split split) {
        bindBaseModel(stmt, split);
        if (split.getMemo() != null) {
            stmt.bindString(1, split.getMemo());
        }
        stmt.bindString(2, split.getType().name());
        stmt.bindLong(3, split.getValue().getNumerator());
        stmt.bindLong(4, split.getValue().getDenominator());
        stmt.bindLong(5, split.getQuantity().getNumerator());
        stmt.bindLong(6, split.getQuantity().getDenominator());
        stmt.bindString(7, TimestampHelper.getUtcStringFromTimestamp(split.getCreatedTimestamp()));
        stmt.bindString(8, String.valueOf(split.getReconcileState()));
        stmt.bindString(9, TimestampHelper.getUtcStringFromTimestamp(split.getReconcileDate()));
        stmt.bindString(10, split.getAccountUID());
        stmt.bindString(11, split.getTransactionUID());

        return stmt;
    }

    /**
     * Builds a split instance from the data pointed to by the cursor provided
     * <p>This method will not move the cursor in any way. So the cursor should already by pointing to the correct entry</p>
     *
     * @param cursor Cursor pointing to transaction record in database
     * @return {@link org.gnucash.android.model.Split} instance
     */
    public Split buildModelInstance(@NonNull final Cursor cursor) {
        long valueNum = cursor.getLong(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_VALUE_NUM));
        long valueDenom = cursor.getLong(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_VALUE_DENOM));
        long quantityNum = cursor.getLong(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_QUANTITY_NUM));
        long quantityDenom = cursor.getLong(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_QUANTITY_DENOM));
        String typeName = cursor.getString(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_TYPE));
        String accountUID = cursor.getString(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_ACCOUNT_UID));
        String transxUID = cursor.getString(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_TRANSACTION_UID));
        String memo = cursor.getString(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_MEMO));
        String reconcileState = cursor.getString(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_RECONCILE_STATE));
        String reconcileDate = cursor.getString(cursor.getColumnIndexOrThrow(SplitEntry.COLUMN_RECONCILE_DATE));

        String transactionCurrencyUID = getAttribute(TransactionEntry.TABLE_NAME, transxUID, TransactionEntry.COLUMN_COMMODITY_UID);
        Commodity transactionCurrency = commoditiesDbAdapter.getRecord(transactionCurrencyUID);
        Money value = new Money(valueNum, valueDenom, transactionCurrency);
        Commodity commodity = getCommodity(accountUID);
        Money quantity = new Money(quantityNum, quantityDenom, commodity);

        Split split = new Split(value, accountUID);
        populateBaseModelAttributes(cursor, split);
        split.setQuantity(quantity);
        split.setTransactionUID(transxUID);
        split.setType(TransactionType.valueOf(typeName));
        split.setMemo(memo);
        split.setReconcileState(reconcileState.charAt(0));
        if (reconcileDate != null && !reconcileDate.isEmpty())
            split.setReconcileDate(TimestampHelper.getTimestampFromUtcString(reconcileDate));

        return split;
    }

    /**
     * Returns the sum of the splits for given set of accounts.
     * This takes into account the kind of movement caused by the split in the account (which also depends on account type)
     * The Caller must make sure all accounts have the currency, which is passed in as currencyCode
     *
     * @param accountUIDList List of String unique IDs of given set of accounts
     * @param currencyCode   currencyCode for all the accounts in the list
     * @return Balance of the splits for this account
     */
    public Money computeSplitBalance(List<String> accountUIDList, String currencyCode) {
        return calculateSplitBalance(accountUIDList, currencyCode, -1, -1);
    }

    /**
     * Returns the sum of the splits for given set of accounts within the specified time range.
     * This takes into account the kind of movement caused by the split in the account (which also depends on account type)
     * The Caller must make sure all accounts have the currency, which is passed in as currencyCode
     *
     * @param accountUIDList List of String unique IDs of given set of accounts
     * @param currencyCode   currencyCode for all the accounts in the list
     * @param startTimestamp the start timestamp of the time range
     * @param endTimestamp   the end timestamp of the time range
     * @return Balance of the splits for this account within the specified time range
     */
    public Money computeSplitBalance(
        List<String> accountUIDList,
        String currencyCode,
        long startTimestamp,
        long endTimestamp
    ) {
        return calculateSplitBalance(accountUIDList, currencyCode, startTimestamp, endTimestamp);
    }

    private Money calculateSplitBalance(
        List<String> accountUIDList,
        String currencyCode,
        long startTimestamp,
        long endTimestamp
    ) {
        final Commodity currency = commoditiesDbAdapter.getCommodity(currencyCode);
        final String currencyUID = currency.getUID();
        BigDecimal total = BigDecimal.ZERO;
        if (accountUIDList.isEmpty()) {
            return new Money(total, currency);
        }

        String selection = "a." + CommonColumns.COLUMN_UID + " IN ('" + TextUtils.join("','", accountUIDList) + "')"
            + " AND t." + TransactionEntry.COLUMN_TEMPLATE + " = 0"
            + " AND a." + AccountEntry.COLUMN_CURRENCY + " != '" + Commodity.TEMPLATE + "'"
            + " AND s." + SplitEntry.COLUMN_QUANTITY_DENOM + " >= 1";

        boolean validStart = startTimestamp != -1;
        boolean validEnd = endTimestamp != -1;
        if (validStart && validEnd) {
            selection += " AND t." + TransactionEntry.COLUMN_TIMESTAMP + " BETWEEN " + startTimestamp + " AND " + endTimestamp;
        } else if (validEnd) {
            selection += " AND t." + TransactionEntry.COLUMN_TIMESTAMP + " <= " + endTimestamp;
        } else if (validStart) {
            selection += " AND t." + TransactionEntry.COLUMN_TIMESTAMP + " >= " + startTimestamp;
        }

        String sql = "SELECT SUM(s." + SplitEntry.COLUMN_QUANTITY_NUM + ")"
            + ", s." + SplitEntry.COLUMN_QUANTITY_DENOM
            + ", s." + SplitEntry.COLUMN_TYPE
            + ", a." + AccountEntry.COLUMN_COMMODITY_UID
            + " FROM " + TransactionEntry.TABLE_NAME + " t, "
            + SplitEntry.TABLE_NAME + " s ON t." + TransactionEntry.COLUMN_UID + "= s." + SplitEntry.COLUMN_TRANSACTION_UID + ", "
            + AccountEntry.TABLE_NAME + " a ON s." + SplitEntry.COLUMN_ACCOUNT_UID + " = a." + AccountEntry.COLUMN_UID
            + " WHERE " + selection
            + " GROUP BY a.commodity_uid, s.type, s.quantity_denom";
        Cursor cursor = mDb.rawQuery(sql, null);

        try {
            while (cursor.moveToNext()) {
                //FIXME beware of 64-bit overflow - get as BigInteger
                long amount_num = cursor.getLong(0);
                long amount_denom = cursor.getLong(1);
                String splitType = cursor.getString(2);
                String commodityUID = cursor.getString(3);

                if (credit.equals(splitType)) {
                    amount_num = -amount_num;
                }
                BigDecimal amount = toBigDecimal(amount_num, amount_denom);
                if (!commodityUID.equals(currencyUID)) {
                    // there is a second currency involved - get price, e.g. EUR -> ILS
                    // FIXME get the price for the transaction date.
                    Commodity commodity = commoditiesDbAdapter.getRecord(commodityUID);
                    Price price = pricesDbAdapter.getPrice(commodity, currency);
                    if (isNullOrEmpty(price)) {
                        // TODO Try with transaction currency, e.g. EUR -> ETB -> ILS
                        // no price exists, just ignore it
                        continue;
                    }
                    amount = amount.multiply(price.toBigDecimal());
                }
                total = total.add(amount);
            }
            return new Money(total, currency);
        } finally {
            cursor.close();
        }
    }

    /**
     * Returns the list of splits for a transaction
     *
     * @param transactionUID String unique ID of transaction
     * @return List of {@link org.gnucash.android.model.Split}s
     */
    public List<Split> getSplitsForTransaction(String transactionUID) {
        Cursor cursor = fetchSplitsForTransaction(transactionUID);
        return getRecords(cursor);
    }

    /**
     * Returns the list of splits for a transaction
     *
     * @param transactionID DB record ID of the transaction
     * @return List of {@link org.gnucash.android.model.Split}s
     * @see #getSplitsForTransaction(String)
     * @see #getTransactionUID(long)
     */
    public List<Split> getSplitsForTransaction(long transactionID) {
        return getSplitsForTransaction(getTransactionUID(transactionID));
    }

    /**
     * Fetch splits for a given transaction within a specific account
     *
     * @param transactionUID String unique ID of transaction
     * @param accountUID     String unique ID of account
     * @return List of splits
     */
    public List<Split> getSplitsForTransactionInAccount(String transactionUID, String accountUID) {
        Cursor cursor = fetchSplitsForTransactionAndAccount(transactionUID, accountUID);
        return getRecords(cursor);
    }

    /**
     * Fetches a collection of splits for a given condition and sorted by <code>sortOrder</code>
     *
     * @param where     String condition, formatted as SQL WHERE clause
     * @param whereArgs where args
     * @param sortOrder Sort order for the returned records
     * @return Cursor to split records
     */
    public Cursor fetchSplits(String where, String[] whereArgs, String sortOrder) {
        return mDb.query(SplitEntry.TABLE_NAME, null, where, whereArgs, null, null, sortOrder);
    }

    /**
     * Returns a Cursor to a dataset of splits belonging to a specific transaction
     *
     * @param transactionUID Unique idendtifier of the transaction
     * @return Cursor to splits
     */
    public Cursor fetchSplitsForTransaction(String transactionUID) {
        Timber.v("Fetching all splits for transaction UID %s", transactionUID);
        return mDb.query(SplitEntry.TABLE_NAME,
            null, SplitEntry.COLUMN_TRANSACTION_UID + " = ?",
            new String[]{transactionUID},
            null, null, null);
    }

    /**
     * Fetches splits for a given account
     *
     * @param accountUID String unique ID of account
     * @return Cursor containing splits dataset
     */
    public Cursor fetchSplitsForAccount(String accountUID) {
        Timber.d("Fetching all splits for account UID %s", accountUID);

        //This is more complicated than a simple "where account_uid=?" query because
        // we need to *not* return any splits which belong to recurring transactions
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables(TransactionEntry.TABLE_NAME
            + " INNER JOIN " + SplitEntry.TABLE_NAME + " ON "
            + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " = "
            + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID);
        queryBuilder.setDistinct(true);
        String[] projectionIn = new String[]{SplitEntry.TABLE_NAME + ".*"};
        String selection = SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID + " = ?"
            + " AND " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TEMPLATE + " = 0";
        String[] selectionArgs = new String[]{accountUID};
        String sortOrder = TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TIMESTAMP + " DESC";

        return queryBuilder.query(mDb, projectionIn, selection, selectionArgs, null, null, sortOrder);

    }

    /**
     * Returns a cursor to splits for a given transaction and account
     *
     * @param transactionUID Unique idendtifier of the transaction
     * @param accountUID     String unique ID of account
     * @return Cursor to splits data set
     */
    public Cursor fetchSplitsForTransactionAndAccount(String transactionUID, String accountUID) {
        if (transactionUID == null || accountUID == null)
            return null;

        Timber.v("Fetching all splits for transaction ID " + transactionUID
            + "and account ID " + accountUID);
        return mDb.query(SplitEntry.TABLE_NAME,
            null, SplitEntry.COLUMN_TRANSACTION_UID + " = ? AND "
                + SplitEntry.COLUMN_ACCOUNT_UID + " = ?",
            new String[]{transactionUID, accountUID},
            null, null, SplitEntry.COLUMN_VALUE_NUM + " ASC");
    }

    /**
     * Returns the unique ID of a transaction given the database record ID of same
     *
     * @param transactionId Database record ID of the transaction
     * @return String unique ID of the transaction or null if transaction with the ID cannot be found.
     */
    public String getTransactionUID(long transactionId) {
        Cursor cursor = mDb.query(TransactionEntry.TABLE_NAME,
            new String[]{TransactionEntry.COLUMN_UID},
            TransactionEntry._ID + " = " + transactionId,
            null, null, null, null);

        try {
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(TransactionEntry.COLUMN_UID));
            } else {
                throw new IllegalArgumentException("transaction " + transactionId + " does not exist");
            }
        } finally {
            cursor.close();
        }
    }

    @Override
    public boolean deleteRecord(long rowId) {
        Split split = getRecord(rowId);
        String transactionUID = split.getTransactionUID();
        boolean result = super.deleteRecord(rowId);

        if (!result) //we didn't delete for whatever reason, invalid rowId etc
            return false;

        //if we just deleted the last split, then remove the transaction from db
        Cursor cursor = fetchSplitsForTransaction(transactionUID);
        try {
            if (cursor.getCount() > 0) {
                long transactionID = getTransactionID(transactionUID);
                result = mDb.delete(TransactionEntry.TABLE_NAME,
                    TransactionEntry._ID + "=" + transactionID, null) > 0;
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    /**
     * Returns the database record ID for the specified transaction UID
     *
     * @param transactionUID Unique idendtifier of the transaction
     * @return Database record ID for the transaction
     */
    public long getTransactionID(String transactionUID) {
        Cursor c = mDb.query(TransactionEntry.TABLE_NAME,
            new String[]{TransactionEntry._ID},
            TransactionEntry.COLUMN_UID + "=?",
            new String[]{transactionUID}, null, null, null);
        try {
            if (c.moveToFirst()) {
                return c.getLong(0);
            } else {
                throw new IllegalArgumentException("transaction " + transactionUID + " does not exist");
            }
        } finally {
            c.close();
        }
    }

    public void reassignAccount(@NonNull String accountUID, @NonNull String newParentAccountUID) {
        updateRecords(
            SplitEntry.COLUMN_ACCOUNT_UID + " = ?",
            new String[]{accountUID},
            SplitEntry.COLUMN_ACCOUNT_UID,
            newParentAccountUID
        );
    }

    /**
     * Returns the commodity of the account
     * with unique Identifier <code>accountUID</code>
     *
     * @param accountUID Unique Identifier of the account
     * @return Commodity of the account.
     */
    public Commodity getCommodity(@NonNull String accountUID) {
        Cursor cursor = mDb.query(
            AccountEntry.TABLE_NAME,
            new String[]{AccountEntry.COLUMN_COMMODITY_UID},
            AccountEntry.COLUMN_UID + "= ?",
            new String[]{accountUID}, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                String commodityUID = cursor.getString(0);
                return commoditiesDbAdapter.getRecord(commodityUID);
            } else {
                throw new IllegalArgumentException("Account " + accountUID + " does not exist");
            }
        } finally {
            cursor.close();
        }
    }
}
