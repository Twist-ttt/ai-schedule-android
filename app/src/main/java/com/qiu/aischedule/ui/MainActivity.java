package com.qiu.aischedule.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.qiu.aischedule.R;

/**
 * 首页（输入页）入口 Activity。
 * 当前为阶段 0 的最小可运行占位：展示欢迎信息与"开始使用"按钮。
 * 后续阶段将在此页实现"一句话输入 + 调用 AI 解析"的核心闭环。
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}
