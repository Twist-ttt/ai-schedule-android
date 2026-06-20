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

import java.util.Calendar;

/**
 * 玻璃风格日期选择对话框：年/月/日三个 NumberPicker 转轮并排。
 *
 * 相比 MaterialDatePicker，月份不再只能靠箭头一个月一个月翻——三个转轮均可独立滚动，
 * 任意年/月/日都能快捷选中。window 背景在 onStart 置透明，玻璃外观由布局的
 * {@code bg_glass_card} 提供；NumberPicker 内部文字色统一为玻璃主色。
 */
public class GlassDatePickerDialog extends DialogFragment {

    /** 日期选中回调；month 为 0-based（与 {@link java.util.Calendar} 一致）。 */
    public interface OnDatePickListener {
        void onDatePicked(int year, int month0, int day);
    }

    private static final String ARG_YEAR = "year";
    private static final String ARG_MONTH = "month";
    private static final String ARG_DAY = "day";

    private OnDatePickListener listener;
    private NumberPicker npYear;
    private NumberPicker npMonth;
    private NumberPicker npDay;

    public static GlassDatePickerDialog newInstance(long localMidnight, OnDatePickListener listener) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(localMidnight);
        GlassDatePickerDialog d = new GlassDatePickerDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_YEAR, c.get(Calendar.YEAR));
        args.putInt(ARG_MONTH, c.get(Calendar.MONTH));
        args.putInt(ARG_DAY, c.get(Calendar.DAY_OF_MONTH));
        d.setArguments(args);
        d.listener = listener;
        return d;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.dialog_date_picker, container, false);

        npYear = v.findViewById(R.id.npYear);
        npMonth = v.findViewById(R.id.npMonth);
        npDay = v.findViewById(R.id.npDay);

        Bundle args = requireArguments();
        int year = args.getInt(ARG_YEAR);
        int month0 = args.getInt(ARG_MONTH);
        int day = args.getInt(ARG_DAY);

        npYear.setMinValue(2000);
        npYear.setMaxValue(2100);
        npYear.setWrapSelectorWheel(false);
        npYear.setValue(clamp(year, 2000, 2100));

        npMonth.setMinValue(1);
        npMonth.setMaxValue(12);
        npMonth.setValue(clamp(month0 + 1, 1, 12));

        applyDayRange(year, month0, day);

        // NumberPicker 内部是 EditText，需遍历子视图把文字色统一为玻璃主色
        int textColor = ContextCompat.getColor(requireContext(), R.color.glass_text_primary);
        tint(npYear, textColor);
        tint(npMonth, textColor);
        tint(npDay, textColor);

        // 年/月变化 → 重算当月天数，防止「2月30日」
        NumberPicker.OnValueChangeListener dayRangeUpdater = (picker, oldVal, newVal) ->
                applyDayRange(npYear.getValue(), npMonth.getValue() - 1, npDay.getValue());
        npYear.setOnValueChangedListener(dayRangeUpdater);
        npMonth.setOnValueChangedListener(dayRangeUpdater);

        v.findViewById(R.id.btnCancel).setOnClickListener(b -> dismiss());
        v.findViewById(R.id.btnConfirm).setOnClickListener(b -> {
            if (listener != null) {
                listener.onDatePicked(npYear.getValue(), npMonth.getValue() - 1, npDay.getValue());
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
                // 宽度约屏幕 86%，高度自适应
                int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.86);
                window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        }
    }

    /** 设置日转轮范围（按年月计算当月天数），并 clamp 当前值。 */
    private void applyDayRange(int year, int month0, int day) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.YEAR, year);
        c.set(Calendar.MONTH, month0);
        int max = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        npDay.setMinValue(1);
        npDay.setMaxValue(max);
        npDay.setValue(clamp(day, 1, max));
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
