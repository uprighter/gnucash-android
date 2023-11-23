package org.gnucash.android.ui.transaction;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;
import androidx.databinding.library.baseAdapters.BR;

public class SplitEntryViewModel extends BaseObservable {
    public static final String LOG_TAG = "SplitEntryViewModel";

    private String splitCurrencySymbol;
    private String inputSplitAmount;
    private boolean splitType;
    private String inputSplitMemo;
    private int inputAccountPos;
    private String splitUid;

    private Object viewHolder;

    public Object getSplit() {
        return split;
    }

    public void setSplit(Object split) {
        this.split = split;
    }

    private Object split;

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
        this.splitCurrencySymbol = splitCurrencySymbol;
        notifyPropertyChanged(BR.splitCurrencySymbol);
    }

    public void setInputSplitAmount(String inputSplitAmount) {
        this.inputSplitAmount = inputSplitAmount;
        notifyPropertyChanged(BR.inputSplitAmount);
    }

    public void setSplitType(boolean splitType) {
        this.splitType = splitType;
        notifyPropertyChanged(BR.splitType);
    }

    public void setInputSplitMemo(String inputSplitMemo) {
        this.inputSplitMemo = inputSplitMemo;
        notifyPropertyChanged(BR.inputSplitMemo);
    }

    public void setInputAccountPos(int inputAccountPos) {
        this.inputAccountPos = inputAccountPos;
        notifyPropertyChanged(BR.inputAccountPos);
    }

    public void setSplitUid(String splitUid) {
        this.splitUid = splitUid;
        notifyPropertyChanged(BR.splitUid);
    }
}
