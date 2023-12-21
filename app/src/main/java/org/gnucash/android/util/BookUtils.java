package org.gnucash.android.util;

import android.util.Log;

import androidx.annotation.NonNull;

import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.ui.account.AccountsActivity;

/**
 * Utility class for common operations involving books
 */

public class BookUtils {
    private static final String LOG_TAG = BookUtils.class.getName();

    /**
     * Activates the book with unique identifier {@code bookUID}, and refreshes the database adapters
     *
     * @param bookUID GUID of the book to be activated
     */
    public static void activateBook(@NonNull String bookUID) {
        String activeBook = GnuCashApplication.getBooksDbAdapter().setActive(bookUID);
        Log.d(LOG_TAG, String.format("Book %s is set active.", activeBook));
        GnuCashApplication.initializeDatabaseAdapters();
    }

    /**
     * Loads the book with GUID {@code bookUID} and opens the AccountsActivity
     *
     * @param bookUID GUID of the book to be loaded
     */
    public static void loadBook(@NonNull String bookUID) {
        activateBook(bookUID);
        AccountsActivity.start(GnuCashApplication.getAppContext());
    }
}
