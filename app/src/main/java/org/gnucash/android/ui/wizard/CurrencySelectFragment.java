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

package org.gnucash.android.ui.wizard;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.fragment.app.ListFragment;

import com.tech.freak.wizardpager.ui.PageFragmentCallbacks;

import org.gnucash.android.R;
import org.gnucash.android.databinding.FragmentWizardCurrencySelectPageBinding;
import org.gnucash.android.db.adapter.CommoditiesDbAdapter;
import org.gnucash.android.util.CommoditiesCursorAdapter;

/**
 * Displays a list of all currencies in the database and allows selection of one
 * <p>This fragment is intended for use with the first run wizard</p>
 *
 * @author Ngewi Fet <ngewif@gmail.com>
 * @see CurrencySelectPage
 * @see FirstRunWizardActivity
 * @see FirstRunWizardModel
 */
public class CurrencySelectFragment extends ListFragment {

    private FragmentWizardCurrencySelectPageBinding mBinding;
    private CurrencySelectPage mPage;
    private PageFragmentCallbacks mCallbacks;

    private CommoditiesDbAdapter mCommoditiesDbAdapter;

    String mPageKey;

    public static CurrencySelectFragment newInstance(String key) {
        CurrencySelectFragment fragment = new CurrencySelectFragment();
        fragment.mPageKey = key;
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mBinding = FragmentWizardCurrencySelectPageBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPage = (CurrencySelectPage) mCallbacks.onGetPage(mPageKey);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        CommoditiesCursorAdapter commoditiesCursorAdapter = new CommoditiesCursorAdapter(getActivity(), R.layout.list_item_commodity);
        setListAdapter(commoditiesCursorAdapter);

        getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        mCommoditiesDbAdapter = CommoditiesDbAdapter.getInstance();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (!(context instanceof PageFragmentCallbacks)) {
            throw new ClassCastException("Activity must implement PageFragmentCallbacks");
        }

        mCallbacks = (PageFragmentCallbacks) context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallbacks = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mBinding = null;
    }

    @Override
    public void onListItemClick(@NonNull ListView l, @NonNull View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        String currencyCode = mCommoditiesDbAdapter.getCurrencyCode(mCommoditiesDbAdapter.getUID(id));
        mPage.getData().putString(CurrencySelectPage.CURRENCY_CODE_DATA_KEY, currencyCode);
    }

}
