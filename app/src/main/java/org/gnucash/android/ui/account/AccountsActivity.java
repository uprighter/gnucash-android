/*
 * Copyright (c) 2012 - 2014 Ngewi Fet <ngewif@gmail.com>
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

package org.gnucash.android.ui.account;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import org.gnucash.android.BuildConfig;
import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.databinding.ActivityAccountsBinding;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.AccountsDbAdapter;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.export.Exporter;
import org.gnucash.android.importer.ImportAsyncTask;
import org.gnucash.android.ui.common.BaseDrawerActivity;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.Refreshable;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.transaction.TransactionsActivity;
import org.gnucash.android.ui.util.TaskDelegate;
import org.gnucash.android.ui.wizard.FirstRunWizardActivity;
import org.gnucash.android.util.BackupManager;

/**
 * Manages actions related to accounts, displaying, exporting and creating new accounts
 * The various actions are implemented as Fragments which are then added to this activity
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
public class AccountsActivity extends BaseDrawerActivity implements OnAccountClickedListener {

    /**
     * Request code for GnuCash account structure file to import
     */
    public static final int REQUEST_PICK_ACCOUNTS_FILE = 0x1;

    /**
     * Logging tag
     */
    protected static final String LOG_TAG = AccountsActivity.class.getName();

    /**
     * Number of pages to show
     */
    private static final int DEFAULT_NUM_PAGES = 3;

    /**
     * Index for the recent accounts tab
     */
    public static final int INDEX_RECENT_ACCOUNTS_FRAGMENT = 0;

    /**
     * Index of the top level (all) accounts tab
     */
    public static final int INDEX_TOP_LEVEL_ACCOUNTS_FRAGMENT = 1;

    /**
     * Index of the favorite accounts tab
     */
    public static final int INDEX_FAVORITE_ACCOUNTS_FRAGMENT = 2;

    /**
     * Used to save the index of the last open tab and restore the pager to that index
     */
    public static final String LAST_OPEN_TAB_INDEX = "last_open_tab";

    /**
     * Key for putting argument for tab into bundle arguments
     */
    public static final String EXTRA_TAB_INDEX = "org.gnucash.android.extra.TAB_INDEX";

    /**
     * Map containing fragments for the different tabs
     */
    private final SparseArray<Refreshable> mFragmentPageReferenceMap = new SparseArray<>();

    /**
     * ViewPager which manages the different tabs
     */
    ViewPager2 mViewPager;
    FloatingActionButton mFloatingActionButton;
    CoordinatorLayout mCoordinatorLayout;
    TabLayout mTabLayout;

    private AccountViewPagerAdapter mPagerAdapter;

    private final ActivityResultLauncher<Intent> addAccountLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Log.d(LOG_TAG, "launch intent: result = " + result);
                if (result.getResultCode() == Activity.RESULT_CANCELED) {
                    Log.d(LOG_TAG, "intent cancelled.");
                }
            }
    );
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
                    BackupManager.putBookBackupFileUri(getApplicationContext(), null, backupFileUri);
                }
            }
    );

    /**
     * Adapter for managing the sub-account and transaction fragment pages in the accounts view
     */
    private class AccountViewPagerAdapter extends FragmentStateAdapter {

        public AccountViewPagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        @Override
        @NonNull
        public Fragment createFragment(int position) {
            AccountsListFragment currentFragment = (AccountsListFragment) mFragmentPageReferenceMap.get(position);
            if (currentFragment == null) {
                currentFragment = switch (position) {
                    case INDEX_RECENT_ACCOUNTS_FRAGMENT ->
                            AccountsListFragment.newInstance(AccountsListFragment.DisplayMode.RECENT);
                    case INDEX_FAVORITE_ACCOUNTS_FRAGMENT ->
                            AccountsListFragment.newInstance(AccountsListFragment.DisplayMode.FAVORITES);
                    default ->
                            AccountsListFragment.newInstance(AccountsListFragment.DisplayMode.TOP_LEVEL);
                };
                mFragmentPageReferenceMap.put(position, currentFragment);
            }
            return currentFragment;
        }

        @Override
        public int getItemCount() {
            return DEFAULT_NUM_PAGES;
        }
    }

    public AccountsListFragment getCurrentAccountListFragment() {
        int index = mViewPager.getCurrentItem();
        Fragment fragment = (Fragment) mFragmentPageReferenceMap.get(index);
        if (fragment == null) {
            fragment = mPagerAdapter.createFragment(index);
        }
        return (AccountsListFragment) fragment;
    }

    @Override
    public ViewBinding bindViews() {
        ActivityAccountsBinding viewBinding = ActivityAccountsBinding.inflate(getLayoutInflater());
        mDrawerLayout = viewBinding.drawerLayout;
        mNavigationView = viewBinding.navView;
        mToolbar = viewBinding.toolbarLayout.toolbar;
        mToolbarProgress = viewBinding.toolbarLayout.actionbarProgressIndicator.toolbarProgress;

        mViewPager = viewBinding.pager;
        mFloatingActionButton = viewBinding.fabCreateAccount;
        mCoordinatorLayout = viewBinding.coordinatorLayout;
        mTabLayout = viewBinding.tabLayout;

        return viewBinding;
    }

    @Override
    public int getTitleRes() {
        return R.string.title_accounts;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        handleOpenFileIntent(intent);

        init();

        mTabLayout.addTab(mTabLayout.newTab().setText(R.string.title_recent_accounts));
        mTabLayout.addTab(mTabLayout.newTab().setText(R.string.title_all_accounts));
        mTabLayout.addTab(mTabLayout.newTab().setText(R.string.title_favorite_accounts));
        mTabLayout.setTabGravity(TabLayout.GRAVITY_FILL);

        //show the simple accounts list
        mPagerAdapter = new AccountViewPagerAdapter(this);
        mViewPager.setAdapter(mPagerAdapter);

        new TabLayoutMediator(mTabLayout, mViewPager,
                (@NonNull TabLayout.Tab tab, int position) -> {
                    Log.d(LOG_TAG, String.format("TabLayoutMediator, position=%d, tab.getText()=%s.", position, tab.getText()));
                    switch (position) {
                        case INDEX_RECENT_ACCOUNTS_FRAGMENT ->
                                tab.setText(getString(R.string.title_recent_accounts));
                        case INDEX_FAVORITE_ACCOUNTS_FRAGMENT ->
                                tab.setText(getString(R.string.title_favorite_accounts));
                        default -> tab.setText(getString(R.string.title_all_accounts));
                    }
                }
        ).attach();

        mTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                mViewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                //nothing to see here, move along
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                //nothing to see here, move along
            }
        });

        setCurrentTab();

        mFloatingActionButton.setOnClickListener(v -> {
            Intent addAccountIntent = new Intent(AccountsActivity.this, FormActivity.class);
            addAccountIntent.setAction(Intent.ACTION_INSERT_OR_EDIT);
            addAccountIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.ACCOUNT.name());
            addAccountLauncher.launch(addAccountIntent);
        });
    }

    /**
     * Handles the case where another application has selected to open a (.gnucash or .gnca) file with this app
     *
     * @param intent Intent containing the data to be imported
     */
    private void handleOpenFileIntent(Intent intent) {
        //when someone launches the app to view a (.gnucash or .gnca) file
        Uri data = intent.getData();
        if (data != null) {
            BackupManager.backupActiveBook();
            intent.setData(null);
            new ImportAsyncTask(this, data).asyncExecute();
            removeFirstRunFlag();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        setCurrentTab();

        int index = mViewPager.getCurrentItem();
        Fragment fragment = (Fragment) mFragmentPageReferenceMap.get(index);
        if (fragment != null) {
            ((Refreshable) fragment).refresh();
        }

        handleOpenFileIntent(intent);
    }

    /**
     * Sets the current tab in the ViewPager
     */
    public void setCurrentTab() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        int lastTabIndex = preferences.getInt(LAST_OPEN_TAB_INDEX, INDEX_TOP_LEVEL_ACCOUNTS_FRAGMENT);
        int index = getIntent().getIntExtra(EXTRA_TAB_INDEX, lastTabIndex);
        mViewPager.setCurrentItem(index);
    }

    /**
     * Loads default setting for currency and performs app first-run initialization.
     * <p>Also handles displaying the What's New dialog</p>
     */
    private void init() {
        String bookUID = BooksDbAdapter.getInstance().getActiveBookUID();
        PreferenceManager.setDefaultValues(this, bookUID,
                Context.MODE_PRIVATE, R.xml.fragment_transaction_preferences, true);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        boolean firstRun = prefs.getBoolean(getString(R.string.key_first_run), true);
        if (firstRun) {
            startActivity(new Intent(GnuCashApplication.getAppContext(), FirstRunWizardActivity.class));

            //default to using double entry and save the preference explicitly
            prefs.edit().putBoolean(getString(R.string.key_use_double_entry), true).apply();
            finish();
            return;
        }

        // If backup failed due to SecurityException, re-choose backup files.
        if (BackupManager.getBookBackupFileUri(bookUID) == null) {
            String bookName = BooksDbAdapter.getInstance().getActiveBookDisplayName();
            new AlertDialog.Builder(this)
                    .setTitle("Select backup file for current book.")
                    .setMessage("This is used for auto-backup. If not set, it will ask everytime you open this.")
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            createBackupFileLauncher.launch(BackupManager.createBackupFileIntent(
                                    Exporter.sanitizeFilename(bookName) + "_" + getString(R.string.label_backup_filename)));
                        }
                    })
                    .setNegativeButton(android.R.string.no, null).show();
        }

        if (hasNewFeatures()) {
            showWhatsNewDialog(this);
        }
        GnuCashApplication.startScheduledActionExecutionService(this);
        BackupManager.schedulePeriodicBackups(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.edit().putInt(LAST_OPEN_TAB_INDEX, mViewPager.getCurrentItem()).apply();
    }

    /**
     * Checks if the minor version has been increased and displays the What's New dialog box.
     * This is the minor version as per semantic versioning.
     *
     * @return <code>true</code> if the minor version has been increased, <code>false</code> otherwise.
     */
    private boolean hasNewFeatures() {
        String minorVersion = getResources().getString(R.string.app_minor_version);
        int currentMinor = Integer.parseInt(minorVersion);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int previousMinor = prefs.getInt(getString(R.string.key_previous_minor_version), 0);
        if (currentMinor > previousMinor) {
            Editor editor = prefs.edit();
            editor.putInt(getString(R.string.key_previous_minor_version), currentMinor);
            editor.apply();
            return true;
        }
        return false;
    }

    /**
     * Show dialog with new features for this version
     */
    public static AlertDialog showWhatsNewDialog(Context context) {
        Resources resources = context.getResources();
        StringBuilder releaseTitle = new StringBuilder(resources.getString(R.string.title_whats_new));
        PackageInfo packageInfo;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            releaseTitle.append(" - v").append(packageInfo.versionName);
        } catch (NameNotFoundException e) {
            FirebaseCrashlytics.getInstance().recordException(e);
            Log.e(LOG_TAG, "Error displaying 'Whats new' dialog");
        }

        return new AlertDialog.Builder(context)
                .setTitle(releaseTitle.toString())
                .setMessage(R.string.whats_new)
                .setPositiveButton(R.string.label_dismiss, (dialog, which) -> dialog.dismiss()).show();
    }

    /**
     * Displays the dialog for exporting transactions
     */
    public static void openExportFragment(AppCompatActivity activity) {
        Intent intent = new Intent(activity, FormActivity.class);
        intent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.EXPORT.name());
        activity.startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.global_actions, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            return super.onOptionsItemSelected(item);
        }
        return false;
    }

    /**
     * Creates default accounts with the specified currency code.
     * If the currency parameter is null, then locale currency will be used if available
     *
     * @param currencyCode Currency code to assign to the imported accounts
     * @param activity     Activity for providing context and displaying dialogs
     */
    public static void createDefaultAccounts(final String currencyCode, final Activity activity) {
        TaskDelegate delegate = null;
        if (currencyCode != null) {
            delegate = () -> {
                int updated = AccountsDbAdapter.getInstance().updateAllAccounts(DatabaseSchema.AccountEntry.COLUMN_CURRENCY, currencyCode);
                Log.d(LOG_TAG, String.format("createDefaultAccounts created %d accounts.", updated));
                GnuCashApplication.setDefaultCurrencyCode(currencyCode);
            };
        }

        Uri uri = Uri.parse("android.resource://" + BuildConfig.APPLICATION_ID + "/" + R.raw.default_accounts);
        new ImportAsyncTask(activity, delegate, uri).asyncExecute();
    }

    /**
     * Reads and XML file from an intent and imports it into the database
     *
     * @param context      Activity context
     * @param uri          XML Uri
     * @param onFinishTask Task to be executed when import is complete
     */
    public static void importXmlFileFromIntent(Activity context, Uri uri, TaskDelegate onFinishTask) {
        BackupManager.backupActiveBook();
        new ImportAsyncTask(context, onFinishTask, uri).asyncExecute();
    }

    /**
     * Starts the AccountsActivity and clears the activity stack
     *
     * @param context Application context
     */
    public static void start(Context context) {
        Intent accountsActivityIntent = new Intent(context, AccountsActivity.class);
        accountsActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        accountsActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(accountsActivityIntent);
    }

    @Override
    public void accountSelected(String accountUID) {
        Intent intent = new Intent(this, TransactionsActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID);

        startActivity(intent);
    }

    /**
     * Removes the flag indicating that the app is being run for the first time.
     * This is called every time the app is started because the next time won't be the first time
     */
    public static void removeFirstRunFlag() {
        Context context = GnuCashApplication.getAppContext();
        Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putBoolean(context.getString(R.string.key_first_run), false);
        editor.apply();
    }

}