package io.finett.droidclaw.workflow;

/**
 * A node's prompt. Exactly one of {@link Form#TEXT}, {@link Form#TEMPLATE} or
 * {@link Form#FROM_AGENT} must be present; {@code system} is optional and
 * orthogonal to all three.
 *
 * <p>{@code from_agent} is sugar retained for compatibility with the original
 * sketch: {@code {"from_agent": "x"}} is equivalent to
 * {@code {"template": "{{x.output}}"}}. New workflows should prefer
 * {@code template}, because it composes.
 */
public final class WorkflowPrompt {

    public enum Form { TEXT, TEMPLATE, FROM_AGENT }

    private final String system;
    private final Form form;
    private final String value;

    private WorkflowPrompt(String system, Form form, String value) {
        this.system = system;
        this.form = form;
        this.value = value;
    }

    public static WorkflowPrompt text(String system, String text) {
        return new WorkflowPrompt(system, Form.TEXT, text);
    }

    public static WorkflowPrompt template(String system, String template) {
        return new WorkflowPrompt(system, Form.TEMPLATE, template);
    }

    public static WorkflowPrompt fromAgent(String system, String agentKey) {
        return new WorkflowPrompt(system, Form.FROM_AGENT, agentKey);
    }

    public String getSystem() { return system; }
    public Form getForm() { return form; }

    /** The prompt body: literal text, template source, or the referenced agent key. */
    public String getValue() { return value; }

    /** The agent key for {@link Form#FROM_AGENT}, otherwise {@code null}. */
    public String getFromAgent() {
        return form == Form.FROM_AGENT ? value : null;
    }

    public boolean isTemplated() {
        return form == Form.TEMPLATE;
    }

    /**
     * The template source to resolve at runtime. For {@code from_agent} this is
     * the desugared equivalent, so the runner has a single code path.
     */
    public String effectiveTemplate() {
        switch (form) {
            case TEMPLATE:     return value;
            case FROM_AGENT:   return "{{" + value + ".output}}";
            case TEXT:
            default:           return value;
        }
    }
}
