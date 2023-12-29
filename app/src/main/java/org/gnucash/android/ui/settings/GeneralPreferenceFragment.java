/*
 * Copyright (c) 2014 - 2015 Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
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
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.gnucash.android.R;
import org.gnucash.android.ui.common.UxArgument;
import org.gnucash.android.ui.passcode.PasscodeLockScreenActivity;
import org.gnucash.android.ui.passcode.PasscodePreferenceActivity;

/**
 * Fragment for general preferences. Currently caters to the passcode and reporting preferences
 *
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
public class GeneralPreferenceFragment extends PreferenceFragmentCompat implements
        Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    public static final String LOG_TAG = GeneralPreferenceFragment.class.getName();

    private SharedPreferences.Editor mEditor;
    private CheckBoxPreference mCheckBoxPreference;

    private final ActivityResultLauncher<Intent> retrievePasscodeLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Log.d(LOG_TAG, "launch intent: result = " + result);

                if (mEditor == null) {
                    mEditor = getPreferenceManager().getSharedPreferences().edit();
                }
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    mEditor.putString(UxArgument.PASSCODE, data.getStringExtra(UxArgument.PASSCODE));
                    mEditor.putBoolean(UxArgument.ENABLED_PASSCODE, true);
                    Toast.makeText(getActivity(), R.string.toast_passcode_set, Toast.LENGTH_SHORT).show();
                } else if (result.getResultCode() == Activity.RESULT_CANCELED) {
                    mEditor.putBoolean(UxArgument.ENABLED_PASSCODE, false);
                    mCheckBoxPreference.setChecked(false);
                }
                mEditor.commit();
            }
    );
    private final ActivityResultLauncher<Intent> disablePasscodeLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Log.d(LOG_TAG, "launch intent: result = " + result);

                if (mEditor == null) {
                    mEditor = getPreferenceManager().getSharedPreferences().edit();
                }
                boolean flag = (result.getResultCode() != Activity.RESULT_OK);
                mEditor.putBoolean(UxArgument.ENABLED_PASSCODE, flag);
                mCheckBoxPreference.setChecked(flag);
                mEditor.commit();
            }
    );
    private final ActivityResultLauncher<Intent> changePasscodeLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Log.d(LOG_TAG, "launch intent: result = " + result);

                if (mEditor == null) {
                    mEditor = getPreferenceManager().getSharedPreferences().edit();
                }
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    mEditor.putString(UxArgument.PASSCODE, result.getData().getStringExtra(UxArgument.PASSCODE));
                    mEditor.putBoolean(UxArgument.ENABLED_PASSCODE, true);
                    Toast.makeText(getActivity(), R.string.toast_passcode_set, Toast.LENGTH_SHORT).show();
                }
                mEditor.commit();
            }
    );

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        addPreferencesFromResource(R.xml.fragment_general_preferences);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.title_general_prefs);
    }

    @Override
    public void onResume() {
        super.onResume();

        final Intent intent = new Intent(getActivity(), PasscodePreferenceActivity.class);

        mCheckBoxPreference = findPreference(getString(R.string.key_enable_passcode));
        mCheckBoxPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            if ((Boolean) newValue) {
                retrievePasscodeLauncher.launch(intent);
            } else {
                Intent passIntent = new Intent(getActivity(), PasscodeLockScreenActivity.class);
                passIntent.putExtra(UxArgument.DISABLE_PASSCODE, UxArgument.DISABLE_PASSCODE);
                disablePasscodeLauncher.launch(passIntent);
            }
            return true;
        });
        findPreference(getString(R.string.key_change_passcode)).setOnPreferenceClickListener(this);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        if (key.equals(getString(R.string.key_change_passcode))) {
            changePasscodeLauncher.launch(new Intent(getActivity(), PasscodePreferenceActivity.class));
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference.getKey().equals(getString(R.string.key_enable_passcode))) {
            if ((Boolean) newValue) {
                retrievePasscodeLauncher.launch(new Intent(getActivity(), PasscodePreferenceActivity.class));
            } else {
                Intent passIntent = new Intent(getActivity(), PasscodeLockScreenActivity.class);
                passIntent.putExtra(UxArgument.DISABLE_PASSCODE, UxArgument.DISABLE_PASSCODE);
                disablePasscodeLauncher.launch(passIntent);
            }
        }

        if (preference.getKey().equals(getString(R.string.key_use_account_color))) {
            getPreferenceManager().getSharedPreferences()
                    .edit()
                    .putBoolean(getString(R.string.key_use_account_color), Boolean.parseBoolean(newValue.toString()))
                    .apply();
        }
        return true;
    }
}
