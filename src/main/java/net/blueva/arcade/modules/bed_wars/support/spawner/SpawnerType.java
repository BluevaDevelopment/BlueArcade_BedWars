package net.blueva.arcade.modules.bed_wars.support.spawner;

import org.bukkit.Material;

public enum SpawnerType {
    IRON(Material.IRON_INGOT, "Iron"),
    GOLD(Material.GOLD_INGOT, "Gold"),
    DIAMOND(Material.DIAMOND, "Diamond"),
    EMERALD(Material.EMERALD, "Emerald");

    private final Material material;
    private final String displayName;

    SpawnerType(Material material, String displayName) {
        this.material = material;
        this.displayName = displayName;
    }

    public Material getMaterial() {
        return material;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static SpawnerType fromString(String name) {
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
