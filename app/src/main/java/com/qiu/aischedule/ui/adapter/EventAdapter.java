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
 * 卡片信息优先级：时间 > 标题 > 地点 > 提醒。
 *  - tvTime：左侧锚点，HH:mm 大字（强调色）；
 *  - tvTitle：标题（主文本）；
 *  - tvMeta：地点 · 提前N分钟提醒（复合副行，无提醒时只显地点）。
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

        holder.time.setText(DateUtils.formatTime(e.startTime));   // 左侧锚点：仅 HH:mm
        holder.title.setText(e.title);
        holder.meta.setText(buildMeta(ctx, e));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(e);
            }
        });
    }

    /** 复合副行：地点（无则占位） · 提前N分钟提醒（无提醒则省略）。 */
    private String buildMeta(Context ctx, EventRecord e) {
        StringBuilder sb = new StringBuilder();
        sb.append(isEmpty(e.location) ? ctx.getString(R.string.event_no_location) : e.location);
        if (e.reminderMinutes > 0) {
            sb.append("  ·  ")
                    .append(e.reminderMinutes).append(' ')
                    .append(ctx.getString(R.string.event_reminder_suffix));
        }
        return sb.toString();
    }

    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    static class EventVH extends RecyclerView.ViewHolder {
        final TextView time;
        final TextView title;
        final TextView meta;

        EventVH(@NonNull View v) {
            super(v);
            time = v.findViewById(R.id.tvTime);
            title = v.findViewById(R.id.tvTitle);
            meta = v.findViewById(R.id.tvMeta);
        }
    }
}
