/*
 * Copyright (c) 2012 Ngewi Fet <ngewif@gmail.com>
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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.export.Exporter;
import org.gnucash.android.importer.ImportAsyncTask;
import org.gnucash.android.util.BackupManager;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;


/**
 * Fragment for displaying general preferences
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class BackupPreferenceFragment extends PreferenceFragmentCompat implements
        Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener {

    /**
     * String for tagging log statements
     */
    public static final String LOG_TAG = "BackupPreferenceFragment";

    private final ActivityResultLauncher<Intent> createBackupFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Log.d(LOG_TAG, "launch createBackupFileIntent: result = " + result);
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    if (data == null) {
                        Log.d(LOG_TAG, "data is null!");
                        return;
                    }

                    Uri backupFileUri = data.getData();
                    if (backupFileUri == null) {
                        Log.d(LOG_TAG, "backupFileUri is null!");
                        return;
                    }
                    final int takeFlags;
                    if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) == Intent.FLAG_GRANT_READ_URI_PERMISSION) {
                        takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                    } else if ((data.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == Intent.FLAG_GRANT_WRITE_URI_PERMISSION) {
                        takeFlags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                    } else {
                        takeFlags = 0;
                    }
                    requireActivity().getContentResolver().takePersistableUriPermission(backupFileUri, takeFlags);

                    PreferenceActivity.getActiveBookSharedPreferences()
                            .edit()
                            .putString(BackupManager.KEY_BACKUP_FILE, backupFileUri.toString())
                            .apply();

                    Preference pref = findPreference(getString(R.string.key_backup_location));
                    if (pref != null) {
                        pref.setSummary(backupFileUri.getAuthority());
                    }
                }
            }
    );

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        addPreferencesFromResource(R.xml.fragment_backup_preferences);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        Objects.requireNonNull(actionBar).setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.title_backup_prefs);
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireActivity());

        String keyDefaultEmail = getString(R.string.key_default_export_email);
        Preference pref = findPreference(keyDefaultEmail);
        if (pref != null) {
            String defaultEmail = sharedPrefs.getString(keyDefaultEmail, null);
            if (defaultEmail != null && !defaultEmail.trim().isEmpty()) {
                pref.setSummary(defaultEmail);
            }
            pref.setOnPreferenceChangeListener(this);
        }

        String keyDefaultExportFormat = getString(R.string.key_default_export_format);
        pref = findPreference(keyDefaultExportFormat);
        if (pref != null) {
            String defaultExportFormat = sharedPrefs.getString(keyDefaultExportFormat, null);
            if (defaultExportFormat != null && !defaultExportFormat.trim().isEmpty()) {
                pref.setSummary(defaultExportFormat);
            }
            pref.setOnPreferenceChangeListener(this);
        }

        pref = findPreference(getString(R.string.key_restore_backup));
        if (pref != null) {
            pref.setOnPreferenceClickListener(this);
        }

        pref = findPreference(getString(R.string.key_create_backup));
        if (pref != null) {
            pref.setOnPreferenceClickListener(this);
        }

        pref = findPreference(getString(R.string.key_backup_location));
        if (pref != null) {
            pref.setOnPreferenceClickListener(this);
            String defaultBackupLocation = BackupManager.getBookBackupFileUri(BooksDbAdapter.getInstance().getActiveBookUID());
            if (defaultBackupLocation != null) {
                pref.setSummary(Uri.parse(defaultBackupLocation).getAuthority());
            }
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();

        if (key.equals(getString(R.string.key_restore_backup))) {
            restoreBackup();
        }

        if (key.equals(getString(R.string.key_backup_location))) {
            Intent createBackupFileIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            createBackupFileIntent.setType("*/*");
            createBackupFileIntent.addCategory(Intent.CATEGORY_OPENABLE);
            String bookName = BooksDbAdapter.getInstance().getActiveBookDisplayName();
            createBackupFileIntent.putExtra(Intent.EXTRA_TITLE, Exporter.sanitizeFilename(bookName) + "_" + getString(R.string.label_backup_filename));
            createBackupFileLauncher.launch(createBackupFileIntent);
        }

        if (key.equals(getString(R.string.key_create_backup))) {
            boolean result = BackupManager.backupActiveBook();
            int msg = result ? R.string.toast_backup_successful : R.string.toast_backup_failed;
            Snackbar.make(requireView(), msg, Snackbar.LENGTH_SHORT).show();
        }

        return false;
    }

    /**
     * Listens for changes to the preference and sets the preference summary to the new value
     *
     * @param preference Preference which has been changed
     * @param newValue   New value for the changed preference
     * @return <code>true</code> if handled, <code>false</code> otherwise
     */
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        preference.setSummary(newValue.toString());
        if (preference.getKey().equals(getString(R.string.key_default_currency))) {
            GnuCashApplication.setDefaultCurrencyCode(newValue.toString());
        }

        if (preference.getKey().equals(getString(R.string.key_default_export_email))) {
            String emailSetting = newValue.toString();
            if (emailSetting.trim().isEmpty()) {
                preference.setSummary(R.string.summary_default_export_email);
            }
        }

        if (preference.getKey().equals(getString(R.string.key_default_export_format))) {
            String exportFormat = newValue.toString();
            if (exportFormat.trim().isEmpty()) {
                preference.setSummary(R.string.summary_default_export_format);
            }
        }
        return true;
    }

    /**
     * Opens a dialog for a user to select a backup to restore and then restores the backup
     */
    private void restoreBackup() {
        Log.i("Settings", "Opening GnuCash XML backups for restore");
        final String bookUID = BooksDbAdapter.getInstance().getActiveBookUID();

        final String defaultBackupFile = BackupManager.getBookBackupFileUri(bookUID);
        if (defaultBackupFile != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.title_confirm_restore_backup)
                    .setMessage(R.string.msg_confirm_restore_backup_into_new_book)
                    .setNegativeButton(R.string.btn_cancel, (dialog, which) -> dialog.dismiss())
                    .setPositiveButton(R.string.btn_restore, (dialogInterface, i) -> new ImportAsyncTask(getActivity()).execute(Uri.parse(defaultBackupFile)));
            builder.create().show();
            return; //stop here if the default backup file exists
        }

        //If no default location was set, look in the internal SD card location
        if (BackupManager.getBackupList(bookUID).isEmpty()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.title_no_backups_found)
                    .setMessage(R.string.msg_no_backups_to_restore_from)
                    .setNegativeButton(R.string.label_dismiss, (dialog, which) -> dialog.dismiss());
            builder.create().show();
            return;
        }


        final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(requireActivity(), android.R.layout.select_dialog_singlechoice);
        final DateFormat dateFormatter = SimpleDateFormat.getDateTimeInstance();
        for (File backupFile : BackupManager.getBackupList(bookUID)) {
            long time = Exporter.getExportTime(backupFile.getName());
            if (time > 0)
                arrayAdapter.add(dateFormatter.format(new Date(time)));
            else //if no timestamp was found in the filename, just use the name
                arrayAdapter.add(backupFile.getName());
        }

        AlertDialog.Builder restoreDialogBuilder = new AlertDialog.Builder(requireActivity());
        restoreDialogBuilder.setTitle(R.string.title_select_backup_to_restore);
        restoreDialogBuilder.setNegativeButton(R.string.alert_dialog_cancel,
                (dialog, which) -> dialog.dismiss());
        restoreDialogBuilder.setAdapter(arrayAdapter, (dialog, which) -> {
            File backupFile = BackupManager.getBackupList(bookUID).get(which);
            new ImportAsyncTask(getActivity()).execute(Uri.fromFile(backupFile));
        });

        restoreDialogBuilder.create().show();
    }
}
