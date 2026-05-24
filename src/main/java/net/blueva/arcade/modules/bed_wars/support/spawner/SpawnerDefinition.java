package net.blueva.arcade.modules.bed_wars.support.spawner;

import org.bukkit.Location;




public class SpawnerDefinition {

    private final String spawnerId;
    private final SpawnerType type;
    private final Location location;
    private final int intervalTicks;
    private final boolean hologramEnabled;
    private String teamId;

    public SpawnerDefinition(String spawnerId, SpawnerType type, Location location,
                             int intervalTicks, boolean hologramEnabled, String teamId) {
        this.spawnerId = spawnerId;
        this.type = type;
        this.location = location;
        this.intervalTicks = intervalTicks;
        this.hologramEnabled = hologramEnabled;
        this.teamId = teamId;
    }

    public SpawnerDefinition(String spawnerId, SpawnerType type, Location location,
                             int intervalTicks, boolean hologramEnabled) {
        this(spawnerId, type, location, intervalTicks, hologramEnabled, null);
    }

    public String getSpawnerId() {
        return spawnerId;
    }

    public String getKey() {
        return spawnerId.toLowerCase();
    }

    public SpawnerType getType() {
        return type;
    }

    public Location getLocation() {
        return location;
    }

    public int getIntervalTicks() {
        return intervalTicks;
    }

    public boolean isHologramEnabled() {
        return hologramEnabled;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }
}
