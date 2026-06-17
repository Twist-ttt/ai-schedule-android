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
import com.qiu.aischedule.data.local.entity.ParseHistory;
import com.qiu.aischedule.util.DateUtils;

/**
 * 解析历史列表 Adapter（第二个 RecyclerView）。
 * 每项展示 原句 / AI 返回 JSON / 模型·可信度·时间（≥3 字段），
 * 用于回溯与展示 AI 辅助过程。
 */
public class HistoryAdapter extends ListAdapter<ParseHistory, HistoryAdapter.VH> {

    public HistoryAdapter() {
        super(new DiffUtil.ItemCallback<ParseHistory>() {
            @Override
            public boolean areItemsTheSame(@NonNull ParseHistory o, @NonNull ParseHistory n) {
                return o.id == n.id;
            }

            @Override
            public boolean areContentsTheSame(@NonNull ParseHistory o, @NonNull ParseHistory n) {
                return o.id == n.id
                        && eq(o.inputText, n.inputText)
                        && eq(o.jsonResult, n.jsonResult)
                        && o.confidence == n.confidence;
            }

            private boolean eq(String a, String b) {
                return a == null ? b == null : a.equals(b);
            }
        });
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ParseHistory h = getItem(position);
        Context ctx = holder.itemView.getContext();

        holder.input.setText(h.inputText == null ? "" : h.inputText);
        holder.json.setText(h.jsonResult == null ? "" : h.jsonResult);
        int pct = Math.round(h.confidence * 100f);
        holder.meta.setText((h.modelName == null ? "" : h.modelName)
                + "  ·  " + ctx.getString(R.string.history_label_input) + " "
                + DateUtils.formatDateTime(h.createdAt)
                + "  ·  " + pct + "%");
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView input;
        final TextView json;
        final TextView meta;

        VH(@NonNull View v) {
            super(v);
            input = v.findViewById(R.id.tvInput);
            json = v.findViewById(R.id.tvJson);
            meta = v.findViewById(R.id.tvMeta);
        }
    }
}
