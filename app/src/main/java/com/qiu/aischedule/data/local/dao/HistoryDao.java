package com.qiu.aischedule.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.qiu.aischedule.data.local.entity.ParseHistory;

import java.util.List;

/** 解析历史表的数据访问对象。 */
@Dao
public interface HistoryDao {

    @Insert
    long insert(ParseHistory history);

    @Update
    void update(ParseHistory history);

    @Query("SELECT * FROM parse_history ORDER BY createdAt DESC")
    LiveData<List<ParseHistory>> getAll();

    @Query("SELECT * FROM parse_history WHERE id = :id")
    ParseHistory getByIdSync(long id);

    @Query("SELECT COUNT(*) FROM parse_history")
    int count();
}
