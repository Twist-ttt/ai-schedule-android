package com.qiu.aischedule.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CalendarView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.qiu.aischedule.R;
import com.qiu.aischedule.data.local.entity.EventRecord;
import com.qiu.aischedule.data.repository.ScheduleRepository;
import com.qiu.aischedule.ui.adapter.EventAdapter;
import com.qiu.aischedule.util.DateUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 日历看板页（RecyclerView 页）。
 * 上方 CalendarView 选日期，下方 RecyclerView 展示当天日程。
 * 数据来自 Room 的 LiveData：observe 全部日程后按所选日期过滤，
 * 数据增删改后自动刷新（满足"修改/删除后界面如何刷新"）。
 */
public class ScheduleListActivity extends AppCompatActivity {

    private ScheduleRepository repo;
    private EventAdapter adapter;
    private final List<EventRecord> allEvents = new ArrayList<>();
    private long selectedDayStart;

    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule_list);

        repo = ScheduleRepository.getInstance(this);
        selectedDayStart = DateUtils.todayStart();

        RecyclerView rv = findViewById(R.id.rvEvents);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EventAdapter(event -> {
            Intent intent = new Intent(this, DetailActivity.class);
            intent.putExtra(DetailActivity.EXTRA_EVENT_ID, event.id);
            startActivity(intent);
        });
        rv.setAdapter(adapter);

        tvEmpty = findViewById(R.id.tvEmpty);

        CalendarView calendarView = findViewById(R.id.calendarView);
        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            Calendar c = Calendar.getInstance();
            c.set(Calendar.YEAR, year);
            c.set(Calendar.MONTH, month);
            c.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            selectedDayStart = c.getTimeInMillis();
            applyFilter();
        });

        findViewById(R.id.fabAdd).setOnClickListener(v ->
                startActivity(new Intent(this, AiConfirmActivity.class)));

        // 关键：observe LiveData，数据变化自动回调刷新
        repo.getEventsAll().observe(this, events -> {
            allEvents.clear();
            if (events != null) {
                allEvents.addAll(events);
            }
            applyFilter();
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_history) {
            startActivity(new Intent(this, HistoryActivity.class));
            return true;
        }
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void applyFilter() {
        long[] range = DateUtils.dayRange(selectedDayStart);
        List<EventRecord> dayList = new ArrayList<>();
        for (EventRecord e : allEvents) {
            if (e.startTime >= range[0] && e.startTime < range[1]) {
                dayList.add(e);
            }
        }
        adapter.submitList(dayList);
        tvEmpty.setVisibility(dayList.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
