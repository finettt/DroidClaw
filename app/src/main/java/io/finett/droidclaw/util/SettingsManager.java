package io.finett.droidclaw.util;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SettingsManager {
    private static final String PREFS_NAME = "droidclaw_settings";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_API_URL = "api_url";
    private static final String KEY_MODEL_NAME = "model_name";
    private static final String KEY_SYSTEM_PROMPT = "system_prompt";
    private static final String KEY_MAX_TOKENS = "max_tokens";
    private static final String KEY_TEMPERATURE = "temperature";

    // Onboarding settings
    private static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";
    private static final String KEY_USER_NAME = "user_name";

    // Multi-model and provider settings
    private static final String KEY_MODELS = "models";
    private static final String KEY_PROVIDERS = "providers";
    private static final String KEY_DEFAULT_MODEL = "default_model";

    // Agent settings keys (DroidClaw is always an agent)
    private static final String KEY_SHELL_ACCESS_ENABLED = "shell_access_enabled";
    private static final String KEY_SANDBOX_MODE = "sandbox_mode";
    private static final String KEY_MAX_AGENT_ITERATIONS = "max_agent_iterations";
    private static final String KEY_REQUIRE_APPROVAL = "require_approval";
    private static final String KEY_SHELL_TIMEOUT_SECONDS = "shell_timeout_seconds";

    private static final String DEFAULT_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL_NAME = "gpt-3.5-turbo";
    private static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant.";
    private static final int DEFAULT_MAX_TOKENS = 1024;
    private static final float DEFAULT_TEMPERATURE = 0.7f;

    // Agent settings defaults (DroidClaw is always an agent)
    private static final boolean DEFAULT_SHELL_ACCESS_ENABLED = false;
    private static final String DEFAULT_SANDBOX_MODE = "strict"; // "strict" or "relaxed"
    private static final int DEFAULT_MAX_AGENT_ITERATIONS = 20;
    private static final boolean DEFAULT_REQUIRE_APPROVAL = true;
    private static final int DEFAULT_SHELL_TIMEOUT_SECONDS = 30;

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
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    // Onboarding methods
    public boolean isOnboardingCompleted() {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false);
    }

    public void setOnboardingCompleted(boolean completed) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply();
    }

    public String getUserName() {
        return prefs.getString(KEY_USER_NAME, "");
    }

    public void setUserName(String userName) {
        prefs.edit().putString(KEY_USER_NAME, userName).apply();
    }

    public void resetOnboarding() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_ONBOARDING_COMPLETED, false);
        editor.apply();
    }

    // Model methods
    public String getDefaultModel() {
        return prefs.getString(KEY_DEFAULT_MODEL, DEFAULT_MODEL_NAME);
    }

    public void setDefaultModel(String modelName) {
        prefs.edit().putString(KEY_DEFAULT_MODEL, modelName).apply();
    }

    public List<ModelConfig> getModels() {
        List<ModelConfig> models = new ArrayList<>();
        String modelsJson = prefs.getString(KEY_MODELS, "");
        if (!modelsJson.isEmpty()) {
            try {
                JSONArray array = new JSONArray(modelsJson);
                for (int i = 0; i < array.length(); i++) {
                    models.add(ModelConfig.fromJson(array.getJSONObject(i)));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return models;
    }

    public void setModels(List<ModelConfig> models) {
        JSONArray array = new JSONArray();
        for (ModelConfig model : models) {
            array.put(model.toJson());
        }
        prefs.edit().putString(KEY_MODELS, array.toString()).apply();
    }

    public void addModel(ModelConfig model) {
        List<ModelConfig> models = getModels();
        models.add(model);
        setModels(models);
    }

    public void removeModel(String modelName) {
        List<ModelConfig> models = getModels();
        models.removeIf(m -> m.getName().equals(modelName));
        setModels(models);
    }

    // Provider methods
    public List<ProviderConfig> getProviders() {
        List<ProviderConfig> providers = new ArrayList<>();
        String providersJson = prefs.getString(KEY_PROVIDERS, "");
        if (!providersJson.isEmpty()) {
            try {
                JSONArray array = new JSONArray(providersJson);
                for (int i = 0; i < array.length(); i++) {
                    providers.add(ProviderConfig.fromJson(array.getJSONObject(i)));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return providers;
    }

    public void setProviders(List<ProviderConfig> providers) {
        JSONArray array = new JSONArray();
        for (ProviderConfig provider : providers) {
            array.put(provider.toJson());
        }
        prefs.edit().putString(KEY_PROVIDERS, array.toString()).apply();
    }

    public void addProvider(ProviderConfig provider) {
        List<ProviderConfig> providers = getProviders();
        providers.add(provider);
        setProviders(providers);
    }

    public void removeProvider(String providerName) {
        List<ProviderConfig> providers = getProviders();
        providers.removeIf(p -> p.getName().equals(providerName));
        setProviders(providers);
    }

    // Agent settings (DroidClaw is always an agent)
    public boolean isShellAccessEnabled() {
        return prefs.getBoolean(KEY_SHELL_ACCESS_ENABLED, DEFAULT_SHELL_ACCESS_ENABLED);
    }
    
    public void setShellAccessEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SHELL_ACCESS_ENABLED, enabled).apply();
    }
    
    public String getSandboxMode() {
        return prefs.getString(KEY_SANDBOX_MODE, DEFAULT_SANDBOX_MODE);
    }
    
    public void setSandboxMode(String mode) {
        prefs.edit().putString(KEY_SANDBOX_MODE, mode).apply();
    }
    
    public int getMaxAgentIterations() {
        return prefs.getInt(KEY_MAX_AGENT_ITERATIONS, DEFAULT_MAX_AGENT_ITERATIONS);
    }
    
    public void setMaxAgentIterations(int iterations) {
        prefs.edit().putInt(KEY_MAX_AGENT_ITERATIONS, iterations).apply();
    }
    
    public boolean isRequireApproval() {
        return prefs.getBoolean(KEY_REQUIRE_APPROVAL, DEFAULT_REQUIRE_APPROVAL);
    }
    
    public void setRequireApproval(boolean require) {
        prefs.edit().putBoolean(KEY_REQUIRE_APPROVAL, require).apply();
    }
    
    public int getShellTimeoutSeconds() {
        return prefs.getInt(KEY_SHELL_TIMEOUT_SECONDS, DEFAULT_SHELL_TIMEOUT_SECONDS);
    }
    
    public void setShellTimeoutSeconds(int seconds) {
        prefs.edit().putInt(KEY_SHELL_TIMEOUT_SECONDS, seconds).apply();
    }

    // ModelConfig inner class
    public static class ModelConfig {
        private String name;
        private String provider;
        private String systemPrompt;
        private int maxTokens;
        private float temperature;

        public ModelConfig(String name, String provider, String systemPrompt, int maxTokens, float temperature) {
            this.name = name;
            this.provider = provider;
            this.systemPrompt = systemPrompt;
            this.maxTokens = maxTokens;
            this.temperature = temperature;
        }

        public String getName() { return name; }
        public String getProvider() { return provider; }
        public String getSystemPrompt() { return systemPrompt; }
        public int getMaxTokens() { return maxTokens; }
        public float getTemperature() { return temperature; }

        public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public void setTemperature(float temperature) { this.temperature = temperature; }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("name", name);
                obj.put("provider", provider);
                obj.put("systemPrompt", systemPrompt);
                obj.put("maxTokens", maxTokens);
                obj.put("temperature", temperature);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return obj;
        }

        public static ModelConfig fromJson(JSONObject obj) {
            try {
                return new ModelConfig(
                    obj.getString("name"),
                    obj.getString("provider"),
                    obj.getString("systemPrompt", ""),
                    obj.getInt("maxTokens", 1024),
                    (float) obj.getDouble("temperature", 0.7)
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    // ProviderConfig inner class
    public static class ProviderConfig {
        private String name;
        private String baseUrl;
        private String apiKey;
        private boolean isPredefined;

        public ProviderConfig(String name, String baseUrl, String apiKey, boolean isPredefined) {
            this.name = name;
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.isPredefined = isPredefined;
        }

        public String getName() { return name; }
        public String getBaseUrl() { return baseUrl; }
        public String getApiKey() { return apiKey; }
        public boolean isPredefined() { return isPredefined; }

        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("name", name);
                obj.put("baseUrl", baseUrl);
                obj.put("apiKey", apiKey);
                obj.put("isPredefined", isPredefined);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return obj;
        }

        public static ProviderConfig fromJson(JSONObject obj) {
            try {
                return new ProviderConfig(
                    obj.getString("name"),
                    obj.getString("baseUrl"),
                    obj.getString("apiKey", ""),
                    obj.getBoolean("isPredefined", false)
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}