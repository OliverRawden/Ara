package tech.rawden.ara.integration;

import tech.rawden.ara.ai.ModelTier;
import tech.rawden.ara.model.ChatSession;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Multi-agent team orchestration from Vex {@code type: team} protocols. Members hand off via
 * shared session context; {@link tech.rawden.ara.ai.ModelRouter} routes sub-tasks by member tier hints.
 */
public final class TeamOrchestrator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern MEMBER_PREFIX =
            Pattern.compile("(?m)^\\s*\\[([a-zA-Z][a-zA-Z0-9_-]*)]\\s*", Pattern.CASE_INSENSITIVE);

    private TeamOrchestrator() {}

    public record TeamMember(String role, ModelTier tier, String focus) {}

    public static boolean isTeam(VexProtocol protocol) {
        return protocol != null && "team".equalsIgnoreCase(protocol.type());
    }

    public static List<TeamMember> parseMembers(VexProtocol team) {
        if (team == null || team.membersJson() == null || team.membersJson().isBlank()) {
            return List.of();
        }
        try {
            JsonNode array = MAPPER.readTree(team.membersJson());
            if (!array.isArray()) {
                return List.of();
            }
            var members = new ArrayList<TeamMember>();
            for (var node : array) {
                var role = text(node, "role", "agent");
                var tier = parseTier(text(node, "tier", "light"));
                var focus = text(node, "focus", "");
                members.add(new TeamMember(role, tier, focus));
            }
            return List.copyOf(members);
        } catch (Exception e) {
            return List.of();
        }
    }

    public static String formatTeamPrompt(VexProtocol team, String handoffContext) {
        var members = parseMembers(team);
        var sb = new StringBuilder();
        sb.append("\n\n## Active agent team: ")
                .append(team.name())
                .append(" (protocol ")
                .append(team.id())
                .append(")\n");
        sb.append("Coordinate as a team. Prefix each reply with [role] matching a member below. ");
        sb.append("Pass findings forward in shared handoff context; prefer memory graph tools for structured facts.\n");
        if (!members.isEmpty()) {
            sb.append("Members:\n");
            for (var member : members) {
                sb.append("- [")
                        .append(member.role())
                        .append("] tier=")
                        .append(member.tier().name().toLowerCase(Locale.ROOT));
                if (!member.focus().isBlank()) {
                    sb.append(" — ").append(member.focus());
                }
                sb.append('\n');
            }
        }
        if (handoffContext != null && !handoffContext.isBlank()) {
            sb.append("\nShared handoff context:\n").append(truncate(handoffContext, 2000)).append('\n');
        }
        return sb.toString();
    }

    public static String wrapUserMessageForTeam(String userMessage, VexProtocol team, ChatSession session) {
        if (team == null || userMessage == null) {
            return userMessage;
        }
        var handoff = session != null ? session.teamHandoffContext() : "";
        var prefix = formatTeamPrompt(team, handoff);
        if (userMessage.isBlank()) {
            return prefix;
        }
        return prefix + "\n\n" + userMessage;
    }

    public static Optional<ModelTier> tierHintFromMessage(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        var matcher = MEMBER_PREFIX.matcher(message);
        if (!matcher.find()) {
            return Optional.empty();
        }
        var role = matcher.group(1).toLowerCase(Locale.ROOT);
        for (var team : VexProtocolCatalog.teams()) {
            for (var member : parseMembers(team)) {
                if (member.role().equalsIgnoreCase(role)) {
                    return Optional.of(member.tier());
                }
            }
        }
        if (role.contains("cod") || role.contains("implement") || role.contains("debug")) {
            return Optional.of(ModelTier.HEAVY);
        }
        if (role.contains("research") || role.contains("search") || role.contains("read")) {
            return Optional.of(ModelTier.LIGHT);
        }
        return Optional.empty();
    }

    public static void appendHandoff(ChatSession session, String role, String snippet) {
        if (session == null || snippet == null || snippet.isBlank()) {
            return;
        }
        var entry = "[" + (role != null ? role : "agent") + "] " + truncate(snippet.strip(), 800);
        var existing = session.teamHandoffContext();
        var combined = existing == null || existing.isBlank() ? entry : existing + "\n" + entry;
        session.setTeamHandoffContext(truncate(combined, 4000));
    }

    public static Optional<VexProtocol> activeTeam(ChatSession session) {
        if (session == null || session.activeTeamId() == null) {
            return Optional.empty();
        }
        return VexProtocolCatalog.findById(session.activeTeamId()).filter(TeamOrchestrator::isTeam);
    }

    private static ModelTier parseTier(String raw) {
        if (raw == null) {
            return ModelTier.LIGHT;
        }
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "heavy", "power", "coder", "code" -> ModelTier.HEAVY;
            default -> ModelTier.LIGHT;
        };
    }

    private static String text(JsonNode node, String field, String fallback) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText(fallback);
        }
        return fallback;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}