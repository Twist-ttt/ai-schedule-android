package com.qiu.aischedule.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * API Key 加密存储：基于 EncryptedSharedPreferences（底层 Android Keystore + AES256）。
 * 明文 Key 不入库、不进 Logcat。
 */
public final class SecretStore {

    private static final String TAG = "SecretStore";
    private static final String FILE_NAME = "ai_schedule_secrets";
    private static final String KEY_API_KEY = "api_key";

    // TEMP(测试用)：DeepSeek 测试 Key。**用完务必删除本常量，并到 DeepSeek 账号注销该 Key。**
    private static final String TEST_API_KEY = "sk-c1cc12e8139e41e48a8be51a8ae4c031";

    private SecretStore() {
    }

    public static String getApiKey(Context context) {
        try {
            String stored = prefs(context).getString(KEY_API_KEY, "");
            if (stored != null && !stored.isEmpty()) {
                return stored;
            }
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "读取 API Key 失败", e);
        }
        // TEMP(测试用)：设置页未填写时，回退到内置测试 Key
        return TEST_API_KEY;
    }

    public static void setApiKey(Context context, String apiKey) {
        try {
            prefs(context).edit().putString(KEY_API_KEY, apiKey == null ? "" : apiKey).apply();
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "保存 API Key 失败", e);
        }
    }

    private static SharedPreferences prefs(Context context) throws GeneralSecurityException, IOException {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
        return EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
    }
}
