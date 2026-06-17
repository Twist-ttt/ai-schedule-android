package com.qiu.aischedule.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.qiu.aischedule.data.local.AppDatabase;
import com.qiu.aischedule.data.local.dao.ApiConfigDao;
import com.qiu.aischedule.data.local.dao.EventDao;
import com.qiu.aischedule.data.local.dao.HistoryDao;
import com.qiu.aischedule.data.local.entity.ApiConfig;
import com.qiu.aischedule.data.local.entity.EventRecord;
import com.qiu.aischedule.data.local.entity.ParseHistory;
import com.qiu.aischedule.util.AppExecutors;

import java.util.List;

/**
 * 数据层统一入口。UI 只与 Repository 交互，不直接接触 DAO / 数据库。
 * - 读：返回 LiveData，UI 端 observe 自动刷新；
 * - 写：经 {@link AppExecutors#diskIO()} 切到后台线程，避免主线程访问数据库报错；
 * - 同步读：getEventSync/getApiConfigSync 必须在后台线程调用（如通知、详情预取）。
 */
public class ScheduleRepository {

    private static volatile ScheduleRepository INSTANCE;

    private final EventDao eventDao;
    private final HistoryDao historyDao;
    private final ApiConfigDao apiConfigDao;
    private final AppExecutors executors;

    private ScheduleRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.eventDao = db.eventDao();
        this.historyDao = db.historyDao();
        this.apiConfigDao = db.apiConfigDao();
        this.executors = AppExecutors.getInstance();
    }

    public static ScheduleRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (ScheduleRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ScheduleRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    // ---------- 读（LiveData） ----------

    public LiveData<List<EventRecord>> getEventsAll() {
        return eventDao.getAll();
    }

    public LiveData<List<EventRecord>> getEventsByDay(long dayStart, long dayEnd) {
        return eventDao.getByDayRange(dayStart, dayEnd);
    }

    public LiveData<EventRecord> observeEvent(long id) {
        return eventDao.observeById(id);
    }

    public LiveData<List<ParseHistory>> getHistory() {
        return historyDao.getAll();
    }

    public LiveData<ApiConfig> observeApiConfig() {
        return apiConfigDao.observe();
    }

    // ---------- 写（后台线程） ----------

    public void insertEvent(EventRecord event) {
        executors.diskIO().execute(() -> eventDao.insert(event));
    }

    public void updateEvent(EventRecord event) {
        executors.diskIO().execute(() -> eventDao.update(event));
    }

    public void deleteEvent(EventRecord event) {
        executors.diskIO().execute(() -> eventDao.delete(event));
    }

    public void insertHistory(ParseHistory history) {
        executors.diskIO().execute(() -> historyDao.insert(history));
    }

    public void markHistoryApplied(long historyId, boolean applied) {
        executors.diskIO().execute(() -> {
            ParseHistory h = historyDao.getByIdSync(historyId);
            if (h != null) {
                h.isApplied = applied;
                historyDao.update(h);
            }
        });
    }

    public void saveApiConfig(ApiConfig config) {
        long now = System.currentTimeMillis();
        config.updatedAt = now;
        if (config.createdAt == 0) {
            config.createdAt = now;
        }
        executors.diskIO().execute(() -> apiConfigDao.upsert(config));
    }

    // ---------- 同步读（必须在后台线程调用） ----------

    public EventRecord getEventSync(long id) {
        return eventDao.getByIdSync(id);
    }

    public ApiConfig getApiConfigSync() {
        return apiConfigDao.get();
    }

    /** 异步读取单条日程并切回主线程回调。 */
    public void getEvent(long id, final Callback<EventRecord> callback) {
        executors.diskIO().execute(() -> {
            final EventRecord event = eventDao.getByIdSync(id);
            executors.mainThread().execute(() -> callback.onResult(event));
        });
    }

    /** 通用回调接口（避免依赖 java.util.function）。 */
    public interface Callback<T> {
        void onResult(T result);
    }
}
