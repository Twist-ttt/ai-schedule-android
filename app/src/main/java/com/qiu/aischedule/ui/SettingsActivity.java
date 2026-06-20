package com.qiu.aischedule.ui;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qiu.aischedule.R;
import com.qiu.aischedule.data.local.entity.ApiConfig;
import com.qiu.aischedule.data.repository.ScheduleRepository;
import com.qiu.aischedule.security.SecretStore;

/**
 * 设置页：配置 LLM provider / baseUrl / model，以及 API Key。
 * provider/baseUrl/model 存 Room 的 api_config 表；API Key 用 EncryptedSharedPreferences 加密存储。
 */
public class SettingsActivity extends BaseActivity {

    private ScheduleRepository repo;

    private EditText etProvider;
    private EditText etBaseUrl;
    private EditText etModel;
    private EditText etApiKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        repo = ScheduleRepository.getInstance(this);

        etProvider = findViewById(R.id.etProvider);
        etBaseUrl = findViewById(R.id.etBaseUrl);
        etModel = findViewById(R.id.etModel);
        etApiKey = findViewById(R.id.etApiKey);

        // 默认值（DeepSeek）
        etProvider.setText("deepseek");
        etBaseUrl.setText("https://api.deepseek.com");
        etModel.setText("deepseek-chat");
        etApiKey.setText(SecretStore.getApiKey(this));

        // 已有配置则覆盖回填
        repo.observeApiConfig().observe(this, cfg -> {
            if (cfg == null) {
                return;
            }
            if (cfg.provider != null && !cfg.provider.isEmpty()) etProvider.setText(cfg.provider);
            if (cfg.baseUrl != null && !cfg.baseUrl.isEmpty()) etBaseUrl.setText(cfg.baseUrl);
            if (cfg.modelName != null && !cfg.modelName.isEmpty()) etModel.setText(cfg.modelName);
        });

        findViewById(R.id.btnSave).setOnClickListener(v -> {
            ApiConfig config = new ApiConfig();
            config.provider = etProvider.getText().toString().trim();
            config.baseUrl = etBaseUrl.getText().toString().trim();
            config.modelName = etModel.getText().toString().trim();
            config.keyAlias = "api_key";
            repo.saveApiConfig(config);
            SecretStore.setApiKey(this, etApiKey.getText().toString().trim());
            Toast.makeText(this, R.string.toast_config_saved, Toast.LENGTH_LONG).show();
            finish();
        });
    }
}
