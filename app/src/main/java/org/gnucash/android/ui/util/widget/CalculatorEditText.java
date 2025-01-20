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
package org.gnucash.android.ui.util.widget;

import static org.gnucash.android.app.ContextExtKt.getActivity;
import static org.gnucash.android.ui.util.widget.ViewExtKt.addFilter;
import static org.gnucash.android.ui.util.widget.ViewExtKt.setTextToEnd;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;

import org.gnucash.android.R;
import org.gnucash.android.databinding.KbdCalculatorBinding;
import org.gnucash.android.inputmethodservice.CalculatorKeyboardView;
import org.gnucash.android.model.Commodity;
import org.gnucash.android.model.Money;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.util.AmountParser;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

import timber.log.Timber;

/**
 * A custom EditText which supports computations and uses a custom calculator keyboard.
 * <p>After the view is inflated, make sure to call {@link #bindKeyboard(CalculatorKeyboardView)}
 * with the view from your layout where the calculator keyboard should be displayed.</p>
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class CalculatorEditText extends AppCompatEditText {

    @Nullable
    private CalculatorKeyboard mCalculatorKeyboard;

    private Commodity mCommodity = Commodity.DEFAULT_COMMODITY;

    /**
     * Flag which is set if the contents of this view have been modified
     */
    private boolean isContentModified = false;
    private String originalText = "";

    private final DecimalFormat formatter = (DecimalFormat) NumberFormat.getInstance(Locale.getDefault());

    public CalculatorEditText(Context context) {
        super(context, null);
        init();
    }

    public CalculatorEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CalculatorEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * Initialize.
     */
    private void init() {
        setBackground(null);
        setSingleLine(true);

        // Disable spell check (hex strings look like words to Android)
        setInputType(InputType.TYPE_NULL);
        setRawInputType(InputType.TYPE_CLASS_NUMBER);

        addFilter(this, CalculatorKeyboard.getFilter());

        // Disable system keyboard appearing on long-press, but for some reason, this prevents the text selection from working.
        setShowSoftInputOnFocus(false);

        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                isContentModified = !TextUtils.equals(originalText, s);
            }
        });

        setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if ((actionId & EditorInfo.IME_MASK_ACTION) > EditorInfo.IME_ACTION_NONE) {
                    evaluate();
                    return true;
                }
                return false;
            }
        });

        setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (v != CalculatorEditText.this) return;
                CalculatorKeyboard calculatorKeyboard = mCalculatorKeyboard;
                if (hasFocus) {
                    setSelection(getText().length());
                    if (calculatorKeyboard != null) {
                        calculatorKeyboard.showCustomKeyboard(v);
                    }
                } else {
                    if (calculatorKeyboard != null) {
                        calculatorKeyboard.hideCustomKeyboard();
                    }
                    evaluate();
                }
            }
        });

        setOnClickListener(new OnClickListener() {
            // NOTE By setting the on click listener we can show the custom keyboard again,
            // by tapping on an edit box that already had focus (but that had the keyboard hidden).
            @Override
            public void onClick(View v) {
                CalculatorKeyboard calculatorKeyboard = mCalculatorKeyboard;
                if (calculatorKeyboard != null) {
                    calculatorKeyboard.showCustomKeyboard(v);
                }
            }
        });
    }

    private void bindKeyboard(@Nullable final CalculatorKeyboard calculatorKeyboard) {
        mCalculatorKeyboard = calculatorKeyboard;

        // Although this handler doesn't make sense, if removed, the standard keyboard
        // shows up in addition to the calculator one when the EditText gets a touch event.
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // XXX: Use dispatchTouchEvent()?
                onTouchEvent(event);
                return false;
            }
        });

        Activity activity = getActivity(this);
        if (activity instanceof FormActivity) {
            ((FormActivity) activity).setOnBackListener(calculatorKeyboard);
        }
    }

    /**
     * Initializes listeners on the EditText
     *
     * @param keyboardView the calculator keyboard view.
     */
    public void bindKeyboard(@NonNull CalculatorKeyboardView keyboardView) {
        bindKeyboard(new CalculatorKeyboard(keyboardView));
    }

    /**
     * Initializes listeners on the EditText
     *
     * @param keyboardBinding the calculator keyboard binding.
     */
    public void bindKeyboard(@NonNull KbdCalculatorBinding keyboardBinding) {
        bindKeyboard(keyboardBinding.calculatorKeyboard);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (CalculatorKeyboard.onKeyDown(keyCode, event)) {
            evaluate();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Returns the currency used for computations
     *
     * @return ISO 4217 currency
     */
    public Commodity getCommodity() {
        return mCommodity;
    }

    /**
     * Sets the commodity to use for calculations
     * The commodity determines the number of decimal places used
     *
     * @param commodity ISO 4217 currency
     */
    public void setCommodity(Commodity commodity) {
        this.mCommodity = commodity;
    }

    /**
     * Evaluates the arithmetic expression in the EditText and sets the text property
     *
     * @return Result of arithmetic evaluation which is same as text displayed in EditText
     */
    public String evaluate() {
        String amountString = getCleanString();
        if (TextUtils.isEmpty(amountString)) {
            return "";
        }

        BigDecimal amount = AmountParser.evaluate(amountString);
        if (amount != null) {
            try {
                Money money = new Money(amount, getCommodity());
                // Currently the numerator has a limit of 64 bits.
                money.getNumerator();
            } catch (ArithmeticException e) {
                setError(getContext().getString(R.string.label_error_invalid_expression));
                Timber.w(e, "Invalid amount: %s", amountString);
                return "";
            }
            setValue(amount);
        } else {
            setError(getContext().getString(R.string.label_error_invalid_expression));
            Timber.w("Invalid amount: %s", amountString);
        }
        return getText().toString();
    }

    /**
     * Evaluates the expression in the text and returns true if the result is valid
     *
     * @return @{code true} if the input is valid, {@code false} otherwise
     */
    public boolean isInputValid() {
        String text = evaluate();
        return !text.isEmpty() && getError() == null;
    }

    /**
     * Returns the amount string formatted as a decimal in Locale.US and trimmed.
     * This also converts decimal operators from other locales into a period (.)
     *
     * @return String with the amount in the EditText or empty string if there is no input
     */
    @NonNull
    public String getCleanString() {
        return getText().toString().replaceAll(",", ".").trim();
    }

    /**
     * Returns true if the content of this view has been modified
     *
     * @return {@code true} if content has changed, {@code false} otherwise
     */
    public boolean isInputModified() {
        return this.isContentModified;
    }

    /**
     * Returns the value of the amount in the edit text or null if the field is empty.
     * Performs an evaluation of the expression first
     *
     * @return BigDecimal value
     */
    public @Nullable BigDecimal getValue() {
        String text = evaluate();
        if (text.isEmpty()) {
            return null;
        }
        try { //catch any exceptions in the conversion e.g. if a string with only "-" is entered
            return AmountParser.parse(text);
        } catch (ParseException e) {
            String msg = "Error parsing amount string \"" + text + "\" from CalculatorEditText";
            Timber.i(e, msg);
            return null;
        }
    }

    /**
     * Set the text to the value of {@code amount} formatted according to the locale.
     * <p>The number of decimal places are determined by the currency set to the view, and the
     * decimal separator is determined by the device locale. There are no thousandths separators.</p>
     *
     * @param amount BigDecimal amount
     */
    public void setValue(@Nullable BigDecimal amount) {
        setValue(amount, false);
    }

    public void setValue(@Nullable BigDecimal amount, boolean isOriginal) {
        formatter.setMinimumFractionDigits(0);
        formatter.setMaximumFractionDigits(mCommodity.getSmallestFractionDigits());
        formatter.setGroupingUsed(false);
        String resultString = (amount != null) ? formatter.format(amount) : "";

        if (isOriginal) {
            originalText = resultString;
        }

        setTextToEnd(this, resultString);
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && isFocused()) {
            CalculatorKeyboard keyboard = mCalculatorKeyboard;
            if (keyboard != null) {
                keyboard.showCustomKeyboard(this);
            }
        }
    }
}
