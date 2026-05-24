package net.blueva.arcade.modules.bed_wars.support.armory;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ArenaEnderChestHolder implements InventoryHolder {

    private final int arenaId;
    private final String teamId;

    public ArenaEnderChestHolder(int arenaId, String teamId) {
        this.arenaId = arenaId;
        this.teamId = teamId;
    }

    public int getArenaId() {
        return arenaId;
    }

    public String getTeamId() {
        return teamId;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
