package com.azeesoft.lib.colorpicker;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

import fragments.tool.Util;
import mt.karimi.ronevis.R;

public class ColorPickerRootView extends RelativeLayout {
    private final int DEFAULT_TEXT_COLOR = Color.parseColor("#222222");
    private final String FLAG_POS_ACTION_TEXT = Util.Persian(R.string.Pick);
    private final String FLAG_NEG_ACTION_TEXT = Util.Persian(R.string.cancel);
    private boolean FLAG_SHOW_HEX = true, FLAG_SHOW_COLOR_COMPS = true, FLAG_EDIT_HSV = true, FLAG_EDIT_RGB = true;
    private int FLAG_HEX_COLOR, FLAG_COMPS_COLOR, FLAG_POSITIVE_COLOR, FLAG_NEGATIVE_COLOR, FLAG_SLIDER_THUMB_COLOR, FLAG_BACKGROUND_COLOR;

    public ColorPickerRootView(Context context) {
        super(context);
    }

    public ColorPickerRootView(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ColorPickerRootView, 0, 0);
        try {
            FLAG_SHOW_HEX = a.getBoolean(R.styleable.ColorPickerRootView_cp_showHexaDecimalValue, true);
            FLAG_SHOW_COLOR_COMPS = a.getBoolean(R.styleable.ColorPickerRootView_cp_showColorComponentsInfo, true);
            FLAG_EDIT_HSV = a.getBoolean(R.styleable.ColorPickerRootView_cp_editHSV, true);
            FLAG_EDIT_RGB = a.getBoolean(R.styleable.ColorPickerRootView_cp_editRGB, true);
            FLAG_HEX_COLOR = a.getColor(R.styleable.ColorPickerRootView_cp_hexaDecimalTextColor, DEFAULT_TEXT_COLOR);
            FLAG_COMPS_COLOR = a.getColor(R.styleable.ColorPickerRootView_cp_colorComponentsTextColor, DEFAULT_TEXT_COLOR);
            FLAG_POSITIVE_COLOR = a.getColor(R.styleable.ColorPickerRootView_cp_positiveActionTextColor, DEFAULT_TEXT_COLOR);
            FLAG_NEGATIVE_COLOR = a.getColor(R.styleable.ColorPickerRootView_cp_negativeActionTextColor, DEFAULT_TEXT_COLOR);
            FLAG_SLIDER_THUMB_COLOR = a.getColor(R.styleable.ColorPickerRootView_cp_sliderThumbColor, Color.parseColor("#333333"));
            FLAG_BACKGROUND_COLOR = a.getColor(R.styleable.ColorPickerRootView_cp_backgroundColor, Color.parseColor("#eeeeee"));
            setBackgroundColor(FLAG_BACKGROUND_COLOR);
        } finally {
            a.recycle();
        }
    }

    public boolean isFLAG_SHOW_HEX() {
        return FLAG_SHOW_HEX;
    }

    public boolean isFLAG_SHOW_COLOR_COMPS() {
        return FLAG_SHOW_COLOR_COMPS;
    }

    public int getFLAG_HEX_COLOR() {
        return FLAG_HEX_COLOR;
    }

    public int getFLAG_COMPS_COLOR() {
        return FLAG_COMPS_COLOR;
    }

    public int getFLAG_POSITIVE_COLOR() {
        return FLAG_POSITIVE_COLOR;
    }

    public int getFLAG_NEGATIVE_COLOR() {
        return FLAG_NEGATIVE_COLOR;
    }

    public int getFLAG_SLIDER_THUMB_COLOR() {
        return FLAG_SLIDER_THUMB_COLOR;
    }

    public String getFLAG_POS_ACTION_TEXT() {
        return FLAG_POS_ACTION_TEXT;
    }

    public String getFLAG_NEG_ACTION_TEXT() {
        return FLAG_NEG_ACTION_TEXT;
    }

    public boolean isFLAG_EDIT_HSV() {
        return FLAG_EDIT_HSV;
    }

    public boolean isFLAG_EDIT_RGB() {
        return FLAG_EDIT_RGB;
    }

    public int getFLAG_BACKGROUND_COLOR() {
        return FLAG_BACKGROUND_COLOR;
    }
}
