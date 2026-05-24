package net.blueva.arcade.modules.bed_wars.support.upgrades;

import org.bukkit.Material;

import java.util.List;

public record UpgradeDefinition(String id, Material icon, int amount, String name, List<String> lore, int slot, List<UpgradeTier> tiers) {
}
