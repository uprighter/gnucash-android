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

package org.gnucash.android.ui.budget;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.database.Cursor;
import android.inputmethodservice.KeyboardView;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputLayout;
import com.maltaisn.recurpicker.format.RRuleFormatter;
import com.maltaisn.recurpicker.format.RecurrenceFormatter;

import org.gnucash.android.R;
import org.gnucash.android.databinding.FragmentBudgetFormBinding;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BudgetsDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.model.Budget;
import org.gnucash.android.model.BudgetAmount;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.model.Recurrence;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.transaction.TransactionFormFragment;
import org.gnucash.android.ui.util.DateTimePicker;
import org.gnucash.android.ui.util.RecurrenceParser;
import org.gnucash.android.ui.util.widget.CalculatorEditText;
import org.gnucash.android.util.QualifiedAccountNameCursorAdapter;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Objects;

/**
 * Fragment for creating or editing Budgets
 */
public class BudgetFormFragment extends Fragment
        implements DatePickerDialog.OnDateSetListener, DateTimePicker.RRulePickerListener {

    public static final String LOG_TAG = BudgetFormFragment.class.getName();

    EditText mBudgetNameInput;
    EditText mDescriptionInput;
    TextView mRecurrenceInput;
    TextInputLayout mNameTextInputLayout;
    KeyboardView mKeyboardView;
    CalculatorEditText mBudgetAmountInput;
    Spinner mBudgetAccountSpinner;
    Button mAddBudgetAmount;
    TextView mStartDateInput;
    View mBudgetAmountLayout;

    private String mRRule;
    private final RRuleFormatter mRRuleFormatter = new RRuleFormatter();
    private final RecurrenceFormatter mRecurrenceFormatter = new RecurrenceFormatter(DateFormat.getInstance());

    private BudgetsDbAdapter mBudgetsDbAdapter;

    private Budget mBudget;
    private Calendar mStartDate;
    private ArrayList<BudgetAmount> mBudgetAmounts;
    private AccountsDbAdapter mAccountsDbAdapter;
    private QualifiedAccountNameCursorAdapter mAccountsCursorAdapter;


    private final ActivityResultLauncher<Intent> addBudgetLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Log.d(LOG_TAG, "launch intent: result = " + result);
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    assert data != null;
                    ArrayList<BudgetAmount> budgetAmounts = data.getParcelableArrayListExtra(UxArgument.BUDGET_AMOUNT_LIST, BudgetAmount.class);
                    if (budgetAmounts != null) {
                        mBudgetAmounts = budgetAmounts;
                        toggleAmountInputVisibility();
                    }
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        FragmentBudgetFormBinding binding = FragmentBudgetFormBinding.inflate(inflater, container, false);
        mBudgetNameInput = binding.inputBudgetName;
        mDescriptionInput = binding.inputDescription;
        mRecurrenceInput = binding.inputRecurrence;
        mNameTextInputLayout = binding.nameTextInputLayout;
        mKeyboardView = binding.calculatorKeyboard;
        mBudgetAmountInput = binding.budgetAmountLayout.inputBudgetAmount;
        mBudgetAccountSpinner = binding.budgetAmountLayout.inputBudgetAccountSpinner;
        mAddBudgetAmount = binding.btnAddBudgetAmount;
        mStartDateInput = binding.inputStartDate;
        mBudgetAmountLayout = binding.budgetAmountLayout.getRoot();

        binding.budgetAmountLayout.btnRemoveItem.setVisibility(View.GONE);
        mBudgetAmountInput.bindListeners(mKeyboardView);
        mStartDateInput.setText(TransactionFormFragment.DATE_FORMATTER.format(mStartDate.getTime()));
        return binding.getRoot();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBudgetsDbAdapter = BudgetsDbAdapter.getInstance();
        mStartDate = Calendar.getInstance();
        mBudgetAmounts = new ArrayList<>();
        String conditions = "(" + DatabaseSchema.AccountEntry.COLUMN_HIDDEN + " = 0 )";
        mAccountsDbAdapter = AccountsDbAdapter.getInstance();
        Cursor accountCursor = mAccountsDbAdapter.fetchAccountsOrderedByFavoriteAndFullName(conditions, null);
        mAccountsCursorAdapter = new QualifiedAccountNameCursorAdapter(getActivity(), accountCursor);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setHasOptionsMenu(true);

        mBudgetAccountSpinner.setAdapter(mAccountsCursorAdapter);
        String budgetUID = requireArguments().getString(UxArgument.BUDGET_UID);
        if (budgetUID != null) { // if we are editing the budget
            initViews(mBudget = mBudgetsDbAdapter.getRecord(budgetUID));
        }
        ActionBar actionbar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        assert actionbar != null;
        if (mBudget == null) {
            actionbar.setTitle("Create Budget");
        } else {
            actionbar.setTitle("Edit Budget");
        }

        mRecurrenceInput.setOnClickListener(v -> {
            Log.d(LOG_TAG, "mRecurrenceTextView.setOnClickListener.");
            new DateTimePicker.RRulePickerFragment(this, mRRule).show(getChildFragmentManager());
        });

        mStartDateInput.setOnClickListener((View v) -> {
            long dateMillis = 0;
            try {
                Date date = TransactionFormFragment.DATE_FORMATTER.parse(((TextView) v).getText().toString());
                assert date != null;
                dateMillis = date.getTime();
            } catch (ParseException e) {
                Log.e(LOG_TAG, "Error converting input time to Date object");
            }
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(dateMillis);

            int year = calendar.get(Calendar.YEAR);
            int monthOfYear = calendar.get(Calendar.MONTH);
            int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);

            new DateTimePicker.DatePickerFragment(BudgetFormFragment.this, year, monthOfYear, dayOfMonth)
                    .show(getChildFragmentManager(), "date_picker_dialog_fragment");
        });

        mAddBudgetAmount.setOnClickListener((View v) -> {
            Intent addBudgetIntent = new Intent(getActivity(), FormActivity.class);
            addBudgetIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.BUDGET_AMOUNT_EDITOR.name());
            mBudgetAmounts = extractBudgetAmounts();
            addBudgetIntent.putParcelableArrayListExtra(UxArgument.BUDGET_AMOUNT_LIST, mBudgetAmounts);
            addBudgetLauncher.launch(addBudgetIntent);
        });

    }

    /**
     * Initialize views when editing an existing budget
     *
     * @param budget Budget to use to initialize the views
     */
    private void initViews(Budget budget) {
        mBudgetNameInput.setText(budget.getName());
        mDescriptionInput.setText(budget.getDescription());

        mRRule = Objects.requireNonNull(budget.getRecurrence()).getRrule();
        mRecurrenceInput.setText(budget.getRecurrence().getRrule());

        mBudgetAmounts = (ArrayList<BudgetAmount>) budget.getCompactedBudgetAmounts();
        toggleAmountInputVisibility();
    }

    /**
     * Extracts the budget amounts from the form
     * <p>If the budget amount was input using the simple form, then read the values.<br>
     * Else return the values gotten from the BudgetAmountEditor</p>
     *
     * @return List of budget amounts
     */
    private ArrayList<BudgetAmount> extractBudgetAmounts() {
        BigDecimal value = mBudgetAmountInput.getValue();
        if (value == null)
            return mBudgetAmounts;

        if (mBudgetAmounts.isEmpty()) { //has not been set in budget amounts editor
            ArrayList<BudgetAmount> budgetAmounts = new ArrayList<>();
            Money amount = new Money(value, Commodity.DEFAULT_COMMODITY);
            String accountUID = mAccountsDbAdapter.getUID(mBudgetAccountSpinner.getSelectedItemId());
            BudgetAmount budgetAmount = new BudgetAmount(amount, accountUID);
            budgetAmounts.add(budgetAmount);
            return budgetAmounts;
        } else {
            return mBudgetAmounts;
        }
    }

    /**
     * Checks that this budget can be saved
     * Also sets the appropriate error messages on the relevant views
     * <p>For a budget to be saved, it needs to have a name, an amount and a schedule</p>
     *
     * @return {@code true} if the budget can be saved, {@code false} otherwise
     */
    private boolean canSave() {

        mBudgetAmounts = extractBudgetAmounts();
        String budgetName = mBudgetNameInput.getText().toString();
        boolean canSave = (mRRule != null)
                && !budgetName.isEmpty()
                && !mBudgetAmounts.isEmpty();

        if (!canSave) {
            if (budgetName.isEmpty()) {
                mNameTextInputLayout.setError("A name is required");
                mNameTextInputLayout.setErrorEnabled(true);
            } else {
                mNameTextInputLayout.setErrorEnabled(false);
            }

            if (mBudgetAmounts.isEmpty()) {
                mBudgetAmountInput.setError("Enter an amount for the budget");
                Toast.makeText(getActivity(), "Add budget amounts in order to save the budget",
                        Toast.LENGTH_SHORT).show();
            }

            if (mRRule == null) {
                Toast.makeText(getActivity(), "Set a repeat pattern to create a budget!",
                        Toast.LENGTH_SHORT).show();
            }
        }

        return canSave;
    }

    /**
     * Extracts the information from the form and saves the budget
     */
    private void saveBudget() {
        if (!canSave())
            return;
        String name = mBudgetNameInput.getText().toString().trim();


        if (mBudget == null) {
            mBudget = new Budget(name);
        } else {
            mBudget.setName(name);
        }

        // TODO: 22.10.2015 set the period num of the budget amount
        extractBudgetAmounts();
        mBudget.setBudgetAmounts(mBudgetAmounts);

        mBudget.setDescription(mDescriptionInput.getText().toString().trim());

        Recurrence recurrence = RecurrenceParser.parse(0, mRRule);
        recurrence.setPeriodStart(new Timestamp(mStartDate.getTimeInMillis()));
        mBudget.setRecurrence(recurrence);

        mBudgetsDbAdapter.addRecord(mBudget, DatabaseAdapter.UpdateMethod.insert);
        requireActivity().finish();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.default_save_actions, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_save) {
            saveBudget();
            return true;
        }
        return false;
    }

    @Override
    public void onRecurrenceSet(String rRule) {
        Log.d(LOG_TAG, String.format("onRecurrenceSet(%s).", rRule));
        String repeatString = getString(R.string.label_tap_to_create_schedule);
        if (rRule != null) {
            mRRule = rRule;
            repeatString = mRecurrenceFormatter.format(requireContext(), mRRuleFormatter.parse(mRRule));
        }

        mRecurrenceInput.setText(repeatString);
    }

    @Override
    public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
        Calendar cal = new GregorianCalendar(year, monthOfYear, dayOfMonth);
        mStartDateInput.setText(TransactionFormFragment.DATE_FORMATTER.format(cal.getTime()));
        mStartDate.set(Calendar.YEAR, year);
        mStartDate.set(Calendar.MONTH, monthOfYear);
        mStartDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
    }

    /**
     * Toggles the visibility of the amount input based on {@link #mBudgetAmounts}
     */
    private void toggleAmountInputVisibility() {
        if (mBudgetAmounts.size() > 1) {
            mBudgetAmountLayout.setVisibility(View.GONE);
        } else {
            mBudgetAmountLayout.setVisibility(View.VISIBLE);
            if (!mBudgetAmounts.isEmpty()) {
                BudgetAmount budgetAmount = mBudgetAmounts.get(0);
                mBudgetAmountInput.setValue(Objects.requireNonNull(budgetAmount.getAmount()).asBigDecimal());
                mBudgetAccountSpinner.setSelection(mAccountsCursorAdapter.getPosition(Objects.requireNonNull(budgetAmount.getAccountUID())));
            }
        }
    }
}
