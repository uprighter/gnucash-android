/*
 * Copyright (c) 2014 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.importer;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

import org.gnucash.android.R;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.ui.util.TaskDelegate;
import org.gnucash.android.util.BookUtils;

import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Imports a GnuCash (desktop) account file and displays a progress dialog.
 * The AccountsActivity is opened when importing is done.
 */
public class ImportAsyncTask {
    public static final String LOG_TAG = ImportAsyncTask.class.getName();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Activity mContext;
    private final TaskDelegate mDelegate;
    private final Uri mUri;
    private ProgressBar mProgressBar;

    private String mImportedBookUID;

    public ImportAsyncTask(Activity context, Uri uri) {
        this(context, null, uri);
    }

    public ImportAsyncTask(Activity context, TaskDelegate delegate, Uri uri) {
        this.mContext = context;
        this.mDelegate = delegate;
        this.mUri = uri;
    }

    protected void onPreExecute() {
        mProgressBar = new ProgressBar(mContext);
//        mProgressBar.setTitle(R.string.title_progress_importing_accounts);
        mProgressBar.setIndeterminate(true);
        mProgressBar.setVisibility(View.VISIBLE);
        Log.d(LOG_TAG, "mProgressBar=" + mProgressBar);

    }

    public Future<Boolean> asyncExecute() {
        onPreExecute();

        Callable<Boolean> callable = () -> {
            Boolean successfulBoolean = false;
            try {
                final Boolean successful = doInBackground(mUri);
                successfulBoolean = successful;

                handler.post(() -> onPostExecute(successful));
            } catch (Exception ex) {
                Log.e(LOG_TAG, "asyncExecute error: ", ex);
                FirebaseCrashlytics.getInstance().recordException(ex);
            }
            return successfulBoolean;
        };
        return executor.submit(callable);
    }


    protected Boolean doInBackground(Uri uri) {
        try {
            InputStream accountInputStream = mContext.getContentResolver().openInputStream(uri);
            mImportedBookUID = GncXmlImporter.parse(accountInputStream);

        } catch (Exception exception) {
            Log.e(LOG_TAG, "doInBackground: " + exception.getMessage());
            FirebaseCrashlytics.getInstance().log("Could not open: " + uri);
            FirebaseCrashlytics.getInstance().recordException(exception);
            exception.printStackTrace();

            final String err_msg = exception.getLocalizedMessage();
            FirebaseCrashlytics.getInstance().log(String.format("%s exception: %s", LOG_TAG, err_msg));
            mContext.runOnUiThread(() -> Toast.makeText(mContext,
                    mContext.getString(R.string.toast_error_importing_accounts) + "\n" + err_msg,
                    Toast.LENGTH_LONG).show());

            return false;
        }

        Cursor cursor = mContext.getContentResolver().query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            String displayName = cursor.getString(nameIndex);
            ContentValues contentValues = new ContentValues();
            contentValues.put(DatabaseSchema.BookEntry.COLUMN_DISPLAY_NAME, displayName);
            contentValues.put(DatabaseSchema.BookEntry.COLUMN_SOURCE_URI, uri.toString());
            int updated = BooksDbAdapter.getInstance().updateRecord(mImportedBookUID, contentValues);
            Log.d(LOG_TAG, String.format("%d records updated for book %s.", updated, mImportedBookUID));

            cursor.close();
        }

        //set the preferences to their default values
        mContext.getSharedPreferences(mImportedBookUID, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(mContext.getString(R.string.key_use_double_entry), true)
                .apply();

        return true;
    }

    protected void onPostExecute(Boolean importSuccess) {
        try {
            if (mProgressBar != null && mProgressBar.getVisibility() == View.VISIBLE) {
                mProgressBar.setProgress(100);
                mProgressBar.setVisibility(View.GONE);
            }
        } catch (IllegalArgumentException ex) {
            //TODO: This is a hack to catch "View not attached to window" exceptions
            //FIXME by moving the creation and display of the progress dialog to the Fragment
        } finally {
            mProgressBar = null;
        }

        int message = importSuccess ? R.string.toast_success_importing_accounts : R.string.toast_error_importing_accounts;
        Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();

        if (mImportedBookUID != null) {
            BookUtils.loadBook(mImportedBookUID);
        }

        if (mDelegate != null) {
            mDelegate.onTaskComplete();
        }
    }
}
