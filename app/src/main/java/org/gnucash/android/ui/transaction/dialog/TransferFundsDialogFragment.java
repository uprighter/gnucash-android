/*
 * Copyright (c) 2015 Ngewi Fet <ngewif@gmail.com>
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

package org.gnucash.android.ui.transaction.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.textfield.TextInputLayout;

import org.gnucash.android.R;
import org.gnucash.android.databinding.DialogTransferFundsBinding;
import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.db.adapter.PricesDbAdapter;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Price;
import org.gnucash.android.ui.transaction.OnTransferFundsListener;
import org.gnucash.android.ui.transaction.TransactionsActivity;
import org.gnucash.android.util.AmountParser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;

/**
 * Dialog fragment for handling currency conversions when inputting transactions.
 * <p>This is used whenever a multi-currency transaction is being created.</p>
 */
public class TransferFundsDialogFragment extends DialogFragment {
    public static final String LOG_TAG = TransferFundsDialogFragment.class.getName();

    DialogTransferFundsBinding mBinding;
    TextView mFromCurrencyLabel;
    TextView mToCurrencyLabel;
    TextView mConvertedAmountCurrencyLabel;
    TextView mStartAmountLabel;
    EditText mExchangeRateInput;
    EditText mConvertedAmountInput;
    Button mFetchExchangeRateButton;
    RadioButton mExchangeRateRadioButton;
    RadioButton mConvertedAmountRadioButton;
    TextView mSampleExchangeRate;
    TextInputLayout mExchangeRateInputLayout;
    TextInputLayout mConvertedAmountInputLayout;

    Button mSaveButton;
    Button mCancelButton;

    Money mOriginAmount;
    private Commodity mTargetCommodity;

    Money mConvertedAmount;
    OnTransferFundsListener mOnTransferFundsListener;

    public static TransferFundsDialogFragment getInstance(Money transactionAmount, String targetCurrencyCode,
                                                          OnTransferFundsListener transferFundsListener) {
        TransferFundsDialogFragment fragment = new TransferFundsDialogFragment();
        fragment.mOriginAmount = transactionAmount;
        fragment.mTargetCommodity = CommoditiesDbAdapter.getInstance().getCommodity(targetCurrencyCode);
        fragment.mOnTransferFundsListener = transferFundsListener;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        DialogTransferFundsBinding mBinding = DialogTransferFundsBinding.inflate(inflater, container, false);

        mFromCurrencyLabel = mBinding.fromCurrency;
        mToCurrencyLabel = mBinding.toCurrency;
        mConvertedAmountCurrencyLabel = mBinding.targetCurrency;
        mStartAmountLabel = mBinding.amountToConvert;
        mExchangeRateInput = mBinding.inputExchangeRate;
        mConvertedAmountInput = mBinding.inputConvertedAmount;
        mFetchExchangeRateButton = mBinding.btnFetchExchangeRate;
        mExchangeRateRadioButton = mBinding.radioExchangeRate;
        mConvertedAmountRadioButton = mBinding.radioConvertedAmount;
        mSampleExchangeRate = mBinding.labelExchangeRateExample;
        mExchangeRateInputLayout = mBinding.exchangeRateTextInputLayout;
        mConvertedAmountInputLayout = mBinding.convertedAmountTextInputLayout;

        mSaveButton = mBinding.defaultButtons.btnSave;
        mCancelButton = mBinding.defaultButtons.btnCancel;


        TransactionsActivity.displayBalance(mStartAmountLabel, mOriginAmount);
        String fromCurrencyCode = mOriginAmount.getCommodity().getCurrencyCode();
        mFromCurrencyLabel.setText(fromCurrencyCode);
        mToCurrencyLabel.setText(mTargetCommodity.getCurrencyCode());
        mConvertedAmountCurrencyLabel.setText(mTargetCommodity.getCurrencyCode());

        mSampleExchangeRate.setText(String.format(getString(R.string.sample_exchange_rate),
                fromCurrencyCode,
                mTargetCommodity.getCurrencyCode()));
        final InputLayoutErrorClearer textChangeListener = new InputLayoutErrorClearer();

        CommoditiesDbAdapter commoditiesDbAdapter = CommoditiesDbAdapter.getInstance();
        String commodityUID = commoditiesDbAdapter.getCommodityUID(fromCurrencyCode);
        String currencyUID = mTargetCommodity.getUID();
        PricesDbAdapter pricesDbAdapter = PricesDbAdapter.getInstance();
        Pair<Long, Long> pricePair = pricesDbAdapter.getPrice(commodityUID, currencyUID);

        if (pricePair.first > 0 && pricePair.second > 0) {
            // a valid price exists
            Price price = new Price(commodityUID, currencyUID);
            price.setValueNum(pricePair.first);
            price.setValueDenom(pricePair.second);
            mExchangeRateInput.setText(price.toString());

            BigDecimal numerator = new BigDecimal(pricePair.first);
            BigDecimal denominator = new BigDecimal(pricePair.second);
            // convertedAmount = mOriginAmount * numerator / denominator
            BigDecimal convertedAmount = mOriginAmount.asBigDecimal().multiply(numerator)
                    .divide(denominator, mTargetCommodity.getSmallestFractionDigits(), RoundingMode.HALF_EVEN);
            DecimalFormat formatter = (DecimalFormat) NumberFormat.getNumberInstance();
            mConvertedAmountInput.setText(formatter.format(convertedAmount));
        }

        mExchangeRateInput.addTextChangedListener(textChangeListener);
        mConvertedAmountInput.addTextChangedListener(textChangeListener);

        mConvertedAmountRadioButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mConvertedAmountInput.setEnabled(isChecked);
            mConvertedAmountInputLayout.setErrorEnabled(isChecked);
            mExchangeRateRadioButton.setChecked(!isChecked);
            if (isChecked) {
                mConvertedAmountInput.requestFocus();
            }
        });

        mExchangeRateRadioButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mExchangeRateInput.setEnabled(isChecked);
            mExchangeRateInputLayout.setErrorEnabled(isChecked);
            mFetchExchangeRateButton.setEnabled(isChecked);
            mConvertedAmountRadioButton.setChecked(!isChecked);
            if (isChecked) {
                mExchangeRateInput.requestFocus();
            }
        });

        mFetchExchangeRateButton.setOnClickListener(v -> {
            //TODO: Pull the exchange rate for the currency here
        });

        mCancelButton.setOnClickListener(v -> dismiss());

        mSaveButton.setOnClickListener(v -> transferFunds());
        return mBinding.getRoot();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mBinding = null;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setTitle(R.string.title_transfer_funds);
        return dialog;
    }

    /**
     * Converts the currency amount with the given exchange rate and saves the price to the db
     */
    private void transferFunds() {
        Price price = null;

        String originCommodityUID = mOriginAmount.getCommodity().getUID();
        String targetCommodityUID = mTargetCommodity.getUID();

        if (mExchangeRateRadioButton.isChecked()) {
            BigDecimal rate;
            try {
                rate = AmountParser.parse(mExchangeRateInput.getText().toString());
            } catch (ParseException e) {
                mExchangeRateInputLayout.setError(getString(R.string.error_invalid_exchange_rate));
                return;
            }
            price = new Price(originCommodityUID, targetCommodityUID, rate);

            mConvertedAmount = mOriginAmount.multiply(rate).withCurrency(mTargetCommodity);
        }

        if (mConvertedAmountRadioButton.isChecked()) {
            BigDecimal amount;
            try {
                amount = AmountParser.parse(mConvertedAmountInput.getText().toString());
            } catch (ParseException e) {
                mConvertedAmountInputLayout.setError(getString(R.string.error_invalid_amount));
                return;
            }
            mConvertedAmount = new Money(amount, mTargetCommodity);

            price = new Price(originCommodityUID, targetCommodityUID);
            // fractions cannot be exactly represented by BigDecimal.
            price.setValueNum(mConvertedAmount.getNumerator() * mOriginAmount.getDenominator());
            price.setValueDenom(mOriginAmount.getNumerator() * mConvertedAmount.getDenominator());
        }

        price.setSource(Price.SOURCE_USER);
        PricesDbAdapter.getInstance().addRecord(price);

        if (mOnTransferFundsListener != null)
            mOnTransferFundsListener.transferComplete(mConvertedAmount);

        dismiss();
    }

    /**
     * Hides the error message from mConvertedAmountInputLayout and mExchangeRateInputLayout
     * when the user edits their content.
     */
    private class InputLayoutErrorClearer implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            mConvertedAmountInputLayout.setErrorEnabled(false);
            mExchangeRateInputLayout.setErrorEnabled(false);
        }
    }
}
