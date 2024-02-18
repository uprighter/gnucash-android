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
import java.util.Objects;

/**
 * Dialog for editing the splits in a transaction
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class SplitEditorFragment extends Fragment {

    public static final String LOG_TAG = SplitEditorFragment.class.getName();

    private FragmentSplitEditorBinding mBinding;

    private KeyboardView mKeyboardView;

    private RecyclerView mRecyclerView;
    private RecyclerViewAdapter mRecyclerViewAdaptor;
    private ArrayList<SplitEntryViewModel> mSplitEntryViewModelList;

    private AccountsDbAdapter mAccountsDbAdapter;
    private SimpleCursorAdapter mCursorAdapter;
    private String mAccountUID;
    private Commodity mCommodity;

    private BigDecimal mBaseAmount = BigDecimal.ZERO;

    private CalculatorKeyboard mCalculatorKeyboard;

    private TextView mImbalanceTextView;
    private BigDecimal mImbalance = BigDecimal.ZERO;
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
        mBinding = FragmentSplitEditorBinding.inflate(inflater, container, false);
        Log.d(LOG_TAG, "onCreateView: binding = " + mBinding + ", savedInstanceState = " + savedInstanceState);

        mKeyboardView = mBinding.calculatorKeyboard;
        mImbalanceTextView = mBinding.imbalanceTextview;
        mRecyclerView = mBinding.splitListRecycler;

        mRecyclerViewAdaptor = new RecyclerViewAdapter();
        mRecyclerView.setAdapter(mRecyclerViewAdaptor);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(requireActivity()));
        mRecyclerView.setItemViewCacheSize(25);  // No need to recycle memory in most cases.

        return mBinding.getRoot();
    }

    @Override
    public void onDestroyView() {
        Log.d(LOG_TAG, "onDestroyView: binding = " + mBinding);
        super.onDestroyView();
        mBinding = null;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        assert actionBar != null;
        actionBar.setTitle(R.string.title_split_editor);
        setHasOptionsMenu(true);

        initAdaptersAndArgs();

        mCalculatorKeyboard = new CalculatorKeyboard(requireActivity(), mKeyboardView, R.xml.calculator_keyboard);

        //we are editing splits for a new transaction.
        // But the user may have already created some splits before. Let's check

        mSplitEntryViewModelList = new ArrayList<>();
        List<Split> splitList = requireArguments().getParcelableArrayList(UxArgument.SPLIT_LIST, Split.class);
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

        mAccountUID = ((FormActivity) requireActivity()).getCurrentAccountUID();
        mCommodity = CommoditiesDbAdapter.getInstance().getCommodity(mAccountsDbAdapter.getCurrencyCode(mAccountUID));
        mBaseAmount = new BigDecimal(requireArguments().getString(UxArgument.AMOUNT_STRING));

        String conditions = "("
                + DatabaseSchema.AccountEntry.COLUMN_HIDDEN + " = 0 AND "
                + DatabaseSchema.AccountEntry.COLUMN_PLACEHOLDER + " = 0"
                + ")";
        Cursor cursor = mAccountsDbAdapter.fetchAccountsOrderedByFullName(conditions, null);
        mCursorAdapter = new QualifiedAccountNameCursorAdapter(requireActivity(), cursor);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mCalculatorKeyboard = new CalculatorKeyboard(requireActivity(), mKeyboardView, R.xml.calculator_keyboard);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.split_editor_actions, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == android.R.id.home) {
            if (getActivity() != null) {
                requireActivity().setResult(Activity.RESULT_CANCELED);
                requireActivity().finish();
            }
            return true;
        } else if (item.getItemId() == R.id.menu_save) {
            saveSplits();
            return true;
        } else if (item.getItemId() == R.id.menu_add_split) {
            addSplitView(null);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void loadSplitViews(List<Split> splitList) {
        Collections.reverse(splitList);
        for (Split split : splitList) {
            addSplitView(split);
        }
    }

    /**
     * Add a split view and initialize it with <code>split</code>
     *
     * @param split Split to initialize the contents to
     */
    private void addSplitView(Split split) {
        SplitEntryViewModel viewModel = new SplitEntryViewModel(
                mAccountsDbAdapter, mCursorAdapter, mCommodity.getSymbol(), split);
        mSplitEntryViewModelList.add(0, viewModel);
        mRecyclerViewAdaptor.notifyItemInserted(0);
        mRecyclerView.scrollToPosition(0);

//        Log.d(LOG_TAG, mSplitEntryViewModelList.size() + " splits, after added " + split);
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
            Log.d(LOG_TAG, "onBindViewHolder at position " + position + " for binding " + splitEntryViewHolder.mViewBinding);
            Log.d(LOG_TAG, "onBindViewHolder viewModel: " + mSplitEntryViewModelList.get(position));

            SplitEntryViewModel viewModel = mSplitEntryViewModelList.get(position);
            splitEntryViewHolder.bind(viewModel);
            splitEntryViewHolder.init();
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

        private final ItemSplitEntryBinding mViewBinding;
        private SplitEntryViewModel mViewModel;

        private Money quantity;

        private CalculatorEditText splitAmountEditText;
        private Spinner accountsSpinner;
        private TransactionTypeSwitch splitTypeSwitch;

        public SplitEntryViewHolder(ItemSplitEntryBinding binding) {
            super(binding.getRoot());
            this.mViewBinding = binding;
        }

        public SplitEntryViewModel getViewModel() {
            return mViewModel;
        }

        public void bind(SplitEntryViewModel viewModel) {
            this.mViewModel = viewModel;
            Log.d(LOG_TAG, "SplitEntryViewHolder.setListeners: this = " + this + ", mViewModel = " + mViewModel);

            ImageButton dragButton = mViewBinding.dragButton;
            splitAmountEditText = mViewBinding.inputSplitAmount;
            ImageView removeSplitButton = mViewBinding.btnRemoveSplit;
            accountsSpinner = mViewBinding.inputAccountsSpinner;
            TextView splitCurrencyTextView = mViewBinding.splitCurrencySymbol;
            splitTypeSwitch = mViewBinding.btnSplitType;
            ImageButton copyImbalanceButton = mViewBinding.copyImbalanceButton;
            ImageButton copyAboveButton = mViewBinding.copyAboveButton;
            ImageButton copyBelowButton = mViewBinding.copyBelowButton;

            mViewBinding.setSplitEntryViewModel(mViewModel);
            mViewModel.setViewHolder(this);
            // Call ViewModel.bind first to assign widgets. This should be part of the constructor,
            // but by that time, these widgets are not inflated yet.
            mViewModel.bind(splitAmountEditText, splitTypeSwitch);

            dragButton.setOnClickListener((View view) -> {
                // Hide the calculator keyboard to drag item up or down more easily.
                mCalculatorKeyboard.hideCustomKeyboard();
            });

            splitAmountEditText.bindListeners(mCalculatorKeyboard);
            splitAmountEditText.addTextChangedListener(mImbalanceWatcher);

            removeSplitButton.setOnClickListener((View view) -> {
                int clickedPosition = SplitEntryViewHolder.this.getAbsoluteAdapterPosition();
                if (clickedPosition < mSplitEntryViewModelList.size()) {
                    mSplitEntryViewModelList.remove(mViewModel);
                    mRecyclerViewAdaptor.notifyItemRemoved(clickedPosition);
                    mImbalanceWatcher.afterTextChanged(null);
                }
            });

            splitTypeSwitch.setAmountFormattingListener(splitAmountEditText, splitCurrencyTextView);
            splitTypeSwitch.addOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
                mViewModel.setSplitTypeChecked(isChecked);
                mImbalanceWatcher.afterTextChanged(null);
            });

            // Updates the list of possible transfer accounts. Only accounts with the same currency can be transferred to
            accountsSpinner.setAdapter(mCursorAdapter);
            accountsSpinner.setOnItemSelectedListener(new SplitAccountListener(splitTypeSwitch, this));

            copyImbalanceButton.setOnClickListener((View _view) -> {
                // First, set current value to zero (if it's not) and recalculate the imbalance.
                mViewModel.setInputSplitAmount(BigDecimal.ZERO);
                mImbalanceWatcher.afterTextChanged(null);

                // Copy the imbalance.
                mViewModel.setSplitType(mImbalance.signum() > 0 ? TransactionType.DEBIT : TransactionType.CREDIT);
                mViewModel.setInputSplitAmount(mImbalance.abs());

                splitAmountEditText.requestFocus();
                mImbalanceWatcher.afterTextChanged(null);
            });

            copyAboveButton.setOnClickListener((View _view) -> {
                int clickedPosition = SplitEntryViewHolder.this.getAbsoluteAdapterPosition();
                if (clickedPosition <= 0) {
                    // Do nothing if this is the first split.
                    return;
                }
                SplitEntryViewModel aboveViewModel = mSplitEntryViewModelList.get(clickedPosition - 1);
                mViewModel.setInputSplitAmount(aboveViewModel.getInputSplitAmount());

                splitAmountEditText.requestFocus();
                mImbalanceWatcher.afterTextChanged(null);
            });

            copyBelowButton.setOnClickListener((View _view) -> {
                int clickedPosition = SplitEntryViewHolder.this.getAbsoluteAdapterPosition();
                if (clickedPosition >= (mSplitEntryViewModelList.size() - 1)) {
                    // Do nothing if this is the last split.
                    return;
                }
                SplitEntryViewModel belowViewModel = mSplitEntryViewModelList.get(clickedPosition + 1);
                mViewModel.setInputSplitAmount(belowViewModel.getInputSplitAmount());

                splitAmountEditText.requestFocus();
                mImbalanceWatcher.afterTextChanged(null);
            });
        }

        public void init() {
            // executePendingBindings first, so that any changes in ViewModel could trigger event listeners.
            mViewBinding.executePendingBindings();
            mViewModel.init();
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
            if (amountString.isEmpty()) {
                return BigDecimal.ZERO;
            }

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
     * Check if all the split amounts have valid values that can be saved
     *
     * @return {@code true} if splits can be saved, {@code false} otherwise
     */
    private boolean canSave() {
        for (SplitEntryViewModel viewModel : mSplitEntryViewModelList) {
            SplitEntryViewHolder viewHolder = (SplitEntryViewHolder) viewModel.getViewHolder();
            if (viewHolder != null && viewHolder.splitAmountEditText.getValue() == null) {
                Log.d(LOG_TAG, String.format("canSave returns false, splitAmountEditText has invalid value: %s", viewHolder.splitAmountEditText.getText()));
                // split amount input is invalid.
                return false;
            }
            //TODO: also check that multi-currency splits have a conversion amount present
        }
        if (mImbalance.equals(BigDecimal.ZERO)) {
            Log.d(LOG_TAG, String.format("canSave returns false, mImbalance=%s", mImbalance));
            return false;
        }
        return true;
    }

    /**
     * Save all the splits from the split editor
     */
    private void saveSplits() {
        if (!canSave()) {
            Toast.makeText(requireActivity(), R.string.toast_error_check_split_amounts,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Intent data = new Intent();
        data.putParcelableArrayListExtra(UxArgument.SPLIT_LIST, extractSplitsFromView());
        requireActivity().setResult(Activity.RESULT_OK, data);

        requireActivity().finish();
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
            if (viewHolder == null || viewHolder.splitAmountEditText.getValue() == null) {
                Log.d(LOG_TAG, "splitEntryViewModel has no viewHolder: " + splitEntryViewModel);
                continue;
            }

            BigDecimal amountBigDecimal = viewHolder.splitAmountEditText.getValue();

            String currencyCode = mAccountsDbAdapter.getCurrencyCode(mAccountUID);
            Money valueAmount = new Money(amountBigDecimal.abs(), Commodity.getInstance(currencyCode));

            String accountUID = mAccountsDbAdapter.getUID(viewHolder.accountsSpinner.getSelectedItemId());
            Split split = new Split(valueAmount, accountUID);
            split.setMemo(splitEntryViewModel.getInputSplitMemo());
            split.setUID(splitEntryViewModel.getSplitUid().trim());
            split.setType(viewHolder.splitTypeSwitch.getTransactionType());
            if (viewHolder.quantity != null) {
                split.setQuantity(viewHolder.quantity.abs());
            }
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
                        Log.d(LOG_TAG, "viewModel has no Split: " + viewModel);
                        continue;
                    }
                    BigDecimal amount = Objects.requireNonNull(split.getValue()).abs().asBigDecimal();
                    if (split.getType() == TransactionType.CREDIT) {
                        imbalance = imbalance.add(amount);
                    } else {
                        imbalance = imbalance.subtract(amount);
                    }
                } else {
                    try {
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
                    } catch (ArithmeticException e) {
                        Log.d(LOG_TAG, String.format("possible transient expression error, ignore: %s.", e.getMessage()));
                        return;
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
        SplitEntryViewModel mViewModel;

        /**
         * Flag to know when account spinner callback is due to user interaction or layout of components
         */
        boolean userInteraction = false;

        public SplitAccountListener(TransactionTypeSwitch typeToggleButton, SplitEntryViewHolder viewHolder) {
            this.mTypeToggleButton = typeToggleButton;
            this.mSplitEntryViewHolder = viewHolder;
            this.mViewModel = viewHolder.getViewModel();
        }

        @Override
        public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
            AccountType accountType = mAccountsDbAdapter.getAccountType(id);
            mTypeToggleButton.setAccountType(accountType);
            mViewModel.setSplitTypeChecked(mTypeToggleButton.isChecked());

            String fromCurrencyCode = mAccountsDbAdapter.getCurrencyCode(mAccountUID);
            String targetCurrencyCode = mAccountsDbAdapter.getCurrencyCode(mAccountsDbAdapter.getUID(id));
            mViewModel.setInputAccountPos(position);

            //refresh the imbalance amount if we change the account
            mImbalanceWatcher.afterTextChanged(null);

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
            fragment.show(requireActivity().getSupportFragmentManager(), "transfer_funds_editor");
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {
            //nothing to see here, move along
        }
    }
}
