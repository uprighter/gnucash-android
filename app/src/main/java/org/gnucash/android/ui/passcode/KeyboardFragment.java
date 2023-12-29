/*
 * Copyright (c) 2014 Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
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

package org.gnucash.android.ui.passcode;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import org.gnucash.android.databinding.FragmentNumericKeyboardBinding;

/**
 * Soft numeric keyboard for lock screen and passcode preference.
 *
 * @author Oleksandr Tyshkovets <olexandr.tyshkovets@gmail.com>
 */
public class KeyboardFragment extends Fragment {

    private static final int DELAY = 500;

    private TextView pass1;
    private TextView pass2;
    private TextView pass3;
    private TextView pass4;

    private int length = 0;

    public interface OnPasscodeEnteredListener {
        void onPasscodeEntered(String pass);
    }

    private OnPasscodeEnteredListener listener;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        FragmentNumericKeyboardBinding binding = FragmentNumericKeyboardBinding.inflate(inflater, container, false);

        pass1 = binding.passcode1;
        pass2 = binding.passcode2;
        pass3 = binding.passcode3;
        pass4 = binding.passcode4;

        binding.oneBtn.setOnClickListener(v -> add("1"));
        binding.twoBtn.setOnClickListener(v -> add("2"));
        binding.threeBtn.setOnClickListener(v -> add("3"));
        binding.fourBtn.setOnClickListener(v -> add("4"));
        binding.fiveBtn.setOnClickListener(v -> add("5"));
        binding.sixBtn.setOnClickListener(v -> add("6"));
        binding.sevenBtn.setOnClickListener(v -> add("7"));
        binding.eightBtn.setOnClickListener(v -> add("8"));
        binding.nineBtn.setOnClickListener(v -> add("9"));
        binding.zeroBtn.setOnClickListener(v -> add("0"));
        binding.deleteBtn.setOnClickListener(v -> {
            switch (length) {
                case 1 -> {
                    pass1.setText(null);
                    length--;
                }
                case 2 -> {
                    pass2.setText(null);
                    length--;
                }
                case 3 -> {
                    pass3.setText(null);
                    length--;
                }
                case 4 -> {
                    pass4.setText(null);
                    length--;
                }
            }
        });

        return binding.getRoot();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            listener = (OnPasscodeEnteredListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement "
                    + KeyboardFragment.OnPasscodeEnteredListener.class);
        }
    }

    private void add(String num) {
        switch (length + 1) {
            case 1 -> {
                pass1.setText(num);
                length++;
            }
            case 2 -> {
                pass2.setText(num);
                length++;
            }
            case 3 -> {
                pass3.setText(num);
                length++;
            }
            case 4 -> {
                pass4.setText(num);
                length++;
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    listener.onPasscodeEntered(pass1.getText().toString() + pass2.getText()
                            + pass3.getText() + pass4.getText());
                    pass1.setText(null);
                    pass2.setText(null);
                    pass3.setText(null);
                    pass4.setText(null);
                    length = 0;
                }, DELAY);
            }
        }
    }
}
