package com.qiu.aischedule.data.local.dao;

import android.database.Cursor;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.qiu.aischedule.data.local.entity.EventRecord;

import java.util.List;

/**
 * 日程表的数据访问对象。
 * - 读操作返回 LiveData，详情页 observe 后数据变更自动刷新；
 * - 同步查询 getByIdSync 供 ContentProvider/通知在后台线程使用；
 * - getAllCursor/getByIdCursor 返回 Cursor，供 ContentProvider 直接对外暴露。
 */
@Dao
public interface EventDao {

    @Insert
    long insert(EventRecord event);

    @Update
    int update(EventRecord event);

    @Delete
    int delete(EventRecord event);

    @Query("SELECT * FROM events ORDER BY startTime ASC")
    LiveData<List<EventRecord>> getAll();

    @Query("SELECT * FROM events WHERE startTime >= :dayStart AND startTime < :dayEnd ORDER BY startTime ASC")
    LiveData<List<EventRecord>> getByDayRange(long dayStart, long dayEnd);

    @Query("SELECT * FROM events WHERE id = :id")
    LiveData<EventRecord> observeById(long id);

    @Query("SELECT * FROM events WHERE id = :id")
    EventRecord getByIdSync(long id);

    @Query("SELECT * FROM events ORDER BY startTime ASC")
    Cursor getAllCursor();

    @Query("SELECT * FROM events WHERE id = :id")
    Cursor getByIdCursor(long id);

    @Query("SELECT COUNT(*) FROM events")
    int count();

    @Query("DELETE FROM events")
    void deleteAll();
}
