package org.gnucash.android.ui.transaction;

import android.util.Log;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;
import androidx.databinding.library.baseAdapters.BR;

import org.gnucash.android.model.Split;

public class SplitEntryViewModel extends BaseObservable {
    public static final String LOG_TAG = "SplitEntryViewModel";


    // Enabled 2-way binding.
    private String splitCurrencySymbol = "$";
    private String inputSplitMemo = "";
    private String splitUid = "";
    private String inputSplitAmount = "";

    // Enabled normal data binding.
    private boolean splitType = true;
    private int inputAccountPos = 0;

    private Object viewHolder;
    private final Split split;

    public SplitEntryViewModel() {
        this.split = null;
    }

    public SplitEntryViewModel(Split split) {
        this.split = split;
    }

    public Split getSplit() {
        return split;
    }

    public Object getViewHolder() {
        return viewHolder;
    }

    public void setViewHolder(Object viewHolder) {
        this.viewHolder = viewHolder;
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
    public boolean getSplitType() {
        Log.d(LOG_TAG, "getSplitType for " + this + " type: " + this.splitType);
        return splitType;
    }

    @Bindable
    public String getInputSplitMemo() {
        return inputSplitMemo;
    }

    @Bindable
    public int getInputAccountPos() {
        return inputAccountPos;
    }

    @Bindable
    public String getSplitUid() {
        return splitUid;
    }

    public void setSplitCurrencySymbol(String splitCurrencySymbol) {
        if (!this.splitCurrencySymbol.equals(splitCurrencySymbol)) {
            this.splitCurrencySymbol = splitCurrencySymbol;
            notifyPropertyChanged(BR.splitCurrencySymbol);
        }
    }

    public void setInputSplitAmount(String inputSplitAmount) {
        if (!this.inputSplitAmount.equals(inputSplitAmount)) {
            this.inputSplitAmount = inputSplitAmount;
            notifyPropertyChanged(BR.inputSplitAmount);
        }
    }

    public void setSplitType(boolean splitType) {
        Log.d(LOG_TAG, "setSplitType for " + this + " old type: " + this.splitType + ", new type: " + splitType);
        if (this.splitType != splitType) {
            this.splitType = splitType;
            notifyPropertyChanged(BR.splitType);
        }
    }

    public void setInputSplitMemo(String inputSplitMemo) {
        if (!this.inputSplitMemo.equals(inputSplitMemo)) {
            this.inputSplitMemo = inputSplitMemo;
            notifyPropertyChanged(BR.inputSplitMemo);
        }
    }

    public void setInputAccountPos(int inputAccountPos) {
        if (this.inputAccountPos != inputAccountPos) {
            this.inputAccountPos = inputAccountPos;
            notifyPropertyChanged(BR.inputAccountPos);
        }
    }

    public void setSplitUid(String splitUid) {
        if (!this.splitUid.equals(splitUid)) {
            this.splitUid = splitUid;
            notifyPropertyChanged(BR.splitUid);
        }
    }
}
