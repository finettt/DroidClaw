package io.finett.droidclaw.model;

import java.util.ArrayList;
import java.util.List;

public class Model {
    private String id;
    private String name;
    private String api;
    private boolean reasoning;
    private List<String> input;
    private int contextWindow;
    private int maxTokens;

    public Model() {
        this.input = new ArrayList<>();
    }

    public Model(String id, String name, String api, boolean reasoning, 
                 List<String> input, int contextWindow, int maxTokens) {
        this.id = id;
        this.name = name;
        this.api = api;
        this.reasoning = reasoning;
        this.input = input != null ? new ArrayList<>(input) : new ArrayList<>();
        this.contextWindow = contextWindow;
        this.maxTokens = maxTokens;
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

    public String getApi() {
        return api;
    }

    public void setApi(String api) {
        this.api = api;
    }

    public boolean isReasoning() {
        return reasoning;
    }

    public void setReasoning(boolean reasoning) {
        this.reasoning = reasoning;
    }

    public List<String> getInput() {
        return input;
    }

    public void setInput(List<String> input) {
        this.input = input != null ? new ArrayList<>(input) : new ArrayList<>();
    }

    public int getContextWindow() {
        return contextWindow;
    }

    public void setContextWindow(int contextWindow) {
        this.contextWindow = contextWindow;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public boolean hasTextInput() {
        return input.contains("text");
    }

    public boolean hasImageInput() {
        return input.contains("image");
    }

    public void setTextInput(boolean enabled) {
        if (enabled && !hasTextInput()) {
            input.add("text");
        } else if (!enabled && hasTextInput()) {
            input.remove("text");
        }
    }

    public void setImageInput(boolean enabled) {
        if (enabled && !hasImageInput()) {
            input.add("image");
        } else if (!enabled && hasImageInput()) {
            input.remove("image");
        }
    }
}