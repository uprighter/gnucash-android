/*
 * Copyright (c) 2016 Àlex Magaz Graça <rivaldi8@gmail.com>
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
package org.gnucash.android.test.unit.importer;

import static org.assertj.core.api.Assertions.assertThat;

import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.export.xml.GncXmlHelper;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.ScheduledAction;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.test.unit.BookHelperTest;
import org.junit.Ignore;
import org.junit.Test;

import java.text.ParseException;
import java.util.Calendar;
import java.util.List;

/**
 * Imports GnuCash XML files and checks the objects defined in them are imported correctly.
 */
public class GncXmlHandlerTest extends BookHelperTest {

    /**
     * Tests basic accounts import.
     *
     * <p>Checks hierarchy and attributes. We should have:</p>
     * <pre>
     * Root
     * |_ Assets
     * |   |_ Cash in wallet
     * |_ Expenses
     *     |_ Dining
     * </pre>
     */
    @Test
    public void accountsImport() {
        String bookUID = importGnuCashXml("accountsImport.xml");
        assertThat(BooksDbAdapter.isBookDatabase(bookUID)).isTrue();

        assertThat(mAccountsDbAdapter.getRecordsCount()).isEqualTo(5); // 4 accounts + root

        Account rootAccount = mAccountsDbAdapter.getRecord("308ade8cf0be2b0b05c5eec3114a65fa");
        assertThat(rootAccount.getParentUID()).isNull();
        assertThat(rootAccount.getName()).isEqualTo("Root Account");
        assertThat(rootAccount.isHidden()).isTrue();

        Account assetsAccount = mAccountsDbAdapter.getRecord("3f44d61cb1afd201e8ea5a54ec4fbbff");
        assertThat(assetsAccount.getParentUID()).isEqualTo(rootAccount.getUID());
        assertThat(assetsAccount.getName()).isEqualTo("Assets");
        assertThat(assetsAccount.isHidden()).isFalse();
        assertThat(assetsAccount.isPlaceholderAccount()).isTrue();
        assertThat(assetsAccount.getAccountType()).isEqualTo(AccountType.ASSET);

        Account diningAccount = mAccountsDbAdapter.getRecord("6a7cf8267314992bdddcee56d71a3908");
        assertThat(diningAccount.getParentUID()).isEqualTo("9b607f63aecb1a175556676904432365");
        assertThat(diningAccount.getName()).isEqualTo("Dining");
        assertThat(diningAccount.getDescription()).isEqualTo("Dining");
        assertThat(diningAccount.isHidden()).isFalse();
        assertThat(diningAccount.isPlaceholderAccount()).isFalse();
        assertThat(diningAccount.isFavorite()).isFalse();
        assertThat(diningAccount.getAccountType()).isEqualTo(AccountType.EXPENSE);
        assertThat(diningAccount.getCommodity().getCurrencyCode()).isEqualTo("USD");
        assertThat(diningAccount.getColor()).isEqualTo(Account.DEFAULT_COLOR);
        assertThat(diningAccount.getDefaultTransferAccountUID()).isNull();
    }

    /**
     * Tests importing a simple transaction with default splits.
     *
     * @throws ParseException
     */
    @Test
    public void simpleTransactionImport() throws Exception {
        String bookUID = importGnuCashXml("simpleTransactionImport.xml");
        assertThat(BooksDbAdapter.isBookDatabase(bookUID)).isTrue();

        assertThat(mTransactionsDbAdapter.getRecordsCount()).isEqualTo(1);

        Transaction transaction = mTransactionsDbAdapter.getRecord("b33c8a6160494417558fd143731fc26a");

        // Check attributes
        assertThat(transaction.getDescription()).isEqualTo("Kahuna Burger");
        assertThat(transaction.getCommodity().getCurrencyCode()).isEqualTo("USD");
        assertThat(transaction.getNote()).isEqualTo("");
        assertThat(transaction.getScheduledActionUID()).isNull();
        assertThat(transaction.isExported()).isTrue();
        assertThat(transaction.isTemplate()).isFalse();
        assertThat(transaction.getTimeMillis()).
                isEqualTo(GncXmlHelper.parseDateTime("2016-08-23 10:00:00 +0200"));
        assertThat(transaction.getCreatedTimestamp().getTime()).
                isEqualTo(GncXmlHelper.parseDateTime("2016-08-23 12:44:19 +0200"));

        // Check splits
        assertThat(transaction.getSplits().size()).isEqualTo(2);
        // FIXME: don't depend on the order
        Split split1 = transaction.getSplits().get(0);
        assertThat(split1.getUID()).isEqualTo("ad2cbc774fc4e71885d17e6932448e8e");
        assertThat(split1.getAccountUID()).isEqualTo("6a7cf8267314992bdddcee56d71a3908");
        assertThat(split1.getTransactionUID()).isEqualTo("b33c8a6160494417558fd143731fc26a");
        assertThat(split1.getType()).isEqualTo(TransactionType.DEBIT);
        assertThat(split1.getMemo()).isNull();
        assertThat(split1.getValue()).isEqualTo(new Money("10", "USD"));
        assertThat(split1.getQuantity()).isEqualTo(new Money("10", "USD"));
        assertThat(split1.getReconcileState()).isEqualTo('n');

        Split split2 = transaction.getSplits().get(1);
        assertThat(split2.getUID()).isEqualTo("61d4d604bc00a59cabff4e8875d00bee");
        assertThat(split2.getAccountUID()).isEqualTo("dae686a1636addc0dae1ae670701aa4a");
        assertThat(split2.getTransactionUID()).isEqualTo("b33c8a6160494417558fd143731fc26a");
        assertThat(split2.getType()).isEqualTo(TransactionType.CREDIT);
        assertThat(split2.getMemo()).isNull();
        assertThat(split2.getValue()).isEqualTo(new Money("10", "USD"));
        assertThat(split2.getQuantity()).isEqualTo(new Money("10", "USD"));
        assertThat(split2.getReconcileState()).isEqualTo('n');
        assertThat(split2.isPairOf(split1)).isTrue();
    }

    /**
     * Tests importing a transaction with non-default splits.
     *
     * @throws ParseException
     */
    @Test
    public void transactionWithNonDefaultSplitsImport() {
        String bookUID = importGnuCashXml("transactionWithNonDefaultSplitsImport.xml");
        assertThat(BooksDbAdapter.isBookDatabase(bookUID)).isTrue();

        assertThat(mTransactionsDbAdapter.getRecordsCount()).isEqualTo(1);

        Transaction transaction = mTransactionsDbAdapter.getRecord("042ff745a80e94e6237fb0549f6d32ae");

        // Ensure it's the correct one
        assertThat(transaction.getDescription()).isEqualTo("Tandoori Mahal");

        // Check splits
        assertThat(transaction.getSplits().size()).isEqualTo(3);
        // FIXME: don't depend on the order
        Split expenseSplit = transaction.getSplits().get(0);
        assertThat(expenseSplit.getUID()).isEqualTo("c50cce06e2bf9085730821c82d0b36ca");
        assertThat(expenseSplit.getAccountUID()).isEqualTo("6a7cf8267314992bdddcee56d71a3908");
        assertThat(expenseSplit.getTransactionUID()).isEqualTo("042ff745a80e94e6237fb0549f6d32ae");
        assertThat(expenseSplit.getType()).isEqualTo(TransactionType.DEBIT);
        assertThat(expenseSplit.getMemo()).isNull();
        assertThat(expenseSplit.getValue()).isEqualTo(new Money("50", "USD"));
        assertThat(expenseSplit.getQuantity()).isEqualTo(new Money("50", "USD"));

        Split assetSplit1 = transaction.getSplits().get(1);
        assertThat(assetSplit1.getUID()).isEqualTo("4930f412665a705eedba39789b6c3a35");
        assertThat(assetSplit1.getAccountUID()).isEqualTo("dae686a1636addc0dae1ae670701aa4a");
        assertThat(assetSplit1.getTransactionUID()).isEqualTo("042ff745a80e94e6237fb0549f6d32ae");
        assertThat(assetSplit1.getType()).isEqualTo(TransactionType.CREDIT);
        assertThat(assetSplit1.getMemo()).isEqualTo("tip");
        assertThat(assetSplit1.getValue()).isEqualTo(new Money("5", "USD"));
        assertThat(assetSplit1.getQuantity()).isEqualTo(new Money("5", "USD"));
        assertThat(assetSplit1.isPairOf(expenseSplit)).isFalse();

        Split assetSplit2 = transaction.getSplits().get(2);
        assertThat(assetSplit2.getUID()).isEqualTo("b97cd9bbaa17f181d0a5b39b260dabda");
        assertThat(assetSplit2.getAccountUID()).isEqualTo("ee139a5658a0d37507dc26284798e347");
        assertThat(assetSplit2.getTransactionUID()).isEqualTo("042ff745a80e94e6237fb0549f6d32ae");
        assertThat(assetSplit2.getType()).isEqualTo(TransactionType.CREDIT);
        assertThat(assetSplit2.getMemo()).isNull();
        assertThat(assetSplit2.getValue()).isEqualTo(new Money("45", "USD"));
        assertThat(assetSplit2.getQuantity()).isEqualTo(new Money("45", "USD"));
        assertThat(assetSplit2.isPairOf(expenseSplit)).isFalse();
    }

    /**
     * Tests importing a transaction with multiple currencies.
     *
     * @throws ParseException
     */
    @Test
    public void multiCurrencyTransactionImport() {
        String bookUID = importGnuCashXml("multiCurrencyTransactionImport.xml");
        assertThat(BooksDbAdapter.isBookDatabase(bookUID)).isTrue();

        assertThat(mTransactionsDbAdapter.getRecordsCount()).isEqualTo(1);

        Transaction transaction = mTransactionsDbAdapter.getRecord("ded49386f8ea319ccaee043ba062b3e1");

        // Ensure it's the correct one
        assertThat(transaction.getDescription()).isEqualTo("Salad express");
        assertThat(transaction.getCommodity().getCurrencyCode()).isEqualTo("USD");
        assertThat(transaction.getCommodity().getSmallestFraction()).isEqualTo(100);

        // Check splits
        assertThat(transaction.getSplits().size()).isEqualTo(2);
        // FIXME: don't depend on the order
        Split split1 = transaction.getSplits().get(0);
        assertThat(split1.getUID()).isEqualTo("88bbbbac7689a8657b04427f8117a783");
        assertThat(split1.getAccountUID()).isEqualTo("6a7cf8267314992bdddcee56d71a3908");
        assertThat(split1.getTransactionUID()).isEqualTo("ded49386f8ea319ccaee043ba062b3e1");
        assertThat(split1.getType()).isEqualTo(TransactionType.DEBIT);
        assertThat(split1.getValue().getNumerator()).isEqualTo(2000);
        assertThat(split1.getValue().getDenominator()).isEqualTo(100);
        assertThat(split1.getValue()).isEqualTo(new Money("20", "USD"));
        assertThat(split1.getQuantity().getNumerator()).isEqualTo(2000);
        assertThat(split1.getQuantity().getDenominator()).isEqualTo(100);
        assertThat(split1.getQuantity()).isEqualTo(new Money("20", "USD"));

        Split split2 = transaction.getSplits().get(1);
        assertThat(split2.getUID()).isEqualTo("e0dd885065bfe3c9ef63552fe84c6d23");
        assertThat(split2.getAccountUID()).isEqualTo("0469e915a22ba7846aca0e69f9f9b683");
        assertThat(split2.getTransactionUID()).isEqualTo("ded49386f8ea319ccaee043ba062b3e1");
        assertThat(split2.getType()).isEqualTo(TransactionType.CREDIT);
        assertThat(split2.getValue().getNumerator()).isEqualTo(2000);
        assertThat(split2.getValue().getDenominator()).isEqualTo(100);
        assertThat(split2.getValue()).isEqualTo(new Money("20", "USD"));
        assertThat(split2.getQuantity().getNumerator()).isEqualTo(1793);
        assertThat(split2.getQuantity().getDenominator()).isEqualTo(100);
        assertThat(split2.getQuantity()).isEqualTo(new Money("17.93", "EUR"));
        assertThat(split2.isPairOf(split1)).isTrue();
    }

    /**
     * Tests importing a simple scheduled transaction with default splits.
     */
    //@Test Disabled as currently amounts are only read from credit/debit-numeric
    // slots and transactions without amount are ignored.
    @Ignore
    public void simpleScheduledTransactionImport() throws Exception {
        String bookUID = importGnuCashXml("simpleScheduledTransactionImport.xml");
        assertThat(BooksDbAdapter.isBookDatabase(bookUID)).isTrue();

        assertThat(mTransactionsDbAdapter.getTemplateTransactionsCount()).isEqualTo(1);

        Transaction scheduledTransaction =
                mTransactionsDbAdapter.getRecord("b645bef06d0844aece6424ceeec03983");

        // Check attributes
        assertThat(scheduledTransaction.getDescription()).isEqualTo("Los pollos hermanos");
        assertThat(scheduledTransaction.getCommodity().getCurrencyCode()).isEqualTo("USD");
        assertThat(scheduledTransaction.getNote()).isEqualTo("");
        assertThat(scheduledTransaction.getScheduledActionUID()).isNull();
        assertThat(scheduledTransaction.isExported()).isTrue();
        assertThat(scheduledTransaction.isTemplate()).isTrue();
        assertThat(scheduledTransaction.getTimeMillis())
                .isEqualTo(GncXmlHelper.parseDateTime("2016-08-24 10:00:00 +0200"));
        assertThat(scheduledTransaction.getCreatedTimestamp().getTime())
                .isEqualTo(GncXmlHelper.parseDateTime("2016-08-24 19:50:15 +0200"));

        // Check splits
        assertThat(scheduledTransaction.getSplits().size()).isEqualTo(2);

        Split split1 = scheduledTransaction.getSplits().get(0);
        assertThat(split1.getUID()).isEqualTo("f66794ef262aac3ae085ecc3030f2769");
        assertThat(split1.getAccountUID()).isEqualTo("6a7cf8267314992bdddcee56d71a3908");
        assertThat(split1.getTransactionUID()).isEqualTo("b645bef06d0844aece6424ceeec03983");
        assertThat(split1.getType()).isEqualTo(TransactionType.CREDIT);
        assertThat(split1.getMemo()).isNull();
        assertThat(split1.getValue()).isEqualTo(new Money("20", "USD"));
        // FIXME: the quantity is always 0 as it's set from <split:quantity> instead
        // of from the slots
        //assertThat(split1.getQuantity()).isEqualTo(new Money("20", "USD"));

        Split split2 = scheduledTransaction.getSplits().get(1);
        assertThat(split2.getUID()).isEqualTo("57e2be6ca6b568f8f7c9b2e455e1e21f");
        assertThat(split2.getAccountUID()).isEqualTo("dae686a1636addc0dae1ae670701aa4a");
        assertThat(split2.getTransactionUID()).isEqualTo("b645bef06d0844aece6424ceeec03983");
        assertThat(split2.getType()).isEqualTo(TransactionType.DEBIT);
        assertThat(split2.getMemo()).isNull();
        assertThat(split2.getValue()).isEqualTo(new Money("20", "USD"));
        // FIXME: the quantity is always 0 as it's set from <split:quantity> instead
        // of from the slots
        //assertThat(split2.getQuantity()).isEqualTo(new Money("20", "USD"));
        assertThat(split2.isPairOf(split1)).isTrue();
    }

    /**
     * Tests that importing a weekly scheduled action sets the days of the
     * week of the recursion.
     */
    @Test
    public void importingScheduledAction_shouldSetByDays() {
        String bookUID = importGnuCashXml("importingScheduledAction_shouldSetByDays.xml");
        assertThat(BooksDbAdapter.isBookDatabase(bookUID)).isTrue();

        ScheduledAction scheduledTransaction =
                mScheduledActionDbAdapter.getRecord("b5a13acb5a9459ebed10d06b75bbad10");

        // There are 3 byDays but, for now, getting one is enough to ensure it is executed
        assertThat(scheduledTransaction.getRecurrence().getByDays().size()).isGreaterThanOrEqualTo(1);

        // Until we implement parsing of days of the week for scheduled actions,
        // we'll just use the day of the week of the start time.
        int dayOfWeekFromByDays = scheduledTransaction.getRecurrence().getByDays().get(0);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(scheduledTransaction.getStartTime());
        int dayOfWeekFromStartTime = calendar.get(Calendar.DAY_OF_WEEK);
        assertThat(dayOfWeekFromByDays).isEqualTo(dayOfWeekFromStartTime);
    }

    /**
     * Checks for bug 562 - Scheduled transaction imported with imbalanced splits.
     *
     * <p>Happens when an scheduled transaction is defined with both credit and
     * debit slots in each split.</p>
     */
    @Test
    public void bug562_scheduledTransactionImportedWithImbalancedSplits() {
        String bookUID = importGnuCashXml("bug562_scheduledTransactionImportedWithImbalancedSplits.xml");
        assertThat(BooksDbAdapter.isBookDatabase(bookUID)).isTrue();

        assertThat(mTransactionsDbAdapter.getTemplateTransactionsCount()).isEqualTo(1);

        Transaction scheduledTransaction =
                mTransactionsDbAdapter.getRecord("b645bef06d0844aece6424ceeec03983");

        // Ensure it's the correct transaction
        assertThat(scheduledTransaction.getDescription()).isEqualTo("Los pollos hermanos");
        assertThat(scheduledTransaction.isTemplate()).isTrue();

        // Check splits
        assertThat(scheduledTransaction.getSplits().size()).isEqualTo(2);

        Split split1 = scheduledTransaction.getSplits().get(0);
        assertThat(split1.getAccountUID()).isEqualTo("6a7cf8267314992bdddcee56d71a3908");
        assertThat(split1.getType()).isEqualTo(TransactionType.CREDIT);
        assertThat(split1.getValue()).isEqualTo(new Money("20", "USD"));
        // FIXME: the quantity is always 0 as it's set from <split:quantity> instead
        // of from the slots
        //assertThat(split1.getQuantity()).isEqualTo(new Money("20", "USD"));

        Split split2 = scheduledTransaction.getSplits().get(1);
        assertThat(split2.getAccountUID()).isEqualTo("dae686a1636addc0dae1ae670701aa4a");
        assertThat(split2.getType()).isEqualTo(TransactionType.DEBIT);
        assertThat(split2.getValue()).isEqualTo(new Money("20", "USD"));
        // FIXME: the quantity is always 0 as it's set from <split:quantity> instead
        // of from the slots
        //assertThat(split2.getQuantity()).isEqualTo(new Money("20", "USD"));
        assertThat(split2.isPairOf(split1)).isTrue();
    }

    @Test
    public void commodities() {
        String bookUID = importGnuCashXml("commodities.xml");
        assertThat(BooksDbAdapter.isBookDatabase(bookUID)).isTrue();

        CommoditiesDbAdapter commoditiesDbAdapter = mCommoditiesDbAdapter;
        assertThat(commoditiesDbAdapter).isNotNull();
        List<Commodity> commodities = commoditiesDbAdapter.getAllRecords();
        assertThat(commodities).isNotNull();
        assertThat(commodities.size()).isGreaterThanOrEqualTo(3);

        Commodity commodity1 = commodities.stream()
            .filter(c -> c.getCurrencyCode().equals("APPS"))
            .findFirst()
            .get();
        assertThat(commodity1).isNotNull();
        assertThat(commodity1.getNamespace()).isEqualTo("NASDAQ");
        assertThat(commodity1.getFullname()).isEqualTo("Digital Turbine");
        assertThat(commodity1.getSmallestFraction()).isEqualTo(10000);
        assertThat(commodity1.getQuoteFlag()).isFalse();
        assertThat(commodity1.getQuoteSource()).isNull();
        assertThat(commodity1.getQuoteTimeZone()).isNull();

        Commodity commodity2 = commodities.stream()
            .filter(c -> c.getCurrencyCode().equals("QUAN_ELSS_TAX_KBGFAS"))
            .findFirst()
            .get();
        assertThat(commodity2).isNotNull();
        assertThat(commodity2.getNamespace()).isEqualTo("MF");
        assertThat(commodity2.getFullname()).isEqualTo("Quant ELSS Growth");
        assertThat(commodity2.getSmallestFraction()).isEqualTo(10000);
        assertThat(commodity2.getQuoteFlag()).isTrue();
        assertThat(commodity2.getQuoteSource()).isEqualTo("googleweb");
        assertThat(commodity2.getQuoteTimeZone()).isNull();
    }
}