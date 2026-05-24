package net.blueva.arcade.modules.bed_wars.state;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.modules.bed_wars.support.bed.BedDefinition;
import net.blueva.arcade.modules.bed_wars.support.bed.BedState;
import net.blueva.arcade.modules.bed_wars.support.npc.ShopNpcDefinition;
import net.blueva.arcade.modules.bed_wars.support.spawner.SpawnerDefinition;
import net.blueva.arcade.modules.bed_wars.support.spawner.SpawnerType;
import net.blueva.arcade.api.ui.Hologram;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ArenaState {

    private final GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context;
    private final Map<UUID, Integer> playerKills = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerDeaths = new ConcurrentHashMap<>();
    private final Map<String, Long> chestRefillTimes = new ConcurrentHashMap<>();
    private final Set<String> trackedChestKeys = ConcurrentHashMap.newKeySet();
    private final ArenaBlockData blocks = new ArenaBlockData();


    private final ArenaBedData beds = new ArenaBedData();
    private final ArenaSpawnerData spawners = new ArenaSpawnerData();


    private final Map<String, List<TeamEnchantment>> teamSwordEnchantments = new ConcurrentHashMap<>();
    private final Map<String, List<TeamEnchantment>> teamArmorEnchantments = new ConcurrentHashMap<>();
    private final Map<String, List<TeamEnchantment>> teamBowEnchantments = new ConcurrentHashMap<>();
    private final Map<String, List<TeamPotionEffect>> teamEffects = new ConcurrentHashMap<>();
    private final Map<String, List<TeamPotionEffect>> baseEffects = new ConcurrentHashMap<>();


    private final ArenaNpcData npcs = new ArenaNpcData();


    private final Map<String, Location[]> teamRestrictedZones = new ConcurrentHashMap<>();
    private final Map<String, Location> teamSpawns = new ConcurrentHashMap<>();
    private List<ScheduledEvent> scheduledEvents = new ArrayList<>();
    private int nextEventIndex;

    private UUID winnerId;
    private boolean ended;
    private int supplyTicks;

    private double stormRadius;
    private double stormMaxRadius;
    private double stormFinalRadius;
    private double stormDamagePerSecond;
    private int stormShrinkDurationSeconds;
    private Location stormCenter;
    private int stormLightningTicks;
    private boolean stormActive;
    private int matchSeconds;

    private VoteState voteState;
    private int selectedHearts = 10;
    private String selectedTime = "day";
    private String selectedWeather = "sunny";

    private WorldBorder stormBorder;

    public ArenaState(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        this.context = context;
    }

    public GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> getContext() {
        return context;
    }

    public int getArenaId() {
        return context.getArenaId();
    }

    public void initializePlayer(UUID playerId) {
        playerKills.putIfAbsent(playerId, 0);
        playerDeaths.putIfAbsent(playerId, 0);
    }

    public int addKill(UUID playerId) {
        return playerKills.merge(playerId, 1, Integer::sum);
    }

    public int getKills(UUID playerId) {
        return playerKills.getOrDefault(playerId, 0);
    }

    public int addDeath(UUID playerId) {
        return playerDeaths.merge(playerId, 1, Integer::sum);
    }

    public int getDeaths(UUID playerId) {
        return playerDeaths.getOrDefault(playerId, 0);
    }

    public Map<UUID, Integer> getKillSnapshot() {
        return new ConcurrentHashMap<>(playerKills);
    }

    public boolean markEnded() {
        boolean wasEnded = ended;
        ended = true;
        return wasEnded;
    }

    public boolean isEnded() {
        return ended;
    }

    public void setWinner(UUID winnerId) {
        this.winnerId = winnerId;
    }

    public UUID getWinnerId() {
        return winnerId;
    }

    public int incrementSupplyTicks(int increment) {
        supplyTicks += increment;
        return supplyTicks;
    }

    public void resetSupplyTicks() {
        supplyTicks = 0;
    }

    public boolean markChestRefill(Location location, long nextRefillAt) {
        if (location == null) {
            return false;
        }
        chestRefillTimes.put(toKey(location), nextRefillAt);
        return true;
    }

    public boolean shouldRefillChest(Location location, long now) {
        if (location == null) {
            return false;
        }
        Long nextRefill = chestRefillTimes.get(toKey(location));
        return nextRefill == null || now >= nextRefill;
    }

    public boolean isTrackedChest(Location location) {
        if (location == null) {
            return false;
        }
        return trackedChestKeys.contains(toKey(location));
    }

    public Map<String, Long> getChestRefillTimes() {
        return new ConcurrentHashMap<>(chestRefillTimes);
    }

    public void setStormCenter(Location stormCenter) {
        this.stormCenter = stormCenter;
    }

    public Location getStormCenter() {
        return stormCenter;
    }

    public double getStormRadius() {
        return stormRadius;
    }

    public void setStormRadius(double stormRadius) {
        this.stormRadius = stormRadius;
    }

    public double getStormMaxRadius() {
        return stormMaxRadius;
    }

    public void setStormMaxRadius(double stormMaxRadius) {
        this.stormMaxRadius = stormMaxRadius;
    }

    public double getStormFinalRadius() {
        return stormFinalRadius;
    }

    public void setStormFinalRadius(double stormFinalRadius) {
        this.stormFinalRadius = stormFinalRadius;
    }

    public double getStormDamagePerSecond() {
        return stormDamagePerSecond;
    }

    public void setStormDamagePerSecond(double stormDamagePerSecond) {
        this.stormDamagePerSecond = stormDamagePerSecond;
    }

    public int getStormShrinkDurationSeconds() {
        return stormShrinkDurationSeconds;
    }

    public void setStormShrinkDurationSeconds(int stormShrinkDurationSeconds) {
        this.stormShrinkDurationSeconds = stormShrinkDurationSeconds;
    }

    public int incrementStormLightningTicks() {
        return ++stormLightningTicks;
    }

    public void resetStormLightningTicks() {
        stormLightningTicks = 0;
    }

    public boolean isStormActive() {
        return stormActive;
    }

    public void setStormActive(boolean stormActive) {
        this.stormActive = stormActive;
    }

    public int incrementMatchSeconds() {
        return ++matchSeconds;
    }

    public int getMatchSeconds() {
        return matchSeconds;
    }

    public VoteState getVoteState() {
        return voteState;
    }

    public void setVoteState(VoteState voteState) {
        this.voteState = voteState;
    }

    public int getSelectedHearts() {
        return selectedHearts;
    }

    public void setSelectedHearts(int selectedHearts) {
        if (selectedHearts > 0) {
            this.selectedHearts = selectedHearts;
        }
    }

    public String getSelectedTime() {
        return selectedTime;
    }

    public void setSelectedTime(String selectedTime) {
        if (selectedTime != null) {
            this.selectedTime = selectedTime;
        }
    }

    public String getSelectedWeather() {
        return selectedWeather;
    }

    public void setSelectedWeather(String selectedWeather) {
        if (selectedWeather != null) {
            this.selectedWeather = selectedWeather;
        }
    }

    public void setFallProtection(UUID playerId, long untilMillis) { blocks.setFallProtection(playerId, untilMillis); }

    public boolean hasFallProtection(UUID playerId) { return blocks.hasFallProtection(playerId); }

    public void setScheduledEvents(List<ScheduledEvent> events) {
        if (events == null) {
            scheduledEvents = new ArrayList<>();
            nextEventIndex = 0;
            return;
        }
        scheduledEvents = new ArrayList<>(events);
        scheduledEvents.sort(Comparator.comparingInt(ScheduledEvent::getTriggerSeconds));
        nextEventIndex = 0;
    }

    public List<ScheduledEvent> getScheduledEvents() {
        return List.copyOf(scheduledEvents);
    }

    public ScheduledEvent getNextEvent() {
        if (nextEventIndex < 0 || nextEventIndex >= scheduledEvents.size()) {
            return null;
        }
        return scheduledEvents.get(nextEventIndex);
    }

    public void advanceEvent() {
        nextEventIndex++;
    }

    public int getSecondsUntilNextEvent() {
        ScheduledEvent event = getNextEvent();
        if (event == null) {
            return -1;
        }
        return event.getTriggerSeconds() - matchSeconds;
    }

    public WorldBorder getStormBorder() {
        return stormBorder;
    }

    public void setStormBorder(WorldBorder stormBorder) {
        this.stormBorder = stormBorder;
    }

    public void trackCageBlock(Location location, Material previousMaterial) { blocks.trackCageBlock(location, previousMaterial); }

    public Map<String, Material> getCageBlocks() { return blocks.getCageBlocks(); }

    public void clearCageBlocks() { blocks.clearCageBlocks(); }

    public boolean hasCage(UUID playerId) { return blocks.hasCage(playerId); }

    public void markCageBuilt(UUID playerId) { blocks.markCageBuilt(playerId); }

    public int getCagedPlayerCount() { return blocks.getCagedPlayerCount(); }

    public void clearCagedPlayers() { blocks.clearCagedPlayers(); }

    public boolean isSpawnCaged(Location location) { return blocks.isSpawnCaged(location); }

    public void trackPlacedBlock(Location location) { blocks.trackPlacedBlock(location); }

    public boolean isPlayerPlacedBlock(Location location) { return blocks.isPlayerPlacedBlock(location); }

    public void untrackPlacedBlock(Location location) { blocks.untrackPlacedBlock(location); }

    public void markSpawnCaged(Location location) { blocks.markSpawnCaged(location); }

    public void clearCagedSpawns() { blocks.clearCagedSpawns(); }



    public void setBedDefinitions(List<BedDefinition> definitions) { beds.setDefinitions(definitions); }

    public List<BedDefinition> getBedDefinitions() { return beds.getDefinitions(); }

    public void removeBedDefinition(String teamKey) { beds.removeDefinition(teamKey); }

    public BedState getBedState(String teamKey) { return beds.getState(teamKey); }

    public void setBedState(String teamKey, BedState state) { beds.setState(teamKey, state); }

    public void setBedHologram(String teamKey, Hologram<Location> hologram) { beds.setHologram(teamKey, hologram); }

    public Hologram<Location> getBedHologram(String teamKey) { return beds.getHologram(teamKey); }

    public void removeBedHologram(String teamKey) { beds.removeHologram(teamKey); }

    public Set<String> getBedHologramKeys() { return beds.getHologramKeys(); }



    public void setSpawnerDefinitions(List<SpawnerDefinition> definitions) { spawners.setDefinitions(definitions); }

    public List<SpawnerDefinition> getSpawnerDefinitions() { return spawners.getDefinitions(); }

    public void removeSpawnerDefinition(String spawnerKey) { spawners.removeDefinition(spawnerKey); }

    public int incrementSpawnerTicks(String spawnerKey) { return spawners.incrementTicks(spawnerKey); }

    public void resetSpawnerTicks(String spawnerKey) { spawners.resetTicks(spawnerKey); }

    public void setSpawnerHologram(String spawnerKey, Hologram<Location> hologram) { spawners.setHologram(spawnerKey, hologram); }

    public Hologram<Location> getSpawnerHologram(String spawnerKey) { return spawners.getHologram(spawnerKey); }

    public void removeSpawnerHologram(String spawnerKey) { spawners.removeHologram(spawnerKey); }

    public Set<String> getSpawnerHologramKeys() { return spawners.getHologramKeys(); }

    public void setSpawnerItemStand(String spawnerKey, org.bukkit.entity.ArmorStand stand) { spawners.setItemStand(spawnerKey, stand); }

    public org.bukkit.entity.ArmorStand getSpawnerItemStand(String spawnerKey) { return spawners.getItemStand(spawnerKey); }

    public void removeSpawnerItemStand(String spawnerKey) { spawners.removeItemStand(spawnerKey); }

    public int getSpawnerItemRotation(String spawnerKey) { return spawners.getItemRotation(spawnerKey); }

    public void setSpawnerItemRotation(String spawnerKey, int rotation) { spawners.setItemRotation(spawnerKey, rotation); }

    public Set<String> getSpawnerItemStandKeys() { return spawners.getItemStandKeys(); }

    public int getSpawnerTicks(String spawnerKey) { return spawners.getTicks(spawnerKey); }

    public double getSpawnerMultiplier(String teamId, SpawnerType type) { return spawners.getMultiplier(teamId, type); }

    public void setSpawnerMultiplier(String teamId, SpawnerType type, double multiplier) { spawners.setMultiplier(teamId, type, multiplier); }

    public void setGlobalSpawnerMultiplier(SpawnerType type, double multiplier) { spawners.setGlobalMultiplier(type, multiplier); }

    public boolean markEventFired(int index) { return spawners.markEventFired(index); }

    public boolean isEventFired(int index) { return spawners.isEventFired(index); }



    public void setNpcDefinitions(List<ShopNpcDefinition> definitions) { npcs.setDefinitions(definitions); }

    public List<ShopNpcDefinition> getNpcDefinitions() { return npcs.getDefinitions(); }

    public void setNpcEntity(String npcKey, UUID entityId) { npcs.setEntity(npcKey, entityId); }

    public UUID getNpcEntity(String npcKey) { return npcs.getEntity(npcKey); }

    public void setNpcHologram(String npcKey, Hologram<Location> hologram) { npcs.setHologram(npcKey, hologram); }

    public Hologram<Location> getNpcHologram(String npcKey) { return npcs.getHologram(npcKey); }

    public void removeNpcHologram(String npcKey) { npcs.removeHologram(npcKey); }

    public Set<String> getNpcHologramKeys() { return npcs.getHologramKeys(); }



    public void addTeamRestrictedZone(String teamId, Location min, Location max) {
        if (teamId != null && min != null && max != null) {
            String key = teamId.toLowerCase() + ":" + System.nanoTime();
            teamRestrictedZones.put(key, new Location[]{min, max});
        }
    }

    public boolean isInRestrictedZone(String teamId, Location location) {
        if (teamId == null || location == null) {
            return false;
        }
        String normalizedTeamId = teamId.toLowerCase();
        for (Map.Entry<String, Location[]> entry : teamRestrictedZones.entrySet()) {
            if (!entry.getKey().startsWith(normalizedTeamId + ":")) {
                continue;
            }
            Location[] bounds = entry.getValue();
            if (isInsideBounds(location, bounds[0], bounds[1])) {
                return true;
            }
        }
        return false;
    }

    private boolean isInsideBounds(Location loc, Location min, Location max) {
        if (loc == null || min == null || max == null) {
            return false;
        }
        if (loc.getWorld() != null && min.getWorld() != null && !loc.getWorld().equals(min.getWorld())) {
            return false;
        }
        if (loc.getWorld() != null && max.getWorld() != null && !loc.getWorld().equals(max.getWorld())) {
            return false;
        }
        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int minY = Math.min(min.getBlockY(), max.getBlockY());
        int maxY = Math.max(min.getBlockY(), max.getBlockY());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());
        return loc.getBlockX() >= minX && loc.getBlockX() <= maxX
                && loc.getBlockY() >= minY && loc.getBlockY() <= maxY
                && loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ;
    }

    public void setTeamSpawn(String teamId, Location location) {
        if (teamId != null && location != null) {
            teamSpawns.put(teamId.toLowerCase(), location);
        }
    }

    public Location getTeamSpawn(String teamId) {
        if (teamId == null) {
            return null;
        }
        return teamSpawns.get(teamId.toLowerCase());
    }

    public Map<String, Location> getTeamSpawns() {
        return new ConcurrentHashMap<>(teamSpawns);
    }

    private String toKey(Location location) {
        return location.getWorld().getName() + ":" +
                location.getBlockX() + ":" +
                location.getBlockY() + ":" +
                location.getBlockZ();
    }

    public boolean hasPermanentItem(UUID playerId, String itemId) { return blocks.hasPermanentItem(playerId, itemId); }

    public void markPermanentItem(UUID playerId, String itemId) { blocks.markPermanentItem(playerId, itemId); }

    public static class ScheduledEvent {
        private final int triggerSeconds;
        private final String type;
        private final String label;
        private final double multiplier;

        public ScheduledEvent(int triggerSeconds, String type, String label) {
            this(triggerSeconds, type, label, 1.0);
        }

        public ScheduledEvent(int triggerSeconds, String type, String label, double multiplier) {
            this.triggerSeconds = triggerSeconds;
            this.type = type;
            this.label = label;
            this.multiplier = multiplier;
        }

        public int getTriggerSeconds() {
            return triggerSeconds;
        }

        public String getType() {
            return type;
        }

        public String getLabel() {
            return label;
        }

        public double getMultiplier() {
            return multiplier;
        }
    }



    public List<TeamEnchantment> getTeamSwordEnchantments(String teamId) {
        return teamSwordEnchantments.computeIfAbsent(teamId.toLowerCase(), k -> new ArrayList<>());
    }

    public List<TeamEnchantment> getTeamArmorEnchantments(String teamId) {
        return teamArmorEnchantments.computeIfAbsent(teamId.toLowerCase(), k -> new ArrayList<>());
    }

    public List<TeamEnchantment> getTeamBowEnchantments(String teamId) {
        return teamBowEnchantments.computeIfAbsent(teamId.toLowerCase(), k -> new ArrayList<>());
    }

    public List<TeamPotionEffect> getTeamEffects(String teamId) {
        return teamEffects.computeIfAbsent(teamId.toLowerCase(), k -> new ArrayList<>());
    }

    public List<TeamPotionEffect> getBaseEffects(String teamId) {
        return baseEffects.computeIfAbsent(teamId.toLowerCase(), k -> new ArrayList<>());
    }

    public static class TeamEnchantment {
        private final String enchantmentName;
        private final int level;

        public TeamEnchantment(String enchantmentName, int level) {
            this.enchantmentName = enchantmentName;
            this.level = level;
        }

        public String getEnchantmentName() { return enchantmentName; }
        public int getLevel() { return level; }
    }

    public static class TeamPotionEffect {
        private final String typeName;
        private final int duration;
        private final int amplifier;

        public TeamPotionEffect(String typeName, int duration, int amplifier) {
            this.typeName = typeName;
            this.duration = duration;
            this.amplifier = amplifier;
        }

        public String getTypeName() { return typeName; }
        public int getDuration() { return duration; }
        public int getAmplifier() { return amplifier; }
    }
}
