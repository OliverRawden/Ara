package tech.rawden.ara.ai;

/** Active inference tier — light stays hot; heavy loads on demand. */
public enum ModelTier {
    LIGHT("Light – Current", "Light model — fast chat and routing"),
    HEAVY("Heavy – Power", "Heavy model — complex reasoning and code");

    private final String badgeLabel;
    private final String tooltip;

    ModelTier(String badgeLabel, String tooltip) {
        this.badgeLabel = badgeLabel;
        this.tooltip = tooltip;
    }

    public String badgeLabel() {
        return badgeLabel;
    }

    public String tooltip() {
        return tooltip;
    }
}