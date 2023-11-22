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

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.inputmethodservice.KeyboardView;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import org.gnucash.android.R;
import org.gnucash.android.databinding.FragmentSplitEditorBinding;
import org.gnucash.android.databinding.ItemSplitEntryBinding;

import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.model.AccountType;
import org.gnucash.android.model.BaseModel;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Split;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.model.TransactionType;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.transaction.dialog.TransferFundsDialogFragment;
import org.gnucash.android.ui.util.widget.CalculatorEditText;
import org.gnucash.android.ui.util.widget.CalculatorKeyboard;
import org.gnucash.android.ui.util.widget.TransactionTypeSwitch;
import org.gnucash.android.util.QualifiedAccountNameCursorAdapter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for editing the splits in a transaction
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class SplitEditorFragment extends Fragment {

    public static final String LOG_TAG = "SplitEditorFragment";

    private FragmentSplitEditorBinding binding;

    KeyboardView mKeyboardView;
    TextView mImbalanceTextView;

    private RecyclerView mRecyclerView;
    private RecyclerViewAdapter mRecyclerViewAdaptor;
    private ArrayList<View> mSplitItemViewList;
    private ArrayList<Split> mSplitList;


    private AccountsDbAdapter mAccountsDbAdapter;
    private Cursor mCursor;
    private SimpleCursorAdapter mCursorAdapter;
    private String mAccountUID;
    private Commodity mCommodity;

    private BigDecimal mBaseAmount = BigDecimal.ZERO;

    CalculatorKeyboard mCalculatorKeyboard;

    BalanceTextWatcher mImbalanceWatcher = new BalanceTextWatcher();

    /**
     * Create and return a new instance of the fragment with the appropriate parameters
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
    public @Nullable View onCreateView(@NonNull LayoutInflater inflater,
                                       @Nullable ViewGroup container,
                                       @Nullable Bundle savedInstanceState) {
        binding = FragmentSplitEditorBinding.inflate(inflater, container, false);

        mKeyboardView = binding.calculatorKeyboard;
        mImbalanceTextView = binding.imbalanceTextview;
        mRecyclerView = binding.splitListRecycler;

        mRecyclerViewAdaptor = new RecyclerViewAdapter();
        mRecyclerView.setAdapter(mRecyclerViewAdaptor);
        LinearLayoutManager recyclerViewManager = new LinearLayoutManager(getActivity());
        mRecyclerView.setLayoutManager(recyclerViewManager);

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        assert actionBar != null;
        actionBar.setTitle(R.string.title_split_editor);
        setHasOptionsMenu(true);

        initArgs();

        mCalculatorKeyboard = new CalculatorKeyboard(getActivity(), mKeyboardView, R.xml.calculator_keyboard);
        mSplitItemViewList = new ArrayList<>();

        //we are editing splits for a new transaction.
        // But the user may have already created some splits before. Let's check

        mSplitList = new ArrayList<>();
        List<Split> splitList = getArguments().getParcelableArrayList(UxArgument.SPLIT_LIST, Split.class);
        assert splitList != null;

        if (!splitList.isEmpty()) {
            //aha! there are some splits. Let's load those instead
            loadSplitViews(splitList);
            mImbalanceWatcher.afterTextChanged(null);
        } else {
            final String currencyCode = mAccountsDbAdapter.getAccountCurrencyCode(mAccountUID);
            Split split = new Split(new Money(mBaseAmount, Commodity.getInstance(currencyCode)), mAccountUID);
            AccountType accountType = mAccountsDbAdapter.getAccountType(mAccountUID);
            TransactionType transactionType = Transaction.getTypeForBalance(accountType, mBaseAmount.signum() < 0);
            split.setType(transactionType);
            addSplitView(split);
//            View splitView = addSplitView(split);
//            splitView.findViewById(R.id.input_accounts_spinner).setEnabled(false);
//            splitView.findViewById(R.id.btn_remove_split).setVisibility(View.GONE);
            TransactionsActivity.displayBalance(mImbalanceTextView, new Money(mBaseAmount.negate(), mCommodity));
        }
    }

    /**
     * Extracts arguments passed to the view and initializes necessary adapters and cursors
     */
    private void initArgs() {
        mAccountsDbAdapter = AccountsDbAdapter.getInstance();

        mAccountUID = ((FormActivity) getActivity()).getCurrentAccountUID();
        mBaseAmount = new BigDecimal(getArguments().getString(UxArgument.AMOUNT_STRING));

        String conditions = "("
                + DatabaseSchema.AccountEntry.COLUMN_HIDDEN + " = 0 AND "
                + DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER + " = 0"
                + ")";
        mCursor = mAccountsDbAdapter.fetchAccountsOrderedByFullName(conditions, null);
        mCommodity = CommoditiesDbAdapter.getInstance().getCommodity(mAccountsDbAdapter.getCurrencyCode(mAccountUID));
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mCalculatorKeyboard = new CalculatorKeyboard(getActivity(), mKeyboardView, R.xml.calculator_keyboard);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.split_editor_actions, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case android.R.id.home:
                if (getActivity() != null) {
                    getActivity().setResult(Activity.RESULT_CANCELED);
                    getActivity().finish();
                }
                return true;

            case R.id.menu_save:
                saveSplits();
                return true;

            case R.id.menu_add_split:
                addSplitView(null);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void loadSplitViews(List<Split> splitList) {
        for (Split split : splitList) {
            Log.d(LOG_TAG, "load split: " + split);
            addSplitView(split);
        }
    }

    /**
     * Add a split view and initialize it with <code>split</code>
     *
     * @param split Split to initialize the contents to
     */
    private void addSplitView(Split split) {
        mSplitList.add(0, split);
        mSplitItemViewList.add(0, null);
        mRecyclerViewAdaptor.notifyItemInserted(0);
        mRecyclerView.scrollToPosition(0);
        Log.d(LOG_TAG, mSplitItemViewList.size() + " splits, after added " + split);
    }

    /**
     * Provide views to RecyclerView with mSplitItemViewList.
     */
    class RecyclerViewAdapter extends RecyclerView.Adapter<SplitViewHolder> {

        @Override
        public @NonNull SplitViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Log.d(LOG_TAG, "onCreateViewHolder, viewType " + viewType);
            View splitView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_split_entry, parent, false);
            SplitViewHolder viewHolder = new SplitViewHolder(splitView);
            splitView.setTag(viewHolder);
            return viewHolder;
        }

        @Override
        public void onBindViewHolder(@NonNull SplitViewHolder splitViewHolder, int position) {
            Log.d(LOG_TAG, "onBindViewHolder at position " + position + " for " + splitViewHolder.splitView);
            Log.d(LOG_TAG, "onBindViewHolder split: " + mSplitList.get(position) + ", view: " + mSplitItemViewList.get(position));

            if (mSplitItemViewList.get(position) == null) {
                mSplitItemViewList.set(position, splitViewHolder.splitView);
            }
            splitViewHolder.setListeners(mSplitList.get(position));
        }

        @Override
        public int getItemCount() {
            return mSplitItemViewList.size();
        }
    }

    /**
     * Holds a split item view and binds the items in it
     */
    class SplitViewHolder extends RecyclerView.ViewHolder implements OnTransferFundsListener {
        EditText splitMemoEditText;
        CalculatorEditText splitAmountEditText;
        ImageView removeSplitButton;
        Spinner accountsSpinner;
        TextView splitCurrencyTextView;
        TextView splitUidTextView;
        TransactionTypeSwitch splitTypeSwitch;

        View splitView;
        Money quantity;

        public SplitViewHolder(View splitView) {
            super(splitView);
            this.splitView = splitView;
            ItemSplitEntryBinding itemSplitEntryBinding = ItemSplitEntryBinding.bind(splitView);
            splitMemoEditText = itemSplitEntryBinding.inputSplitMemo;
            splitAmountEditText = itemSplitEntryBinding.inputSplitAmount;
            removeSplitButton = itemSplitEntryBinding.btnRemoveSplit;
            accountsSpinner = itemSplitEntryBinding.inputAccountsSpinner;
            splitCurrencyTextView = itemSplitEntryBinding.splitCurrencySymbol;
            splitUidTextView = itemSplitEntryBinding.splitUid;
            splitTypeSwitch = itemSplitEntryBinding.btnSplitType;
        }

        @Override
        public void transferComplete(Money amount) {
            quantity = amount;
        }

        public void setListeners(Split split) {
            splitAmountEditText.bindListeners(mCalculatorKeyboard);

            removeSplitButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View _view) {
                    int removedPosition = 0;
                    for (View v : mSplitItemViewList) {
                        if (v == splitView) {
                            break;
                        }
                        removedPosition ++;
                    }
                    if (removedPosition < mSplitItemViewList.size()) {
                        mSplitItemViewList.remove(splitView);
                        mSplitList.remove(split);
                        mRecyclerViewAdaptor.notifyItemRemoved(removedPosition);
                        mImbalanceWatcher.afterTextChanged(null);
                    }
                }
            });

            updateTransferAccountsList(accountsSpinner);

            splitCurrencyTextView.setText(mCommodity.getSymbol());
            splitTypeSwitch.setAmountFormattingListener(splitAmountEditText, splitCurrencyTextView);
            splitTypeSwitch.setChecked(mBaseAmount.signum() > 0);
            splitUidTextView.setText(BaseModel.generateUID());

            if (split != null) {
                splitAmountEditText.setCommodity(split.getValue().getCommodity());
                splitAmountEditText.setValue(split.getFormattedValue().asBigDecimal());
                splitCurrencyTextView.setText(split.getValue().getCommodity().getSymbol());
                splitMemoEditText.setText(split.getMemo());
                splitUidTextView.setText(split.getUID());
                String splitAccountUID = split.getAccountUID();
                assert splitAccountUID != null;
                setSelectedTransferAccount(mAccountsDbAdapter.getID(splitAccountUID), accountsSpinner);
                splitTypeSwitch.setAccountType(mAccountsDbAdapter.getAccountType(splitAccountUID));
                splitTypeSwitch.setChecked(split.getType());
            } else {
                this.splitView.requestFocus();
            }

            accountsSpinner.setOnItemSelectedListener(new SplitAccountListener(splitTypeSwitch, this));
            splitTypeSwitch.addOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    mImbalanceWatcher.afterTextChanged(null);
                }
            });
            splitAmountEditText.addTextChangedListener(mImbalanceWatcher);
        }

        /**
         * Returns the value of the amount in the splitAmountEditText field without setting the value to the view
         * <p>If the expression in the view is currently incomplete or invalid, null is returned.
         * This method is used primarily for computing the imbalance</p>
         *
         * @return Value in the split item amount field, or {@link BigDecimal#ZERO} if the expression is empty or invalid
         */
        public BigDecimal getAmountValue() {
            String amountString = splitAmountEditText.getCleanString();
            if (amountString.isEmpty())
                return BigDecimal.ZERO;

            ExpressionBuilder expressionBuilder = new ExpressionBuilder(amountString);
            Expression expression;

            try {
                expression = expressionBuilder.build();
            } catch (RuntimeException e) {
                return BigDecimal.ZERO;
            }

            if (expression != null && expression.validate().isValid()) {
                return BigDecimal.valueOf(expression.evaluate());
            } else {
                Log.v(SplitEditorFragment.this.getClass().getSimpleName(),
                        "Incomplete expression for updating imbalance: " + expression);
                return BigDecimal.ZERO;
            }
        }
    }

    /**
     * Updates the spinner to the selected transfer account
     *
     * @param accountId Database ID of the transfer account
     */
    private void setSelectedTransferAccount(long accountId, final Spinner accountsSpinner) {
        for (int pos = 0; pos < mCursorAdapter.getCount(); pos++) {
            if (mCursorAdapter.getItemId(pos) == accountId) {
                accountsSpinner.setSelection(pos);
                break;
            }
        }
    }

    /**
     * Updates the list of possible transfer accounts.
     * Only accounts with the same currency can be transferred to
     */
    private void updateTransferAccountsList(Spinner transferAccountSpinner) {
        mCursorAdapter = new QualifiedAccountNameCursorAdapter(getActivity(), mCursor);
        transferAccountSpinner.setAdapter(mCursorAdapter);
    }

    /**
     * Check if all the split amounts have valid values that can be saved
     *
     * @return {@code true} if splits can be saved, {@code false} otherwise
     */
    private boolean canSave() {
        for (View splitView : mSplitItemViewList) {
            if (splitView != null) {
                SplitViewHolder viewHolder = (SplitViewHolder) splitView.getTag();
                viewHolder.splitAmountEditText.evaluate();
                if (viewHolder.splitAmountEditText.getError() != null) {
                    return false;
                }
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
            Toast.makeText(getActivity(), R.string.toast_error_check_split_amounts,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Intent data = new Intent();
        data.putParcelableArrayListExtra(UxArgument.SPLIT_LIST, extractSplitsFromView());
        getActivity().setResult(Activity.RESULT_OK, data);

        getActivity().finish();
    }

    /**
     * Extracts the input from the views and builds {@link org.gnucash.android.model.Split}s to correspond to the input.
     *
     * @return List of {@link org.gnucash.android.model.Split}s represented in the view
     */
    private ArrayList<Split> extractSplitsFromView() {
        ArrayList<Split> splitList = new ArrayList<>();
        for (int i=0; i<mSplitItemViewList.size(); i++) {
            View splitView = mSplitItemViewList.get(i);
            if (splitView == null) {
                Split split = mSplitList.get(i);
                if (split != null) {
                    splitList.add(split);
                }
                continue;
            }
            SplitViewHolder viewHolder = (SplitViewHolder) splitView.getTag();
            if (viewHolder.splitAmountEditText.getValue() == null)
                continue;

            BigDecimal amountBigDecimal = viewHolder.splitAmountEditText.getValue();

            String currencyCode = mAccountsDbAdapter.getCurrencyCode(mAccountUID);
            Money valueAmount = new Money(amountBigDecimal.abs(), Commodity.getInstance(currencyCode));

            String accountUID = mAccountsDbAdapter.getUID(viewHolder.accountsSpinner.getSelectedItemId());
            Split split = new Split(valueAmount, accountUID);
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

            for (int i=0; i<mSplitItemViewList.size(); i++) {
                View splitView = mSplitItemViewList.get(i);
                if (splitView == null) {
                    Split split = mSplitList.get(i);
                    if (split == null) {
                        continue;
                    }
                    if (split.getType() == TransactionType.CREDIT) {
                        imbalance = imbalance.add(split.getValue().asBigDecimal());
                    } else {
                        imbalance = imbalance.subtract(split.getValue().asBigDecimal());
                    }
                } else {
                    SplitViewHolder viewHolder = (SplitViewHolder) splitView.getTag();
                    BigDecimal amount = viewHolder.getAmountValue().abs();
                    long accountId = viewHolder.accountsSpinner.getSelectedItemId();
                    boolean hasDebitNormalBalance = AccountsDbAdapter.getInstance()
                            .getAccountType(accountId).hasDebitNormalBalance();

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
            }

            TransactionsActivity.displayBalance(mImbalanceTextView, new Money(imbalance, mCommodity));
        }
    }

    /**
     * Listens to changes in the transfer account and updates the currency symbol, the label of the
     * transaction type and if necessary
     */
    private class SplitAccountListener implements AdapterView.OnItemSelectedListener {
        TransactionTypeSwitch mTypeToggleButton;
        SplitViewHolder mSplitViewHolder;

        /**
         * Flag to know when account spinner callback is due to user interaction or layout of components
         */
        boolean userInteraction = false;

        public SplitAccountListener(TransactionTypeSwitch typeToggleButton, SplitViewHolder viewHolder) {
            this.mTypeToggleButton = typeToggleButton;
            this.mSplitViewHolder = viewHolder;
        }

        @Override
        public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
            AccountType accountType = mAccountsDbAdapter.getAccountType(id);
            mTypeToggleButton.setAccountType(accountType);

            //refresh the imbalance amount if we change the account
            mImbalanceWatcher.afterTextChanged(null);

            String fromCurrencyCode = mAccountsDbAdapter.getCurrencyCode(mAccountUID);
            String targetCurrencyCode = mAccountsDbAdapter.getCurrencyCode(mAccountsDbAdapter.getUID(id));

            if (!userInteraction || fromCurrencyCode.equals(targetCurrencyCode)) {
                //first call is on layout, subsequent calls will be true and transfer will work as usual
                userInteraction = true;
                return;
            }

            BigDecimal amountBigD = mSplitViewHolder.splitAmountEditText.getValue();
            if (amountBigD == null)
                return;

            Money amount = new Money(amountBigD, Commodity.getInstance(fromCurrencyCode));
            TransferFundsDialogFragment fragment
                    = TransferFundsDialogFragment.getInstance(amount, targetCurrencyCode, mSplitViewHolder);
            fragment.show(getActivity().getSupportFragmentManager(), "transfer_funds_editor");
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {
            //nothing to see here, move along
        }
    }

}
