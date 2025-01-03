/*
 * Copyright (c) 2014 Ngewi Fet <ngewif@gmail.com>
 * Copyright (c) 2014 Yongxin Wang <fefe.wyx@gmail.com>
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

package org.gnucash.android.ui.util;

import static org.gnucash.android.ui.util.TextViewExtKt.displayBalance;

import android.os.AsyncTask;
import android.widget.TextView;

import androidx.annotation.ColorInt;

import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.model.Money;

import java.lang.ref.WeakReference;

import timber.log.Timber;

/**
 * An asynchronous task for computing the account balance of an account.
 * This is done asynchronously because in cases of deeply nested accounts,
 * it can take some time and would block the UI thread otherwise.
 */
public class AccountBalanceTask extends AsyncTask<String, Void, Money> {

    private final WeakReference<TextView> accountBalanceTextViewReference;
    private final AccountsDbAdapter accountsDbAdapter;
    @ColorInt
    private final int colorBalanceZero;

    public AccountBalanceTask(TextView balanceTextView, @ColorInt int colorZero) {
        accountBalanceTextViewReference = new WeakReference<>(balanceTextView);
        accountsDbAdapter = AccountsDbAdapter.getInstance();
        colorBalanceZero = colorZero;
    }

    @Override
    protected Money doInBackground(String... params) {
        String accountUID = params[0];
        //if the view for which we are doing this job is dead, kill the job as well
        if (accountBalanceTextViewReference.get() == null) {
            cancel(true);
            return Money.getZeroInstance();
        }

        Money balance = Money.getZeroInstance();
        try {
            balance = accountsDbAdapter.getAccountBalance(accountUID, -1, -1);
        } catch (Exception ex) {
            Timber.e(ex, "Error computing account balance");
        }
        return balance;
    }

    @Override
    protected void onPostExecute(Money balance) {
        if (accountBalanceTextViewReference.get() != null && balance != null) {
            final TextView balanceTextView = accountBalanceTextViewReference.get();
            if (balanceTextView != null) {
                displayBalance(balanceTextView, balance, colorBalanceZero);
            }
        }
    }
}
