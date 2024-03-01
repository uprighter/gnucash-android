/*
 * Copyright 2012 Roman Nurik
 * Copyright 2012 Ngewi Fet
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

package org.gnucash.android.ui.wizard;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.tech.freak.wizardpager.model.AbstractWizardModel;
import com.tech.freak.wizardpager.model.ModelCallbacks;
import com.tech.freak.wizardpager.model.Page;
import com.tech.freak.wizardpager.model.ReviewItem;
import com.tech.freak.wizardpager.ui.PageFragmentCallbacks;
import com.tech.freak.wizardpager.ui.ReviewFragment;
import com.tech.freak.wizardpager.ui.StepPagerStrip;

import org.gnucash.android.R;
import org.gnucash.android.app.GnuCashApplication;
import org.gnucash.android.databinding.ActivityFirstRunWizardBinding;
import org.gnucash.android.db.adapter.BooksDbAdapter;
import org.gnucash.android.ui.account.AccountsActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity for managing the wizard displayed upon first run of the application
 */
public class FirstRunWizardActivity extends AppCompatActivity implements
        PageFragmentCallbacks, ReviewFragment.Callbacks, ModelCallbacks {
    public static final String LOG_TAG = FirstRunWizardActivity.class.getName();

    ViewPager2 mPager;
    AppCompatButton mNextButton;
    Button mPrevButton;
    StepPagerStrip mStepPagerStrip;

    private MyPagerAdapter mPagerAdapter;

    private boolean mEditingAfterReview;

    private AbstractWizardModel mWizardModel;

    private boolean mConsumePageSelectedEvent;

    private List<Page> mCurrentPageSequence;
    private String mAccountOptions;
    private String mCurrencyCode;


    private final ActivityResultLauncher<String> mGetContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                Log.d(LOG_TAG, String.format("mGetContent returns %s.", uri));
                AccountsActivity.importXmlFileFromIntent(this, uri, null);
            });

    public void onCreate(Bundle savedInstanceState) {
        // we need to construct the wizard model before we call super.onCreate, because it's used in
        // onGetPage (which is indirectly called through super.onCreate if savedInstanceState is not
        // null)
        mWizardModel = createWizardModel(savedInstanceState);

        super.onCreate(savedInstanceState);

        ActivityFirstRunWizardBinding binding = ActivityFirstRunWizardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        mPager = binding.pager;
        mNextButton = (AppCompatButton)binding.defaultButtons.btnSave;
        mPrevButton = binding.defaultButtons.btnCancel;
        mStepPagerStrip = binding.strip;

        setTitle(getString(R.string.title_setup_gnucash));

        mPagerAdapter = new MyPagerAdapter(this);
        mPager.setAdapter(mPagerAdapter);
        mStepPagerStrip
                .setOnPageSelectedListener(position -> {
                    position = Math.min(mPagerAdapter.getItemCount() - 1,
                            position);
                    if (mPager.getCurrentItem() != position) {
                        mPager.setCurrentItem(position);
                    }
                });


        mPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                mStepPagerStrip.setCurrentPage(position);

                if (mConsumePageSelectedEvent) {
                    mConsumePageSelectedEvent = false;
                    return;
                }

                mEditingAfterReview = false;
                updateBottomBar();
            }
        });

        mNextButton.setOnClickListener(view -> {
            if (mPager.getCurrentItem() == mCurrentPageSequence.size()) {
                ArrayList<ReviewItem> reviewItems = new ArrayList<>();
                for (Page page : mCurrentPageSequence) {
                    page.getReviewItems(reviewItems);
                }

                mCurrencyCode = GnuCashApplication.getDefaultCurrencyCode();
                mAccountOptions = getString(R.string.wizard_option_let_me_handle_it); //default value, do nothing
                String feedbackOption = getString(R.string.wizard_option_disable_crash_reports);
                for (ReviewItem reviewItem : reviewItems) {
                    String title = reviewItem.getTitle();
                    if (title.equals(getString(R.string.wizard_title_default_currency))) {
                        mCurrencyCode = reviewItem.getDisplayValue();
                    } else if (title.equals(getString(R.string.wizard_title_select_currency))) {
                        mCurrencyCode = reviewItem.getDisplayValue();
                    } else if (title.equals(getString(R.string.wizard_title_account_setup))) {
                        mAccountOptions = reviewItem.getDisplayValue();
                    } else if (title.equals(getString(R.string.wizard_title_feedback_options))) {
                        feedbackOption = reviewItem.getDisplayValue();
                    }
                }

                GnuCashApplication.setDefaultCurrencyCode(mCurrencyCode);
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(FirstRunWizardActivity.this);
                SharedPreferences.Editor preferenceEditor = preferences.edit();

                preferenceEditor.putBoolean(getString(R.string.key_enable_crashlytics), feedbackOption.equals(getString(R.string.wizard_option_auto_send_crash_reports)));
                preferenceEditor.apply();

                createAccountsAndFinish();
            } else {
                if (mEditingAfterReview) {
                    mPager.setCurrentItem(mPagerAdapter.getItemCount() - 1);
                } else {
                    mPager.setCurrentItem(mPager.getCurrentItem() + 1);
                }
            }
        });

        mPrevButton.setText(R.string.wizard_btn_back);
        TypedValue v = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.textAppearanceMedium, v,
                true);
        mPrevButton.setTextAppearance(v.resourceId);
        mNextButton.setTextAppearance(v.resourceId);

        mPrevButton.setOnClickListener(view -> mPager.setCurrentItem(mPager.getCurrentItem() - 1));

        onPageTreeChanged();
        updateBottomBar();
    }

    /**
     * Create the wizard model for the activity, taking into accoun the savedInstanceState if it
     * exists (and if it contains a "model" key that we can use).
     *
     * @param savedInstanceState the instance state available in {{@link #onCreate(Bundle)}}
     * @return an appropriate wizard model for this activity
     */
    private AbstractWizardModel createWizardModel(Bundle savedInstanceState) {
        AbstractWizardModel model = new FirstRunWizardModel(this);
        if (savedInstanceState != null) {
            Bundle wizardModel = savedInstanceState.getBundle("model");
            if (wizardModel != null) {
                model.load(wizardModel);
            }
        }
        model.registerListener(this);
        return model;
    }

    /**
     * Create accounts depending on the user preference (import or default set) and finish this activity
     * <p>This method also removes the first run flag from the application</p>
     */
    private void createAccountsAndFinish() {
        AccountsActivity.removeFirstRunFlag();

        if (mAccountOptions.equals(getString(R.string.wizard_option_create_default_accounts))) {
            //save the UID of the active book, and then delete it after successful import
            String bookUID = BooksDbAdapter.getInstance().getActiveBookUID();
            AccountsActivity.createDefaultAccounts(mCurrencyCode, FirstRunWizardActivity.this);
            BooksDbAdapter.getInstance().deleteBook(bookUID); //a default book is usually created
            finish();
        } else if (mAccountOptions.equals(getString(R.string.wizard_option_import_my_accounts))) {
            mGetContent.launch("*/*");
        } else { //user prefers to handle account creation themselves
            AccountsActivity.start(this);
            finish();
        }
    }

    @Override
    public void onPageTreeChanged() {
        mCurrentPageSequence = mWizardModel.getCurrentPageSequence();
        recalculateCutOffPage();
        mStepPagerStrip.setPageCount(mCurrentPageSequence.size() + 1); // + 1 =
        // review
        // step
        mPagerAdapter.notifyDataSetChanged();
        updateBottomBar();
    }

    private void updateBottomBar() {
        int position = mPager.getCurrentItem();
        if (position == mCurrentPageSequence.size()) {
            mNextButton.setText(R.string.btn_wizard_finish);

            mNextButton.setBackgroundDrawable(
                    new ColorDrawable(ContextCompat.getColor(this, android.R.color.transparent)));
            mNextButton.setTextColor(ContextCompat.getColor(this, R.color.theme_accent));
        } else {
            mNextButton.setText(mEditingAfterReview ? R.string.review
                    : R.string.btn_wizard_next);
            mNextButton.setBackgroundDrawable(
                    new ColorDrawable(ContextCompat.getColor(this, android.R.color.transparent)));
            mNextButton.setTextColor(ContextCompat.getColor(this, R.color.theme_accent));
            mNextButton.setEnabled(position != mPagerAdapter.getCutOffPage());
        }

        mPrevButton
                .setVisibility(position <= 0 ? View.INVISIBLE : View.VISIBLE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == AccountsActivity.REQUEST_PICK_ACCOUNTS_FILE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                AccountsActivity.importXmlFileFromIntent(this, data.getData(), this::finish);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mWizardModel.unregisterListener(this);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBundle("model", mWizardModel.save());
    }

    @Override
    public AbstractWizardModel onGetModel() {
        return mWizardModel;
    }

    @Override
    public void onEditScreenAfterReview(String key) {
        for (int i = mCurrentPageSequence.size() - 1; i >= 0; i--) {
            if (mCurrentPageSequence.get(i).getKey().equals(key)) {
                mConsumePageSelectedEvent = true;
                mEditingAfterReview = true;
                mPager.setCurrentItem(i);
                updateBottomBar();
                break;
            }
        }
    }

    @Override
    public void onPageDataChanged(Page page) {
        if (page.isRequired()) {
            if (recalculateCutOffPage()) {
                mPagerAdapter.notifyDataSetChanged();
                updateBottomBar();
            }
        }
    }

    @Override
    public Page onGetPage(String key) {
        return mWizardModel.findByKey(key);
    }

    private boolean recalculateCutOffPage() {
        // Cut off the pager adapter at first required page that isn't completed
        int cutOffPage = mCurrentPageSequence.size() + 1;
        for (int i = 0; i < mCurrentPageSequence.size(); i++) {
            Page page = mCurrentPageSequence.get(i);
            if (page.isRequired() && !page.isCompleted()) {
                cutOffPage = i;
                break;
            }
        }

        if (mPagerAdapter.getCutOffPage() != cutOffPage) {
            mPagerAdapter.setCutOffPage(cutOffPage);
            return true;
        }

        return false;
    }

    public class MyPagerAdapter extends FragmentStateAdapter {
        private int mCutOffPage;

        public MyPagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        @Override
        @NonNull
        public Fragment createFragment(int position) {
            if (position >= mCurrentPageSequence.size()) {
                return new ReviewFragment();
            }

            return mCurrentPageSequence.get(position).createFragment();
        }

        @Override
        public int getItemCount() {
            return Math.min(mCutOffPage + 1, mCurrentPageSequence == null ? 1
                    : mCurrentPageSequence.size() + 1);
        }


        public void setCutOffPage(int cutOffPage) {
            if (cutOffPage < 0) {
                cutOffPage = Integer.MAX_VALUE;
            }
            mCutOffPage = cutOffPage;
        }

        public int getCutOffPage() {
            return mCutOffPage;
        }
    }
}
