/*
 * Copyright (c) 2013 - 2015 Ngewi Fet <ngewif@gmail.com>
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

package org.gnucash.android.ui.transaction;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.core.content.ContextCompat;
import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.ListFragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import com.maltaisn.recurpicker.format.RRuleFormatter;
import com.maltaisn.recurpicker.format.RecurrenceFormatter;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.db.DatabaseCursorLoader;
import org.gnucash.android.db.DatabaseSchema;
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter;
import org.gnucash.android.db.adapter.TransactionsDbAdapter;
import org.gnucash.android.export.ExportParams;
import org.gnucash.android.model.ScheduledAction;
import org.gnucash.android.model.Transaction;
import org.gnucash.android.ui.common.FormActivity;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.util.BackupManager;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Fragment which displays the scheduled actions in the system
 * <p>Currently, it handles the display of scheduled transactions and scheduled exports</p>
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 */
public class ScheduledActionsListFragment extends ListFragment implements
        LoaderManager.LoaderCallbacks<Cursor> {

    /**
     * Logging tag
     */
    protected static final String LOG_TAG = ScheduledActionsListFragment.class.getName();

    private TransactionsDbAdapter mTransactionsDbAdapter;
    private SimpleCursorAdapter mCursorAdapter;
    private ActionMode mActionMode = null;

    /**
     * Flag which is set when a transaction is selected
     */
    private boolean mInEditMode = false;

    private ScheduledAction.ActionType mActionType = ScheduledAction.ActionType.TRANSACTION;

    private final ActivityResultLauncher<Intent> refreshOnSuccessLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Log.d(LOG_TAG, "launch intent: result = " + result);
                if (result.getResultCode() == Activity.RESULT_OK) {
                    refreshList();
                }
            }
    );

    private final RRuleFormatter mRRuleFormatter = new RRuleFormatter();
    private final RecurrenceFormatter mRecurrenceFormatter = new RecurrenceFormatter(DateFormat.getInstance());

    /**
     * Callbacks for the menu items in the Context ActionBar (CAB) in action mode
     */
    private final ActionMode.Callback mActionModeCallbacks = new ActionMode.Callback() {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.schedxactions_context_menu, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            //nothing to see here, move along
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            finishEditMode();
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (item.getItemId() == R.id.context_menu_delete) {
                BackupManager.backupActiveBook();

                for (long id : getListView().getCheckedItemIds()) {
                    if (mActionType == ScheduledAction.ActionType.TRANSACTION) {
                        Log.i(LOG_TAG, "Cancelling scheduled transaction(s)");
                        String trnUID = mTransactionsDbAdapter.getUID(id);
                        ScheduledActionDbAdapter scheduledActionDbAdapter = GnuCashApplication.getScheduledEventDbAdapter();
                        List<ScheduledAction> actions = scheduledActionDbAdapter.getScheduledActionsWithUID(trnUID);

                        if (mTransactionsDbAdapter.deleteRecord(id)) {
                            Toast.makeText(getActivity(),
                                    R.string.toast_recurring_transaction_deleted,
                                    Toast.LENGTH_SHORT).show();
                            for (ScheduledAction action : actions) {
                                if (action.getUID() != null) {
                                    scheduledActionDbAdapter.deleteRecord(action.getUID());
                                }
                            }
                        }
                    } else if (mActionType == ScheduledAction.ActionType.BACKUP) {
                        Log.i(LOG_TAG, "Removing scheduled exports");
                        ScheduledActionDbAdapter.getInstance().deleteRecord(id);
                    }
                }
                mode.finish();
                setDefaultStatusBarColor();
                refreshList();
                return true;
            }
            setDefaultStatusBarColor();
            return false;
        }
    };

    private void setDefaultStatusBarColor() {
        requireActivity().getWindow().setStatusBarColor(
                ContextCompat.getColor(requireContext(), R.color.theme_primary_dark));
    }

    /**
     * Returns a new instance of the fragment for displayed the scheduled action
     *
     * @param actionType Type of scheduled action to be displayed
     * @return New instance of fragment
     */
    public static Fragment getInstance(ScheduledAction.ActionType actionType) {
        ScheduledActionsListFragment fragment = new ScheduledActionsListFragment();
        fragment.mActionType = actionType;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mTransactionsDbAdapter = TransactionsDbAdapter.getInstance();
        switch (mActionType) {
            case TRANSACTION -> mCursorAdapter = new ScheduledTransactionsCursorAdapter(
                    requireActivity().getApplicationContext(),
                    R.layout.list_item_scheduled_trxn, null,
                    new String[]{DatabaseSchema.TransactionEntry.COLUMN_DESCRIPTION},
                    new int[]{R.id.primary_text});
            case BACKUP -> mCursorAdapter = new ScheduledExportCursorAdapter(
                    requireActivity().getApplicationContext(),
                    R.layout.list_item_scheduled_trxn, null,
                    new String[]{}, new int[]{});
            default ->
                    throw new IllegalArgumentException("Unable to display scheduled actions for the specified action type");
        }

        setListAdapter(mCursorAdapter);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scheduled_events_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }

        setHasOptionsMenu(true);
        getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        ((TextView) getListView().getEmptyView())
                .setTextColor(ContextCompat.getColor(requireContext(), R.color.theme_accent));
        if (mActionType == ScheduledAction.ActionType.TRANSACTION) {
            ((TextView) getListView().getEmptyView()).setText(R.string.label_no_recurring_transactions);
        } else if (mActionType == ScheduledAction.ActionType.BACKUP) {
            ((TextView) getListView().getEmptyView()).setText(R.string.label_no_scheduled_exports_to_display);
        }
    }

    /**
     * Reload the list of transactions and recompute account balances
     */
    public void refreshList() {
        LoaderManager.getInstance(this).destroyLoader(0);
        LoaderManager.getInstance(this).restartLoader(0, null, this);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshList();
    }


    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        if (mActionType == ScheduledAction.ActionType.BACKUP) {
            inflater.inflate(R.menu.scheduled_export_actions, menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_add_scheduled_export) {
            Intent addScheduledExportIntent = new Intent(getActivity(), FormActivity.class);
            addScheduledExportIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.EXPORT.name());
            refreshOnSuccessLauncher.launch(addScheduledExportIntent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onListItemClick(@NonNull ListView l, @NonNull View v, int position, long id) {
        Log.d(LOG_TAG, String.format("onListItemClick: position=%d, id=%d. mActionMode=%s, mActionType=%s.",
                position, id, mActionMode, mActionType));
        super.onListItemClick(l, v, position, id);
        if (mActionMode != null) {
            CheckBox checkbox = v.findViewById(R.id.checkbox);
            checkbox.setChecked(!checkbox.isChecked());
            return;
        }

        if (mActionType == ScheduledAction.ActionType.BACKUP) {
            //nothing to do for export actions
            return;
        }

        Transaction transaction = mTransactionsDbAdapter.getRecord(id);

        //this should actually never happen, but has happened once. So perform check for the future
        if (transaction.getSplits().size() == 0) {
            Toast.makeText(getActivity(), R.string.toast_transaction_has_no_splits_and_cannot_open, Toast.LENGTH_SHORT).show();
            return;
        }

        String accountUID = transaction.getSplits().get(0).getAccountUID();
        openTransactionForEdit(accountUID, mTransactionsDbAdapter.getUID(id), v.getTag().toString());
    }

    /**
     * Opens the transaction editor to enable editing of the transaction
     *
     * @param accountUID     GUID of account to which transaction belongs
     * @param transactionUID GUID of transaction to be edited
     */
    public void openTransactionForEdit(String accountUID, String transactionUID, String scheduledActionUid) {
        Log.d(LOG_TAG, String.format("openTransactionForEdit(%s, %s, %s).", accountUID, transactionUID, scheduledActionUid));
        Intent createTransactionIntent = new Intent(getActivity(), FormActivity.class);
        createTransactionIntent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        createTransactionIntent.putExtra(UxArgument.FORM_TYPE, FormActivity.FormType.TRANSACTION.name());
        createTransactionIntent.putExtra(UxArgument.SELECTED_ACCOUNT_UID, accountUID);
        createTransactionIntent.putExtra(UxArgument.SELECTED_TRANSACTION_UID, transactionUID);
        createTransactionIntent.putExtra(UxArgument.SCHEDULED_ACTION_UID, scheduledActionUid);
        refreshOnSuccessLauncher.launch(createTransactionIntent);
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
        Log.d(LOG_TAG, String.format("Creating ScheduledAction loader, mActionType %s.", mActionType));
        if (mActionType == ScheduledAction.ActionType.TRANSACTION)
            return new ScheduledTransactionsCursorLoader(getActivity());
        else { // if (mActionType == ScheduledAction.ActionType.BACKUP) {
            return new ScheduledExportCursorLoader(getActivity());
        }
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
        Log.d(LOG_TAG, "ScheduledAction loader finished. Swapping in cursor");
        mCursorAdapter.swapCursor(cursor);
        mCursorAdapter.notifyDataSetChanged();
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        Log.d(LOG_TAG, "Resetting ScheduledAction loader");
        mCursorAdapter.swapCursor(null);
    }

    /**
     * Finishes the edit mode in the transactions list.
     * Edit mode is started when at least one transaction is selected
     */
    public void finishEditMode() {
        Log.d(LOG_TAG, "finishEditMode.");
        mInEditMode = false;
        uncheckAllItems();
        mActionMode = null;
    }

    /**
     * Sets the title of the Context ActionBar when in action mode.
     * It sets the number highlighted items
     */
    public void setActionModeTitle() {
        int count = getListView().getCheckedItemIds().length; //mSelectedIds.size();
        if (count > 0) {
            mActionMode.setTitle(getResources().getString(R.string.title_selected, count));
        }
    }

    /**
     * Unchecks all the checked items in the list
     */
    private void uncheckAllItems() {
        SparseBooleanArray checkedPositions = getListView().getCheckedItemPositions();
        ListView listView = getListView();
        for (int i = 0; i < checkedPositions.size(); i++) {
            int position = checkedPositions.keyAt(i);
            listView.setItemChecked(position, false);
        }
    }

    /**
     * Starts action mode and activates the Context ActionBar (CAB)
     * Action mode is initiated as soon as at least one transaction is selected (highlighted)
     */
    private void startActionMode() {
        Log.d(LOG_TAG, String.format("startActionMode: startActionMode=%s.", mActionMode));
        if (mActionMode != null) {
            return;
        }
        mInEditMode = true;
        // Start the CAB using the ActionMode.Callback defined above
        mActionMode = ((AppCompatActivity) requireActivity())
                .startSupportActionMode(mActionModeCallbacks);
        requireActivity().getWindow().setStatusBarColor(
                ContextCompat.getColor(requireContext(), android.R.color.darker_gray));
    }

    /**
     * Stops action mode and deselects all selected transactions.
     * This method only has effect if the number of checked items is greater than 0 and {@link #mActionMode} is not null
     */
    private void stopActionMode() {
        int checkedCount = getListView().getCheckedItemCount();
        Log.d(LOG_TAG, String.format("stopActionMode: checkedCount=%d.", checkedCount));
        if (checkedCount == 0 && mActionMode != null) {
            mActionMode.finish();
            setDefaultStatusBarColor();
        }
    }

    /**
     * Extends a simple cursor adapter to bind transaction attributes to views
     *
     * @author Ngewi Fet <ngewif@gmail.com>
     */
    protected class ScheduledTransactionsCursorAdapter extends SimpleCursorAdapter {

        public ScheduledTransactionsCursorAdapter(Context context, int layout, Cursor c,
                                                  String[] from, int[] to) {
            super(context, layout, c, from, to, 0);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final View view = super.getView(position, convertView, parent);
            final CheckBox checkBox = view.findViewById(R.id.checkbox);
            final int itemPosition = position;
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                Log.d(LOG_TAG, String.format("checkBox.OnCheckedChange itemPosition=%d, checked=%b", itemPosition, checkBox.isChecked()));
                getListView().setItemChecked(itemPosition, isChecked);
                if (isChecked) {
                    startActionMode();
                } else {
                    stopActionMode();
                }
                setActionModeTitle();
            });

            final TextView secondaryText = view.findViewById(R.id.secondary_text);
            ListView listView = (ListView) parent;
            Log.d(LOG_TAG, String.format("getView listView.isItemChecked(%d)=%b, %b",
                    position, listView.isItemChecked(position), checkBox.isChecked()));
            if (mInEditMode && listView.isItemChecked(position)) {
                view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.abs__holo_blue_light));
                secondaryText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white));
            } else {
                view.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.transparent));
                secondaryText.setTextColor(ContextCompat.getColor(requireContext(),
                        android.R.color.holo_green_light));
                checkBox.setChecked(false);
            }

            view.post(() -> {
                if (isAdded()) { //may be run when fragment has been unbound from activity
                    float extraPadding = getResources().getDimension(R.dimen.edge_padding);
                    final Rect hitRect = new Rect();
                    checkBox.getHitRect(hitRect);
                    hitRect.right += extraPadding;
                    hitRect.bottom += 3 * extraPadding;
                    hitRect.top -= extraPadding;
                    hitRect.left -= 2 * extraPadding;
                    view.setTouchDelegate(new TouchDelegate(hitRect, checkBox));
                }
            });

            return view;
        }

        @SuppressLint("StringFormatInvalid")
        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            super.bindView(view, context, cursor);
            Log.d(LOG_TAG, String.format("bindView: view=%s", view));

            TextView amountTextView = view.findViewById(R.id.right_text);
            Transaction transaction = mTransactionsDbAdapter.buildModelInstance(cursor);
            if (transaction.getSplits().size() == 2) {
                if (transaction.getSplits().get(0).isPairOf(transaction.getSplits().get(1))) {
                    amountTextView.setText(Objects.requireNonNull(transaction.getSplits().get(0).getValue()).formattedString());
                }
            } else {
                amountTextView.setText(getString(R.string.label_split_count, transaction.getSplits().size()));
            }

            TextView descriptionTextView = view.findViewById(R.id.secondary_text);
            ScheduledActionDbAdapter scheduledActionDbAdapter = ScheduledActionDbAdapter.getInstance();
            String scheduledActionUID = cursor.getString(cursor.getColumnIndexOrThrow("origin_scheduled_action_uid")); //column created from join when fetching scheduled transactions
            view.setTag(scheduledActionUID);
            ScheduledAction scheduledAction = scheduledActionDbAdapter.getRecord(scheduledActionUID);
            long endTime = scheduledAction.getEndTime();
            if (endTime > 0 && endTime < System.currentTimeMillis()) {
                ((TextView) view.findViewById(R.id.primary_text)).setTextColor(
                        ContextCompat.getColor(requireContext(), android.R.color.darker_gray));
                descriptionTextView.setText(getString(R.string.label_scheduled_action_ended,
                        DateFormat.getInstance().format(new Date(scheduledAction.getLastRunTime()))));
            } else {
                String repeatString = getString(R.string.label_tap_to_create_schedule);
                if (scheduledAction.getRecurrence() != null) {
                    repeatString = mRecurrenceFormatter.format(ScheduledActionsListFragment.this.requireContext(),
                            mRRuleFormatter.parse(Objects.requireNonNull(scheduledAction.getRecurrence().getRrule())));
                }
                descriptionTextView.setText(repeatString);
            }
        }
    }

    /**
     * Extends a simple cursor adapter to bind transaction attributes to views
     *
     * @author Ngewi Fet <ngewif@gmail.com>
     */
    protected class ScheduledExportCursorAdapter extends SimpleCursorAdapter {

        public ScheduledExportCursorAdapter(Context context, int layout, Cursor c,
                                            String[] from, int[] to) {
            super(context, layout, c, from, to, 0);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final View view = super.getView(position, convertView, parent);
            final CheckBox checkBox = view.findViewById(R.id.checkbox);
            final int itemPosition = position;
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                getListView().setItemChecked(itemPosition, isChecked);
                if (isChecked) {
                    startActionMode();
                } else {
                    stopActionMode();
                }
                setActionModeTitle();
            });

            final TextView secondaryText = view.findViewById(R.id.secondary_text);
            ListView listView = (ListView) parent;
            if (mInEditMode && listView.isItemChecked(position)) {
                view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.abs__holo_blue_light));
                secondaryText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white));
            } else {
                view.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.transparent));
                secondaryText.setTextColor(
                        ContextCompat.getColor(requireContext(), android.R.color.holo_green_light));
                checkBox.setChecked(false);
            }

            view.post(() -> {
                if (isAdded()) { //may be run when fragment has been unbound from activity
                    float extraPadding = getResources().getDimension(R.dimen.edge_padding);
                    final Rect hitRect = new Rect();
                    checkBox.getHitRect(hitRect);
                    hitRect.right += extraPadding;
                    hitRect.bottom += 3 * extraPadding;
                    hitRect.top -= extraPadding;
                    hitRect.left -= 2 * extraPadding;
                    view.setTouchDelegate(new TouchDelegate(hitRect, checkBox));
                }
            });

            return view;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            super.bindView(view, context, cursor);

            ScheduledActionDbAdapter mScheduledActionDbAdapter = ScheduledActionDbAdapter.getInstance();
            ScheduledAction scheduledAction = mScheduledActionDbAdapter.buildModelInstance(cursor);

            TextView primaryTextView = view.findViewById(R.id.primary_text);
            ExportParams params = ExportParams.parseCsv(Objects.requireNonNull(scheduledAction.getTag()));
            String exportDestination = params.getExportTarget().getDescription();
            if (params.getExportTarget() == ExportParams.ExportTarget.URI) {
                exportDestination = exportDestination + " (" + Uri.parse(params.getExportLocation()).getHost() + ")";
            }
            primaryTextView.setText(String.format("%s %s to %s", params.getExportFormat().name(),
                    scheduledAction.getActionType().name().toLowerCase(),
                    exportDestination));

            view.findViewById(R.id.right_text).setVisibility(View.GONE);

            TextView descriptionTextView = view.findViewById(R.id.secondary_text);
            long endTime = scheduledAction.getEndTime();
            if (endTime > 0 && endTime < System.currentTimeMillis()) {
                ((TextView) view.findViewById(R.id.primary_text))
                        .setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray));
                descriptionTextView.setText(getString(R.string.label_scheduled_action_ended,
                        DateFormat.getInstance().format(new Date(scheduledAction.getLastRunTime()))));
            } else {
                String repeatString = getString(R.string.label_tap_to_create_schedule);
                if (scheduledAction.getRecurrence() != null) {
                    repeatString = mRecurrenceFormatter.format(ScheduledActionsListFragment.this.requireContext(),
                            mRRuleFormatter.parse(Objects.requireNonNull(scheduledAction.getRecurrence().getRrule())));
                }
                descriptionTextView.setText(repeatString);
            }
        }
    }


    /**
     * {@link DatabaseCursorLoader} for loading recurring transactions asynchronously from the database
     *
     * @author Ngewi Fet <ngewif@gmail.com>
     */
    protected static class ScheduledTransactionsCursorLoader extends DatabaseCursorLoader {

        public ScheduledTransactionsCursorLoader(Context context) {
            super(context);
        }

        @Override
        public Cursor loadInBackground() {
            mDatabaseAdapter = TransactionsDbAdapter.getInstance();

            Cursor c = ((TransactionsDbAdapter) mDatabaseAdapter).fetchAllScheduledTransactions();

            registerContentObserver(c);
            return c;
        }
    }

    /**
     * {@link DatabaseCursorLoader} for loading recurring transactions asynchronously from the database
     *
     * @author Ngewi Fet <ngewif@gmail.com>
     */
    protected static class ScheduledExportCursorLoader extends DatabaseCursorLoader {

        public ScheduledExportCursorLoader(Context context) {
            super(context);
        }

        @Override
        public Cursor loadInBackground() {
            mDatabaseAdapter = ScheduledActionDbAdapter.getInstance();

            Cursor c = mDatabaseAdapter.fetchAllRecords(
                    DatabaseSchema.ScheduledActionEntry.COLUMN_TYPE + "=?",
                    new String[]{ScheduledAction.ActionType.BACKUP.name()}, null);

            registerContentObserver(c);
            return c;
        }
    }
}
