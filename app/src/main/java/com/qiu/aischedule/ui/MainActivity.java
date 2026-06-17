package com.qiu.aischedule.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.qiu.aischedule.R;

/**
 * 首页（输入页）。
 * 阶段 2：输入一句话 → 进入确认页（手动填写/阶段4 起 AI 解析回填）；
 * 也可直接进入日程看板。
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btnToConfirm).setOnClickListener(v -> {
            String text = ((android.widget.EditText) findViewById(R.id.etInput)).getText().toString().trim();
            Intent intent = new Intent(this, AiConfirmActivity.class);
            intent.putExtra(AiConfirmActivity.EXTRA_SOURCE_TEXT, text);
            startActivity(intent);
        });

        findViewById(R.id.btnToSchedule).setOnClickListener(v ->
                startActivity(new Intent(this, ScheduleListActivity.class)));
    }
}
