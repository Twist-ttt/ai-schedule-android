package com.qiu.aischedule.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.qiu.aischedule.R;
import com.qiu.aischedule.data.local.entity.EventRecord;
import com.qiu.aischedule.util.DateUtils;

/**
 * 日程列表 Adapter（看板页）。
 * 每项展示 标题 / 时间 / 地点 / 提醒 共 4 个字段（满足"列表项≥3 字段"）。
 * 使用 ListAdapter + DiffUtil，数据更新时自动做差异比较与刷新。
 */
public class EventAdapter extends ListAdapter<EventRecord, EventAdapter.EventVH> {

    public interface OnEventClickListener {
        void onClick(EventRecord event);
    }

    private final OnEventClickListener listener;

    public EventAdapter(OnEventClickListener listener) {
        super(new DiffUtil.ItemCallback<EventRecord>() {
            @Override
            public boolean areItemsTheSame(@NonNull EventRecord o, @NonNull EventRecord n) {
                return o.id == n.id;
            }

            @Override
            public boolean areContentsTheSame(@NonNull EventRecord o, @NonNull EventRecord n) {
                return o.id == n.id
                        && strEq(o.title, n.title)
                        && o.startTime == n.startTime
                        && o.endTime == n.endTime
                        && strEq(o.location, n.location)
                        && o.reminderMinutes == n.reminderMinutes;
            }

            private boolean strEq(String a, String b) {
                return a == null ? b == null : a.equals(b);
            }
        });
        this.listener = listener;
    }

    @NonNull
    @Override
    public EventVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_event, parent, false);
        return new EventVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull EventVH holder, int position) {
        EventRecord e = getItem(position);
        Context ctx = holder.itemView.getContext();

        holder.title.setText(e.title);
        holder.time.setText(DateUtils.formatDateTime(e.startTime));
        holder.location.setText(isEmpty(e.location) ? ctx.getString(R.string.event_no_location) : e.location);
        holder.reminder.setText(e.reminderMinutes > 0
                ? (e.reminderMinutes + " " + ctx.getString(R.string.event_reminder_suffix))
                : ctx.getString(R.string.event_no_reminder));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(e);
            }
        });
    }

    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    static class EventVH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView time;
        final TextView location;
        final TextView reminder;

        EventVH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.tvTitle);
            time = v.findViewById(R.id.tvTime);
            location = v.findViewById(R.id.tvLocation);
            reminder = v.findViewById(R.id.tvReminder);
        }
    }
}
