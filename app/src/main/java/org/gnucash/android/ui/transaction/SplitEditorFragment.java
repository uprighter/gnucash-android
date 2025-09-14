/*
 * Copyright (c) 2014 - 2016 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.transaction;

import static org.gnucash.android.ui.util.TextViewExtKt.displayBalance;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

import org.gnucash.android.R;
import org.gnucash.android.app.MenuFragment;
import org.gnucash.android.databinding.FragmentSplitEditorBinding;
import org.gnucash.android.databinding.ItemSplitEntryBinding;
import org.gnucash.android.inputmethodservice.CalculatorKeyboardView;
import org.gnucash.android.model.Account;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.BaseModel;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.ui.adapter.QualifiedAccountNameAdapter;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.transaction.dialog.TransferFundsDialogFragment;
import org.gnucash.android.ui.util.widget.CalculatorEditText;
import org.gnucash.android.ui.util.widget.CalculatorKeyboard;
import org.gnucash.android.ui.util.widget.TransactionTypeSwitch;
import org.gnucash.android.util.AmountParser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import timber.log.Timber;

/**
 * Dialog for editing the splits in a transaction
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class SplitEditorFragment extends MenuFragment {
    private QualifiedAccountNameAdapter accountNameAdapter;
    private final List<SplitViewHolder> splitViewHolders = new ArrayList<>();
    private Account account;

    private BigDecimal mBaseAmount = BigDecimal.ZERO;

    private final BalanceTextWatcher mImbalanceWatcher = new BalanceTextWatcher();

    /**
     * Flag for checking where the TransferFunds dialog has already been displayed to the user
     */
    private boolean mCurrencyConversionDone = false;

    /**
     * Flag which is set if another action is triggered during a transaction save (which interrrupts the save process).
     * Allows the fragment to check and resume the save operation.
     * Primarily used for multi-currency transactions when the currency transfer dialog is opened during save
     */
    private boolean onSaveAttempt = false;
    private final Collection<SplitViewHolder> transferAttempt = new ArrayList<>();

    private FragmentSplitEditorBinding mBinding;
    @ColorInt
    private int colorBalanceZero;

    /**
     * Create and return a new instance of the fragment with the appropriate paramenters
     *
     * @param args Arguments to be set to the fragment. <br>
     *             See {@link UxArgument#AMOUNT_STRING} and {@link UxArgument#SPLIT_LIST}
     * @return New instance of SplitEditorFragment
     */
    public static SplitEditorFragment newInstance(Bundle args) {
        SplitEditorFragment fragment = new SplitEditorFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mBinding = FragmentSplitEditorBinding.inflate(inflater, container, false);
        colorBalanceZero = mBinding.imbalanceTextview.getCurrentTextColor();
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        assert actionBar != null;
        actionBar.setTitle(R.string.title_split_editor);

        //we are editing splits for a new transaction.
        // But the user may have already created some splits before. Let's check

        Bundle args = getArguments();
        FormActivity activity = ((FormActivity) requireActivity());
        mBaseAmount = new BigDecimal(args.getString(UxArgument.AMOUNT_STRING));

        accountNameAdapter = new QualifiedAccountNameAdapter(requireContext(), getViewLifecycleOwner());
        accountNameAdapter.load(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                account = accountNameAdapter.getAccountDb(activity.getCurrentAccountUID());
                if (account == null) {
                    Timber.e("Account not found!");
                    activity.finish();
                    return null;
                }
                loadSplits();
                return null;
            }
        });
    }

    private void loadSplits() {
        splitViewHolders.clear();
        mBinding.splitListLayout.removeAllViews();

        List<Split> splitList = getArguments().getParcelableArrayList(UxArgument.SPLIT_LIST);
        assert splitList != null;

        if (!splitList.isEmpty()) {
            //aha! there are some splits. Let's load those instead
            loadSplitViews(splitList);
            mImbalanceWatcher.afterTextChanged(null);
        } else {
            Account account = this.account;
            Commodity commodity = account.getCommodity();
            Split split = new Split(new Money(mBaseAmount, commodity), account.getUID());
            AccountType accountType = account.getAccountType();
            TransactionType transactionType = Transaction.getTypeForBalance(accountType, mBaseAmount.signum() < 0);
            split.setType(transactionType);
            SplitViewHolder splitViewHolder = addSplitView(split);
            ItemSplitEntryBinding splitViewBinding = splitViewHolder.binding;
            splitViewBinding.inputAccountsSpinner.setEnabled(false);
            splitViewBinding.btnRemoveSplit.setVisibility(View.GONE);
            displayBalance(mBinding.imbalanceTextview, new Money(mBaseAmount.negate(), commodity), colorBalanceZero);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        View view = getView();
        if (view instanceof ViewGroup parent) {
            CalculatorKeyboardView keyboardView = mBinding.calculatorKeyboard.calculatorKeyboard;
            keyboardView = CalculatorKeyboard.rebind(parent, keyboardView, null);
            for (SplitViewHolder viewHolder : splitViewHolders) {
                viewHolder.splitAmountEditText.bindKeyboard(keyboardView);
            }
        }
    }

    private void loadSplitViews(List<Split> splits) {
        for (Split split : splits) {
            addSplitView(split);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.split_editor_actions, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        switch (item.getItemId()) {
            case android.R.id.home: {
                Activity activity = getActivity();
                if (activity == null) {
                    Timber.w("Activity required");
                    return false;
                }
                activity.setResult(Activity.RESULT_CANCELED);
                activity.finish();
                return true;
            }

            case R.id.menu_save:
                saveSplits();
                return true;

            case R.id.menu_add:
                addSplitView(null);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Add a split view and initialize it with <code>split</code>
     *
     * @param split Split to initialize the contents to
     * @return Returns the split view which was added
     */
    private SplitViewHolder addSplitView(Split split) {
        ItemSplitEntryBinding binding = ItemSplitEntryBinding.inflate(getLayoutInflater(), mBinding.splitListLayout, true);
        View splitView = binding.getRoot();
        SplitViewHolder viewHolder = new SplitViewHolder(binding);
        viewHolder.bind(split);
        splitView.setTag(viewHolder);
        splitViewHolders.add(viewHolder);
        return viewHolder;
    }

    /**
     * Holds a split item view and binds the items in it
     */
    class SplitViewHolder implements OnTransferFundsListener {
        private final ItemSplitEntryBinding binding;
        private final View itemView;
        private final EditText splitMemoEditText;
        private final CalculatorEditText splitAmountEditText;
        private final ImageView removeSplitButton;
        private final Spinner accountsSpinner;
        private final TextView splitCurrencyTextView;
        private final TextView splitUidTextView;
        private final TransactionTypeSwitch splitTypeSwitch;

        private Money quantity;

        public SplitViewHolder(ItemSplitEntryBinding binding) {
            itemView = binding.getRoot();
            this.binding = binding;
            this.splitMemoEditText = binding.inputSplitMemo;
            this.splitAmountEditText = binding.inputSplitAmount;
            this.removeSplitButton = binding.btnRemoveSplit;
            this.accountsSpinner = binding.inputAccountsSpinner;
            this.splitCurrencyTextView = binding.splitCurrencySymbol;
            this.splitUidTextView = binding.splitUid;
            this.splitTypeSwitch = binding.btnSplitType;

            splitAmountEditText.bindKeyboard(mBinding.calculatorKeyboard);

            removeSplitButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    SplitViewHolder viewHolder = (SplitViewHolder) itemView.getTag();
                    mBinding.splitListLayout.removeView(itemView);
                    splitViewHolders.remove(viewHolder);
                    mImbalanceWatcher.afterTextChanged(null);
                }
            });

            accountsSpinner.setOnItemSelectedListener(new SplitAccountListener(splitTypeSwitch, this));
            splitTypeSwitch.setAmountFormattingListener(splitAmountEditText, splitCurrencyTextView);
            splitTypeSwitch.addOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    mImbalanceWatcher.afterTextChanged(null);
                }
            });
            splitAmountEditText.addTextChangedListener(mImbalanceWatcher);
        }

        @Override
        public void transferComplete(Money value, Money amount) {
            mCurrencyConversionDone = true;
            quantity = amount;

            //The transfer dialog was called while attempting to save. So try saving again
            SplitViewHolder viewHolder = this;
            transferAttempt.remove(viewHolder);
            if (onSaveAttempt && transferAttempt.isEmpty()) {
                onSaveAttempt = false;
                saveSplits();
            }
        }

        /**
         * Returns the value of the amount in the binding.inputSplitAmount field without setting the value to the view
         * <p>If the expression in the view is currently incomplete or invalid, null is returned.
         * This method is used primarily for computing the imbalance</p>
         *
         * @return Value in the split item amount field, or {@link BigDecimal#ZERO} if the expression is empty or invalid
         */
        public BigDecimal getAmountValue() {
            String amountString = splitAmountEditText.getCleanString();
            BigDecimal amount = AmountParser.evaluate(amountString);
            return (amount != null) ? amount : BigDecimal.ZERO;
        }

        public void bind(@Nullable final Split split) {
            if (split != null && !split.getQuantity().equals(split.getValue())) {
                this.quantity = split.getQuantity();
            }

            accountsSpinner.setAdapter(accountNameAdapter);

            if (split != null) {
                Commodity valueCommodity = split.getValue().getCommodity();
                splitAmountEditText.setCommodity(valueCommodity);
                String splitAccountUID = split.getAccountUID();
                Account account = accountNameAdapter.getAccount(splitAccountUID);
                if (account == null) {
                    Timber.e("Account for split not found");
                    bind(null);
                    return;
                }
                splitAmountEditText.setValue(split.getFormattedValue(account).toBigDecimal(), true /* isOriginal */);
                splitCurrencyTextView.setText(valueCommodity.getSymbol());
                splitMemoEditText.setText(split.getMemo());
                splitUidTextView.setText(split.getUID());
                setSelectedTransferAccount(splitAccountUID, accountsSpinner);
                splitTypeSwitch.setAccountType(account.getAccountType());
                splitTypeSwitch.setChecked(split.getType());
            } else {
                Account account = SplitEditorFragment.this.account;
                Commodity commodity = account.getCommodity();
                splitCurrencyTextView.setText(commodity.getSymbol());
                splitUidTextView.setText(BaseModel.generateUID());

                String transferUID = account.getDefaultTransferAccountUID();
                Account accountTransfer = TextUtils.isEmpty(transferUID) ? null : accountNameAdapter.getAccountDb(transferUID);
                if (accountTransfer != null) {
                    setSelectedTransferAccount(transferUID, accountsSpinner);
                    splitTypeSwitch.setAccountType(accountTransfer.getAccountType());
                }
                splitTypeSwitch.setChecked(mBaseAmount.signum() > 0);
            }
        }
    }

    /**
     * Updates the spinner to the selected transfer account
     *
     * @param accountUID Database ID of the transfer account
     */
    private void setSelectedTransferAccount(@Nullable String accountUID, final Spinner inputAccountsSpinner) {
        int pos = accountNameAdapter.getPosition(accountUID);
        inputAccountsSpinner.setSelection(pos);
    }

    /**
     * Check if all the split amounts have valid values that can be saved
     *
     * @return {@code true} if splits can be saved, {@code false} otherwise
     */
    private boolean canSave() {
        for (SplitViewHolder viewHolder : splitViewHolders) {
            if (!viewHolder.splitAmountEditText.isInputValid()) {
                return false;
            }
            //TODO: also check that multi-currency splits have a conversion amount present
        }
        return true;
    }

    /**
     * Save all the splits from the split editor
     */
    private void saveSplits() {
        if (!canSave()) {
            Snackbar.make(getView(), R.string.toast_error_check_split_amounts, Snackbar.LENGTH_SHORT).show();
            return;
        }

        if (isMultiCurrencyTransaction() && !mCurrencyConversionDone) {
            onSaveAttempt = true;
            if (startTransferFunds()) {
                return;
            }
        }

        Activity activity = getActivity();
        if (activity == null) {
            Timber.w("Activity required");
            return;
        }
        Intent data = new Intent()
            .putParcelableArrayListExtra(UxArgument.SPLIT_LIST, extractSplitsFromView());
        activity.setResult(Activity.RESULT_OK, data);
        activity.finish();
    }

    /**
     * Extracts the input from the views and builds {@link org.gnucash.android.model.Split}s to correspond to the input.
     *
     * @return List of {@link org.gnucash.android.model.Split}s represented in the view
     */
    private ArrayList<Split> extractSplitsFromView() {
        ArrayList<Split> splitList = new ArrayList<>();
        for (SplitViewHolder viewHolder : splitViewHolders) {
            BigDecimal enteredAmount = viewHolder.splitAmountEditText.getValue();
            if (enteredAmount == null)
                continue;

            Account account = this.account;
            Money valueAmount = new Money(enteredAmount.abs(), account.getCommodity());

            int position = viewHolder.accountsSpinner.getSelectedItemPosition();
            account = accountNameAdapter.getAccount(position);
            if (account == null) continue;
            Split split = new Split(valueAmount, account.getUID());
            split.setMemo(viewHolder.splitMemoEditText.getText().toString());
            split.setType(viewHolder.splitTypeSwitch.getTransactionType());
            split.setUID(viewHolder.splitUidTextView.getText().toString().trim());
            if (viewHolder.quantity != null)
                split.setQuantity(viewHolder.quantity.abs());
            splitList.add(split);
        }
        return splitList;
    }

    /**
     * Updates the displayed balance of the accounts when the amount of a split is changed
     */
    private class BalanceTextWatcher implements TextWatcher {

        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            //nothing to see here, move along
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            //nothing to see here, move along
        }

        @Override
        public void afterTextChanged(Editable editable) {
            BigDecimal imbalance = BigDecimal.ZERO;

            for (SplitViewHolder viewHolder : splitViewHolders) {
                BigDecimal amount = viewHolder.getAmountValue().abs();
                int position = viewHolder.accountsSpinner.getSelectedItemPosition();
                if (position < 0) return;
                Account account = accountNameAdapter.getAccount(position);
                if (account == null) return;
                boolean hasDebitNormalBalance = account.getAccountType().hasDebitNormalBalance;

                if (viewHolder.splitTypeSwitch.isChecked()) {
                    if (hasDebitNormalBalance)
                        imbalance = imbalance.add(amount);
                    else
                        imbalance = imbalance.subtract(amount);
                } else {
                    if (hasDebitNormalBalance)
                        imbalance = imbalance.subtract(amount);
                    else
                        imbalance = imbalance.add(amount);
                }
            }

            Account account = SplitEditorFragment.this.account;
            Commodity commodity = account.getCommodity();
            displayBalance(mBinding.imbalanceTextview, new Money(imbalance, commodity), colorBalanceZero);
        }
    }

    /**
     * Listens to changes in the transfer account and updates the currency symbol, the label of the
     * transaction type and if necessary
     */
    private class SplitAccountListener implements AdapterView.OnItemSelectedListener {
        private final TransactionTypeSwitch mTypeToggleButton;
        private final SplitViewHolder mSplitViewHolder;

        /**
         * Flag to know when account spinner callback is due to user interaction or layout of components
         */
        boolean userInteraction = false;

        public SplitAccountListener(TransactionTypeSwitch typeToggleButton, SplitViewHolder viewHolder) {
            this.mTypeToggleButton = typeToggleButton;
            this.mSplitViewHolder = viewHolder;
        }

        @Override
        public void onItemSelected(AdapterView<?> parentView, View view, int position, long id) {
            if (view == null) return;
            Account accountFrom = SplitEditorFragment.this.account;

            Account accountTo = accountNameAdapter.getAccount(position);
            if (accountTo == null) return;
            AccountType accountType = accountTo.getAccountType();
            mTypeToggleButton.setAccountType(accountType);

            //refresh the imbalance amount if we change the account
            mImbalanceWatcher.afterTextChanged(null);

            Commodity fromCommodity = accountFrom.getCommodity();
            Commodity targetCommodity = accountTo.getCommodity();

            if (!userInteraction || fromCommodity.equals(targetCommodity)) {
                //first call is on layout, subsequent calls will be true and transfer will work as usual
                userInteraction = true;
                return;
            }

            transferAttempt.clear();
            startTransferFunds(fromCommodity, targetCommodity, mSplitViewHolder);
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {
            //nothing to see here, move along
        }
    }

    /**
     * Starts the transfer of funds from one currency to another
     */
    private void startTransferFunds(Commodity fromCommodity, Commodity targetCommodity, SplitViewHolder splitViewHolder) {
        BigDecimal enteredAmount = splitViewHolder.splitAmountEditText.getValue();
        if ((enteredAmount == null) || enteredAmount.equals(BigDecimal.ZERO))
            return;

        transferAttempt.add(splitViewHolder);

        Money amount = new Money(enteredAmount, fromCommodity).abs();
        TransferFundsDialogFragment fragment
            = TransferFundsDialogFragment.getInstance(amount, targetCommodity, splitViewHolder);
        fragment.show(getParentFragmentManager(), "transfer_funds_editor;" + fromCommodity + ";" + targetCommodity + ";" + amount.toPlainString());
    }

    /**
     * Starts the transfer of funds from one currency to another
     */
    private boolean startTransferFunds() {
        boolean result = false;
        Account accountFrom = this.account;
        Commodity fromCommodity = accountFrom.getCommodity();
        transferAttempt.clear();

        for (SplitViewHolder viewHolder : splitViewHolders) {
            if (!viewHolder.splitAmountEditText.isInputModified()) continue;
            Money splitQuantity = viewHolder.quantity;
            if (splitQuantity == null) continue;
            Commodity splitCommodity = splitQuantity.getCommodity();
            if (fromCommodity.equals(splitCommodity)) continue;
            startTransferFunds(fromCommodity, splitCommodity, viewHolder);
            result = true;
        }

        return result;
    }

    /**
     * Checks if this is a multi-currency transaction being created/edited
     * <p>A multi-currency transaction is one in which the main account and transfer account have different currencies. <br>
     * Single-entry transactions cannot be multi-currency</p>
     *
     * @return {@code true} if multi-currency transaction, {@code false} otherwise
     */
    private boolean isMultiCurrencyTransaction() {
        Account accountFrom = this.account;
        Commodity accountCommodity = accountFrom.getCommodity();

        List<Split> splits = extractSplitsFromView();
        for (Split split : splits) {
            Commodity splitCommodity = split.getQuantity().getCommodity();
            if (!accountCommodity.equals(splitCommodity)) {
                return true;
            }
        }

        return false;
    }
}
