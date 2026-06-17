package com.qiu.aischedule.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.qiu.aischedule.R;

/**
 * 首页（输入页）。
 * 输入一句话 → 进入确认页（手动填写 / 阶段4 起 AI 解析回填）；也可进入日程看板。
 * 菜单提供"解析历史"与"设置"入口。
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btnToConfirm).setOnClickListener(v -> {
            String text = ((EditText) findViewById(R.id.etInput)).getText().toString().trim();
            Intent intent = new Intent(this, AiConfirmActivity.class);
            intent.putExtra(AiConfirmActivity.EXTRA_SOURCE_TEXT, text);
            startActivity(intent);
        });

        findViewById(R.id.btnToSchedule).setOnClickListener(v ->
                startActivity(new Intent(this, ScheduleListActivity.class)));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_history) {
            startActivity(new Intent(this, HistoryActivity.class));
            return true;
        }
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
