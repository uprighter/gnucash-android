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

import androidx.databinding.ViewDataBinding;
import androidx.databinding.library.baseAdapters.BR;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
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
import java.util.Collections;
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
    private ArrayList<SplitEntryViewModel> mSplitEntryViewModelList;

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

        //we are editing splits for a new transaction.
        // But the user may have already created some splits before. Let's check

        mSplitEntryViewModelList = new ArrayList<>();
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
            TransactionsActivity.displayBalance(mImbalanceTextView, new Money(mBaseAmount.negate(), mCommodity));
        }


        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {

                Collections.swap(mSplitEntryViewModelList, viewHolder.getAbsoluteAdapterPosition(), target.getAbsoluteAdapterPosition());
                Log.i(LOG_TAG, "onMove: " + viewHolder.getAbsoluteAdapterPosition() + ", " +
                        target.getAbsoluteAdapterPosition());
                mRecyclerViewAdaptor.notifyItemMoved(viewHolder.getAbsoluteAdapterPosition(), target.getAbsoluteAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // no-op
                Log.i(LOG_TAG, "onSwiped: " + viewHolder.getAbsoluteAdapterPosition() + ", direction: " +
                        direction);
            }

            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                return makeFlag(ItemTouchHelper.ACTION_STATE_DRAG,
                        ItemTouchHelper.DOWN | ItemTouchHelper.UP | ItemTouchHelper.START | ItemTouchHelper.END);
            }
        });
        touchHelper.attachToRecyclerView(mRecyclerView);

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

        Log.d(LOG_TAG, "mCursor: " + mCursor);
        Log.d(LOG_TAG, "mCommodity: " + mCommodity);
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
        SplitEntryViewModel viewModel = new SplitEntryViewModel();
        viewModel.setSplit(split);
        mSplitEntryViewModelList.add(0, viewModel);
        mRecyclerViewAdaptor.notifyItemInserted(0);
        mRecyclerView.scrollToPosition(0);
        Log.d(LOG_TAG, mSplitEntryViewModelList.size() + " splits, after added " + split);
    }

    /**
     * Provide views to RecyclerView with mSplitItemViewList.
     */
    class RecyclerViewAdapter extends RecyclerView.Adapter<SplitEntryViewHolder> {

        @Override
        public @NonNull SplitEntryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Log.d(LOG_TAG, "onCreateViewHolder, viewType " + viewType);

            ViewDataBinding binding = ItemSplitEntryBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new SplitEntryViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull SplitEntryViewHolder splitEntryViewHolder, int position) {
            Log.d(LOG_TAG, "onBindViewHolder at position " + position + " for binding " + splitEntryViewHolder.binding);
            Log.d(LOG_TAG, "onBindViewHolder view: " + mSplitEntryViewModelList.get(position));

            SplitEntryViewModel viewModel = mSplitEntryViewModelList.get(position);
            splitEntryViewHolder.bind(viewModel);
            viewModel.setViewHolder(splitEntryViewHolder);
            splitEntryViewHolder.setListeners((Split)viewModel.getSplit());
        }

        @Override
        public int getItemCount() {
            return mSplitEntryViewModelList.size();
        }
    }

    /**
     * Holds a split item view and binds the items in it
     */
    class SplitEntryViewHolder extends RecyclerView.ViewHolder implements OnTransferFundsListener {

        private final ViewDataBinding binding;
        private SplitEntryViewModel bindedViewModel;

        Money quantity;
        EditText splitMemoEditText;
        CalculatorEditText splitAmountEditText;
        ImageView removeSplitButton;
        Spinner accountsSpinner;
        TextView splitCurrencyTextView;
        TextView splitUidTextView;
        TransactionTypeSwitch splitTypeSwitch;

        public SplitEntryViewHolder(ViewDataBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Object obj) {
            bindedViewModel = (SplitEntryViewModel)obj;
            binding.setVariable(BR.splitEntryViewModel, obj);
            binding.executePendingBindings();

            ItemSplitEntryBinding itemSplitEntryBinding = (ItemSplitEntryBinding)binding;
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
                    for (SplitEntryViewModel viewModel : mSplitEntryViewModelList) {
                        if (viewModel == bindedViewModel) {
                            break;
                        }
                        removedPosition ++;
                    }
                    if (removedPosition < mSplitEntryViewModelList.size()) {
                        mSplitEntryViewModelList.remove(bindedViewModel);
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
                binding.getRoot().requestFocus();
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
        for (SplitEntryViewModel viewModel : mSplitEntryViewModelList) {
            if (viewModel.getInputSplitAmount() == null) {
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
        for (int i=0; i<mSplitEntryViewModelList.size(); i++) {
            SplitEntryViewModel splitEntryViewModel = mSplitEntryViewModelList.get(i);
            SplitEntryViewHolder viewHolder = (SplitEntryViewHolder) splitEntryViewModel.getViewHolder();
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

            for (int i=0; i<mSplitEntryViewModelList.size(); i++) {
                SplitEntryViewModel viewModel = mSplitEntryViewModelList.get(i);
                SplitEntryViewHolder viewHolder = (SplitEntryViewHolder) viewModel.getViewHolder();
                if (viewHolder == null) {
                    Split split = (Split) viewModel.getSplit();
                    if (split == null) {
                        continue;
                    }
                    boolean hasDebitNormalBalance = AccountsDbAdapter.getInstance()
                            .getAccountType(split.getAccountUID()).hasDebitNormalBalance();
                    BigDecimal amount = split.getValue().asBigDecimal();
                    if (split.getType() == TransactionType.CREDIT) {
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
                } else {
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
        SplitEntryViewHolder mSplitEntryViewHolder;

        /**
         * Flag to know when account spinner callback is due to user interaction or layout of components
         */
        boolean userInteraction = false;

        public SplitAccountListener(TransactionTypeSwitch typeToggleButton, SplitEntryViewHolder viewHolder) {
            this.mTypeToggleButton = typeToggleButton;
            this.mSplitEntryViewHolder = viewHolder;
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

            BigDecimal amountBigD = mSplitEntryViewHolder.splitAmountEditText.getValue();
            if (amountBigD == null)
                return;

            Money amount = new Money(amountBigD, Commodity.getInstance(fromCurrencyCode));
            TransferFundsDialogFragment fragment
                    = TransferFundsDialogFragment.getInstance(amount, targetCurrencyCode, mSplitEntryViewHolder);
            fragment.show(getActivity().getSupportFragmentManager(), "transfer_funds_editor");
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {
            //nothing to see here, move along
        }
    }

}
