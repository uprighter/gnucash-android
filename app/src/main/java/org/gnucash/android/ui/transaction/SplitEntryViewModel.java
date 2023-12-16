package org.gnucash.android.ui.transaction;

import android.util.Log;

import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;
import androidx.databinding.library.baseAdapters.BR;

import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.model.BaseModel;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.ui.util.widget.CalculatorEditText;
import org.gnucash.android.ui.util.widget.TransactionTypeSwitch;

import java.math.BigDecimal;

public class SplitEntryViewModel extends BaseObservable {
    public static final String LOG_TAG = "SplitEntryViewModel";


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
    private CalculatorEditText mSplitAmountEditText;
    private TransactionTypeSwitch mSplitTypeSwitch;
    private String mAccountUID;
    private Commodity mCommodity;


    private Object mViewHolder;
    private final Split mSplit;

    public SplitEntryViewModel(AccountsDbAdapter accountsDbAdapter,
                               SimpleCursorAdapter cursorAdapter,
                               Split split) {
        this.mAccountsDbAdapter = accountsDbAdapter;
        this.mCursorAdapter = cursorAdapter;
        this.mSplit = split;
    }

    public void init(
            CalculatorEditText splitAmountEditText,
            TransactionTypeSwitch splitTypeSwitch,
            String accountUID,
            Commodity commodity) {
        this.mSplitAmountEditText = splitAmountEditText;
        this.mSplitTypeSwitch = splitTypeSwitch;
        this.mAccountUID = accountUID;
        this.mCommodity = commodity;
        if (mSplit != null) {
            setSplitCurrencySymbol(mSplit.getValue().getCommodity().getSymbol());
            setSplitUid(mSplit.getUID());

            String splitAccountUID = mSplit.getAccountUID();
            assert splitAccountUID != null;
            mSplitTypeSwitch.setAccountType(mAccountsDbAdapter.getAccountType(splitAccountUID));
            setSplitType(mSplit.getType());
            int accountPos = getSelectedTransferAccountPos(mAccountsDbAdapter.getID(splitAccountUID), mCursorAdapter);
            setInputAccountPos(accountPos);
            setInputSplitMemo(mSplit.getMemo());
            setInputSplitAmount(mSplit.getValue().asBigDecimal());
        } else {
            setSplitCurrencySymbol(mCommodity.getSymbol());
            setSplitUid(BaseModel.generateUID());
        }
    }

    public Split getSplit() {
        return mSplit;
    }

    public void setViewHolder(Object viewHolder) {
        this.mViewHolder = viewHolder;
    }

    public Object getViewHolder() {
        return mViewHolder;
    }

    @Override
    public String toString() {
        return String.format("ViewModel(%s, %s, %b, %d).", inputSplitAmount, inputSplitMemo, splitTypeChecked, inputAccountPos);
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
        Log.d(LOG_TAG, "getInputAccountPos, old value " + this.inputAccountPos);
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

    public void setSplitAmountEditText(CalculatorEditText splitAmountEditText) {
        this.mSplitAmountEditText = splitAmountEditText;
    }

    public void setSplitTypeSwitch(TransactionTypeSwitch splitTypeSwitch) {
        this.mSplitTypeSwitch = splitTypeSwitch;
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
