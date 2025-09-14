/*
 * Copyright (c) 2012 - 2015 Ngewi Fet <ngewif@gmail.com>
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

import static org.gnucash.android.db.DatabaseExtKt.getBigDecimal;
import static org.gnucash.android.db.DatabaseHelper.sqlEscapeLike;
import static org.gnucash.android.db.DatabaseSchema.AccountEntry;
import static org.gnucash.android.db.DatabaseSchema.BudgetAmountEntry;
import static org.gnucash.android.db.DatabaseSchema.BudgetEntry;
import static org.gnucash.android.db.DatabaseSchema.CommodityEntry;
import static org.gnucash.android.db.DatabaseSchema.PriceEntry;
import static org.gnucash.android.db.DatabaseSchema.RecurrenceEntry;
import static org.gnucash.android.db.DatabaseSchema.ScheduledActionEntry;
import static org.gnucash.android.db.DatabaseSchema.SplitEntry;
import static org.gnucash.android.db.DatabaseSchema.TransactionEntry;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteStatement;
import android.text.TextUtils;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseHolder;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Price;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.util.TimestampHelper;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import timber.log.Timber;

/**
 * Manages persistence of {@link Account}s in the database
 * Handles adding, modifying and deleting of account records.
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Yongxin Wang <fefe.wyx@gmail.com>
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
public class AccountsDbAdapter extends DatabaseAdapter<Account> {
    /**
     * Separator used for account name hierarchies between parent and child accounts
     */
    public static final String ACCOUNT_NAME_SEPARATOR = ":";

    /**
     * ROOT account full name.
     * should ensure the ROOT account's full name will always sort before any other
     * account's full name.
     */
    public static final String ROOT_ACCOUNT_FULL_NAME = " ";
    public static final String ROOT_ACCOUNT_NAME = "Root Account";
    public static final String TEMPLATE_ACCOUNT_NAME = "Template Root";

    public static final long ALWAYS = -1L;

    /**
     * Transactions database adapter for manipulating transactions associated with accounts
     */
    @NonNull
    public final TransactionsDbAdapter transactionsDbAdapter;

    /**
     * Commodities database adapter for commodity manipulation
     */
    @NonNull
    public final CommoditiesDbAdapter commoditiesDbAdapter;
    @NonNull
    final PricesDbAdapter pricesDbAdapter;

    @Nullable
    private String rootUID = null;

    /**
     * Overloaded constructor. Creates an adapter for an already open database
     */
    public AccountsDbAdapter(@NonNull TransactionsDbAdapter transactionsDbAdapter) {
        this(transactionsDbAdapter, new PricesDbAdapter(transactionsDbAdapter.commoditiesDbAdapter));
    }

    /**
     * Overloaded constructor. Creates an adapter for an already open database
     */
    public AccountsDbAdapter(@NonNull TransactionsDbAdapter transactionsDbAdapter, @NonNull PricesDbAdapter pricesDbAdapter) {
        super(transactionsDbAdapter.holder, AccountEntry.TABLE_NAME, new String[]{
            AccountEntry.COLUMN_NAME,
            AccountEntry.COLUMN_DESCRIPTION,
            AccountEntry.COLUMN_TYPE,
            AccountEntry.COLUMN_CURRENCY,
            AccountEntry.COLUMN_COLOR_CODE,
            AccountEntry.COLUMN_FAVORITE,
            AccountEntry.COLUMN_FULL_NAME,
            AccountEntry.COLUMN_PLACEHOLDER,
            AccountEntry.COLUMN_CREATED_AT,
            AccountEntry.COLUMN_HIDDEN,
            AccountEntry.COLUMN_COMMODITY_UID,
            AccountEntry.COLUMN_PARENT_ACCOUNT_UID,
            AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID,
            AccountEntry.COLUMN_NOTES,
            AccountEntry.COLUMN_TEMPLATE
        }, true);
        this.transactionsDbAdapter = transactionsDbAdapter;
        this.commoditiesDbAdapter = transactionsDbAdapter.commoditiesDbAdapter;
        this.pricesDbAdapter = pricesDbAdapter;
    }

    /**
     * Convenience overloaded constructor.
     * This is used when an AccountsDbAdapter object is needed quickly. Otherwise, the other
     * constructor {@link #AccountsDbAdapter(TransactionsDbAdapter)}
     * should be used whenever possible
     *
     * @param holder Database holder
     */
    public AccountsDbAdapter(@NonNull DatabaseHolder holder) {
        this(new TransactionsDbAdapter(holder));
    }

    /**
     * Returns an application-wide instance of this database adapter
     *
     * @return Instance of Accounts db adapter
     */
    public static AccountsDbAdapter getInstance() {
        return GnuCashApplication.getAccountsDbAdapter();
    }

    @Override
    public void close() throws IOException {
        commoditiesDbAdapter.close();
        transactionsDbAdapter.close();
        pricesDbAdapter.close();
        super.close();
    }

    /**
     * Adds an account to the database.
     * If an account already exists in the database with the same GUID, it is replaced.
     *
     * @param account {@link Account} to be inserted to database
     */
    @Override
    public void addRecord(@NonNull Account account, UpdateMethod updateMethod) throws SQLException {
        Timber.d("Replace account to db");
        if (account.isRoot() && !account.isTemplate()) {
            rootUID = account.getUID();
        }
        //in-case the account already existed, we want to update the templates based on it as well
        super.addRecord(account, updateMethod);
        //now add transactions if there are any
        // NB! Beware of transactions that reference accounts not yet in the db,
        if (!account.isRoot()) {
            for (Transaction t : account.getTransactions()) {
                t.setCommodity(account.getCommodity());
                transactionsDbAdapter.addRecord(t, updateMethod);
            }
            List<Transaction> scheduledTransactions = transactionsDbAdapter.getScheduledTransactionsForAccount(account.getUID());
            for (Transaction transaction : scheduledTransactions) {
                transactionsDbAdapter.addRecord(transaction, UpdateMethod.update);
            }
        }
    }

    /**
     * Adds some accounts and their transactions to the database in bulk.
     * <p>If an account already exists in the database with the same GUID, it is replaced.
     * This function will NOT try to determine the full name
     * of the accounts inserted, full names should be generated prior to the insert.
     * <br>All or none of the accounts will be inserted;</p>
     *
     * @param accountList {@link Account} to be inserted to database
     * @return number of rows inserted
     */
    @Override
    public long bulkAddRecords(@NonNull List<Account> accountList, UpdateMethod updateMethod) {
        //scheduled transactions are not fetched from the database when getting account transactions
        //so we retrieve those which affect this account and then re-save them later
        //this is necessary because the database has ON DELETE CASCADE between accounts and splits
        //and all accounts are editing via SQL REPLACE

        //// TODO: 20.04.2016 Investigate if we can safely remove updating the transactions when bulk updating accounts
        List<Transaction> transactionList = new ArrayList<>(accountList.size() * 2);
        for (Account account : accountList) {
            transactionList.addAll(account.getTransactions());
            transactionList.addAll(transactionsDbAdapter.getScheduledTransactionsForAccount(account.getUID()));
        }
        long nRow = super.bulkAddRecords(accountList, updateMethod);

        if (nRow > 0 && !transactionList.isEmpty()) {
            transactionsDbAdapter.bulkAddRecords(transactionList, updateMethod);
        }
        return nRow;
    }

    @Override
    protected @NonNull SQLiteStatement bind(@NonNull SQLiteStatement stmt, @NonNull final Account account) throws SQLException {
        String parentAccountUID = account.getParentUID();
        if (!account.isRoot()) {
            if (TextUtils.isEmpty(parentAccountUID)) {
                parentAccountUID = getOrCreateRootAccountUID();
                account.setParentUID(parentAccountUID);
            }
            //update the fully qualified account name
            account.setFullName(getFullyQualifiedAccountName(account));
        }

        bindBaseModel(stmt, account);
        stmt.bindString(1, account.getName());
        stmt.bindString(2, account.getDescription());
        stmt.bindString(3, account.getAccountType().name());
        stmt.bindString(4, account.getCommodity().getCurrencyCode());
        if (account.getColor() != Account.DEFAULT_COLOR) {
            stmt.bindString(5, account.getColorHexString());
        }
        stmt.bindLong(6, account.isFavorite() ? 1 : 0);
        stmt.bindString(7, account.getFullName());
        stmt.bindLong(8, account.isPlaceholder() ? 1 : 0);
        stmt.bindString(9, TimestampHelper.getUtcStringFromTimestamp(account.getCreatedTimestamp()));
        stmt.bindLong(10, account.isHidden() ? 1 : 0);
        stmt.bindString(11, account.getCommodity().getUID());
        if (parentAccountUID != null) {
            stmt.bindString(12, parentAccountUID);
        }
        if (account.getDefaultTransferAccountUID() != null) {
            stmt.bindString(13, account.getDefaultTransferAccountUID());
        }
        if (account.getNote() != null) {
            stmt.bindString(14, account.getNote());
        }
        stmt.bindLong(15, account.isTemplate() ? 1 : 0);

        return stmt;
    }

    /**
     * Marks all transactions for a given account as exported
     *
     * @param accountUID Unique ID of the record to be marked as exported
     * @return Number of records marked as exported
     */
    public int markAsExported(String accountUID) {
        if (isCached) cache.clear();
        ContentValues contentValues = new ContentValues();
        contentValues.put(TransactionEntry.COLUMN_EXPORTED, 1);
        return mDb.update(
            TransactionEntry.TABLE_NAME,
            contentValues,
            TransactionEntry.COLUMN_UID + " IN ("
                + "SELECT DISTINCT " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID
                + " FROM " + TransactionEntry.TABLE_NAME + ", " + SplitEntry.TABLE_NAME + " ON "
                + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " = "
                + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID + ", "
                + AccountEntry.TABLE_NAME + " ON " + SplitEntry.TABLE_NAME + "."
                + SplitEntry.COLUMN_ACCOUNT_UID + " = " + AccountEntry.TABLE_NAME + "."
                + AccountEntry.COLUMN_UID + " WHERE " + AccountEntry.TABLE_NAME + "."
                + AccountEntry.COLUMN_UID + " = ?"
                + ")",
            new String[]{accountUID}
        );
    }

    /**
     * This feature goes through all the rows in the accounts and changes value for <code>columnKey</code> to <code>newValue</code><br/>
     * The <code>newValue</code> parameter is taken as string since SQLite typically stores everything as text.
     * <p><b>This method affects all rows, exercise caution when using it</b></p>
     *
     * @param columnKey Name of column to be updated
     * @param newValue  New value to be assigned to the columnKey
     * @return Number of records affected
     */
    public int updateAllAccounts(String columnKey, String newValue) {
        if (isCached) cache.clear();
        ContentValues contentValues = new ContentValues();
        if (newValue == null) {
            contentValues.putNull(columnKey);
        } else {
            contentValues.put(columnKey, newValue);
        }
        return mDb.update(AccountEntry.TABLE_NAME, contentValues, null, null);
    }

    /**
     * Updates a specific entry of an account
     *
     * @param accountId Database record ID of the account to be updated
     * @param columnKey Name of column to be updated
     * @param newValue  New value to be assigned to the columnKey
     * @return Number of records affected
     */
    public int updateAccount(long accountId, String columnKey, String newValue) {
        return updateRecord(mTableName, accountId, columnKey, newValue);
    }

    /**
     * This method goes through all the children of {@code accountUID} and updates the parent account
     * to {@code newParentAccountUID}. The fully qualified account names for all descendant accounts will also be updated.
     *
     * @param parentAccountUID    GUID of the account
     * @param newParentAccountUID GUID of the new parent account
     */
    public void reassignDescendantAccounts(@NonNull String parentAccountUID, @NonNull String newParentAccountUID) {
        if (isCached) cache.clear();
        List<String> descendantAccountUIDs = getDescendantAccountUIDs(parentAccountUID, null, null);
        if (descendantAccountUIDs.isEmpty()) return;
        List<Account> descendantAccounts = getSimpleAccounts(
            AccountEntry.COLUMN_UID + " IN ('" + TextUtils.join("','", descendantAccountUIDs) + "')",
            null,
            null
        );
        Map<String, Account> accountsByUID = new HashMap<>();
        for (Account account : descendantAccounts) {
            accountsByUID.put(account.getUID(), account);
        }
        String parentAccountFullName;
        if (getAccountType(newParentAccountUID) == AccountType.ROOT) {
            parentAccountFullName = "";
        } else {
            parentAccountFullName = getAccountFullName(newParentAccountUID);
        }
        ContentValues contentValues = new ContentValues();
        for (Account account : descendantAccounts) {
            contentValues.clear();

            if (parentAccountUID.equals(account.getParentUID())) {
                // direct descendant
                account.setParentUID(newParentAccountUID);
                if (TextUtils.isEmpty(parentAccountFullName)) {
                    account.setFullName(account.getName());
                } else {
                    account.setFullName(parentAccountFullName + ACCOUNT_NAME_SEPARATOR + account.getName());
                }
                contentValues.put(AccountEntry.COLUMN_PARENT_ACCOUNT_UID, newParentAccountUID);
            } else {
                // indirect descendant
                Account parentAccount = accountsByUID.get(account.getParentUID());
                account.setFullName(parentAccount.getFullName() + ACCOUNT_NAME_SEPARATOR + account.getName());
            }
            // update DB
            contentValues.put(AccountEntry.COLUMN_FULL_NAME, account.getFullName());
            mDb.update(
                mTableName,
                contentValues,
                AccountEntry.COLUMN_UID + " = ?",
                new String[]{account.getUID()}
            );
        }
    }

    /**
     * Deletes an account and its transactions, and all its sub-accounts and their transactions.
     * <p>Not only the splits belonging to the account and its descendants will be deleted, rather,
     * the complete transactions associated with this account and its descendants
     * (i.e. as long as the transaction has at least one split belonging to one of the accounts).
     * This prevents an split imbalance from being caused.</p>
     * <p>If you want to preserve transactions, make sure to first reassign the children accounts (see {@link #reassignDescendantAccounts(String, String)}
     * before calling this method. This method will however not delete a root account. </p>
     * <p><b>This method does a thorough delete, use with caution!!!</b></p>
     *
     * @param accountUID Database UID of account
     * @return <code>true</code> if the account and sub-accounts were all successfully deleted, <code>false</code> if
     * even one was not deleted
     * @see #reassignDescendantAccounts(String, String)
     */
    public boolean recursiveDeleteAccount(String accountUID) {
        if (getAccountType(accountUID) == AccountType.ROOT) {
            // refuse to delete ROOT
            return false;
        }

        Timber.d("Delete account with rowId with its transactions and sub-accounts: %s", accountUID);
        if (isCached) cache.clear();

        List<String> descendantAccountUIDs = getDescendantAccountUIDs(accountUID, null, null);
        try {
            beginTransaction();
            descendantAccountUIDs.add(accountUID); //add account to descendants list just for convenience
            for (String descendantAccountUID : descendantAccountUIDs) {
                transactionsDbAdapter.deleteTransactionsForAccount(descendantAccountUID);
            }

            String accountUIDList = "'" + TextUtils.join("','", descendantAccountUIDs) + "'";

            // delete accounts
            long deletedCount = mDb.delete(
                mTableName,
                AccountEntry.COLUMN_UID + " IN (" + accountUIDList + ")",
                null
            );

            //if we delete some accounts, reset the default transfer account to NULL
            //there is also a database trigger from db version > 12
            if (deletedCount > 0) {
                ContentValues contentValues = new ContentValues();
                contentValues.putNull(AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID);
                mDb.update(mTableName, contentValues,
                    AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID + " IN (" + accountUIDList + ")",
                    null);
            }

            setTransactionSuccessful();
            return true;
        } finally {
            endTransaction();
        }
    }

    /**
     * Builds an account instance with the provided cursor and loads its corresponding transactions.
     *
     * @param c Cursor pointing to account record in database
     * @return {@link Account} object constructed from database record
     */
    @Override
    public Account buildModelInstance(@NonNull final Cursor c) {
        Account account = buildSimpleAccountInstance(c);
        account.setTransactions(transactionsDbAdapter.getAllTransactionsForAccount(account.getUID()));
        return account;
    }

    /**
     * Builds an account instance with the provided cursor and loads its corresponding transactions.
     * <p>The method will not move the cursor position, so the cursor should already be pointing
     * to the account record in the database<br/>
     * <b>Note</b> Unlike {@link  #buildModelInstance(android.database.Cursor)} this method will not load transactions</p>
     *
     * @param c Cursor pointing to account record in database
     * @return {@link Account} object constructed from database record
     */
    public Account buildSimpleAccountInstance(Cursor c) {
        Account account = new Account(c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_NAME)));
        populateBaseModelAttributes(c, account);

        account.setDescription(c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_DESCRIPTION)));
        account.setParentUID(c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_PARENT_ACCOUNT_UID)));
        account.setAccountType(AccountType.valueOf(c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_TYPE))));
        String commodityUID = c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_COMMODITY_UID));
        account.setCommodity(commoditiesDbAdapter.getRecord(commodityUID));
        account.setPlaceholder(c.getInt(c.getColumnIndexOrThrow(AccountEntry.COLUMN_PLACEHOLDER)) != 0);
        account.setDefaultTransferAccountUID(c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID)));
        String color = c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_COLOR_CODE));
        if (!TextUtils.isEmpty(color))
            account.setColor(color);
        account.setFavorite(c.getInt(c.getColumnIndexOrThrow(AccountEntry.COLUMN_FAVORITE)) != 0);
        account.setFullName(c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_FULL_NAME)));
        account.setHidden(c.getInt(c.getColumnIndexOrThrow(AccountEntry.COLUMN_HIDDEN)) != 0);
        if (account.isRoot()) {
            account.setHidden(false);
        }
        account.setNote(c.getString(c.getColumnIndexOrThrow(AccountEntry.COLUMN_NOTES)));
        account.setTemplate(c.getInt(c.getColumnIndexOrThrow(AccountEntry.COLUMN_TEMPLATE)) != 0);
        if (account.isRoot()) {
            account.setHidden(false);
            account.setPlaceholder(false);
        }
        return account;
    }

    /**
     * Returns the  unique ID of the parent account of the account with unique ID <code>uid</code>
     * If the account has no parent, null is returned
     *
     * @param uid Unique Identifier of account whose parent is to be returned. Should not be null
     * @return DB record UID of the parent account, null if the account has no parent
     */
    public String getParentAccountUID(@NonNull String uid) {
        if (isCached) {
            Account account = cache.get(uid);
            if (account != null) return account.getParentUID();
        }
        Cursor cursor = mDb.query(
            mTableName,
            new String[]{AccountEntry.COLUMN_PARENT_ACCOUNT_UID},
            AccountEntry.COLUMN_UID + " = ?",
            new String[]{uid},
            null, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    /**
     * Returns the color code for the account in format #rrggbb
     *
     * @param accountUID UID of the account
     * @return String color code of account or null if none
     */
    @ColorInt
    public int getAccountColor(String accountUID) {
        try {
            Account account = getSimpleRecord(accountUID);
            return (account != null) ? account.getColor() : Account.DEFAULT_COLOR;
        } catch (IllegalArgumentException e) {
            Timber.e(e);
            return Account.DEFAULT_COLOR;
        }
    }

    /**
     * Overloaded method. Resolves the account unique ID from the row ID and makes a call to {@link #getAccountType(String)}
     *
     * @param accountId Database row ID of the account
     * @return {@link AccountType} of the account
     */
    public AccountType getAccountType(long accountId) {
        return getAccountType(getUID(accountId));
    }

    /**
     * Returns a list of all account entries in the system (includes root account)
     * No transactions are loaded, just the accounts
     *
     * @return List of {@link Account}s in the database
     */
    public List<Account> getSimpleAccounts() {
        return getSimpleAccounts(null, null, null);
    }

    /**
     * Returns a list of all account entries in the system (includes root account)
     * No transactions are loaded, just the accounts
     *
     * @return List of {@link Account}s in the database
     */
    public List<Account> getSimpleAccounts(@Nullable String where, @Nullable String[] whereArgs, @Nullable String orderBy) {
        if (orderBy == null) {
            orderBy = AccountEntry.COLUMN_FULL_NAME + " ASC";
        }
        List<Account> accounts = new ArrayList<>();
        Cursor cursor = fetchAccounts(where, whereArgs, orderBy);
        if (cursor == null) return accounts;

        try {
            if (cursor.moveToFirst()) {
                do {
                    Account account = buildSimpleAccountInstance(cursor);
                    accounts.add(account);
                    if (isCached) {
                        cache.put(account.getUID(), account);
                    }
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        return accounts;
    }

    /**
     * Returns a list of accounts which have transactions that have not been exported yet
     *
     * @param lastExportTimeStamp Timestamp after which to any transactions created/modified should be exported
     * @return List of {@link Account}s with unexported transactions
     */
    public List<Account> getExportableAccounts(Timestamp lastExportTimeStamp) {
        Cursor cursor = mDb.query(
            TransactionEntry.TABLE_NAME + ", " + SplitEntry.TABLE_NAME +
                " ON " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " = " +
                SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID + ", " +
                AccountEntry.TABLE_NAME + " ON " + AccountEntry.TABLE_NAME + "." +
                AccountEntry.COLUMN_UID + " = " + SplitEntry.TABLE_NAME + "." +
                SplitEntry.COLUMN_ACCOUNT_UID,
            new String[]{AccountEntry.TABLE_NAME + ".*"},
            TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_MODIFIED_AT + " > ?",
            new String[]{TimestampHelper.getUtcStringFromTimestamp(lastExportTimeStamp)},
            AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_UID,
            null,
            null
        );
        return getRecords(cursor);
    }

    /**
     * Retrieves the unique ID of the imbalance account for a particular currency (creates the imbalance account
     * on demand if necessary)
     *
     * @param commodity Commodity for the imbalance account
     * @return String unique ID of the account
     */
    public String getOrCreateImbalanceAccountUID(@NonNull Context context, @NonNull Commodity commodity) {
        return getOrCreateImbalanceAccount(context, commodity).getUID();
    }

    /**
     * Retrieves the unique ID of the imbalance account for a particular currency (creates the imbalance account
     * on demand if necessary)
     *
     * @param commodity Commodity for the imbalance account
     * @return The account
     */
    public Account getOrCreateImbalanceAccount(@NonNull Context context, @NonNull Commodity commodity) {
        String imbalanceAccountName = getImbalanceAccountName(context, commodity);
        String uid = findAccountUidByFullName(imbalanceAccountName);
        if (TextUtils.isEmpty(uid)) {
            Account account = new Account(imbalanceAccountName, commodity);
            account.setAccountType(AccountType.BANK);
            account.setParentUID(getOrCreateRootAccountUID());
            account.setHidden(!GnuCashApplication.isDoubleEntryEnabled(context));
            addRecord(account, UpdateMethod.insert);
            return account;
        }
        return getSimpleRecord(uid);
    }

    /**
     * Returns the GUID of the imbalance account for the commodity
     *
     * <p>This method will not create the imbalance account if it doesn't exist</p>
     *
     * @param commodity Commodity for the imbalance account
     * @return GUID of the account or null if the account doesn't exist yet
     * @see #getOrCreateImbalanceAccountUID(Context, Commodity)
     */
    public String getImbalanceAccountUID(@NonNull Context context, @NonNull Commodity commodity) {
        String imbalanceAccountName = getImbalanceAccountName(context, commodity);
        return findAccountUidByFullName(imbalanceAccountName);
    }

    /**
     * Creates the account with the specified name and returns its unique identifier.
     * <p>If a full hierarchical account name is provided, then the whole hierarchy is created and the
     * unique ID of the last account (at bottom) of the hierarchy is returned</p>
     *
     * @param fullName    Fully qualified name of the account
     * @param accountType Type to assign to all accounts created
     * @return String unique ID of the account at bottom of hierarchy
     */
    public String createAccountHierarchy(String fullName, AccountType accountType) {
        if (TextUtils.isEmpty(fullName)) {
            throw new IllegalArgumentException("Full name required");
        }
        String[] tokens = fullName.trim().split(ACCOUNT_NAME_SEPARATOR);
        String uid = getOrCreateRootAccountUID();
        String parentName = "";
        ArrayList<Account> accountsList = new ArrayList<>();
        Commodity commodity = commoditiesDbAdapter.getDefaultCommodity();
        for (String token : tokens) {
            parentName += token;
            String parentUID = findAccountUidByFullName(parentName);
            if (parentUID != null) { //the parent account exists, don't recreate
                uid = parentUID;
            } else {
                Account account = new Account(token, commodity);
                account.setAccountType(accountType);
                account.setParentUID(uid); //set its parent
                account.setFullName(parentName);
                accountsList.add(account);
                uid = account.getUID();
            }
            parentName += ACCOUNT_NAME_SEPARATOR;
        }
        if (accountsList.size() > 0) {
            bulkAddRecords(accountsList, UpdateMethod.insert);
        }
        // if fullName is not empty, loop will be entered and then uid will never be null
        return uid;
    }

    /**
     * Returns the unique ID of the opening balance account or creates one if necessary
     *
     * @return String unique ID of the opening balance account
     */
    public String getOrCreateOpeningBalanceAccountUID() {
        String openingBalanceAccountName = getOpeningBalanceAccountFullName();
        String uid = findAccountUidByFullName(openingBalanceAccountName);
        if (uid == null) {
            uid = createAccountHierarchy(openingBalanceAccountName, AccountType.EQUITY);
        }
        return uid;
    }

    /**
     * Finds an account unique ID by its full name
     *
     * @param fullName Fully qualified name of the account
     * @return String unique ID of the account or null if no match is found
     */
    public String findAccountUidByFullName(String fullName) {
        if (isCached) {
            for (Account account : cache.values()) {
                if (account.getFullName().equals(fullName)) {
                    return account.getUID();
                }
            }
        }
        Cursor c = mDb.query(AccountEntry.TABLE_NAME, new String[]{AccountEntry.COLUMN_UID},
            AccountEntry.COLUMN_FULL_NAME + "= ?", new String[]{fullName},
            null, null, null, "1");
        try {
            if (c.moveToNext()) {
                return c.getString(0);
            } else {
                return null;
            }
        } finally {
            c.close();
        }
    }

    /**
     * Returns a cursor to all account records in the database.
     * GnuCash ROOT accounts and hidden accounts will <b>not</b> be included in the result set
     *
     * @return {@link Cursor} to all account records
     */
    @Override
    public Cursor fetchAllRecords() {
        Timber.v("Fetching all accounts from db");
        String where = AccountEntry.COLUMN_HIDDEN + " = 0 AND " + AccountEntry.COLUMN_TYPE + " != ?";
        String[] whereArgs = new String[]{AccountType.ROOT.name()};
        String orderBy = AccountEntry.COLUMN_NAME + " ASC";
        return fetchAccounts(where, whereArgs, orderBy);
    }

    /**
     * Returns a Cursor set of accounts which fulfill <code>where</code>
     * and ordered by <code>orderBy</code>
     *
     * @param where     SQL WHERE statement without the 'WHERE' itself
     * @param whereArgs args to where clause
     * @param orderBy   orderBy clause
     * @return Cursor set of accounts which fulfill <code>where</code>
     */
    public Cursor fetchAccounts(@Nullable String where, @Nullable String[] whereArgs, @Nullable String orderBy) {
        if (TextUtils.isEmpty(orderBy)) {
            orderBy = AccountEntry.COLUMN_NAME + " ASC";
        }
        return fetchAllRecords(where, whereArgs, orderBy);
    }

    /**
     * Returns the balance of an account while taking sub-accounts into consideration
     *
     * @return Account Balance of an account including sub-accounts
     */
    public Money getAccountBalance(String accountUID) {
        return computeBalance(accountUID, ALWAYS, ALWAYS, true);
    }

    /**
     * Returns the balance of an account while taking sub-accounts into consideration
     *
     * @return Account Balance of an account including sub-accounts
     */
    public Money getAccountBalance(Account account) {
        return getAccountBalance(account, ALWAYS, ALWAYS);
    }

    /**
     * Returns the current balance of an account while taking sub-accounts into consideration
     *
     * @return Account Balance of an account including sub-accounts
     */
    public Money getCurrentAccountBalance(Account account) {
        return getAccountBalance(account, ALWAYS, System.currentTimeMillis());
    }

    /**
     * Returns the balance of an account within the specified time range while taking sub-accounts into consideration
     *
     * @param accountUID     the account's UUID
     * @param startTimestamp the start timestamp of the time range
     * @param endTimestamp   the end timestamp of the time range
     * @return the balance of an account within the specified range including sub-accounts
     */
    public Money getAccountBalance(String accountUID, long startTimestamp, long endTimestamp) {
        return getAccountBalance(accountUID, startTimestamp, endTimestamp, true);
    }

    /**
     * Returns the balance of an account within the specified time range while taking sub-accounts into consideration
     *
     * @param account        the account
     * @param startTimestamp the start timestamp of the time range
     * @param endTimestamp   the end timestamp of the time range
     * @return the balance of an account within the specified range including sub-accounts
     */
    public Money getAccountBalance(Account account, long startTimestamp, long endTimestamp) {
        return getAccountBalance(account, startTimestamp, endTimestamp, true);
    }

    /**
     * Returns the balance of an account within the specified time range while taking sub-accounts into consideration
     *
     * @param accountUID         the account's UUID
     * @param startTimestamp     the start timestamp of the time range
     * @param endTimestamp       the end timestamp of the time range
     * @param includeSubAccounts include the sub-accounts' balances?
     * @return the balance of an account within the specified range including sub-accounts
     */
    public Money getAccountBalance(String accountUID, long startTimestamp, long endTimestamp, boolean includeSubAccounts) {
        return computeBalance(accountUID, startTimestamp, endTimestamp, includeSubAccounts);
    }

    /**
     * Returns the balance of an account within the specified time range while taking sub-accounts into consideration
     *
     * @param account            the account
     * @param startTimestamp     the start timestamp of the time range
     * @param endTimestamp       the end timestamp of the time range
     * @param includeSubAccounts include the sub-accounts' balances?
     * @return the balance of an account within the specified range including sub-accounts
     */
    @NonNull
    public Money getAccountBalance(Account account, long startTimestamp, long endTimestamp, boolean includeSubAccounts) {
        return computeBalance(account, startTimestamp, endTimestamp, includeSubAccounts);
    }

    /**
     * Compute the account balance for all accounts with the specified type within a specific duration
     *
     * @param accountType    Account Type for which to compute balance
     * @param currency       the currency
     * @param startTimestamp Begin time for the duration in milliseconds
     * @param endTimestamp   End time for duration in milliseconds
     * @return Account balance
     */
    public Money getAccountsBalance(AccountType accountType, Commodity currency, long startTimestamp, long endTimestamp) {
        String where = AccountEntry.COLUMN_TYPE + " = ?"
            + " AND " + AccountEntry.COLUMN_TEMPLATE + " = 0";
        String[] whereArgs = new String[]{accountType.name()};
        List<Account> accounts = getSimpleAccounts(where, whereArgs, null);
        return getAccountsBalance(accounts, currency, startTimestamp, endTimestamp);
    }

    /**
     * Returns the account balance for all accounts types specified
     *
     * @param accountTypes List of account types
     * @param currency     The currency
     * @param start        Begin timestamp for transactions
     * @param end          End timestamp of transactions
     * @return Money balance of the account types
     */
    public Money getBalancesByType(List<AccountType> accountTypes, Commodity currency, long start, long end) {
        Money balance = Money.createZeroInstance(currency);
        for (AccountType accountType : accountTypes) {
            Money accountsBalance = getAccountsBalance(accountType, currency, start, end);
            balance = balance.plus(accountsBalance);
        }
        return balance;
    }

    /**
     * Returns the current account balance for the accounts type.
     *
     * @param accountTypes The account type
     * @param currency     The currency.
     * @return Money balance of the account type
     */
    public Money getCurrentAccountsBalance(List<AccountType> accountTypes, Commodity currency) {
        return getBalancesByType(accountTypes, currency, ALWAYS, System.currentTimeMillis());
    }

    private Money computeBalance(@NonNull String accountUID, long startTimestamp, long endTimestamp, boolean includeSubAccounts) {
        Account account = getSimpleRecord(accountUID);
        return computeBalance(account, startTimestamp, endTimestamp, includeSubAccounts);
    }

    @NonNull
    private Money computeBalance(@NonNull Account account, long startTimestamp, long endTimestamp, boolean includeSubAccounts) {
        Timber.d("Computing account balance for [%s]", account);
        String accountUID = account.getUID();
        String[] columns = new String[]{AccountEntry.COLUMN_BALANCE};
        String selection = AccountEntry.COLUMN_UID + "=?";
        String[] selectionArgs = new String[]{accountUID};

        // Is the value cached?
        boolean useCachedValue = (startTimestamp == ALWAYS) && (endTimestamp == ALWAYS);
        if (useCachedValue) {
            Cursor cursor = mDb.query(mTableName, columns, selection, selectionArgs, null, null, null);
            try {
                if (cursor.moveToFirst()) {
                    BigDecimal amount = getBigDecimal(cursor, 0);
                    if (amount != null) {
                        return new Money(amount, account.getCommodity());
                    }
                }
            } finally {
                cursor.close();
            }
        }

        Money balance = computeSplitsBalance(account, startTimestamp, endTimestamp);

        if (includeSubAccounts) {
            Commodity commodity = account.getCommodity();
            List<String> children = getChildren(accountUID);
            Timber.d("compute account children : %d", children.size());
            for (String childUID : children) {
                Account child = getSimpleRecord(childUID);
                final Commodity childCommodity = child.getCommodity();
                Money childBalance = computeBalance(child, startTimestamp, endTimestamp, true);
                if (childBalance.isAmountZero()) continue;
                Price price = pricesDbAdapter.getPrice(childCommodity, commodity);
                if (price == null) continue;
                childBalance = childBalance.times(price);
                balance = balance.plus(childBalance);
            }
        }

        // Cache for next read.
        if (useCachedValue) {
            ContentValues values = new ContentValues();
            values.put(AccountEntry.COLUMN_BALANCE, balance.toBigDecimal().toString());
            mDb.update(mTableName, values, selection, selectionArgs);
        }

        return balance;
    }

    @NonNull
    private Money computeSplitsBalance(Account account, long startTimestamp, long endTimestamp) {
        AccountType accountType = account.getAccountType();
        SplitsDbAdapter splitsDbAdapter = transactionsDbAdapter.splitsDbAdapter;
        Money balance = splitsDbAdapter.computeSplitBalance(account, startTimestamp, endTimestamp);
        return accountType.hasDebitNormalBalance ? balance : balance.unaryMinus();
    }

    /**
     * Returns the balance of account list within the specified time range. The default currency
     * takes as base currency.
     *
     * @param accountUIDList list of account UIDs
     * @param startTimestamp the start timestamp of the time range
     * @param endTimestamp   the end timestamp of the time range
     * @return Money balance of account list
     */
    public Money getAccountsBalanceByUID(@NonNull List<String> accountUIDList, long startTimestamp, long endTimestamp) {
        List<Account> accounts = new ArrayList<>();
        for (String accountUID : accountUIDList) {
            accounts.add(getSimpleRecord(accountUID));
        }
        return getAccountsBalance(accounts, startTimestamp, endTimestamp);
    }

    /**
     * Returns the balance of account list within the specified time range. The default currency
     * takes as base currency.
     *
     * @param accounts       list of accounts
     * @param startTimestamp the start timestamp of the time range
     * @param endTimestamp   the end timestamp of the time range
     * @return Money balance of account list
     */
    public Money getAccountsBalance(@NonNull List<Account> accounts, long startTimestamp, long endTimestamp) {
        String currencyCode = GnuCashApplication.getDefaultCurrencyCode();
        Commodity commodity = commoditiesDbAdapter.getCurrency(currencyCode);
        return getAccountsBalance(accounts, commodity, startTimestamp, endTimestamp);
    }

    /**
     * Returns the balance of account list within the specified time range. The default currency
     * takes as base currency.
     *
     * @param accounts       list of accounts
     * @param currency       The target currency
     * @param startTimestamp the start timestamp of the time range
     * @param endTimestamp   the end timestamp of the time range
     * @return Money balance of account list
     */
    public Money getAccountsBalance(@NonNull List<Account> accounts, Commodity currency, long startTimestamp, long endTimestamp) {
        Money balance = Money.createZeroInstance(currency);
        if ((startTimestamp == ALWAYS) && (endTimestamp == ALWAYS)) { // Use cached balances.
            for (Account account : accounts) {
                Money accountBalance = getAccountBalance(account, startTimestamp, endTimestamp, false);
                if (accountBalance.isAmountZero()) continue;
                Price price = pricesDbAdapter.getPrice(accountBalance.getCommodity(), currency);
                if (price == null) continue;
                accountBalance = accountBalance.times(price);
                balance = balance.plus(accountBalance);
            }
        } else {
            Map<String, Money> balances = getAccountsBalances(accounts, startTimestamp, endTimestamp);
            for (Account account : accounts) {
                Money accountBalance = balances.get(account.getUID());
                if ((accountBalance == null) || accountBalance.isAmountZero()) continue;
                Price price = pricesDbAdapter.getPrice(accountBalance.getCommodity(), currency);
                if (price == null) continue;
                accountBalance = accountBalance.times(price);
                balance = balance.plus(accountBalance);
            }
        }
        return balance;
    }

    /**
     * Retrieve all descendant accounts of an account
     * Note, in filtering, once an account is filtered out, all its descendants
     * will also be filtered out, even they don't meet the filter where
     *
     * @param accountUID The account to retrieve descendant accounts
     * @param where      Condition to filter accounts
     * @param whereArgs  Condition args to filter accounts
     * @return The descendant accounts list.
     */
    @NonNull
    public List<String> getDescendantAccountUIDs(String accountUID, String where, String[] whereArgs) {
        // holds accountUID with all descendant accounts.
        List<String> accounts = new ArrayList<>();
        // holds descendant accounts of the same level
        List<String> accountsLevel = new ArrayList<>();
        final String[] projection = new String[]{AccountEntry.COLUMN_UID};
        final String whereAnd = (TextUtils.isEmpty(where) ? "" : " AND " + where);
        final int columnIndexUID = 0;

        accountsLevel.add(accountUID);
        do {
            Cursor cursor = mDb.query(
                mTableName,
                projection,
                AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " IN ('" + TextUtils.join("','", accountsLevel) + "')" + whereAnd,
                whereArgs,
                null,
                null,
                AccountEntry.COLUMN_FULL_NAME
            );
            accountsLevel.clear();
            if (cursor != null && cursor.moveToFirst()) {
                try {
                    do {
                        accountsLevel.add(cursor.getString(columnIndexUID));
                    } while (cursor.moveToNext());
                } finally {
                    cursor.close();
                }
            }
            accounts.addAll(accountsLevel);
        } while (!accountsLevel.isEmpty());
        return accounts;
    }

    public List<String> getChildren(String accountUID) {
        List<String> accounts = new ArrayList<>();
        final String[] projection = new String[]{AccountEntry.COLUMN_UID};
        final int columnIndexUID = 0;
        String where = AccountEntry.COLUMN_PARENT_ACCOUNT_UID + "=?";
        String[] whereArgs = new String[]{accountUID};
        Cursor cursor = mDb.query(
            mTableName,
            projection,
            where,
            whereArgs,
            null,
            null,
            AccountEntry.COLUMN_ID
        );
        try {
            if (cursor.moveToFirst()) {
                do {
                    accounts.add(cursor.getString(columnIndexUID));
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        return accounts;
    }

    /**
     * Returns a cursor to the dataset containing sub-accounts of the account with record ID <code>accountUID</code>
     *
     * @param accountUID           GUID of the parent account
     * @param isShowHiddenAccounts Show hidden accounts?
     * @return {@link Cursor} to the sub accounts data set
     */
    public Cursor fetchSubAccounts(String accountUID, boolean isShowHiddenAccounts) {
        Timber.v("Fetching sub accounts for account id %s", accountUID);
        String selection = AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " = ?";
        if (!isShowHiddenAccounts) {
            selection += " AND " + AccountEntry.COLUMN_HIDDEN + " = 0";
        }
        String[] selectionArgs = new String[]{accountUID};
        return fetchAccounts(selection, selectionArgs, null);
    }

    /**
     * Returns the top level accounts i.e. accounts with no parent or with the GnuCash ROOT account as parent
     *
     * @return Cursor to the top level accounts
     */
    public Cursor fetchTopLevelAccounts(@Nullable String filterName, boolean isShowHiddenAccounts) {
        //condition which selects accounts with no parent, whose UID is not ROOT and whose type is not ROOT
        final String[] selectionArgs;
        String selection = AccountEntry.COLUMN_TYPE + " != ?";
        if (!isShowHiddenAccounts) {
            selection += " AND " + AccountEntry.COLUMN_HIDDEN + " = 0";
        }
        if (TextUtils.isEmpty(filterName)) {
            selection += " AND (" + AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " IS NULL OR "
                + AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " = ?)";
            selectionArgs = new String[]{AccountType.ROOT.name(), getOrCreateRootAccountUID()};
        } else {
            selection += " AND (" + AccountEntry.COLUMN_NAME + " LIKE " + sqlEscapeLike(filterName) + ")";
            selectionArgs = new String[]{AccountType.ROOT.name()};
        }
        return fetchAccounts(selection, selectionArgs, null);
    }

    /**
     * Returns a cursor to accounts which have recently had transactions added to them
     *
     * @return Cursor to recently used accounts
     */
    public Cursor fetchRecentAccounts(int numberOfRecent, @Nullable String filterName, boolean isShowHiddenAccounts) {
        String selection = "";
        if (!isShowHiddenAccounts) {
            selection = AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_HIDDEN + " = 0";
        }
        if (!TextUtils.isEmpty(filterName)) {
            if (!selection.isEmpty()) {
                selection += " AND ";
            }
            selection += "(" + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_NAME + " LIKE " + sqlEscapeLike(filterName) + ")";
        }
        return mDb.query(TransactionEntry.TABLE_NAME
                + " LEFT OUTER JOIN " + SplitEntry.TABLE_NAME + " ON "
                + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_UID + " = "
                + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_TRANSACTION_UID
                + ", " + AccountEntry.TABLE_NAME + " ON " + SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID
                + " = " + AccountEntry.TABLE_NAME + "." + AccountEntry.COLUMN_UID,
            new String[]{AccountEntry.TABLE_NAME + ".*"},
            selection,
            null,
            SplitEntry.TABLE_NAME + "." + SplitEntry.COLUMN_ACCOUNT_UID, //groupby
            null, //having
            "MAX ( " + TransactionEntry.TABLE_NAME + "." + TransactionEntry.COLUMN_TIMESTAMP + " ) DESC", // order
            Integer.toString(numberOfRecent) // limit;
        );
    }

    /**
     * Fetches favorite accounts from the database
     *
     * @return Cursor holding set of favorite accounts
     */
    public Cursor fetchFavoriteAccounts(@Nullable String filterName, boolean isShowHiddenAccounts) {
        Timber.v("Fetching favorite accounts from db");
        String selection = AccountEntry.COLUMN_FAVORITE + " = 1";
        if (!isShowHiddenAccounts) {
            selection += " AND " + AccountEntry.COLUMN_HIDDEN + " = 0";
        }
        if (!TextUtils.isEmpty(filterName)) {
            selection += " AND (" + AccountEntry.COLUMN_NAME + " LIKE " + sqlEscapeLike(filterName) + ")";
        }
        return fetchAccounts(selection, null, null);
    }

    /**
     * Returns the GnuCash ROOT account UID if one exists (or creates one if necessary).
     * <p>In GnuCash desktop account structure, there is a root account (which is not visible in the UI) from which
     * other top level accounts derive. GnuCash Android also enforces a ROOT account now</p>
     *
     * @return Unique ID of the GnuCash root account.
     */
    public String getOrCreateRootAccountUID() {
        if (rootUID != null) {
            return rootUID;
        }
        String where = AccountEntry.COLUMN_TYPE + "=?";
        String[] whereArgs = new String[]{AccountType.ROOT.name()};
        Cursor cursor = fetchAccounts(where, whereArgs, null);
        try {
            if (cursor.moveToFirst()) {
                String uid = cursor.getString(cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_UID));
                rootUID = uid;
                return uid;
            }
        } finally {
            cursor.close();
        }
        // No ROOT exits, create a new one
        Context context = holder.context;
        Commodity commodity = commoditiesDbAdapter.getDefaultCommodity();
        Account rootAccount = new Account(ROOT_ACCOUNT_NAME, commodity);
        rootAccount.setAccountType(AccountType.ROOT);
        rootAccount.setFullName(ROOT_ACCOUNT_FULL_NAME);
        rootAccount.setHidden(false);
        rootAccount.setPlaceholder(false);
        ContentValues contentValues = new ContentValues();
        contentValues.put(AccountEntry.COLUMN_UID, rootAccount.getUID());
        contentValues.put(AccountEntry.COLUMN_NAME, rootAccount.getName());
        contentValues.put(AccountEntry.COLUMN_FULL_NAME, rootAccount.getFullName());
        contentValues.put(AccountEntry.COLUMN_TYPE, rootAccount.getAccountType().name());
        contentValues.put(AccountEntry.COLUMN_HIDDEN, rootAccount.isHidden());
        contentValues.put(AccountEntry.COLUMN_CURRENCY, rootAccount.getCommodity().getCurrencyCode());
        contentValues.put(AccountEntry.COLUMN_COMMODITY_UID, rootAccount.getCommodity().getUID());
        contentValues.put(AccountEntry.COLUMN_PLACEHOLDER, rootAccount.isPlaceholder());
        Timber.i("Creating ROOT account");
        mDb.insert(mTableName, null, contentValues);
        rootUID = rootAccount.getUID();
        return rootUID;
    }

    /**
     * Returns the number of accounts for which the account with ID <code>accountUID</code> is a first level parent
     *
     * @param accountUID String Unique ID (GUID) of the account
     * @return Number of sub accounts
     */
    public int getSubAccountCount(String accountUID) {
        return (int) DatabaseUtils.queryNumEntries(
            mDb,
            mTableName,
            AccountEntry.COLUMN_PARENT_ACCOUNT_UID + " = ?",
            new String[]{accountUID}
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
        Account account = getSimpleRecord(accountUID);
        if (account != null) return account.getCommodity();
        throw new IllegalArgumentException("Account not found");
    }

    /**
     * Returns the simple name of the account with unique ID <code>accountUID</code>.
     *
     * @param accountUID Unique identifier of the account
     * @return Name of the account as String
     * @throws java.lang.IllegalArgumentException if accountUID not found
     * @see #getFullyQualifiedAccountName(String)
     */
    public String getAccountName(String accountUID) {
        Account account = getSimpleRecord(accountUID);
        if (account != null) return account.getName();
        return getAttribute(accountUID, AccountEntry.COLUMN_NAME);
    }

    /**
     * Returns the default transfer account record ID for the account with UID <code>accountUID</code>
     *
     * @param accountID Database ID of the account record
     * @return Record ID of default transfer account
     */
    public long getDefaultTransferAccountID(long accountID) {
        if (isCached) {
            for (Account account : cache.values()) {
                if (account.id == accountID) {
                    String uid = account.getDefaultTransferAccountUID();
                    return TextUtils.isEmpty(uid) ? 0 : getID(uid);
                }
            }
        }
        Cursor cursor = mDb.query(
            mTableName,
            new String[]{AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID},
            AccountEntry._ID + " = " + accountID,
            null, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                String uid = cursor.getString(
                    cursor.getColumnIndexOrThrow(AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID));
                return TextUtils.isEmpty(uid) ? 0 : getID(uid);
            }
        } finally {
            cursor.close();
        }
        return 0;
    }

    /**
     * Returns the full account name including the account hierarchy (parent accounts)
     *
     * @param accountUID Unique ID of account
     * @return Fully qualified (with parent hierarchy) account name
     */
    public String getFullyQualifiedAccountName(String accountUID) {
        String accountName = getAccountName(accountUID);
        String parentAccountUID = getParentAccountUID(accountUID);

        if (parentAccountUID == null || parentAccountUID.equals(accountUID) || parentAccountUID.equals(getOrCreateRootAccountUID())) {
            return accountName;
        }

        String parentAccountName = getFullyQualifiedAccountName(parentAccountUID);

        return parentAccountName + ACCOUNT_NAME_SEPARATOR + accountName;
    }

    /**
     * Returns the full account name including the account hierarchy (parent accounts)
     *
     * @param account The account
     * @return Fully qualified (with parent hierarchy) account name
     */
    public String getFullyQualifiedAccountName(@NonNull Account account) {
        String accountName = account.getName();
        String parentAccountUID = account.getParentUID();

        if (TextUtils.isEmpty(parentAccountUID) || parentAccountUID.equalsIgnoreCase(getOrCreateRootAccountUID())) {
            return accountName;
        }

        String parentAccountName = getFullyQualifiedAccountName(parentAccountUID);

        return parentAccountName + ACCOUNT_NAME_SEPARATOR + accountName;
    }

    /**
     * get account's full name directly from DB
     *
     * @param accountUID the account to retrieve full name
     * @return full name registered in DB
     */
    public String getAccountFullName(String accountUID) {
        Account account = getSimpleRecord(accountUID);
        if (account != null) return account.getFullName();
        throw new IllegalArgumentException("Account not found");
    }


    /**
     * Returns <code>true</code> if the account with unique ID <code>accountUID</code> is a placeholder account.
     *
     * @param accountUID Unique identifier of the account
     * @return <code>true</code> if the account is a placeholder account, <code>false</code> otherwise
     */
    public boolean isPlaceholderAccount(String accountUID) {
        Account account = getSimpleRecord(accountUID);
        if (account != null) return account.isPlaceholder();
        String isPlaceholder = getAttribute(accountUID, AccountEntry.COLUMN_PLACEHOLDER);
        return Integer.parseInt(isPlaceholder) != 0;
    }

    /**
     * Convenience method, resolves the account unique ID and calls {@link #isPlaceholderAccount(String)}
     *
     * @param accountUID GUID of the account
     * @return <code>true</code> if the account is hidden, <code>false</code> otherwise
     */
    public boolean isHiddenAccount(String accountUID) {
        Account account = getSimpleRecord(accountUID);
        if (account != null) return account.isHidden();
        String isHidden = getAttribute(accountUID, AccountEntry.COLUMN_HIDDEN);
        return Integer.parseInt(isHidden) != 0;
    }

    /**
     * Returns true if the account is a favorite account, false otherwise
     *
     * @param accountUID GUID of the account
     * @return <code>true</code> if the account is a favorite account, <code>false</code> otherwise
     */
    public boolean isFavoriteAccount(String accountUID) {
        Account account = getSimpleRecord(accountUID);
        if (account != null) return account.isFavorite();
        String isFavorite = getAttribute(accountUID, AccountEntry.COLUMN_FAVORITE);
        return Integer.parseInt(isFavorite) != 0;
    }

    /**
     * Updates all opening balances to the current account balances
     */
    public List<Transaction> getAllOpeningBalanceTransactions() {
        List<Account> accounts = getSimpleAccounts();
        List<Transaction> openingTransactions = new ArrayList<>();
        for (Account account : accounts) {
            Money balance = getAccountBalance(account, ALWAYS, ALWAYS, false);
            if (balance.isAmountZero())
                continue;

            Transaction transaction = new Transaction(GnuCashApplication.getAppContext().getString(R.string.account_name_opening_balances));
            transaction.setNote(account.getName());
            transaction.setCommodity(account.getCommodity());
            TransactionType transactionType = Transaction.getTypeForBalance(account.getAccountType(),
                balance.isNegative());
            Split split = new Split(balance, account.getUID());
            split.setType(transactionType);
            transaction.addSplit(split);
            transaction.addSplit(split.createPair(getOrCreateOpeningBalanceAccountUID()));
            transaction.setExported(true);
            openingTransactions.add(transaction);
        }
        return openingTransactions;
    }

    public static String getImbalanceAccountPrefix(@NonNull Context context) {
        return context.getString(R.string.imbalance_account_name) + "-";
    }

    /**
     * Returns the imbalance account where to store transactions which are not double entry.
     *
     * @param commodity Commodity of the transaction
     * @return Imbalance account name
     */
    public static String getImbalanceAccountName(@NonNull Context context, @NonNull Commodity commodity) {
        return getImbalanceAccountPrefix(context) + commodity.getCurrencyCode();
    }

    /**
     * Get the name of the default account for opening balances for the current locale.
     * For the English locale, it will be "Equity:Opening Balances"
     *
     * @return Fully qualified account name of the opening balances account
     */
    public static String getOpeningBalanceAccountFullName() {
        Context context = GnuCashApplication.getAppContext();
        String parentEquity = context.getString(R.string.account_name_equity).trim();
        //German locale has no parent Equity account
        if (parentEquity.length() > 0) {
            return parentEquity + ACCOUNT_NAME_SEPARATOR
                + context.getString(R.string.account_name_opening_balances);
        } else
            return context.getString(R.string.account_name_opening_balances);
    }

    /**
     * Returns the account color for the account as an Android resource ID.
     * <p>
     * Basically, if we are in a top level account, use the default title color.
     * but propagate a parent account's title color to children who don't have own color
     * </p>
     *
     * @param context    the context
     * @param accountUID GUID of the account
     * @return Android resource ID representing the color which can be directly set to a view
     */
    @ColorInt
    public int getActiveAccountColor(@NonNull Context context, @Nullable String accountUID) {
        while (!TextUtils.isEmpty(accountUID)) {
            int color = getAccountColor(accountUID);
            if (color != Account.DEFAULT_COLOR) {
                return color;
            }
            accountUID = getParentAccountUID(accountUID);
        }

        return ContextCompat.getColor(context, R.color.theme_primary);
    }

    /**
     * Returns the list of commodities in use in the database.
     *
     * <p>This is not the same as the list of all available commodities.</p>
     *
     * @return List of commodities in use
     */
    public List<Commodity> getCommoditiesInUse() {
        String[] columns = new String[]{AccountEntry.COLUMN_COMMODITY_UID};
        String where = AccountEntry.COLUMN_TEMPLATE + " = 0";
        Cursor cursor = mDb.query(true, mTableName, columns, where, null, null, null, null, null);
        Set<Commodity> accountCommodities = new HashSet<>();
        try {
            if (cursor.moveToFirst()) {
                do {
                    String commodityUID = cursor.getString(0);
                    accountCommodities.add(commoditiesDbAdapter.getRecord(commodityUID));
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        List<Commodity> commodities = new ArrayList<>(accountCommodities);
        Collections.sort(commodities, new Comparator<Commodity>() {
            @Override
            public int compare(Commodity o1, Commodity o2) {
                return Long.compare(o1.id, o2.id);
            }
        });
        return commodities;
    }

    public long getCommoditiesInUseCount() {
        String sql = "SELECT COUNT(DISTINCT " + AccountEntry.COLUMN_COMMODITY_UID + ")"
            + " FROM " + mTableName + " a"
            + ", " + CommodityEntry.TABLE_NAME + " c"
            + " WHERE a." + AccountEntry.COLUMN_COMMODITY_UID + " = c." + CommodityEntry.COLUMN_UID
            + " AND c." + CommodityEntry.COLUMN_NAMESPACE + " != ?";
        String[] sqlArgs = new String[]{Commodity.TEMPLATE};
        return DatabaseUtils.longForQuery(mDb, sql, sqlArgs);
    }

    /**
     * Deletes all accounts, transactions (and their splits) from the database.
     * Basically empties all 3 tables, so use with care ;)
     */
    @Override
    public int deleteAllRecords() {
        // Relies "ON DELETE CASCADE" takes too much time
        // It take more than 300s to complete the deletion on my dataset without
        // clearing the split table first, but only needs a little more that 1s
        // if the split table is cleared first.
        mDb.delete(PriceEntry.TABLE_NAME, null, null);
        mDb.delete(SplitEntry.TABLE_NAME, null, null);
        mDb.delete(TransactionEntry.TABLE_NAME, null, null);
        mDb.delete(ScheduledActionEntry.TABLE_NAME, null, null);
        mDb.delete(BudgetAmountEntry.TABLE_NAME, null, null);
        mDb.delete(BudgetEntry.TABLE_NAME, null, null);
        mDb.delete(RecurrenceEntry.TABLE_NAME, null, null);
        rootUID = null;

        return super.deleteAllRecords();
    }

    @Override
    public boolean deleteRecord(@NonNull String uid) throws SQLException {
        boolean result = super.deleteRecord(uid);
        if (result) {
            if (uid.equals(rootUID)) rootUID = null;
            ContentValues contentValues = new ContentValues();
            contentValues.putNull(AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID);
            mDb.update(mTableName, contentValues,
                AccountEntry.COLUMN_DEFAULT_TRANSFER_ACCOUNT_UID + "=?",
                new String[]{uid});

            if (isCached) {
                for (Account account : cache.values()) {
                    if (uid.equals(account.getDefaultTransferAccountUID())) {
                        account.setDefaultTransferAccountUID(null);
                    }
                    if (uid.equals(account.getParentUID())) {
                        account.setParentUID(getOrCreateRootAccountUID());
                    }
                }
            }
        }
        return result;
    }

    public int getTransactionMaxSplitNum(@NonNull String accountUID) {
        Cursor cursor = mDb.query("trans_extra_info",
            new String[]{"MAX(trans_split_count)"},
            "trans_acct_t_uid IN ( SELECT DISTINCT " + TransactionEntry.TABLE_NAME + "_" + TransactionEntry.COLUMN_UID +
                " FROM trans_split_acct WHERE " + AccountEntry.TABLE_NAME + "_" + AccountEntry.COLUMN_UID +
                " = ? )",
            new String[]{accountUID},
            null,
            null,
            null
        );
        try {
            if (cursor.moveToFirst()) {
                return (int) cursor.getLong(0);
            } else {
                return 0;
            }
        } finally {
            cursor.close();
        }
    }

    @Nullable
    public Account getSimpleRecord(@NonNull String uid) {
        if (TextUtils.isEmpty(uid)) return null;
        if (isCached) {
            Account account = cache.get(uid);
            if (account != null) return account;
            // TODO avoid race-condition when multiple simultaneous calls for same record.
        }

        Timber.v("Fetching simple account %s", uid);
        Cursor cursor = fetchRecord(uid);
        try {
            if (cursor.moveToFirst()) {
                Account account = buildSimpleAccountInstance(cursor);
                if (isCached) cache.put(uid, account);
                return account;
            } else {
                throw new IllegalArgumentException("Account not found");
            }
        } finally {
            cursor.close();
        }
    }

    public long getTransactionCount(@NonNull String uid) {
        return transactionsDbAdapter.getTransactionsCountForAccount(uid);
    }

    /**
     * Returns the {@link org.gnucash.android.model.AccountType} of the account with unique ID <code>uid</code>
     *
     * @param accountUID Unique ID of the account
     * @return {@link org.gnucash.android.model.AccountType} of the account.
     * @throws java.lang.IllegalArgumentException if accountUID does not exist in DB,
     */
    public AccountType getAccountType(@NonNull String accountUID) {
        Account account = getSimpleRecord(accountUID);
        if (account != null) return account.getAccountType();
        throw new IllegalArgumentException("Account not found");
    }

    @NonNull
    @Override
    public List<Account> getAllRecords() {
        String where = AccountEntry.COLUMN_TYPE + " != ?"
            + " AND " + AccountEntry.COLUMN_TEMPLATE + " = 0";
        String[] whereArgs = new String[]{AccountType.ROOT.name()};
        return getAllRecords(where, whereArgs);
    }

    public Map<String, Money> getAccountsBalances(List<Account> accounts, long startTime, long endTime) {
        SplitsDbAdapter splitsDbAdapter = transactionsDbAdapter.splitsDbAdapter;
        Map<String, Money> balances = splitsDbAdapter.computeSplitBalances(accounts, startTime, endTime);
        for (Account account : accounts) {
            Money balance = balances.get(account.getUID());
            if (balance == null) continue;
            if (!account.getAccountType().hasDebitNormalBalance) {
                balances.put(account.getUID(), balance.unaryMinus());
            }
        }
        return balances;
    }

    public List<Account> getDescendants(@NonNull Account account) {
        return getDescendants(account.getUID());
    }

    public List<Account> getDescendants(@NonNull String accountUID) {
        List<Account> result = new ArrayList<>();
        populateDescendants(accountUID, result);
        return result;
    }

    private void populateDescendants(@NonNull String accountUID, @NonNull List<Account> result) {
        List<String> descendantsUIDs = getDescendantAccountUIDs(accountUID, null, null);
        for (String descendantsUID : descendantsUIDs) {
            result.add(getSimpleRecord(descendantsUID));
        }
    }
}
