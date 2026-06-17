package com.qiu.aischedule.provider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.qiu.aischedule.data.local.AppDatabase;
import com.qiu.aischedule.data.local.entity.EventRecord;

/**
 * 日程数据的 ContentProvider：以 URI 方式对外暴露 events 表。
 * 实现 query/insert/update/delete 全部 4 项（满足"数据访问功能≥2 项"）。
 * - 列表 URI：content://com.qiu.aischedule.provider/events
 * - 单条 URI：content://com.qiu.aischedule.provider/events/{id}
 * 看板页经 ContentResolver.query 读取；写入经 Repository 后调用 notifyChange 触发刷新。
 * 注意：CP 方法运行在调用方线程，调用方需在非主线程访问（Room 限制）。
 */
public class ScheduleProvider extends ContentProvider {

    public static final String AUTHORITY = "com.qiu.aischedule.provider";
    public static final String PATH_EVENTS = "events";
    public static final Uri CONTENT_URI_EVENTS = Uri.parse("content://" + AUTHORITY + "/" + PATH_EVENTS);

    private static final int CODE_EVENTS_DIR = 1;
    private static final int CODE_EVENTS_ITEM = 2;

    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        MATCHER.addURI(AUTHORITY, PATH_EVENTS, CODE_EVENTS_DIR);
        MATCHER.addURI(AUTHORITY, PATH_EVENTS + "/#", CODE_EVENTS_ITEM);
    }

    private AppDatabase db;

    @Override
    public boolean onCreate() {
        db = AppDatabase.getInstance(getContext());
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        Cursor cursor;
        int code = MATCHER.match(uri);
        if (code == CODE_EVENTS_DIR) {
            cursor = db.eventDao().getAllCursor();
        } else if (code == CODE_EVENTS_ITEM) {
            cursor = db.eventDao().getByIdCursor(ContentUris.parseId(uri));
        } else {
            throw new IllegalArgumentException("未知 URI: " + uri);
        }
        Context ctx = getContext();
        if (ctx != null) {
            cursor.setNotificationUri(ctx.getContentResolver(), CONTENT_URI_EVENTS);
        }
        return cursor;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        if (MATCHER.match(uri) != CODE_EVENTS_DIR) {
            throw new IllegalArgumentException("不支持在该 URI 插入: " + uri);
        }
        long id = db.eventDao().insert(toEventRecord(values));
        Uri newUri = ContentUris.withAppendedId(CONTENT_URI_EVENTS, id);
        notifyChange();
        return newUri;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        if (MATCHER.match(uri) != CODE_EVENTS_ITEM) {
            throw new IllegalArgumentException("更新需指定 id: " + uri);
        }
        long id = ContentUris.parseId(uri);
        EventRecord e = db.eventDao().getByIdSync(id);
        if (e == null) {
            return 0;
        }
        applyValues(e, values);
        int rows = db.eventDao().update(e);
        notifyChange();
        return rows;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        if (MATCHER.match(uri) != CODE_EVENTS_ITEM) {
            throw new IllegalArgumentException("删除需指定 id: " + uri);
        }
        long id = ContentUris.parseId(uri);
        EventRecord e = db.eventDao().getByIdSync(id);
        if (e == null) {
            return 0;
        }
        int rows = db.eventDao().delete(e);
        notifyChange();
        return rows;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        switch (MATCHER.match(uri)) {
            case CODE_EVENTS_DIR:
                return "vnd.android.cursor.dir/vnd." + AUTHORITY + "." + PATH_EVENTS;
            case CODE_EVENTS_ITEM:
                return "vnd.android.cursor.item/vnd." + AUTHORITY + "." + PATH_EVENTS;
            default:
                return null;
        }
    }

    private void notifyChange() {
        Context ctx = getContext();
        if (ctx != null) {
            ctx.getContentResolver().notifyChange(CONTENT_URI_EVENTS, null);
        }
    }

    private static EventRecord toEventRecord(ContentValues cv) {
        EventRecord e = new EventRecord();
        applyValues(e, cv);
        return e;
    }

    private static void applyValues(EventRecord e, ContentValues cv) {
        if (cv == null) {
            return;
        }
        if (cv.containsKey("title")) e.title = cv.getAsString("title");
        if (cv.containsKey("startTime")) e.startTime = cv.getAsLong("startTime");
        if (cv.containsKey("endTime")) e.endTime = cv.getAsLong("endTime");
        if (cv.containsKey("location")) e.location = cv.getAsString("location");
        if (cv.containsKey("reminderMinutes")) e.reminderMinutes = cv.getAsInteger("reminderMinutes");
        if (cv.containsKey("sourceText")) e.sourceText = cv.getAsString("sourceText");
        if (cv.containsKey("status")) e.status = cv.getAsString("status");
    }
}
