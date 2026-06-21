package com.qiu.aischedule.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.qiu.aischedule.R;
import com.qiu.aischedule.data.local.entity.EventRecord;
import com.qiu.aischedule.data.repository.ScheduleRepository;
import com.qiu.aischedule.network.SchedulePatch;
import com.qiu.aischedule.ui.adapter.EventAdapter;
import com.qiu.aischedule.util.AppExecutors;
import com.qiu.aischedule.util.DateUtils;
import com.qiu.aischedule.util.ScheduleActions;
import com.qiu.aischedule.util.SchedulePatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * 候选选择页：自然语言修改/删除命中多条时，列出真实候选让用户点选目标。
 * <p>入参：候选 id 数组（MainActivity 已用 EventMatcher 算好，<b>不二次匹配</b>）+ 模式(edit/delete)
 * + (edit) patch 的 JSON 字符串 + 原始指令 + historyId。
 * <p>点选后：edit→应用 patch 进 MODE_EDIT；delete→强确认删除。
 */
public class CandidatePickerActivity extends BaseActivity {

    public static final String EXTRA_CANDIDATE_IDS = "extra_candidate_ids";
    public static final String EXTRA_PICKER_MODE = "extra_picker_mode";
    public static final String EXTRA_PATCH_JSON = "extra_patch_json";
    public static final String EXTRA_SOURCE_TEXT = "extra_source_text";
    public static final String EXTRA_HISTORY_ID = "extra_history_id";

    public static final int MODE_EDIT = 0;
    public static final int MODE_DELETE = 1;

    private int mode;
    private String patchJson;
    private String sourceText;
    private long historyId;

    @Override
    protected boolean showUpNavigation() {
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_candidate_picker);

        mode = getIntent().getIntExtra(EXTRA_PICKER_MODE, MODE_EDIT);
        patchJson = getIntent().getStringExtra(EXTRA_PATCH_JSON);
        sourceText = getIntent().getStringExtra(EXTRA_SOURCE_TEXT);
        historyId = getIntent().getLongExtra(EXTRA_HISTORY_ID, -1L);
        final long[] ids = getIntent().getLongArrayExtra(EXTRA_CANDIDATE_IDS);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(mode == MODE_DELETE
                    ? R.string.picker_title_delete : R.string.picker_title_edit);
        }

        TextView tvHint = findViewById(R.id.tvPickerHint);
        tvHint.setText(mode == MODE_DELETE ? R.string.picker_hint_delete : R.string.picker_hint_edit);

        final TextView tvEmpty = findViewById(R.id.tvEmpty);
        RecyclerView rv = findViewById(R.id.rvCandidates);
        rv.setLayoutManager(new LinearLayoutManager(this));
        final EventAdapter adapter = new EventAdapter(this::onItemClick);
        rv.setAdapter(adapter);

        if (ids == null || ids.length == 0) {
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }

        // 按 id 读出候选（不二次匹配，沿用 MainActivity 的匹配结果）
        AppExecutors.getInstance().diskIO().execute(() -> {
            ScheduleRepository repo = ScheduleRepository.getInstance(this);
            List<EventRecord> list = new ArrayList<>();
            for (long id : ids) {
                EventRecord e = repo.getEventSync(id);
                if (e != null) {
                    list.add(e);
                }
            }
            final List<EventRecord> result = list;
            AppExecutors.getInstance().mainThread().execute(() -> {
                adapter.submitList(result);
                tvEmpty.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void onItemClick(EventRecord e) {
        if (mode == MODE_EDIT) {
            SchedulePatch patch = new Gson().fromJson(patchJson, SchedulePatch.class);
            SchedulePatcher.MergedFields f = SchedulePatcher.apply(e, patch);
            Intent intent = new Intent(this, AiConfirmActivity.class);
            intent.putExtra(AiConfirmActivity.EXTRA_MODE, AiConfirmActivity.MODE_EDIT);
            intent.putExtra(AiConfirmActivity.EXTRA_EVENT_ID, e.id);
            intent.putExtra(AiConfirmActivity.EXTRA_SOURCE_TEXT, sourceText);
            intent.putExtra(AiConfirmActivity.EXTRA_TITLE, f.title);
            intent.putExtra(AiConfirmActivity.EXTRA_DATE, DateUtils.formatDate(f.dateMillis));
            intent.putExtra(AiConfirmActivity.EXTRA_TIME,
                    DateUtils.formatTime(DateUtils.combine(f.dateMillis, f.hour, f.minute)));
            intent.putExtra(AiConfirmActivity.EXTRA_LOCATION, f.location);
            intent.putExtra(AiConfirmActivity.EXTRA_REMINDER, f.reminder);
            intent.putExtra(AiConfirmActivity.EXTRA_HISTORY_ID, historyId);
            startActivity(intent);
            finish();
        } else {
            // 删除确认后关闭候选页，回到来源页
            ScheduleActions.confirmDelete(this, e, historyId, this::finish);
        }
    }
}
