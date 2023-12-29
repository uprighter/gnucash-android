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
package org.gnucash.android.ui.common;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewbinding.ViewBinding;

import com.google.android.material.navigation.NavigationView;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.ui.account.AccountsActivity;
import org.gnucash.android.ui.passcode.PasscodeLockActivity;
import org.gnucash.android.ui.report.ReportsActivity;
import org.gnucash.android.ui.settings.PreferenceActivity;
import org.gnucash.android.ui.transaction.ScheduledActionsActivity;
import org.gnucash.android.util.BookUtils;


/**
 * Base activity implementing the navigation drawer, to be extended by all activities requiring one.
 * <p>
 * Each activity inheriting from this class has an indeterminate progress bar at the top,
 * (above the action bar) which can be used to display busy operations. See {@link #getProgressBar()}
 * </p>
 *
 * <p>Sub-classes should provide their layout and bind the views using {@link #bindViews()}.<br>
 * The activity layout of the subclass is expected to contain {@code DrawerLayout} and
 * a {@code NavigationView}.<br>
 * Sub-class should also consider using the {@code toolbar.xml} or {@code toolbar_with_spinner.xml}
 * for the action bar in their XML layout. Otherwise provide another which contains widgets for the
 * toolbar and progress indicator with the IDs {@code R.id.toolbar} and {@code R.id.progress_indicator} respectively.
 * </p>
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public abstract class BaseDrawerActivity extends PasscodeLockActivity implements
        PopupMenu.OnMenuItemClickListener {

    public final String LOG_TAG = this.getClass().getName();

    public static final int ID_MANAGE_BOOKS = 0xB00C;
    protected DrawerLayout mDrawerLayout;
    protected NavigationView mNavigationView;
    protected Toolbar mToolbar;
    protected ProgressBar mToolbarProgress;

    protected TextView mBookNameTextView;

    protected ActionBarDrawerToggle mDrawerToggle;


    private final ActivityResultLauncher<Intent> openLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Log.d(LOG_TAG, "launch intent: result = " + result);
                if (result.getResultCode() == Activity.RESULT_CANCELED) {
                    Log.d(LOG_TAG, "intent cancelled.");
                }
            }
    );

    private class DrawerItemClickListener implements NavigationView.OnNavigationItemSelectedListener {

        @Override
        public boolean onNavigationItemSelected(MenuItem menuItem) {
            onDrawerMenuItemClicked(menuItem.getItemId());
            return true;
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ViewBinding viewBinding = bindViews();
        setContentView(viewBinding.getRoot());

        //if a parameter was passed to open an account within a specific book, then switch
        String bookUID = getIntent().getStringExtra(UxArgument.BOOK_UID);
        if (bookUID != null && !bookUID.equals(BooksDbAdapter.getInstance().getActiveBookUID())) {
            BookUtils.activateBook(bookUID);
        }

        setSupportActionBar(mToolbar);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(getTitleRes());
        }

        mToolbarProgress.getIndeterminateDrawable().setColorFilter(new BlendModeColorFilter(Color.WHITE, BlendMode.SRC_IN));

        View headerView = mNavigationView.getHeaderView(0);
        headerView.findViewById(R.id.drawer_title).setOnClickListener(this::onClickAppTitle);

        mBookNameTextView = headerView.findViewById(R.id.book_name);
        mBookNameTextView.setOnClickListener(this::onClickBook);
        updateActiveBookName();
        setUpNavigationDrawer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateActiveBookName();
    }

    public abstract ViewBinding bindViews();

    /**
     * Return the title for this activity.
     * This will be displayed in the action bar
     *
     * @return String resource identifier
     */
    public abstract @StringRes int getTitleRes();

    /**
     * Returns the progress bar for the activity.
     * <p>This progress bar is displayed above the toolbar and should be used to show busy status
     * for long operations.<br/>
     * The progress bar visibility is set to {@link View#GONE} by default. Make visible to use </p>
     *
     * @return Indeterminate progress bar.
     */
    public ProgressBar getProgressBar() {
        return mToolbarProgress;
    }

    /**
     * Sets up the navigation drawer for this activity.
     */
    private void setUpNavigationDrawer() {
        mNavigationView.setNavigationItemSelectedListener(new DrawerItemClickListener());

        mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                R.string.drawer_open,  /* "open drawer" description */
                R.string.drawer_close  /* "close drawer" description */
        ) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
            }
        };

        mDrawerLayout.addDrawerListener(mDrawerToggle);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (!mDrawerLayout.isDrawerOpen(mNavigationView))
                mDrawerLayout.openDrawer(mNavigationView);
            else
                mDrawerLayout.closeDrawer(mNavigationView);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Update the display name of the currently active book
     */
    protected void updateActiveBookName() {
        mBookNameTextView.setText(BooksDbAdapter.getInstance().getActiveBookDisplayName());
    }

    /**
     * Handler for the navigation drawer items
     */
    protected void onDrawerMenuItemClicked(int itemId) {
        if (itemId == R.id.nav_item_open) { //Open... files
            //use the storage access framework
            Intent openDocument = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            openDocument.addCategory(Intent.CATEGORY_OPENABLE);
            openDocument.setType("text/*|application/*");
            String[] mimeTypes = {"text/*", "application/*"};
            openDocument.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            openLauncher.launch(openDocument);
        } else if (itemId == R.id.nav_item_favorites) { //favorite accounts
            Intent intent = new Intent(this, AccountsActivity.class);
            intent.putExtra(AccountsActivity.EXTRA_TAB_INDEX,
                    AccountsActivity.INDEX_FAVORITE_ACCOUNTS_FRAGMENT);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } else if (itemId == R.id.nav_item_reports) {
            Intent intent = new Intent(this, ReportsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } else if (itemId == R.id.nav_item_scheduled_actions) { //show scheduled transactions
            Intent intent = new Intent(this, ScheduledActionsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } else if (itemId == R.id.nav_item_export) {
            AccountsActivity.openExportFragment(this);
        } else if (itemId == R.id.nav_item_settings) { //Settings activity
            startActivity(new Intent(this, PreferenceActivity.class));
        } else if (itemId == R.id.nav_item_help) {
            Log.d(LOG_TAG, "nav_item_help onDrawerMenuItemClicked.");
        } else {
            Log.d(LOG_TAG, String.format("unknown onDrawerMenuItemClicked itemId = %d.", itemId));
        }
        mDrawerLayout.closeDrawer(mNavigationView);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        long id = item.getItemId();
        if (id == ID_MANAGE_BOOKS) {
            Intent intent = new Intent(this, PreferenceActivity.class);
            intent.setAction(PreferenceActivity.ACTION_MANAGE_BOOKS);
            startActivity(intent);
            mDrawerLayout.closeDrawer(mNavigationView);
            return true;
        }
        BooksDbAdapter booksDbAdapter = BooksDbAdapter.getInstance();
        String bookUID = booksDbAdapter.getUID(id);
        if (!bookUID.equals(booksDbAdapter.getActiveBookUID())) {
            BookUtils.loadBook(bookUID);
            finish();
        }
        AccountsActivity.start(GnuCashApplication.getAppContext());
        return true;
    }

    public void onClickAppTitle(View view) {
        mDrawerLayout.closeDrawer(mNavigationView);
        AccountsActivity.start(this);
    }

    public void onClickBook(View view) {
        PopupMenu popup = new PopupMenu(this, view);
        popup.setOnMenuItemClickListener(this);

        Menu menu = popup.getMenu();
        int maxRecent = 0;
        try (Cursor cursor = BooksDbAdapter.getInstance().fetchAllRecords(null, null,
                DatabaseSchema.BookEntry.COLUMN_MODIFIED_AT + " DESC")) {
            while (cursor.moveToNext() && maxRecent++ < 5) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseSchema.BookEntry._ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseSchema.BookEntry.COLUMN_DISPLAY_NAME));
                menu.add(0, (int) id, maxRecent, name);
            }
        }
        menu.add(0, ID_MANAGE_BOOKS, maxRecent, R.string.menu_manage_books);

        popup.show();
    }
}
