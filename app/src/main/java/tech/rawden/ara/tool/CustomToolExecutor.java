package tech.rawden.ara.tool;

import tech.rawden.ara.integration.VexProtocol;
import tech.rawden.ara.integration.VexProtocolCatalog;
import tech.rawden.ara.model.InferenceConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes user-defined Vex ara-tool protocols (non-builtin). Built-in tools 101–106 are handled
 * directly in {@link tech.rawden.ara.ui.ChatViewComp}.
 */
public final class CustomToolExecutor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern TEMPLATE = Pattern.compile("\\{\\{([a-zA-Z0-9_]+)}}");

    private static final Set<String> BUILTIN_TOOLS = Set.of(
            "execute_command",
            "get_current_datetime",
            "web_search",
            "read_memory",
            "write_memory",
            "append_memory",
            "query_memory_graph",
            "upsert_memory_entity",
            "link_memory_entities");

    private CustomToolExecutor() {}

    public sealed interface Plan permits Plan.Immediate, Plan.Terminal, Plan.AsyncWeb {
        record Immediate(String result) implements Plan {}

        record Terminal(String command) implements Plan {}

        record AsyncWeb(String query) implements Plan {}
    }

    public static boolean isCustomTool(String toolName) {
        if (BUILTIN_TOOLS.contains(toolName)) {
            return false;
        }
        return VexProtocolCatalog.findByAraTool(toolName)
                .filter(p -> !p.builtin())
                .isPresent();
    }

    public static Optional<Plan> plan(ToolCall call, InferenceConfig config) {
        if (BUILTIN_TOOLS.contains(call.name())) {
            return Optional.empty();
        }
        var protocol = VexProtocolCatalog.findByAraTool(call.name());
        if (protocol.isEmpty() || protocol.get().builtin()) {
            return Optional.empty();
        }

        var p = protocol.get();
        var group = p.toolGroup() != null ? p.toolGroup() : "";

        return switch (group) {
            case "terminal" -> terminalPlan(call, config);
            case "web" -> webPlan(call, config);
            case "core" -> Optional.of(new Plan.Immediate(formatCoreResult(p, call)));
            default -> targetPlan(p, call, config);
        };
    }

    private static Optional<Plan> terminalPlan(ToolCall call, InferenceConfig config) {
        if (!config.terminalEnabled()) {
            return Optional.of(new Plan.Immediate("Terminal execution is disabled in Privacy & Security settings."));
        }
        var cmd = firstStringArg(call, "command");
        if (cmd == null || cmd.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Plan.Terminal(cmd));
    }

    private static Optional<Plan> webPlan(ToolCall call, InferenceConfig config) {
        if (!config.webSearchEnabled()) {
            return Optional.of(new Plan.Immediate("Web search is disabled in Privacy & Security settings."));
        }
        var query = firstStringArg(call, "query");
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Plan.AsyncWeb(query));
    }

    private static Optional<Plan> targetPlan(VexProtocol protocol, ToolCall call, InferenceConfig config) {
        if (protocol.target() == null || protocol.target().isBlank()) {
            return Optional.of(new Plan.Immediate(protocol.name() + " result:\n" + call.arguments()));
        }
        if (!config.terminalEnabled()) {
            return Optional.of(new Plan.Immediate("Terminal execution is disabled in Privacy & Security settings."));
        }
        var cmd = substituteTemplate(protocol.target(), call.arguments());
        if (cmd.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Plan.Terminal(cmd));
    }

    private static String formatCoreResult(VexProtocol protocol, ToolCall call) {
        return protocol.name() + " (" + call.name() + "):\n" + call.arguments();
    }

    private static String firstStringArg(ToolCall call, String key) {
        try {
            JsonNode args = MAPPER.readTree(call.arguments());
            if (args.has(key) && !args.get(key).isNull()) {
                return args.get(key).asText();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    static String substituteTemplate(String template, String argumentsJson) {
        try {
            JsonNode args = MAPPER.readTree(argumentsJson);
            var matcher = TEMPLATE.matcher(template);
            var sb = new StringBuilder();
            while (matcher.find()) {
                var key = matcher.group(1);
                var value =
                        args.has(key) && !args.get(key).isNull() ? args.get(key).asText() : "";
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
            }
            matcher.appendTail(sb);
            return sb.toString().strip();
        } catch (Exception e) {
            return template;
        }
    }

    public static String formatWebResult(String query, String body) {
        return "web_search: " + query + "\n\n" + body;
    }

    public static String formatTerminalResult(TerminalExecutor.CommandResult result) {
        var output = new StringBuilder();
        if (!result.stdout().isEmpty()) {
            output.append(result.stdout());
        }
        if (!result.stderr().isEmpty()) {
            if (!output.isEmpty()) {
                output.append("\n");
            }
            output.append(result.stderr());
        }
        if (result.exitCode() != 0) {
            if (!output.isEmpty()) {
                output.append("\n");
            }
            output.append("exit code: ").append(result.exitCode());
        }
        if (result.timedOut()) {
            if (!output.isEmpty()) {
                output.append("\n");
            }
            output.append("[command timed out]");
        }
        return output.isEmpty() ? "(no output)" : output.toString();
    }
}
