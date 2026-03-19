package io.finett.droidclaw.util;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {
    private static final String PREFS_NAME = "droidclaw_settings";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_API_URL = "api_url";
    private static final String KEY_MODEL_NAME = "model_name";
    private static final String KEY_SYSTEM_PROMPT = "system_prompt";
    private static final String KEY_MAX_TOKENS = "max_tokens";
    private static final String KEY_TEMPERATURE = "temperature";

    private static final String DEFAULT_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL_NAME = "gpt-3.5-turbo";
    private static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant.";
    private static final int DEFAULT_MAX_TOKENS = 1024;
    private static final float DEFAULT_TEMPERATURE = 0.7f;

    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getApiKey() {
        return prefs.getString(KEY_API_KEY, "");
    }

    public void setApiKey(String apiKey) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply();
    }

    public String getApiUrl() {
        return prefs.getString(KEY_API_URL, DEFAULT_API_URL);
    }

    public void setApiUrl(String apiUrl) {
        prefs.edit().putString(KEY_API_URL, apiUrl).apply();
    }

    public String getModelName() {
        return prefs.getString(KEY_MODEL_NAME, DEFAULT_MODEL_NAME);
    }

    public void setModelName(String modelName) {
        prefs.edit().putString(KEY_MODEL_NAME, modelName).apply();
    }

    public String getSystemPrompt() {
        return prefs.getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT);
    }

    public void setSystemPrompt(String systemPrompt) {
        prefs.edit().putString(KEY_SYSTEM_PROMPT, systemPrompt).apply();
    }

    public int getMaxTokens() {
        return prefs.getInt(KEY_MAX_TOKENS, DEFAULT_MAX_TOKENS);
    }

    public void setMaxTokens(int maxTokens) {
        prefs.edit().putInt(KEY_MAX_TOKENS, maxTokens).apply();
    }

    public float getTemperature() {
        return prefs.getFloat(KEY_TEMPERATURE, DEFAULT_TEMPERATURE);
    }

    public void setTemperature(float temperature) {
        prefs.edit().putFloat(KEY_TEMPERATURE, temperature).apply();
    }

    public boolean isConfigured() {
        String apiKey = getApiKey();
        return apiKey != null && !apiKey.isEmpty();
    }
}