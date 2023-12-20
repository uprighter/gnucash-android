/*
 * Copyright (c) 2013 - 2015 Ngewi Fet <ngewif@gmail.com>
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

package org.gnucash.android.export;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.DatabaseAdapter;
import org.gnucash.android.db.adapter.SplitsDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.export.csv.CsvAccountExporter;
import org.gnucash.android.export.csv.CsvTransactionsExporter;
import org.gnucash.android.export.ofx.OfxExporter;
import org.gnucash.android.export.qif.QifExporter;
import org.gnucash.android.export.xml.GncXmlExporter;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.ui.account.AccountsActivity;
import org.gnucash.android.ui.account.AccountsListFragment;
import org.gnucash.android.ui.transaction.TransactionsActivity;
import org.gnucash.android.util.BackupManager;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Asynchronous task for exporting transactions.
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class ExportAsyncTask extends AsyncTask<ExportParams, Void, Boolean> {

    /**
     * App context
     */
    private final Context mContext;

    private ProgressDialog mProgressDialog;

    private final SQLiteDatabase mDb;

    /**
     * Log tag
     */
    public static final String LOG_TAG = "ExportAsyncTask";

    /**
     * Export parameters
     */
    private ExportParams mExportParams;

    // File paths generated by the exporter
    private List<String> mExportedFiles = Collections.emptyList();

    public ExportAsyncTask(Context context, SQLiteDatabase db) {
        this.mContext = context;
        this.mDb = db;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        if (mContext instanceof Activity) {
            mProgressDialog = new ProgressDialog(mContext);
            mProgressDialog.setTitle(R.string.title_progress_exporting_transactions);
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setProgressNumberFormat(null);
            mProgressDialog.setProgressPercentFormat(null);

            mProgressDialog.show();
        }
    }

    /**
     * Generates the appropriate exported transactions file for the given parameters
     *
     * @param params Export parameters
     * @return <code>true</code> if export was successful, <code>false</code> otherwise
     */
    @Override
    protected Boolean doInBackground(ExportParams... params) {
        mExportParams = params[0];
        Exporter mExporter = getExporter();

        try {
            mExportedFiles = mExporter.generateExport();
            Log.d(LOG_TAG, String.format("mExportedFiles: %s.", mExportedFiles));

        } catch (final Exception e) {
            Log.e(LOG_TAG, "Error exporting: " + e.getMessage());
            FirebaseCrashlytics.getInstance().recordException(e);
            e.printStackTrace();
            if (mContext instanceof Activity) {
                ((Activity) mContext).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(mContext,
                                mContext.getString(R.string.toast_export_error, mExportParams.getExportFormat().name())
                                        + "\n" + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
            return false;
        }

        if (mExportedFiles.isEmpty()) {
            return false;
        }

        try {
            moveToTarget();
        } catch (Exporter.ExporterException e) {
            FirebaseCrashlytics.getInstance().log("Error sending exported files to target");
            FirebaseCrashlytics.getInstance().recordException(e);
            return false;
        }

        return true;
    }

    /**
     * Transmits the exported transactions to the designated location, either SD card or third-party application
     * Finishes the activity if the export was starting  in the context of an activity
     *
     * @param exportSuccessful Result of background export execution
     */
    @Override
    protected void onPostExecute(Boolean exportSuccessful) {
        if (exportSuccessful) {
            if (mContext instanceof Activity)
                reportSuccess();

            if (mExportParams.shouldDeleteTransactionsAfterExport()) {
                backupAndDeleteTransactions();
                refreshViews();
            }
        } else {
            if (mContext instanceof Activity) {
                dismissProgressDialog();
                if (mExportedFiles.isEmpty()) {
                    Toast.makeText(mContext,
                            R.string.toast_no_transactions_to_export,
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(mContext,
                            mContext.getString(R.string.toast_export_error, mExportParams.getExportFormat().name()),
                            Toast.LENGTH_LONG).show();
                }
            }
        }

        dismissProgressDialog();
    }

    private void dismissProgressDialog() {
        if (mContext instanceof Activity) {
            if (mProgressDialog != null && mProgressDialog.isShowing())
                mProgressDialog.dismiss();
            ((Activity) mContext).finish();
        }
    }

    /**
     * Returns an exporter corresponding to the user settings.
     *
     * @return Object of one of {@link QifExporter}, {@link OfxExporter} or {@link GncXmlExporter}, {@Link CsvAccountExporter} or {@Link CsvTransactionsExporter}
     */
    private Exporter getExporter() {
        switch (mExportParams.getExportFormat()) {
            case QIF:
                return new QifExporter(mExportParams, mDb);
            case OFX:
                return new OfxExporter(mExportParams, mDb);
            case CSVA:
                return new CsvAccountExporter(mExportParams, mDb);
            case CSVT:
                return new CsvTransactionsExporter(mExportParams, mDb);
            case XML:
            default:
                return new GncXmlExporter(mExportParams, mDb);
        }
    }

    /**
     * Moves the generated export files to the target specified by the user
     *
     * @throws Exporter.ExporterException if the move fails
     */
    private void moveToTarget() throws Exporter.ExporterException {
        switch (mExportParams.getExportTarget()) {
            case SHARING:
                shareFiles(mExportedFiles);
                break;

            case URI:
                moveExportToUri();
                break;

            default:
                throw new Exporter.ExporterException(mExportParams, "Invalid target");
        }
    }

    /**
     * Move the exported files to a specified URI.
     * This URI could be a Storage Access Framework file
     *
     * @throws Exporter.ExporterException if something failed while moving the exported file
     */
    private void moveExportToUri() throws Exporter.ExporterException {
        Uri exportUri = Uri.parse(mExportParams.getExportLocation());
        Log.d(LOG_TAG, String.format("moveExportToUri: %s.", exportUri));
        if (exportUri == null) {
            Log.w(LOG_TAG, "No URI found for export destination");
            return;
        }

        if (mExportedFiles.size() > 0) {
            try {
                OutputStream outputStream = mContext.getContentResolver().openOutputStream(exportUri);
                // Now we always get just one file exported (multi-currency QIFs are zipped)
                org.gnucash.android.util.FileUtils.moveFile(mExportedFiles.get(0), outputStream);
            } catch (IOException ex) {
                throw new Exporter.ExporterException(mExportParams, "Error when moving file to URI");
            }
        }
    }

    private static String getFileLastModifiedTimestamp(String path) {
        Long timeStampLong = new File(path).lastModified() / 1000;
        return timeStampLong.toString();
    }

    /**
     * Backups of the database, saves opening balances (if necessary)
     * and deletes all non-template transactions in the database.
     */
    private void backupAndDeleteTransactions() {
        Log.i(LOG_TAG, "Backup and deleting transactions after export");
        BackupManager.backupActiveBook(); //create backup before deleting everything
        List<Transaction> openingBalances = new ArrayList<>();
        boolean preserveOpeningBalances = GnuCashApplication.shouldSaveOpeningBalances(false);

        TransactionsDbAdapter transactionsDbAdapter = new TransactionsDbAdapter(mDb, new SplitsDbAdapter(mDb));
        if (preserveOpeningBalances) {
            openingBalances = new AccountsDbAdapter(mDb, transactionsDbAdapter).getAllOpeningBalanceTransactions();
        }
        transactionsDbAdapter.deleteAllNonTemplateTransactions();

        if (preserveOpeningBalances) {
            transactionsDbAdapter.bulkAddRecords(openingBalances, DatabaseAdapter.UpdateMethod.insert);
        }
    }

    /**
     * Starts an intent chooser to allow the user to select an activity to receive
     * the exported files.
     *
     * @param paths list of full paths of the files to send to the activity.
     */
    private void shareFiles(List<String> paths) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        shareIntent.setType("text/xml");

        ArrayList<Uri> exportFiles = convertFilePathsToUris(paths);
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, exportFiles);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        shareIntent.putExtra(Intent.EXTRA_SUBJECT, mContext.getString(R.string.title_export_email,
                mExportParams.getExportFormat().name()));

        String defaultEmail = PreferenceManager.getDefaultSharedPreferences(mContext)
                .getString(mContext.getString(R.string.key_default_export_email), null);
        if (defaultEmail != null && defaultEmail.trim().length() > 0)
            shareIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{defaultEmail});

        SimpleDateFormat formatter = (SimpleDateFormat) SimpleDateFormat.getDateTimeInstance();
        String extraText = mContext.getString(R.string.description_export_email)
                + " " + formatter.format(new Date(System.currentTimeMillis()));
        shareIntent.putExtra(Intent.EXTRA_TEXT, extraText);

        if (mContext instanceof Activity) {
            List<ResolveInfo> activities = mContext.getPackageManager().queryIntentActivities(shareIntent, 0);
            if (!activities.isEmpty()) {
                mContext.startActivity(Intent.createChooser(shareIntent,
                        mContext.getString(R.string.title_select_export_destination)));
            } else {
                Toast.makeText(mContext, R.string.toast_no_compatible_apps_to_receive_export,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Convert file paths to URIs by adding the file// prefix
     * <p>e.g. /some/path/file.ext --> file:///some/path/file.ext</p>
     *
     * @param paths List of file paths to convert
     * @return List of file URIs
     */
    @NonNull
    private ArrayList<Uri> convertFilePathsToUris(List<String> paths) {
        ArrayList<Uri> exportFiles = new ArrayList<>();

        for (String path : paths) {
            File file = new File(path);
            Uri contentUri = FileProvider.getUriForFile(GnuCashApplication.getAppContext(), GnuCashApplication.FILE_PROVIDER_AUTHORITY, file);
            exportFiles.add(contentUri);
        }
        return exportFiles;
    }

    private void reportSuccess() {
        String targetLocation;
        switch (mExportParams.getExportTarget()) {
            case SD_CARD:
                targetLocation = "SD card";
                break;
            case GOOGLE_DRIVE:
                targetLocation = "Google Drive -> " + mContext.getString(R.string.app_name);
                break;
            default:
                targetLocation = mContext.getString(R.string.label_export_target_external_service);
        }
        Toast.makeText(mContext,
                String.format(mContext.getString(R.string.toast_exported_to), targetLocation),
                Toast.LENGTH_LONG).show();
    }

    private void refreshViews() {
        if (mContext instanceof AccountsActivity) {
            AccountsListFragment fragment =
                    ((AccountsActivity) mContext).getCurrentAccountListFragment();
            if (fragment != null)
                fragment.refresh();
        }
        if (mContext instanceof TransactionsActivity) {
            ((TransactionsActivity) mContext).refresh();
        }
    }
}
