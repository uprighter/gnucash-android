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

/**
 * Activity for displaying transaction information
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class TransactionDetailActivity extends PasscodeLockActivity {

    public static final String LOG_TAG = "TransactionDetailActivity";

    private ActivityTransactionDetailBinding binding;

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
    FloatingActionButton mFabEditTransaction;
    private ActivityResultLauncher<Intent> launcher;

    TableLayout mDetailTableLayout;

    private String mTransactionUID;
    private String mAccountUID;
    private int mDetailTableRows;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityTransactionDetailBinding.inflate(getLayoutInflater());
        Log.d(LOG_TAG, "onCreate: binding = " + binding + ", savedInstanceState = " + savedInstanceState);

        setContentView(binding.getRoot());

        mTransactionUID = getIntent().getStringExtra(UxArgument.SELECTED_TRANSACTION_UID);
        mAccountUID = getIntent().getStringExtra(UxArgument.SELECTED_ACCOUNT_UID);

        if (mTransactionUID == null || mAccountUID == null) {
            throw new MissingFormatArgumentException("You must specify both the transaction and account GUID");
        }

        mTransactionDescription = binding.trnDescription;
        mTimeAndDate = binding.trnTimeAndDate;
        mRecurrence = binding.trnRecurrence;
        mRowTrnRecurrence = binding.rowTrnRecurrence;
        mNotes = binding.trnNotes;
        mRowTrnNotes = binding.rowTrnNotes;
        mToolBar = binding.toolbar;
        mTransactionAccount = binding.transactionAccount;
        mDebitBalance = binding.balanceDebit;
        mCreditBalance = binding.balanceCredit;
        mDetailTableLayout = binding.fragmentTransactionDetails;
        mFabEditTransaction = binding.fabEditTransaction;

        launcher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.d(LOG_TAG, "launch createTransactionIntent: result = " + result);
                }
        );
        mFabEditTransaction.setOnClickListener((View _view) -> {
            Intent createTransactionIntent = new Intent(this.getApplicationContext(), FormActivity.class);
            createTransactionIntent.setAction(Intent.ACTION_INSERT_OR_EDIT);
            createTransactionIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, mAccountUID);
            createTransactionIntent.putExtra(UxArgument.SELECTED_TRANSACTION_UID, mTransactionUID);
            createTransactionIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name());

            launcher.launch(createTransactionIntent);
        });

        setSupportActionBar(mToolBar);

        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.setElevation(0);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeAsUpIndicator(R.drawable.ic_close_white_24dp);
        actionBar.setDisplayShowTitleEnabled(false);

        bindViews();

        int themeColor = AccountsDbAdapter.getActiveAccountColorResource(mAccountUID);
        actionBar.setBackgroundDrawable(new ColorDrawable(themeColor));
        mToolBar.setBackgroundColor(themeColor);
        getWindow().setStatusBarColor(GnuCashApplication.darken(themeColor));

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    private TableRow bindItemSplitAmountInfo(LayoutInflater inflater, Split split) {
        ItemSplitAmountInfoBinding binding = ItemSplitAmountInfoBinding.inflate(inflater, mDetailTableLayout, false);
        TextView accountName = binding.splitAccountName;
        TextView splitDebit = binding.splitDebit;
        TextView splitCredit = binding.splitCredit;
        AccountsDbAdapter accountsDbAdapter = AccountsDbAdapter.getInstance();
        accountName.setText(accountsDbAdapter.getAccountFullName(split.getAccountUID()));
        Money quantity = split.getFormattedQuantity();
        TextView balanceView = quantity.isNegative() ? splitDebit : splitCredit;
        TransactionsActivity.displayBalance(balanceView, quantity);

        return binding.getRoot();
    }

    /**
     * Reads the transaction information from the database and binds it to the views
     */
    private void bindViews() {
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
        int index = 0;
        for (Split split : transaction.getSplits()) {
            if (!useDoubleEntry && split.getAccountUID().equals(
                    accountsDbAdapter.getImbalanceAccountUID(split.getValue().getCommodity()))) {
                //do not show imbalance accounts for single entry use case
                continue;
            }
            TableRow row = bindItemSplitAmountInfo(inflater, split);
            mDetailTableLayout.addView(row, index++);
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
