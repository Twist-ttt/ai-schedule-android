package com.qiu.aischedule.ui;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.NumberPicker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.qiu.aischedule.R;

import java.util.Locale;

/**
 * 玻璃风格时间选择对话框：时/分两个 NumberPicker 转轮并排，与 {@link GlassDatePickerDialog} 风格一致。
 *
 * 时显示 00-23、分显示 00-59（两位数对齐），均可循环滚动。window 背景在 onStart 置透明，
 * 玻璃外观由布局的 {@code bg_glass_card} 提供；NumberPicker 内部文字色统一为玻璃主色。
 */
public class GlassTimePickerDialog extends DialogFragment {

    /** 时间选中回调。 */
    public interface OnTimePickListener {
        void onTimePicked(int hour, int minute);
    }

    private static final String ARG_HOUR = "hour";
    private static final String ARG_MINUTE = "minute";

    private OnTimePickListener listener;
    private NumberPicker npHour;
    private NumberPicker npMinute;

    public static GlassTimePickerDialog newInstance(int hour, int minute, OnTimePickListener listener) {
        GlassTimePickerDialog d = new GlassTimePickerDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_HOUR, hour);
        args.putInt(ARG_MINUTE, minute);
        d.setArguments(args);
        d.listener = listener;
        return d;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.dialog_time_picker, container, false);

        npHour = v.findViewById(R.id.npHour);
        npMinute = v.findViewById(R.id.npMinute);

        Bundle args = requireArguments();
        int hour = args.getInt(ARG_HOUR);
        int minute = args.getInt(ARG_MINUTE);

        npHour.setMinValue(0);
        npHour.setMaxValue(23);
        npHour.setDisplayedValues(twoDigit(24));
        npHour.setValue(clamp(hour, 0, 23));
        npHour.setWrapSelectorWheel(true);

        npMinute.setMinValue(0);
        npMinute.setMaxValue(59);
        npMinute.setDisplayedValues(twoDigit(60));
        npMinute.setValue(clamp(minute, 0, 59));
        npMinute.setWrapSelectorWheel(true);

        int textColor = ContextCompat.getColor(requireContext(), R.color.glass_text_primary);
        tint(npHour, textColor);
        tint(npMinute, textColor);

        v.findViewById(R.id.btnCancel).setOnClickListener(b -> dismiss());
        v.findViewById(R.id.btnConfirm).setOnClickListener(b -> {
            if (listener != null) {
                listener.onTimePicked(npHour.getValue(), npMinute.getValue());
            }
            dismiss();
        });

        return v;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                // window 背景透明，让布局的玻璃圆角显现
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                // 两列转轮，比日期（三列）略窄
                int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.72);
                window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        }
    }

    /** 生成 ["00","01",...,"(count-1)"] 两位数显示串。 */
    private static String[] twoDigit(int count) {
        String[] arr = new String[count];
        for (int i = 0; i < count; i++) {
            arr[i] = String.format(Locale.US, "%02d", i);
        }
        return arr;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static void tint(NumberPicker np, int color) {
        for (int i = 0; i < np.getChildCount(); i++) {
            View child = np.getChildAt(i);
            if (child instanceof EditText) {
                ((EditText) child).setTextColor(color);
            }
        }
    }
}
