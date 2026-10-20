package com.antarmakan.app.ui.merchant;

import android.widget.EditText;
import android.widget.LinearLayout;

import com.antarmakan.app.ui.common.BaseActivity;
import com.antarmakan.app.util.Ui;

/** Pembantu formulir: label + input. */
public abstract class BaseFormActivity extends BaseActivity {

    protected EditText field(LinearLayout parent, String label, String value, EditText input) {
        parent.addView(Ui.label(this, label));
        if (value != null) input.setText(value);
        parent.addView(input);
        return input;
    }
}
