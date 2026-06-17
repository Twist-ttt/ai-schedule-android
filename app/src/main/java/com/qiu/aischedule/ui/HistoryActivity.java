package com.qiu.aischedule.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.qiu.aischedule.R;
import com.qiu.aischedule.data.repository.ScheduleRepository;
import com.qiu.aischedule.ui.adapter.HistoryAdapter;

/**
 * 解析历史页：第二个 RecyclerView，展示每次"原句 → AI 返回 JSON"。
 * 数据来自 Room LiveData，自动刷新。
 */
public class HistoryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        RecyclerView rv = findViewById(R.id.rvHistory);
        rv.setLayoutManager(new LinearLayoutManager(this));
        HistoryAdapter adapter = new HistoryAdapter();
        rv.setAdapter(adapter);

        TextView tvEmpty = findViewById(R.id.tvEmpty);

        ScheduleRepository.getInstance(this).getHistory().observe(this, list -> {
            adapter.submitList(list);
            tvEmpty.setVisibility((list == null || list.isEmpty()) ? View.VISIBLE : View.GONE);
        });
    }
}
