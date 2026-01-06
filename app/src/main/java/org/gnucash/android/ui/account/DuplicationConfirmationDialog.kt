/*
 * Copyright (c) 2024 Ngewi Fet <ngewif@gmail.com>
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
package org.gnucash.android.ui.account

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import org.gnucash.android.R
import org.gnucash.android.db.adapter.AccountsDbAdapter
import org.gnucash.android.ui.common.Refreshable
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.util.dialog.VolatileDialogFragment
import org.gnucash.android.util.BackupManager.backupActiveBookAsync

/**
 * Confirmation dialog for account duplication.
 */
class DuplicationConfirmationDialog : VolatileDialogFragment() {

    companion object {
        const val TAG = "duplication_confirmation_dialog"

        fun newInstance(accountUID: String): DuplicationConfirmationDialog {
            val args = Bundle()
            args.putString(UxArgument.SELECTED_ACCOUNT_UID, accountUID)
            val fragment = DuplicationConfirmationDialog()
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val arguments = requireArguments()
        val accountUID = arguments.getString(UxArgument.SELECTED_ACCOUNT_UID)
        
        return AlertDialog.Builder(requireContext())
            .setIcon(R.drawable.ic_copy)
            .setTitle(R.string.title_confirm_duplicate)
            .setMessage(R.string.msg_confirm_duplicate)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                duplicateAccount(accountUID!!)
            }
            .setNegativeButton(R.string.btn_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
    }

    private fun duplicateAccount(accountUID: String) {
        val activity = activity ?: return
        val accountsDbAdapter = AccountsDbAdapter.instance
        
        // Backup before potentially large changes (though duplication is generally safe)
        // Following similar pattern to deletion
        backupActiveBookAsync(activity) {
            accountsDbAdapter.duplicateAccount(accountUID)
            
            val result = Bundle()
            result.putBoolean(Refreshable.EXTRA_REFRESH, true)
            parentFragmentManager.setFragmentResult(TAG, result)
        }
    }
}
