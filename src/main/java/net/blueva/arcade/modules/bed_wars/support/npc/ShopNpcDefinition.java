package net.blueva.arcade.modules.bed_wars.support.npc;

import org.bukkit.Location;




public class ShopNpcDefinition {

    private final String npcId;
    private final ShopNpcType type;
    private final Location location;

    public ShopNpcDefinition(String npcId, ShopNpcType type, Location location) {
        this.npcId = npcId;
        this.type = type;
        this.location = location;
    }

    public String getNpcId() {
        return npcId;
    }

    public String getKey() {
        return npcId.toLowerCase();
    }

    public ShopNpcType getType() {
        return type;
    }

    public Location getLocation() {
        return location;
    }
}
