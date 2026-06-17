package com.qiu.aischedule.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 统一的线程池入口。
 * - diskIO：单线程，用于 Room 等本地数据库读写（避免在主线程访问数据库）。
 * - mainThread：把回调切回主线程刷新 UI。
 */
public class AppExecutors {

    private static volatile AppExecutors INSTANCE;

    private final ExecutorService diskIO;
    private final Executor mainIO;

    private AppExecutors() {
        this.diskIO = Executors.newSingleThreadExecutor();
        this.mainIO = new MainThreadExecutor();
    }

    public static AppExecutors getInstance() {
        if (INSTANCE == null) {
            synchronized (AppExecutors.class) {
                if (INSTANCE == null) {
                    INSTANCE = new AppExecutors();
                }
            }
        }
        return INSTANCE;
    }

    public ExecutorService diskIO() {
        return diskIO;
    }

    public Executor mainThread() {
        return mainIO;
    }

    private static class MainThreadExecutor implements Executor {
        private final Handler mainHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(Runnable command) {
            mainHandler.post(command);
        }
    }
}
