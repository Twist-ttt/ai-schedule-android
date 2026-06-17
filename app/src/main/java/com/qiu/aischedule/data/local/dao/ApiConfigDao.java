package com.qiu.aischedule.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.qiu.aischedule.data.local.entity.ApiConfig;

/** API 配置表的数据访问对象（单行配置，固定 id=1）。 */
@Dao
public interface ApiConfigDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ApiConfig config);

    @Query("SELECT * FROM api_config WHERE id = 1")
    ApiConfig get();

    @Query("SELECT * FROM api_config WHERE id = 1")
    LiveData<ApiConfig> observe();
}
