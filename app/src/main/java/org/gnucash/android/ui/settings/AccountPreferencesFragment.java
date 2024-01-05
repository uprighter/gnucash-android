/*
 * Copyright (c) 2013 Ngewi Fet <ngewif@gmail.com>
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

package org.gnucash.android.ui.settings;

import android.app.AlertDialog;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.export.ExportAsyncTask;
import org.gnucash.android.export.ExportFormat;
import org.gnucash.android.export.ExportParams;
import org.gnucash.android.export.Exporter;
import org.gnucash.android.model.Money;
import org.gnucash.android.ui.account.AccountsActivity;
import org.gnucash.android.ui.settings.dialog.DeleteAllAccountsConfirmationDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Account settings fragment inside the Settings activity
 *
 * @author Ngewi Fet <ngewi.fet@gmail.com>
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
public class AccountPreferencesFragment extends PreferenceFragmentCompat implements
        Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    public static final String LOG_TAG = AccountPreferencesFragment.class.getName();

    List<CharSequence> mCurrencyEntries = new ArrayList<>();
    List<CharSequence> mCurrencyEntryValues = new ArrayList<>();

    private final ActivityResultLauncher<String> mImportAccountsFromXmlFile = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                Log.d(LOG_TAG, String.format("GetContent returns %s.", uri));
                AccountsActivity.importXmlFileFromIntent(getActivity(), uri, null);
            });

    private final ActivityResultLauncher<String> mExportToXmlFile = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/text"),
            uri -> {
                Log.d(LOG_TAG, String.format("CreateDocument returns %s.", uri));
                    ExportParams exportParams = new ExportParams(ExportFormat.CSVA);
                    exportParams.setExportTarget(ExportParams.ExportTarget.URI);
                    exportParams.setExportLocation(uri.toString());
                    ExportAsyncTask exportTask = new ExportAsyncTask(getActivity(), GnuCashApplication.getActiveDb(), exportParams);

                    try {
                        exportTask.asyncExecute().get();
                    } catch (InterruptedException | ExecutionException e) {
                        FirebaseCrashlytics.getInstance().recordException(e);
                        Toast.makeText(getActivity(), "An error occurred during the Accounts export",
                                Toast.LENGTH_LONG).show();
                    }
            });

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        addPreferencesFromResource(R.xml.fragment_account_preferences);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        actionBar.setTitle(R.string.title_account_preferences);

        Cursor cursor = CommoditiesDbAdapter.getInstance().fetchAllRecords(DatabaseSchema.CommodityEntry.COLUMN_MNEMONIC + " ASC");
        while (cursor.moveToNext()) {
            String code = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.CommodityEntry.COLUMN_MNEMONIC));
            String name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.CommodityEntry.COLUMN_FULLNAME));
            mCurrencyEntries.add(code + " - " + name);
            mCurrencyEntryValues.add(code);
        }
        cursor.close();
    }

    @Override
    public void onResume() {
        super.onResume();

        String defaultCurrency = GnuCashApplication.getDefaultCurrencyCode();
        Preference pref = findPreference(getString(R.string.key_default_currency));
        String currencyName = CommoditiesDbAdapter.getInstance().getCommodity(defaultCurrency).getFullname();
        pref.setSummary(currencyName);
        pref.setOnPreferenceChangeListener(this);

        CharSequence[] entries = new CharSequence[mCurrencyEntries.size()];
        CharSequence[] entryValues = new CharSequence[mCurrencyEntryValues.size()];
        ((ListPreference) pref).setEntries(mCurrencyEntries.toArray(entries));
        ((ListPreference) pref).setEntryValues(mCurrencyEntryValues.toArray(entryValues));

        Preference preference = findPreference(getString(R.string.key_import_accounts));
        preference.setOnPreferenceClickListener(this);

        preference = findPreference(getString(R.string.key_export_accounts_csv));
        preference.setOnPreferenceClickListener(this);

        preference = findPreference(getString(R.string.key_delete_all_accounts));
        preference.setOnPreferenceClickListener(preference1 -> {
            showDeleteAccountsDialog();
            return true;
        });

        preference = findPreference(getString(R.string.key_create_default_accounts));
        preference.setOnPreferenceClickListener(preference12 -> {
            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.title_create_default_accounts)
                    .setMessage(R.string.msg_confirm_create_default_accounts_setting)
                    .setIcon(R.drawable.ic_warning_black_24dp)
                    .setPositiveButton(R.string.btn_create_accounts, (dialogInterface, i) -> AccountsActivity.createDefaultAccounts(Money.DEFAULT_CURRENCY_CODE, getActivity()))
                    .setNegativeButton(R.string.btn_cancel, (dialogInterface, i) -> dialogInterface.dismiss())
                    .create()
                    .show();

            return true;
        });
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();

        if (key.equals(getString(R.string.key_import_accounts))) {
            mImportAccountsFromXmlFile.launch("*/*");
            return true;
        } else if (key.equals(getString(R.string.key_export_accounts_csv))) {
            String bookName = BooksDbAdapter.getInstance().getActiveBookDisplayName();
            String suggestedFilename = Exporter.buildExportFilename(ExportFormat.CSVA, bookName);
            mExportToXmlFile.launch(suggestedFilename);
            return true;
        }

        return false;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference.getKey().equals(getString(R.string.key_default_currency))) {
            GnuCashApplication.setDefaultCurrencyCode(newValue.toString());
            String fullname = CommoditiesDbAdapter.getInstance().getCommodity(newValue.toString()).getFullname();
            preference.setSummary(fullname);
            return true;
        }
        return false;
    }

    /**
     * Show the dialog for deleting accounts
     */
    public void showDeleteAccountsDialog() {
        DeleteAllAccountsConfirmationDialog deleteConfirmationDialog = DeleteAllAccountsConfirmationDialog.newInstance();
        deleteConfirmationDialog.show(getActivity().getSupportFragmentManager(), "account_settings");
    }
}
