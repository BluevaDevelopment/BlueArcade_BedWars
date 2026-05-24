package net.blueva.arcade.modules.bed_wars.support.upgrades;

import org.bukkit.Material;

import java.util.List;

public record UpgradeTier(int tier, int cost, Material currency, List<String> actions) {
}
