package org.gnucash.android.ui.transaction;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;
import androidx.databinding.library.baseAdapters.BR;

import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.model.BaseModel;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.ui.util.widget.CalculatorEditText;
import org.gnucash.android.ui.util.widget.TransactionTypeSwitch;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

public class SplitEntryViewModel extends BaseObservable {
    public static final String LOG_TAG = SplitEntryViewModel.class.getName();

    // Enabled 2-way binding.
    private String inputSplitMemo = "";
    private String inputSplitAmount = "";

    // Enabled normal data binding.
    private boolean splitTypeChecked = true;
    private int inputAccountPos = 0;
    private String splitCurrencySymbol = "$";
    private String splitUid = "";

    private final AccountsDbAdapter mAccountsDbAdapter;
    private final SimpleCursorAdapter mCursorAdapter;
    private final String mDefaultCurrencySymbol;
    private Split mSplit;

    private CalculatorEditText mSplitAmountEditText;
    private TransactionTypeSwitch mSplitTypeSwitch;
    private Object mViewHolder;

    public SplitEntryViewModel(AccountsDbAdapter accountsDbAdapter,
                               SimpleCursorAdapter cursorAdapter,
                               String currencySymbol,
                               Split split) {
        this.mAccountsDbAdapter = accountsDbAdapter;
        this.mCursorAdapter = cursorAdapter;
        this.mDefaultCurrencySymbol = currencySymbol;
        this.mSplit = split;
        if (mSplit != null) {
            setSplitCurrencySymbol(Objects.requireNonNull(Objects.requireNonNull(mSplit.getValue()).getCommodity()).getSymbol());
            setSplitUid(mSplit.getUID());
        } else {
            setSplitCurrencySymbol(mDefaultCurrencySymbol);
            setSplitUid(BaseModel.generateUID());
        }
    }

    public void bindWithView(
            CalculatorEditText splitAmountEditText,
            TransactionTypeSwitch splitTypeSwitch) {
        this.mSplitAmountEditText = splitAmountEditText;
        this.mSplitTypeSwitch = splitTypeSwitch;
    }

    public void init() {
//        Log.d(LOG_TAG, "init, mSplit=" + mSplit);
        if (mSplit != null) {
            String splitAccountUID = mSplit.getAccountUID();
            assert splitAccountUID != null;
            mSplitTypeSwitch.setAccountType(mAccountsDbAdapter.getAccountType(splitAccountUID));
            setSplitType(mSplit.getType());
            int accountPos = getSelectedTransferAccountPos(mAccountsDbAdapter.getID(splitAccountUID), mCursorAdapter);
            setInputAccountPos(accountPos);
            setInputSplitMemo(mSplit.getMemo());
            setInputSplitAmount(mSplit.getValue().asBigDecimal());
        }
    }

    public void setSplit(Split split) {
        Log.d(LOG_TAG, "setSplit, mSplit=" + mSplit);
        mSplit = split;
    }

    public Split getSplit() {
//        Log.d(LOG_TAG, "getSplit, mSplit=" + mSplit);
        return mSplit;
    }

    public void setViewHolder(Object viewHolder) {
        this.mViewHolder = viewHolder;
    }

    public Object getViewHolder() {
        return mViewHolder;
    }

    @Override
    @NonNull
    public String toString() {
        return String.format(Locale.getDefault(), "ViewModel(%s, %s, %b, %s).", inputSplitAmount, inputSplitMemo, splitTypeChecked, mSplit);
    }

    @Bindable
    public String getSplitCurrencySymbol() {
        return splitCurrencySymbol;
    }

    @Bindable
    public String getInputSplitAmount() {
        return inputSplitAmount;
    }

    @Bindable
    public boolean getSplitTypeChecked() {
        return splitTypeChecked;
    }

    @Bindable
    public String getInputSplitMemo() {
        return inputSplitMemo;
    }

    @Bindable
    public int getInputAccountPos() {
//        Log.d(LOG_TAG, "getInputAccountPos, old value " + this.inputAccountPos);
        return inputAccountPos;
    }

    @Bindable
    public String getSplitUid() {
        return splitUid;
    }

    public void setInputSplitAmount(BigDecimal amount) {
        setInputSplitAmount(amount.toPlainString());
    }

    public void setInputSplitAmount(String inputSplitAmount) {
//        Log.d(LOG_TAG, String.format("setInputSplitAmount, old value %s, new value %s.", this.inputSplitAmount, inputSplitAmount));
        if (this.inputSplitAmount == null || !this.inputSplitAmount.equals(inputSplitAmount)) {
            this.inputSplitAmount = inputSplitAmount;
//            notifyPropertyChanged(BR.inputSplitAmount);  // No need to call notifyPropertyChanged as we set value below.
            mSplitAmountEditText.setValue(this.inputSplitAmount);
        }
    }

    public void setSplitType(TransactionType transactionType) {
        mSplitTypeSwitch.setChecked(transactionType);
        setSplitTypeChecked(mSplitTypeSwitch.isChecked());
    }

    public void setSplitTypeChecked(boolean splitTypeChecked) {
        if (this.splitTypeChecked != splitTypeChecked) {
            this.splitTypeChecked = splitTypeChecked;
            notifyPropertyChanged(BR.splitTypeChecked);
        }
    }

    public void setInputSplitMemo(String inputSplitMemo) {
        if (this.inputSplitMemo == null || !this.inputSplitMemo.equals(inputSplitMemo)) {
            this.inputSplitMemo = inputSplitMemo;
            notifyPropertyChanged(BR.inputSplitMemo);
        }
    }

    public void setSplitCurrencySymbol(String splitCurrencySymbol) {
        this.splitCurrencySymbol = splitCurrencySymbol;
        notifyPropertyChanged(BR.splitCurrencySymbol);
    }

    public void setInputAccountPos(int inputAccountPos) {
        this.inputAccountPos = inputAccountPos;
        notifyPropertyChanged(BR.inputAccountPos);
    }

    public void setSplitUid(String splitUid) {
        this.splitUid = splitUid;
        notifyPropertyChanged(BR.splitUid);
    }

    /**
     * Get the position of the selected transfer account in account list.
     *
     * @param accountId Database ID of the transfer account
     * @return the position.
     */
    private int getSelectedTransferAccountPos(long accountId, final SimpleCursorAdapter cursorAdapter) {
        for (int pos = 0; pos < cursorAdapter.getCount(); pos++) {
            if (cursorAdapter.getItemId(pos) == accountId) {
                return pos;
            }
        }
        return 0;
    }
}
