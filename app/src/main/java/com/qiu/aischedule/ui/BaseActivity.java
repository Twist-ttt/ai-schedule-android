package com.qiu.aischedule.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

/**
 * 所有 Activity 的基类：统一处理窗口与系统栏 insets。
 *
 * targetSdk 36（API 35+）默认强制 edge-to-edge，且忽略主题里的
 * {@code windowOptOutEdgeToEdgeEnforcement}——导致 ActionBar 标题、表单卡片、日历月份栏
 * 等内容画到状态栏 / 刘海区域下方（设置页 provider 钻进状态栏、看板页 "June 2026" 贴近摄像头）。
 *
 * 这里在运行时显式 {@link WindowCompat#setDecorFitsSystemWindows} 设为 true，
 * 让系统栏保持不透明占位，ActionBar 与页面内容都落在状态栏下方。
 * 该运行时调用是 SDK35+ 下覆盖强制 edge-to-edge 默认值的权威方式（主题属性已失效）。
 * 在 super.onCreate 之后、子类 setContentView 之前执行，保证布局前生效。
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 显式要求内容不侵入系统栏，覆盖 SDK35+ 强制 edge-to-edge 的默认值
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
    }
}
