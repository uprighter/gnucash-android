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

import static org.gnucash.android.app.IntentExtKt.takePersistableUriPermission;
import static org.gnucash.android.util.ContentExtKt.getDocumentName;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;

import com.google.android.material.snackbar.Snackbar;

import org.gnucash.android.BuildConfig;
import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.export.DropboxHelper;
import org.gnucash.android.export.ExportFormat;
import org.gnucash.android.export.Exporter;
import org.gnucash.android.importer.ImportAsyncTask;
import org.gnucash.android.ui.settings.dialog.OwnCloudDialogFragment;
import org.gnucash.android.util.BackupManager;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.File;

import timber.log.Timber;

/**
 * Fragment for displaying general preferences
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class BackupPreferenceFragment extends GnuPreferenceFragment {

    /**
     * Collects references to the UI elements and binds click listeners
     */
    private static final int REQUEST_LINK_TO_DBX = 0x11;

    /**
     * Request code for the backup file where to save backups
     */
    private static final int REQUEST_BACKUP_FILE = 0x13;

    @Override
    protected int getTitleId() {
        return R.string.title_backup_prefs;
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        addPreferencesFromResource(R.xml.fragment_backup_preferences);

        final Context context = requireContext();

        if (BuildConfig.DEBUG) {
            SwitchPreference delete_transaction_backup = findPreference(getString(R.string.key_delete_transaction_backup));
            delete_transaction_backup.setChecked(false);

            SwitchPreference import_book_backup = findPreference(getString(R.string.key_import_book_backup));
            import_book_backup.setChecked(false);
        }

        //if we are returning from DropBox authentication, save the key which was generated

        String keyDefaultEmail = getString(R.string.key_default_export_email);
        Preference preference = findPreference(keyDefaultEmail);
        if (preference.getSummaryProvider() == null) {
            preference.setSummaryProvider(p -> {
                EditTextPreference textPreference = (EditTextPreference) p;
                String email = textPreference.getText();
                if (TextUtils.isEmpty(email) || email.trim().isEmpty()) {
                    return getString(R.string.summary_default_export_email);
                }
                return email;
            });
        }

        String keyDefaultExportFormat = getString(R.string.key_default_export_format);
        preference = findPreference(keyDefaultExportFormat);
        if (preference.getSummaryProvider() == null) {
            preference.setSummaryProvider(p -> {
                ListPreference listPreference = (ListPreference) p;
                String value = listPreference.getValue();
                if (TextUtils.isEmpty(value)) {
                    return getString(R.string.summary_default_export_format);
                }
                ExportFormat format = ExportFormat.of(value);
                return getString(format.labelId);
            });
        }

        preference = findPreference(getString(R.string.key_restore_backup));
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(@NonNull Preference preference) {
                restoreBackup();
                return true;
            }
        });

        preference = findPreference(getString(R.string.key_create_backup));
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(@NonNull Preference preference) {
                final Fragment fragment = BackupPreferenceFragment.this;
                final Activity activity = requireActivity();
                BackupManager.backupActiveBookAsync(activity, result -> {
                    int msg = result ? R.string.toast_backup_successful : R.string.toast_backup_failed;
                    if (fragment.isVisible()) {
                        View view = fragment.getView();
                        Snackbar.make(view, msg, Snackbar.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
                    }
                    return null;
                });
                return true;
            }
        });

        preference = findPreference(getString(R.string.key_backup_location));
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(@NonNull Preference preference) {
                String bookName = BooksDbAdapter.getInstance().getActiveBookDisplayName();
                String fileName = Exporter.sanitizeFilename(bookName) + "_" + getString(R.string.label_backup_filename);

                Intent createIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .setType(BackupManager.MIME_TYPE)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_TITLE, fileName);
                try {
                    startActivityForResult(createIntent, REQUEST_BACKUP_FILE);
                } catch (ActivityNotFoundException e) {
                    Timber.e(e, "Cannot create document for backup");
                    if (isVisible()) {
                        View view = getView();
                        assert view != null;
                        Snackbar.make(view, R.string.toast_install_file_manager, Snackbar.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(requireContext(), R.string.toast_install_file_manager, Toast.LENGTH_LONG).show();
                    }
                }
                return true;
            }
        });
        Uri defaultBackupLocation = BackupManager.getBookBackupFileUri(context, GnuCashApplication.getActiveBookUID());
        if (defaultBackupLocation != null) {
            preference.setSummary(getDocumentName(defaultBackupLocation, context));
        }

        preference = findPreference(getString(R.string.key_dropbox_sync));
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(@NonNull Preference preference) {
                toggleDropboxSync(preference);
                toggleDropboxPreference(preference);
                return false;
            }
        });
        toggleDropboxPreference(preference);

        preference = findPreference(getString(R.string.key_owncloud_sync));
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(@NonNull Preference preference) {
                toggleOwnCloudSync(preference);
                toggleOwnCloudPreference(preference);
                return false;
            }
        });
        toggleOwnCloudPreference(preference);
    }

    /**
     * Toggles the checkbox of the DropBox Sync preference if a DropBox account is linked
     *
     * @param preference DropBox Sync preference
     */
    public void toggleDropboxPreference(Preference preference) {
        Context context = preference.getContext();
        ((TwoStatePreference) preference).setChecked(DropboxHelper.hasToken(context));
    }

    /**
     * Toggles the checkbox of the ownCloud Sync preference if an ownCloud account is linked
     *
     * @param preference ownCloud Sync preference
     */
    public void toggleOwnCloudPreference(Preference preference) {
        Context context = preference.getContext();
        final OwnCloudPreferences preferences = new OwnCloudPreferences(context);
        ((TwoStatePreference) preference).setChecked(preferences.isSync());
    }

    /**
     * Toggles the authorization state of a DropBox account.
     * If a link exists, it is removed else DropBox authorization is started
     */
    private void toggleDropboxSync(Preference preference) {
        Context context = preference.getContext();
        if (!DropboxHelper.hasToken(context)) {
            DropboxHelper.authenticate(context);
        } else {
            DropboxHelper.deleteAccessToken(context);
        }
    }

    /**
     * Toggles synchronization with ownCloud on or off
     */
    private void toggleOwnCloudSync(Preference preference) {
        Context context = preference.getContext();
        final OwnCloudPreferences preferences = new OwnCloudPreferences(context);

        if (preferences.isSync())
            preferences.setSync(false);
        else {
            OwnCloudDialogFragment ocDialog = OwnCloudDialogFragment.newInstance(preference);
            ocDialog.show(getParentFragmentManager(), "owncloud_dialog");
        }
    }

    /**
     * Opens a dialog for a user to select a backup to restore and then restores the backup
     */
    private void restoreBackup() {
        Timber.i("Opening GnuCash XML backups for restore");
        final Activity activity = requireActivity();
        final String bookUID = GnuCashApplication.getActiveBookUID();

        final Uri defaultBackupFile = BackupManager.getBookBackupFileUri(activity, bookUID);
        if (defaultBackupFile != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.title_confirm_restore_backup)
                .setMessage(R.string.msg_confirm_restore_backup_into_new_book)
                .setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setPositiveButton(R.string.btn_restore, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        new ImportAsyncTask(activity).execute(defaultBackupFile);
                    }
                });
            builder.create().show();
            return; //stop here if the default backup file exists
        }

        //If no default location was set, look in the internal SD card location
        if (BackupManager.getBackupList(activity, bookUID).isEmpty()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.title_no_backups_found)
                .setMessage(R.string.msg_no_backups_to_restore_from)
                .setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
            builder.create().show();
            return;
        }

        final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(activity, android.R.layout.select_dialog_singlechoice);
        final DateTimeFormatter dateFormatter = DateTimeFormat.longDateTime();
        for (File backupFile : BackupManager.getBackupList(activity, bookUID)) {
            long time = Exporter.getExportTime(backupFile.getName());
            if (time > 0)
                arrayAdapter.add(dateFormatter.print(time));
            else //if no timestamp was found in the filename, just use the name
                arrayAdapter.add(backupFile.getName());
        }

        AlertDialog.Builder restoreDialogBuilder = new AlertDialog.Builder(activity);
        restoreDialogBuilder.setTitle(R.string.title_select_backup_to_restore);
        restoreDialogBuilder.setNegativeButton(R.string.alert_dialog_cancel,
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
        restoreDialogBuilder.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                File backupFile = BackupManager.getBackupList(activity, bookUID).get(which);
                new ImportAsyncTask(activity).execute(Uri.fromFile(backupFile));
            }
        });

        restoreDialogBuilder.create().show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {

            case REQUEST_LINK_TO_DBX:
                Preference preference = findPreference(getString(R.string.key_dropbox_sync));
                if (preference == null) //if we are in a preference header fragment, this may return null
                    break;
                toggleDropboxPreference(preference);
                break;

            case REQUEST_BACKUP_FILE:
                if (resultCode == Activity.RESULT_OK) {
                    Uri backupFileUri = data.getData();
                    Context context = requireContext();
                    takePersistableUriPermission(context, data);

                    GnuCashApplication.getBookPreferences(context)
                        .edit()
                        .putString(BackupManager.KEY_BACKUP_FILE, backupFileUri.toString())
                        .apply();

                    Preference pref = findPreference(getString(R.string.key_backup_location));
                    pref.setSummary(getDocumentName(backupFileUri, pref.getContext()));
                }
                break;
        }
    }
}
