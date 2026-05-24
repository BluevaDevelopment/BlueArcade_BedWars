package net.blueva.arcade.modules.bed_wars.support.bed;

import org.bukkit.Location;




public class BedDefinition {

    private final String teamId;
    private final Location location;

    public BedDefinition(String teamId, Location location) {
        this.teamId = teamId;
        this.location = location;
    }

    public String getTeamId() {
        return teamId;
    }

    public Location getLocation() {
        return location;
    }

    public String getKey() {
        return teamId.toLowerCase();
    }
}
