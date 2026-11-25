package io.finett.droidclaw.chat;

import io.finett.droidclaw.model.Effort;
import io.finett.droidclaw.util.SettingsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;

/**
 * Slash commands typed into the chat input instead of a message.
 * Known commands are handled locally and never sent to the LLM;
 * anything else is treated as a normal message.
 */
public final class SlashCommand {

    /** A registered command: name, usage hint, and handler (arg -> reply text). */
    public static final class Command {
        public final String name;
        public final String usage;
        private final BiFunction<String, SettingsManager, String> handler;

        Command(String name, String usage, BiFunction<String, SettingsManager, String> handler) {
            this.name = name;
            this.usage = usage;
            this.handler = handler;
        }

        /** "/name" as shown in hints. */
        public String displayName() {
            return "/" + name;
        }
    }

    private static final List<Command> COMMANDS = new ArrayList<>();

    static {
        register("effort", "set reasoning effort: low|med|high|xhigh|max",
                (arg, settings) -> handleEffort(arg, settings));
    }

    private static void register(String name, String usage,
                                 BiFunction<String, SettingsManager, String> handler) {
        COMMANDS.add(new Command(name, usage, handler));
    }

    /** All registered commands, in display order. */
    public static List<Command> all() {
        return COMMANDS;
    }

    private SlashCommand() {
    }

    /**
     * Try to handle an input starting with "/". Returns the reply text when the
     * input matched a known command, or null when it should be sent to the LLM
     * as a regular message.
     */
    public static String tryHandle(String input, SettingsManager settings) {
        String[] parts = input.trim().split("\\s+", 2);
        String name = parts[0].substring(1).toLowerCase(Locale.ROOT);
        String arg = parts.length > 1 ? parts[1].trim() : "";

        for (Command command : COMMANDS) {
            if (command.name.equals(name)) {
                return command.handler.apply(arg, settings);
            }
        }
        return null;
    }

    private static String handleEffort(String arg, SettingsManager settings) {
        if (arg.isEmpty()) {
            Effort current;
            try {
                current = Effort.fromString(settings.getEffort());
            } catch (IllegalArgumentException e) {
                current = Effort.getDefault();
            }
            return "**Effort:** " + current.persistedName()
                    + " (turn budget " + describeBudget(current) + ")\n"
                    + "Usage: `/effort low|med|high|xhigh|max`";
        }
        try {
            Effort effort = Effort.fromString(arg);
            settings.setEffort(effort.persistedName());
            return "**Effort set to** " + effort.persistedName()
                    + " (turn budget " + describeBudget(effort) + ")";
        } catch (IllegalArgumentException e) {
            return "Unknown effort \"" + arg + "\".\n"
                    + "Usage: `/effort low|med|high|xhigh|max`";
        }
    }

    private static String describeBudget(Effort effort) {
        return effort.isUnlimited() ? "unlimited" : String.valueOf(effort.turnBudget());
    }
}
