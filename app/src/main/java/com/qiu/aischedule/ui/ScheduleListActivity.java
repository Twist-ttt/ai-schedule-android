package com.qiu.aischedule.ui;

import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.qiu.aischedule.notify.ReminderScheduler;
import com.qiu.aischedule.provider.ScheduleProvider;
import com.qiu.aischedule.ui.adapter.EventAdapter;
import com.qiu.aischedule.util.AppExecutors;
import com.qiu.aischedule.util.DateUtils;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 日历看板页（RecyclerView 页）。
 * 阶段 3：数据通过 **ContentProvider** 读取——用 ContentResolver.query 经 URI 获取 Cursor，
 * 再映射为 EventRecord。注册 ContentObserver：Repository 写入并 notifyChange 后自动重新查询刷新。
 * 这样既满足"ContentProvider 通过 URI 访问数据"，又保留"修改/删除后界面刷新"。
 */
public class ScheduleListActivity extends BaseActivity {

    private final List<EventRecord> allEvents = new ArrayList<>();
    private long selectedDayStart;

    private EventAdapter adapter;
    private TextView tvEmpty;
    private ContentObserver observer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule_list);

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

        findViewById(R.id.fabAdd).setOnClickListener(v -> {
            Intent intent = new Intent(this, AiConfirmActivity.class);
            intent.putExtra(AiConfirmActivity.EXTRA_MODE, AiConfirmActivity.MODE_MANUAL);
            startActivity(intent);
        });

        // 通过 ContentProvider 读取
        loadFromProvider();

        // 观察事件变化（Repository 写入后 notifyChange）→ 自动刷新
        observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                loadFromProvider();
            }
        };
        getContentResolver().registerContentObserver(ScheduleProvider.CONTENT_URI_EVENTS, true, observer);
    }

    @Override
    protected void onDestroy() {
        if (observer != null) {
            getContentResolver().unregisterContentObserver(observer);
        }
        super.onDestroy();
    }

    /** 经 ContentResolver 查询 events 表，后台线程读 Cursor，回主线程刷新。 */
    private void loadFromProvider() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            List<EventRecord> list = new ArrayList<>();
            Cursor cursor = null;
            try {
                cursor = getContentResolver().query(
                        ScheduleProvider.CONTENT_URI_EVENTS, null, null, null, null);
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        list.add(fromCursor(cursor));
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            final List<EventRecord> result = list;
            AppExecutors.getInstance().mainThread().execute(() -> {
                allEvents.clear();
                allEvents.addAll(result);
                applyFilter();
            });
        });
    }

    private static EventRecord fromCursor(Cursor c) {
        EventRecord e = new EventRecord();
        e.id = c.getLong(c.getColumnIndexOrThrow("id"));
        e.title = c.getString(c.getColumnIndexOrThrow("title"));
        e.startTime = c.getLong(c.getColumnIndexOrThrow("startTime"));
        e.endTime = c.getLong(c.getColumnIndexOrThrow("endTime"));
        e.location = c.getString(c.getColumnIndexOrThrow("location"));
        e.reminderMinutes = c.getInt(c.getColumnIndexOrThrow("reminderMinutes"));
        e.sourceText = c.getString(c.getColumnIndexOrThrow("sourceText"));
        e.status = c.getString(c.getColumnIndexOrThrow("status"));
        return e;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_test_notify) {
            ReminderScheduler.schedule(this, -1L, System.currentTimeMillis() + 5000);
            Toast.makeText(this, R.string.toast_test_scheduled, Toast.LENGTH_SHORT).show();
            return true;
        }
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
