package net.blueva.arcade.modules.bed_wars.support.npc;

public enum ShopNpcType {
    STORE("Item Shop"),
    UPGRADE("Team Upgrades");

    private final String displayName;

    ShopNpcType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ShopNpcType fromString(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
