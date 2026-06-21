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
import com.qiu.aischedule.provider.ScheduleProvider;
import com.qiu.aischedule.util.AppExecutors;

import java.util.List;

/**
 * 数据层统一入口。UI 只与 Repository 交互，不直接接触 DAO / 数据库 / ContentProvider。
 * - 读：返回 LiveData；
 * - 写：经 {@link AppExecutors#diskIO()} 切到后台线程，并在事件写入后
 *   {@code notifyChange}，使通过 ContentProvider 观察的看板页自动刷新；
 * - 同步读：getEventSync/getApiConfigSync 必须在后台线程调用（如通知、详情预取）。
 */
public class ScheduleRepository {

    private static volatile ScheduleRepository INSTANCE;

    private final Context appContext;
    private final EventDao eventDao;
    private final HistoryDao historyDao;
    private final ApiConfigDao apiConfigDao;
    private final AppExecutors executors;

    private ScheduleRepository(Context context) {
        this.appContext = context.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(appContext);
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

    /** 同步读全部日程（**须后台线程**；供自然语言修改/删除匹配候选）。 */
    public List<EventRecord> getEventsAllSync() {
        return eventDao.getAllSync();
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

    // ---------- 写（后台线程；事件写入后通知 ContentProvider 观察者） ----------

    public void insertEvent(EventRecord event) {
        executors.diskIO().execute(() -> {
            eventDao.insert(event);
            notifyEventsChanged();
        });
    }

    /** 同步插入日程并返回 id（供确认页在后台线程拿到 id 用于设置提醒），须在非主线程调用。 */
    public long insertEventSync(EventRecord event) {
        long id = eventDao.insert(event);
        notifyEventsChanged();
        return id;
    }

    public void updateEvent(EventRecord event) {
        executors.diskIO().execute(() -> {
            eventDao.update(event);
            notifyEventsChanged();
        });
    }

    public void deleteEvent(EventRecord event) {
        executors.diskIO().execute(() -> {
            eventDao.delete(event);
            notifyEventsChanged();
        });
    }

    public void insertHistory(ParseHistory history) {
        executors.diskIO().execute(() -> historyDao.insert(history));
    }

    /** 同步插入解析历史并返回 id（供网络回调在后台线程记录，须在非主线程调用）。 */
    public long insertHistorySync(ParseHistory history) {
        return historyDao.insert(history);
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

    /**
     * 动作落地后回写解析历史：isApplied 与（可选）封装好的 jsonResult（含 meta）。
     * 内部自调度到后台线程，调用方可直接在主线程调用。
     */
    public void completeHistory(long historyId, boolean applied, String jsonResult) {
        executors.diskIO().execute(() -> {
            ParseHistory h = historyDao.getByIdSync(historyId);
            if (h != null) {
                h.isApplied = applied;
                if (jsonResult != null) {
                    h.jsonResult = jsonResult;
                }
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

    private void notifyEventsChanged() {
        appContext.getContentResolver().notifyChange(ScheduleProvider.CONTENT_URI_EVENTS, null);
    }

    /** 通用回调接口（避免依赖 java.util.function）。 */
    public interface Callback<T> {
        void onResult(T result);
    }
}
