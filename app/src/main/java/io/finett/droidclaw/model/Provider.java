package io.finett.droidclaw.model;

import java.util.ArrayList;
import java.util.List;

public class Provider {
    private String id;
    private String name;
    private String baseUrl;
    private String apiKey;
    private String api;
    private List<Model> models;

    public Provider() {
        this.models = new ArrayList<>();
    }

    public Provider(String id, String name, String baseUrl, String apiKey, String api) {
        this.id = id;
        this.name = name;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.api = api;
        this.models = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApi() {
        return api;
    }

    public void setApi(String api) {
        this.api = api;
    }

    public List<Model> getModels() {
        return models;
    }

    public void setModels(List<Model> models) {
        this.models = models != null ? new ArrayList<>(models) : new ArrayList<>();
    }

    public void addModel(Model model) {
        if (model != null) {
            this.models.add(model);
        }
    }

    public void removeModel(Model model) {
        if (model != null) {
            this.models.remove(model);
        }
    }

    public Model getModelById(String modelId) {
        if (modelId == null) {
            return null;
        }
        for (Model model : models) {
            if (modelId.equals(model.getId())) {
                return model;
            }
        }
        return null;
    }

    public int getModelCount() {
        return models != null ? models.size() : 0;
    }
}