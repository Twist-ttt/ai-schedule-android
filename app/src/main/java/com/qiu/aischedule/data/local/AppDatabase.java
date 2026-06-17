package com.qiu.aischedule.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.qiu.aischedule.data.local.dao.ApiConfigDao;
import com.qiu.aischedule.data.local.dao.EventDao;
import com.qiu.aischedule.data.local.dao.HistoryDao;
import com.qiu.aischedule.data.local.entity.ApiConfig;
import com.qiu.aischedule.data.local.entity.EventRecord;
import com.qiu.aischedule.data.local.entity.ParseHistory;

/**
 * Room 数据库（单例）。聚合三张表与各自 DAO。
 * exportSchema=false：本作业不涉及数据库迁移，不导出 schema 文件。
 */
@Database(
        entities = {EventRecord.class, ParseHistory.class, ApiConfig.class},
        version = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DB_NAME = "ai_schedule.db";
    private static volatile AppDatabase INSTANCE;

    public abstract EventDao eventDao();

    public abstract HistoryDao historyDao();

    public abstract ApiConfigDao apiConfigDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    DB_NAME)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
