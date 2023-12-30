package org.gnucash.android.ui.transaction;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.gnucash.android.R;
import org.gnucash.android.databinding.ActivityTransactionDetailBinding;
import org.gnucash.android.databinding.ItemSplitAmountInfoBinding;

import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.ScheduledAction;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.passcode.PasscodeLockActivity;

import java.text.DateFormat;
import java.util.Date;
import java.util.MissingFormatArgumentException;
import java.util.Objects;

/**
 * Activity for displaying transaction information
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class TransactionDetailActivity extends PasscodeLockActivity {

    public static final String LOG_TAG = TransactionDetailActivity.class.getName();

    private ActivityTransactionDetailBinding mBinding;

    TextView mTransactionDescription;
    TextView mTimeAndDate;
    TextView mRecurrence;
    TableRow mRowTrnRecurrence;
    TextView mNotes;
    TableRow mRowTrnNotes;
    Toolbar mToolBar;
    TextView mTransactionAccount;
    TextView mDebitBalance;
    TextView mCreditBalance;
    TableLayout mDetailTableLayout;
    FloatingActionButton mFabEditTransaction;
    private final ActivityResultLauncher<Intent> createTransactionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> Log.d(LOG_TAG, "launch createTransactionIntent: result = " + result)
    );

    private String mTransactionUID;
    private String mAccountUID;
    private int mDetailTableRows;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mTransactionUID = getIntent().getStringExtra(UxArgument.SELECTED_TRANSACTION_UID);
        mAccountUID = getIntent().getStringExtra(UxArgument.SELECTED_ACCOUNT_UID);
        if (mTransactionUID == null || mAccountUID == null) {
            throw new MissingFormatArgumentException("You must specify both the transaction and account GUID");
        }

        mBinding = ActivityTransactionDetailBinding.inflate(getLayoutInflater());
        Log.d(LOG_TAG, "onCreate: binding = " + mBinding + ", savedInstanceState = " + savedInstanceState);

        bindViews();

        mFabEditTransaction.setOnClickListener((View _view) -> {
            Intent createTransactionIntent = new Intent(this.getApplicationContext(), FormActivity.class);
            createTransactionIntent.setAction(Intent.ACTION_INSERT_OR_EDIT);
            createTransactionIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, mAccountUID);
            createTransactionIntent.putExtra(UxArgument.SELECTED_TRANSACTION_UID, mTransactionUID);
            createTransactionIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name());

            createTransactionLauncher.launch(createTransactionIntent);
        });

        setSupportActionBar(mToolBar);

        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.setElevation(0);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeAsUpIndicator(R.drawable.ic_close_white_24dp);
        actionBar.setDisplayShowTitleEnabled(false);

        int themeColor = AccountsDbAdapter.getActiveAccountColorResource(mAccountUID);
        actionBar.setBackgroundDrawable(new ColorDrawable(themeColor));
        mToolBar.setBackgroundColor(themeColor);
        getWindow().setStatusBarColor(GnuCashApplication.darken(themeColor));

        setContentView(mBinding.getRoot());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBinding = null;
    }

    private TableRow bindItemSplitAmountInfo(LayoutInflater inflater, Split split) {
        ItemSplitAmountInfoBinding itemSplitAmountInfoBinding = ItemSplitAmountInfoBinding.inflate(inflater, mDetailTableLayout, false);
        TextView accountName = itemSplitAmountInfoBinding.splitAccountName;
        TextView splitDebit = itemSplitAmountInfoBinding.splitDebit;
        TextView splitCredit = itemSplitAmountInfoBinding.splitCredit;
        AccountsDbAdapter accountsDbAdapter = AccountsDbAdapter.getInstance();
        accountName.setText(accountsDbAdapter.getAccountFullName(split.getAccountUID()));
        Money quantity = split.getFormattedQuantity();
        TextView balanceView = quantity.isNegative() ? splitDebit : splitCredit;
        TransactionsActivity.displayBalance(balanceView, quantity);

        return itemSplitAmountInfoBinding.getRoot();
    }

    /**
     * Reads the transaction information from the database and binds it to the views
     */
    private void bindViews() {
        mTransactionDescription = mBinding.trnDescription;
        mTimeAndDate = mBinding.trnTimeAndDate;
        mRecurrence = mBinding.trnRecurrence;
        mRowTrnRecurrence = mBinding.rowTrnRecurrence;
        mNotes = mBinding.trnNotes;
        mRowTrnNotes = mBinding.rowTrnNotes;
        mToolBar = mBinding.toolbar;
        mTransactionAccount = mBinding.transactionAccount;
        mDebitBalance = mBinding.balanceDebit;
        mCreditBalance = mBinding.balanceCredit;
        mDetailTableLayout = mBinding.fragmentTransactionDetails;
        mFabEditTransaction = mBinding.fabEditTransaction;

        TransactionsDbAdapter transactionsDbAdapter = TransactionsDbAdapter.getInstance();
        Transaction transaction = transactionsDbAdapter.getRecord(mTransactionUID);

        mTransactionDescription.setText(transaction.getDescription());
        mTransactionAccount.setText(getString(R.string.label_inside_account_with_name, AccountsDbAdapter.getInstance().getAccountFullName(mAccountUID)));

        AccountsDbAdapter accountsDbAdapter = AccountsDbAdapter.getInstance();

        Money accountBalance = accountsDbAdapter.getAccountBalance(mAccountUID, -1, transaction.getTimeMillis());
        TextView balanceTextView = accountBalance.isNegative() ? mDebitBalance : mCreditBalance;
        TransactionsActivity.displayBalance(balanceTextView, accountBalance);

        mDetailTableRows = mDetailTableLayout.getChildCount();
        boolean useDoubleEntry = GnuCashApplication.isDoubleEntryEnabled();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Split split : transaction.getSplits()) {
            if (!useDoubleEntry &&
                    accountsDbAdapter.getImbalanceAccountUID(Objects.requireNonNull(split.getValue()).getCommodity()).equals(split.getAccountUID())) {
                //do not show imbalance accounts for single entry use case
                continue;
            }
            TableRow row = bindItemSplitAmountInfo(inflater, split);
            mDetailTableLayout.addView(row);
        }


        Date trnDate = new Date(transaction.getTimeMillis());
        String timeAndDate = DateFormat.getDateInstance(DateFormat.FULL).format(trnDate);
        mTimeAndDate.setText(timeAndDate);

        if (transaction.getScheduledActionUID() != null) {
            ScheduledAction scheduledAction = ScheduledActionDbAdapter.getInstance().getRecord(transaction.getScheduledActionUID());
            mRecurrence.setText(scheduledAction.getRepeatString());
            mRowTrnRecurrence.setVisibility(View.VISIBLE);
        } else {
            mRowTrnRecurrence.setVisibility(View.GONE);
        }

        if (transaction.getNote() != null && !transaction.getNote().isEmpty()) {
            mNotes.setText(transaction.getNote());
            mRowTrnNotes.setVisibility(View.VISIBLE);
        } else {
            mRowTrnNotes.setVisibility(View.GONE);
        }

    }

    /**
     * Refreshes the transaction information
     */
    private void refresh() {
        removeSplitItemViews();
        bindViews();
    }

    /**
     * Remove the split item views from the transaction detail prior to refreshing them
     */
    private void removeSplitItemViews() {
        // Remove all rows that are not special.
        mDetailTableLayout.removeViews(0, mDetailTableLayout.getChildCount() - mDetailTableRows);
        mDebitBalance.setText("");
        mCreditBalance.setText("");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            refresh();
        }
    }
}
