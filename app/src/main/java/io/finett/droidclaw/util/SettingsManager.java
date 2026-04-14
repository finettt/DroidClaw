package io.finett.droidclaw.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.finett.droidclaw.model.AgentConfig;
import io.finett.droidclaw.model.Model;
import io.finett.droidclaw.model.Provider;

public class SettingsManager {
    private static final String TAG = "SettingsManager";
    private static final String PREFS_NAME = "droidclaw_settings";
    private static final String KEY_SETTINGS_JSON = "settings_json";

    private final SharedPreferences prefs;
    private final Context context;

    // Cached data
    private Map<String, Provider> providers;
    private AgentConfig agentConfig;
    private boolean onboardingCompleted;
    private String userName;

    public SettingsManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadFromJson();
    }

    // ==================== JSON Serialization ====================

    private void loadFromJson() {
        String json = prefs.getString(KEY_SETTINGS_JSON, null);
        if (json == null) {
            initializeDefaults();
            return;
        }

        try {
            JSONObject root = new JSONObject(json);
            
            // Load providers
            providers = new HashMap<>();
            if (root.has("providers")) {
                JSONObject providersObj = root.getJSONObject("providers");
                Iterator<String> keys = providersObj.keys();
                while (keys.hasNext()) {
                    String providerId = keys.next();
                    JSONObject providerJson = providersObj.getJSONObject(providerId);
                    Provider provider = parseProvider(providerId, providerJson);
                    providers.put(providerId, provider);
                }
            }

            // Load agent config
            if (root.has("agents") && root.getJSONObject("agents").has("defaults")) {
                JSONObject agentJson = root.getJSONObject("agents").getJSONObject("defaults");
                agentConfig = parseAgentConfig(agentJson);
            } else {
                agentConfig = AgentConfig.getDefaults();
            }

            // Load onboarding state
            if (root.has("onboarding")) {
                JSONObject onboardingJson = root.getJSONObject("onboarding");
                onboardingCompleted = onboardingJson.optBoolean("completed", false);
                userName = onboardingJson.optString("userName", "");
            } else {
                onboardingCompleted = false;
                userName = "";
            }

        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse settings JSON", e);
            initializeDefaults();
        }
    }

    private void initializeDefaults() {
        providers = new HashMap<>();
        agentConfig = AgentConfig.getDefaults();
        onboardingCompleted = false;
        userName = "";
    }

    private Provider parseProvider(String id, JSONObject json) throws JSONException {
        Provider provider = new Provider();
        provider.setId(id);
        provider.setName(json.optString("name", ""));
        provider.setBaseUrl(json.optString("baseUrl", ""));
        provider.setApiKey(json.optString("apiKey", ""));
        provider.setApi(json.optString("api", "openai-completions"));

        if (json.has("models")) {
            JSONArray modelsArray = json.getJSONArray("models");
            for (int i = 0; i < modelsArray.length(); i++) {
                JSONObject modelJson = modelsArray.getJSONObject(i);
                Model model = parseModel(modelJson);
                provider.addModel(model);
            }
        }

        return provider;
    }

    private Model parseModel(JSONObject json) throws JSONException {
        Model model = new Model();
        model.setId(json.optString("id", ""));
        model.setName(json.optString("name", ""));
        model.setApi(json.optString("api", "openai-completions"));
        model.setReasoning(json.optBoolean("reasoning", false));
        model.setContextWindow(json.optInt("contextWindow", 4096));
        model.setMaxTokens(json.optInt("maxTokens", 4096));

        List<String> inputTypes = new ArrayList<>();
        if (json.has("input")) {
            JSONArray inputArray = json.getJSONArray("input");
            for (int i = 0; i < inputArray.length(); i++) {
                inputTypes.add(inputArray.getString(i));
            }
        } else {
            inputTypes.add("text");
        }
        model.setInput(inputTypes);

        return model;
    }

    private AgentConfig parseAgentConfig(JSONObject json) throws JSONException {
        AgentConfig config = new AgentConfig();
        config.setDefaultModel(json.optString("model", ""));
        config.setShellAccess(json.optBoolean("shellAccess", false));
        config.setSandboxMode(json.optString("sandboxMode", "strict"));
        config.setMaxIterations(json.optInt("maxIterations", 20));
        config.setRequireApproval(json.optBoolean("requireApproval", true));
        config.setShellTimeout(json.optInt("shellTimeout", 30));
        return config;
    }

    public void saveToJson() {
        try {
            JSONObject root = new JSONObject();

            // Save providers
            JSONObject providersObj = new JSONObject();
            for (Map.Entry<String, Provider> entry : providers.entrySet()) {
                providersObj.put(entry.getKey(), serializeProvider(entry.getValue()));
            }
            root.put("providers", providersObj);

            // Save agent config
            JSONObject agentsObj = new JSONObject();
            agentsObj.put("defaults", serializeAgentConfig(agentConfig));
            root.put("agents", agentsObj);

            // Save onboarding state
            JSONObject onboardingObj = new JSONObject();
            onboardingObj.put("completed", onboardingCompleted);
            onboardingObj.put("userName", userName);
            root.put("onboarding", onboardingObj);

            prefs.edit().putString(KEY_SETTINGS_JSON, root.toString()).apply();
            Log.d(TAG, "Settings saved to JSON");

        } catch (JSONException e) {
            Log.e(TAG, "Failed to save settings JSON", e);
        }
    }

    private JSONObject serializeProvider(Provider provider) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("name", provider.getName());
        json.put("baseUrl", provider.getBaseUrl());
        json.put("apiKey", provider.getApiKey());
        json.put("api", provider.getApi());

        JSONArray modelsArray = new JSONArray();
        for (Model model : provider.getModels()) {
            modelsArray.put(serializeModel(model));
        }
        json.put("models", modelsArray);

        return json;
    }

    private JSONObject serializeModel(Model model) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", model.getId());
        json.put("name", model.getName());
        json.put("api", model.getApi());
        json.put("reasoning", model.isReasoning());
        json.put("contextWindow", model.getContextWindow());
        json.put("maxTokens", model.getMaxTokens());

        JSONArray inputArray = new JSONArray();
        for (String input : model.getInput()) {
            inputArray.put(input);
        }
        json.put("input", inputArray);

        return json;
    }

    private JSONObject serializeAgentConfig(AgentConfig config) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("model", config.getDefaultModel());
        json.put("shellAccess", config.isShellAccess());
        json.put("sandboxMode", config.getSandboxMode());
        json.put("maxIterations", config.getMaxIterations());
        json.put("requireApproval", config.isRequireApproval());
        json.put("shellTimeout", config.getShellTimeout());
        return json;
    }

    // ==================== Provider Management ====================

    public List<Provider> getProviders() {
        return new ArrayList<>(providers.values());
    }

    public Provider getProvider(String id) {
        return providers.get(id);
    }

    public void addProvider(Provider provider) {
        if (provider != null && provider.getId() != null) {
            providers.put(provider.getId(), provider);
            saveToJson();
        }
    }

    public void updateProvider(Provider provider) {
        if (provider != null && provider.getId() != null) {
            providers.put(provider.getId(), provider);
            saveToJson();
        }
    }

    public void deleteProvider(String id) {
        if (id != null && providers.containsKey(id)) {
            providers.remove(id);
            saveToJson();
        }
    }

    public int getProviderCount() {
        return providers.size();
    }

    // ==================== Model Management ====================

    public Model getModel(String providerId, String modelId) {
        Provider provider = providers.get(providerId);
        if (provider != null) {
            return provider.getModelById(modelId);
        }
        return null;
    }

    public void addModel(String providerId, Model model) {
        Provider provider = providers.get(providerId);
        if (provider != null && model != null) {
            provider.addModel(model);
            saveToJson();
        }
    }

    public void updateModel(String providerId, Model model) {
        Provider provider = providers.get(providerId);
        if (provider != null && model != null) {
            // Remove old model and add updated one
            Model existing = provider.getModelById(model.getId());
            if (existing != null) {
                provider.removeModel(existing);
            }
            provider.addModel(model);
            saveToJson();
        }
    }

    public void deleteModel(String providerId, String modelId) {
        Provider provider = providers.get(providerId);
        if (provider != null) {
            Model model = provider.getModelById(modelId);
            if (model != null) {
                provider.removeModel(model);
                saveToJson();
            }
        }
    }

    // ==================== Agent Configuration ====================

    public AgentConfig getAgentConfig() {
        return agentConfig;
    }

    public void setAgentConfig(AgentConfig config) {
        this.agentConfig = config;
        saveToJson();
    }

    // Convenience methods for agent settings
    public String getDefaultModel() {
        return agentConfig.getDefaultModel();
    }

    public void setDefaultModel(String model) {
        agentConfig.setDefaultModel(model);
        saveToJson();
    }

    public boolean isShellAccessEnabled() {
        return agentConfig.isShellAccess();
    }

    public void setShellAccessEnabled(boolean enabled) {
        agentConfig.setShellAccess(enabled);
        saveToJson();
    }

    public String getSandboxMode() {
        return agentConfig.getSandboxMode();
    }

    public void setSandboxMode(String mode) {
        agentConfig.setSandboxMode(mode);
        saveToJson();
    }

    public int getMaxAgentIterations() {
        return agentConfig.getMaxIterations();
    }

    public void setMaxAgentIterations(int iterations) {
        agentConfig.setMaxIterations(iterations);
        saveToJson();
    }

    public boolean isRequireApproval() {
        return agentConfig.isRequireApproval();
    }

    public void setRequireApproval(boolean require) {
        agentConfig.setRequireApproval(require);
        saveToJson();
    }

    public int getShellTimeoutSeconds() {
        return agentConfig.getShellTimeout();
    }

    public void setShellTimeoutSeconds(int seconds) {
        agentConfig.setShellTimeout(seconds);
        saveToJson();
    }

    // ==================== Onboarding ====================

    public boolean isOnboardingCompleted() {
        return onboardingCompleted;
    }

    public void setOnboardingCompleted(boolean completed) {
        this.onboardingCompleted = completed;
        saveToJson();
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String name) {
        this.userName = name;
        saveToJson();
    }

    public void resetOnboarding() {
        onboardingCompleted = false;
        userName = "";
        saveToJson();
    }

    // ==================== Helper Methods ====================

    public boolean isConfigured() {
        // Check if at least one provider with API key exists
        for (Provider provider : providers.values()) {
            if (provider.getApiKey() != null && !provider.getApiKey().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the currently selected provider and model based on agent config.
     * @return Array of [Provider, Model] or null if not configured
     */
    public Object[] getSelectedProviderAndModel() {
        String defaultModel = agentConfig.getDefaultModel();
        if (defaultModel == null || !defaultModel.contains("/")) {
            return null;
        }

        String providerId = agentConfig.getDefaultProviderId();
        String modelId = agentConfig.getDefaultModelId();

        Provider provider = providers.get(providerId);
        if (provider == null) {
            return null;
        }

        Model model = provider.getModelById(modelId);
        if (model == null) {
            return null;
        }

        return new Object[]{provider, model};
    }

    /**
     * Get API URL for current default model.
     */
    public String getApiUrl() {
        Object[] selected = getSelectedProviderAndModel();
        if (selected != null) {
            return ((Provider) selected[0]).getBaseUrl();
        }
        return "";
    }

    /**
     * Get API type for current default model.
     * Returns the API type (e.g., "anthropic", "openai-completions", "openai-responses", "google").
     */
    public String getApiType() {
        Object[] selected = getSelectedProviderAndModel();
        if (selected != null) {
            Model model = (Model) selected[1];
            String modelApi = model.getApi();
            if (modelApi != null && !modelApi.isEmpty()) {
                return modelApi;
            }
            return ((Provider) selected[0]).getApi();
        }
        return "openai-completions";
    }

    /**
     * Get API key for current default model.
     */
    public String getApiKey() {
        Object[] selected = getSelectedProviderAndModel();
        if (selected != null) {
            return ((Provider) selected[0]).getApiKey();
        }
        return "";
    }

    /**
     * Get model name for current default model.
     */
    public String getModelName() {
        Object[] selected = getSelectedProviderAndModel();
        if (selected != null) {
            return ((Model) selected[1]).getId();
        }
        return "";
    }

    /**
     * Get max tokens for current default model.
     */
    public int getMaxTokens() {
        Object[] selected = getSelectedProviderAndModel();
        if (selected != null) {
            return ((Model) selected[1]).getMaxTokens();
        }
        return 4096;
    }

    /**
     * Get all models across all providers as a flat list.
     * Each entry is formatted as "providerId/modelId"
     */
    public List<String> getAllModelReferences() {
        List<String> refs = new ArrayList<>();
        for (Map.Entry<String, Provider> entry : providers.entrySet()) {
            String providerId = entry.getKey();
            for (Model model : entry.getValue().getModels()) {
                refs.add(providerId + "/" + model.getId());
            }
        }
        return refs;
    }

    /**
     * Get display name for a model reference (providerId/modelId)
     */
    public String getModelDisplayName(String modelRef) {
        if (modelRef == null || !modelRef.contains("/")) {
            return modelRef;
        }
        String[] parts = modelRef.split("/", 2);
        Provider provider = providers.get(parts[0]);
        if (provider == null) {
            return modelRef;
        }
        Model model = provider.getModelById(parts[1]);
        if (model == null) {
            return modelRef;
        }
        return provider.getName() + " / " + model.getName();
    }

    // ==================== Setter Methods ====================

    /**
     * Add or update a provider.
     * @param provider The provider to add/update
     */
    public void setProvider(Provider provider) {
        providers.put(provider.getId(), provider);
        saveToJson();
    }

    /**
     * Set model for the default provider in agent config.
     * @param modelRef Model reference in format "providerId/modelId"
     */
    public void setModelName(String modelRef) {
        if (modelRef != null && modelRef.contains("/")) {
            agentConfig.setDefaultModel(modelRef);
            saveToJson();
        }
    }
}