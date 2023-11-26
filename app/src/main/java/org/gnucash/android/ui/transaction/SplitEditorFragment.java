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
import android.widget.ImageButton;
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
    BigDecimal mImbalance = BigDecimal.ZERO;


    private RecyclerView mRecyclerView;
    private RecyclerViewAdapter mRecyclerViewAdaptor;
    private ArrayList<SplitEntryViewModel> mSplitEntryViewModelList;

    private AccountsDbAdapter mAccountsDbAdapter;
    private SimpleCursorAdapter mCursorAdapter;
    private String mAccountUID;
    private Commodity mCommodity;

    private BigDecimal mBaseAmount = BigDecimal.ZERO;

    private CalculatorKeyboard mCalculatorKeyboard;

    private final BalanceTextWatcher mImbalanceWatcher = new BalanceTextWatcher();

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
        Log.d(LOG_TAG, "onCreateView: binding = " + binding + ", savedInstanceState = " + savedInstanceState);

        mKeyboardView = binding.calculatorKeyboard;
        mImbalanceTextView = binding.imbalanceTextview;
        mRecyclerView = binding.splitListRecycler;

        mRecyclerViewAdaptor = new RecyclerViewAdapter();
        mRecyclerView.setAdapter(mRecyclerViewAdaptor);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        Log.d(LOG_TAG, "onDestroyView: binding = " + binding);
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

        initAdaptersAndArgs();

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

        // Add drag-to-reorder feature.
        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {

                Collections.swap(mSplitEntryViewModelList, viewHolder.getAbsoluteAdapterPosition(), target.getAbsoluteAdapterPosition());
                Log.d(LOG_TAG, "onMove: " + viewHolder.getAbsoluteAdapterPosition() + ", " +
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
                        ItemTouchHelper.DOWN | ItemTouchHelper.UP);
            }
        });
        touchHelper.attachToRecyclerView(mRecyclerView);

    }

    /**
     * Extracts arguments passed to the view and initializes necessary adapters and cursors
     */
    private void initAdaptersAndArgs() {
        mAccountsDbAdapter = AccountsDbAdapter.getInstance();

        mAccountUID = ((FormActivity) getActivity()).getCurrentAccountUID();
        mCommodity = CommoditiesDbAdapter.getInstance().getCommodity(mAccountsDbAdapter.getCurrencyCode(mAccountUID));
        mBaseAmount = new BigDecimal(getArguments().getString(UxArgument.AMOUNT_STRING));

        String conditions = "("
                + DatabaseSchema.AccountEntry.COLUMN_HIDDEN + " = 0 AND "
                + DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER + " = 0"
                + ")";
        Cursor cursor = mAccountsDbAdapter.fetchAccountsOrderedByFullName(conditions, null);
        mCursorAdapter = new QualifiedAccountNameCursorAdapter(getActivity(), cursor);

        Log.d(LOG_TAG, "mCommodity: " + mCommodity);
        Log.d(LOG_TAG, "mCursorAdapter: " + mCursorAdapter);
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
        Collections.reverse(splitList);
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
        SplitEntryViewModel viewModel = new SplitEntryViewModel(split);
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
            ItemSplitEntryBinding binding = ItemSplitEntryBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            Log.d(LOG_TAG, "onCreateViewHolder, binding: " + binding);
            return new SplitEntryViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull SplitEntryViewHolder splitEntryViewHolder, int position) {
            Log.d(LOG_TAG, "onBindViewHolder at position " + position + " for binding " + splitEntryViewHolder.binding);
            Log.d(LOG_TAG, "onBindViewHolder viewModel: " + mSplitEntryViewModelList.get(position));

            SplitEntryViewModel viewModel = mSplitEntryViewModelList.get(position);
            splitEntryViewHolder.bind(viewModel);
            splitEntryViewHolder.setListeners();
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

        private final ItemSplitEntryBinding binding;

        Money quantity;

        CalculatorEditText splitAmountEditText;
        ImageView removeSplitButton;
        Spinner accountsSpinner;
        TextView splitCurrencyTextView;
        TransactionTypeSwitch splitTypeSwitch;

        ImageButton copyImbalanceButton;
        ImageButton copyAboveButton;
        ImageButton copyBelowButton;

        public SplitEntryViewHolder(ItemSplitEntryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(SplitEntryViewModel bindedViewModel) {
            bindedViewModel.setViewHolder(this);
            binding.setSplitEntryViewModel(bindedViewModel);
            binding.executePendingBindings();

            splitAmountEditText = binding.inputSplitAmount;
            removeSplitButton = binding.btnRemoveSplit;
            accountsSpinner = binding.inputAccountsSpinner;
            splitCurrencyTextView = binding.splitCurrencySymbol;
            splitTypeSwitch = binding.btnSplitType;
            copyImbalanceButton = binding.copyImbalanceButton;
            copyAboveButton = binding.copyAboveButton;
            copyBelowButton = binding.copyBelowButton;
        }

        public SplitEntryViewModel getBindedViewModel() {
            return binding.getSplitEntryViewModel();
        }

        public void setListeners() {
            SplitEntryViewModel bindedViewModel = getBindedViewModel();

            Log.d(LOG_TAG, "SplitEntryViewHolder.setListeners: this = " + this + ", bindedViewModel = " + bindedViewModel);

            splitAmountEditText.bindListeners(mCalculatorKeyboard);
            splitAmountEditText.addTextChangedListener(mImbalanceWatcher);

            removeSplitButton.setOnClickListener((View _view) -> {
                int clickedPosition = SplitEntryViewHolder.this.getAbsoluteAdapterPosition();
                if (clickedPosition < mSplitEntryViewModelList.size()) {
                    mSplitEntryViewModelList.remove(bindedViewModel);
                    mRecyclerViewAdaptor.notifyItemRemoved(clickedPosition);
                    mImbalanceWatcher.afterTextChanged(null);
                }
            });

            splitTypeSwitch.setAmountFormattingListener(splitAmountEditText, splitCurrencyTextView);
            splitTypeSwitch.addOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
                bindedViewModel.setSplitType(isChecked);
                mImbalanceWatcher.afterTextChanged(null);
            });

            // Updates the list of possible transfer accounts. Only accounts with the same currency can be transferred to
            accountsSpinner.setAdapter(mCursorAdapter);
            accountsSpinner.setOnItemSelectedListener(new SplitAccountListener(splitTypeSwitch, this));

            copyImbalanceButton.setOnClickListener((View _view) -> {
                // First, set current value to zero (if it's not) and recalculate the imbalance.
                splitAmountEditText.setValue(BigDecimal.ZERO);
                mImbalanceWatcher.afterTextChanged(null);

                // Copy the imbalance.
                if (mImbalance.signum() > 0) {
                    splitTypeSwitch.setChecked(TransactionType.DEBIT);
                } else {
                    splitTypeSwitch.setChecked(TransactionType.CREDIT);
                }
                splitAmountEditText.setValue(mImbalance.abs());
                mImbalanceWatcher.afterTextChanged(null);
            });

            copyAboveButton.setOnClickListener((View _view) -> {
                int clickedPosition = SplitEntryViewHolder.this.getAbsoluteAdapterPosition();
                if (clickedPosition <= 0) {
                    // Do nothing if this is the first split.
                    return;
                }
                SplitEntryViewModel aboveViewModel = mSplitEntryViewModelList.get(clickedPosition - 1);
                bindedViewModel.setInputSplitAmount(aboveViewModel.getInputSplitAmount());
                mImbalanceWatcher.afterTextChanged(null);
            });

            copyBelowButton.setOnClickListener((View _view) -> {
                int clickedPosition = SplitEntryViewHolder.this.getAbsoluteAdapterPosition();
                if (clickedPosition >= (mSplitEntryViewModelList.size() - 1)) {
                    // Do nothing if this is the last split.
                    return;
                }
                SplitEntryViewModel belowViewModel = mSplitEntryViewModelList.get(clickedPosition + 1);
                bindedViewModel.setInputSplitAmount(belowViewModel.getInputSplitAmount());
                mImbalanceWatcher.afterTextChanged(null);
            });

            bindedViewModel.setSplitCurrencySymbol(mCommodity.getSymbol());
            bindedViewModel.setSplitUid(BaseModel.generateUID());
            Split split = bindedViewModel.getSplit();
            if (split != null) {
                bindedViewModel.setSplitCurrencySymbol(split.getValue().getCommodity().getSymbol());
                bindedViewModel.setInputSplitAmount(split.getValue().toPlainString());
//                splitAmountEditText.setValue(split.getFormattedValue().asBigDecimal());

                String splitAccountUID = split.getAccountUID();
                assert splitAccountUID != null;
                splitTypeSwitch.setAccountType(mAccountsDbAdapter.getAccountType(splitAccountUID));
                splitTypeSwitch.setChecked(split.getType());
                int accountPos = getSelectedTransferAccountPos(mAccountsDbAdapter.getID(splitAccountUID), mCursorAdapter);
                bindedViewModel.setInputAccountPos(accountPos);
                bindedViewModel.setInputSplitMemo(split.getMemo());
                bindedViewModel.setSplitUid(split.getUID());
            } else {
                binding.getRoot().requestFocus();
            }
        }

        @Override
        public void transferComplete(Money amount) {
            quantity = amount;
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
     * Get the position of the selected transfer account in account list.
     *
     * @param accountId Database ID of the transfer account
     * @return the postion.
     */
    private int getSelectedTransferAccountPos(long accountId, final SimpleCursorAdapter cursorAdapter) {
        for (int pos = 0; pos < cursorAdapter.getCount(); pos++) {
            if (cursorAdapter.getItemId(pos) == accountId) {
                return pos;
            }
        }
        return 0;
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
        for (SplitEntryViewModel splitEntryViewModel : mSplitEntryViewModelList) {
            SplitEntryViewHolder viewHolder = (SplitEntryViewHolder) splitEntryViewModel.getViewHolder();
            if (viewHolder.splitAmountEditText.getValue() == null)
                continue;

            BigDecimal amountBigDecimal = viewHolder.splitAmountEditText.getValue();

            String currencyCode = mAccountsDbAdapter.getCurrencyCode(mAccountUID);
            Money valueAmount = new Money(amountBigDecimal.abs(), Commodity.getInstance(currencyCode));

            String accountUID = mAccountsDbAdapter.getUID(viewHolder.accountsSpinner.getSelectedItemId());
            Split split = new Split(valueAmount, accountUID);
            split.setMemo(viewHolder.getBindedViewModel().getInputSplitMemo());
            split.setType(viewHolder.splitTypeSwitch.getTransactionType());
            split.setUID(viewHolder.getBindedViewModel().getSplitUid().trim());
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
        public void afterTextChanged(Editable _editable) {
            BigDecimal imbalance = BigDecimal.ZERO;

            for (SplitEntryViewModel viewModel : mSplitEntryViewModelList) {
                SplitEntryViewHolder viewHolder = (SplitEntryViewHolder) viewModel.getViewHolder();
                if (viewHolder == null) {
                    Split split = viewModel.getSplit();
                    if (split == null) {
                        continue;
                    }
                    boolean hasDebitNormalBalance = AccountsDbAdapter.getInstance()
                            .getAccountType(split.getAccountUID()).hasDebitNormalBalance();
                    BigDecimal amount = split.getValue().abs().asBigDecimal();
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

            mImbalance = imbalance;
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
