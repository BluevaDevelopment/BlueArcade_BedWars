package net.blueva.arcade.modules.bed_wars.support.spawner;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import org.bukkit.Bukkit;
import net.blueva.arcade.api.ui.Hologram;
import net.blueva.arcade.api.ui.HologramAPI;
import net.blueva.arcade.modules.bed_wars.state.ArenaState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.LinkedHashSet;

public class SpawnerService {

    private static final int SPAWNER_SCAN_LIMIT = 128;

    private final ModuleConfigAPI moduleConfig;

    public SpawnerService(ModuleConfigAPI moduleConfig) {
        this.moduleConfig = moduleConfig;
    }

    public List<SpawnerDefinition> loadSpawnerDefinitions(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                                           ArenaState state) {
        List<SpawnerDefinition> definitions = new ArrayList<>();
        if (context == null || context.getDataAccess() == null) {
            return definitions;
        }

        World runtimeWorld = null;
        if (context.getArenaAPI() != null) {
            runtimeWorld = context.getArenaAPI().getWorld();
        }

        String spawnerBase = resolveDataBasePath(context, "spawners");
        String registryRaw = context.getDataAccess().getGameData(spawnerBase.replace(".spawners", "") + ".spawner_registry", String.class);
        if (registryRaw == null || registryRaw.isBlank()) {
            registryRaw = context.getDataAccess().getGameData("game.spawner_registry", String.class);
        }

        Set<String> spawnerIds = new LinkedHashSet<>();
        if (registryRaw != null && !registryRaw.isBlank()) {
            for (String id : registryRaw.split(",")) {
                String trimmed = id.trim();
                if (!trimmed.isEmpty()) {
                    spawnerIds.add(trimmed);
                }
            }
        }

        if (spawnerIds.isEmpty()) {
            for (int i = 1; i <= SPAWNER_SCAN_LIMIT; i++) {
                String path = spawnerBase + "." + i + ".type";
                if (context.getDataAccess().hasGameData(path)) {
                    spawnerIds.add(String.valueOf(i));
                }
            }
        }

        for (String spawnerId : spawnerIds) {
            String basePath = spawnerBase + "." + spawnerId;
            String typeName = context.getDataAccess().getGameData(basePath + ".type", String.class);
            if (typeName == null || typeName.isBlank()) {
                continue;
            }

            SpawnerType type = SpawnerType.fromString(typeName);
            if (type == null) {
                continue;
            }

            Location loc = context.getDataAccess().getGameLocation(basePath + ".location");
            if (loc == null) {
                continue;
            }
            if (runtimeWorld != null) {
                loc.setWorld(runtimeWorld);
            }




            int intervalTicks = getDefaultInterval(type);

            Boolean hologramEnabled = context.getDataAccess().getGameData(basePath + ".hologram", Boolean.class);
            if (hologramEnabled == null) {
                hologramEnabled = getDefaultHologram(type);
            }

            definitions.add(new SpawnerDefinition(spawnerId, type, loc, intervalTicks, hologramEnabled));
        }


        for (SpawnerDefinition def : definitions) {
            if (def.getType() == SpawnerType.IRON || def.getType() == SpawnerType.GOLD) {
                String teamId = resolveTeamForSpawnerByBed(def.getLocation(), state);
                def.setTeamId(teamId);
            }
        }

        return definitions;
    }





    public List<ArenaState.ScheduledEvent> loadGeneratorUpgradeEvents() {
        List<ArenaState.ScheduledEvent> events = new ArrayList<>();
        boolean enabled = moduleConfig.getBoolean("generator_upgrades.enabled", false);
        if (!enabled) {
            return events;
        }

        int i = 1;
        while (true) {
            String base = "generator_upgrades.events." + i;
            int timeSeconds = moduleConfig.getInt(base + ".time_seconds", -1);
            if (timeSeconds < 0) {
                break;
            }
            String type = moduleConfig.getString(base + ".type", null);
            String label = moduleConfig.getString(base + ".label", "Generator Upgrade");
            double multiplier = moduleConfig.getDouble(base + ".multiplier", 2.0);
            if (type != null && !type.isBlank()) {
                events.add(new ArenaState.ScheduledEvent(timeSeconds, type.toLowerCase(Locale.ROOT), label, multiplier));
            }
            i++;
        }

        events.sort((a, b) -> Integer.compare(a.getTriggerSeconds(), b.getTriggerSeconds()));
        return events;
    }

    public void spawnResources(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                               ArenaState state) {
        if (state == null || state.isEnded()) {
            return;
        }

        for (SpawnerDefinition def : state.getSpawnerDefinitions()) {
            int ticks = state.incrementSpawnerTicks(def.getKey());
            double multiplier = state.getSpawnerMultiplier(def.getTeamId(), def.getType());
            int effectiveTicks = (int) Math.max(1, def.getIntervalTicks() / multiplier);
            if (ticks >= effectiveTicks) {
                state.resetSpawnerTicks(def.getKey());
                Location dropLoc = def.getLocation().clone().add(0.5D, 1.0D, 0.5D);
                dropLoc.getWorld().dropItem(dropLoc, new ItemStack(def.getType().getMaterial(), 1));
            }
        }
    }

    public void spawnSpawnerHolograms(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                      ArenaState state) {
        if (state == null || context == null) {
            return;
        }
        HologramAPI<Location> hologramAPI = context.getHologramAPI();
        if (hologramAPI == null) {
            return;
        }

        for (SpawnerDefinition def : state.getSpawnerDefinitions()) {
            if (!def.isHologramEnabled()) {
                continue;
            }
            Location holoLoc = def.getLocation().clone().add(0.5D, 2.5D, 0.5D);
            if (!isChunkLoaded(holoLoc)) {
                continue;
            }

            Hologram<Location> existingHolo = state.getSpawnerHologram(def.getKey());
            if (existingHolo != null) {
                if (isHologramAlive(existingHolo)) {
                    continue;
                }
                state.removeSpawnerHologram(def.getKey());
            }

            List<String> lines = buildSpawnerHologramLines(def, -1, state);
            Hologram<Location> hologram = hologramAPI.spawn(state.getArenaId(), holoLoc, lines);
            if (hologram != null) {
                state.setSpawnerHologram(def.getKey(), hologram);
            }

            if (def.getType() == SpawnerType.DIAMOND || def.getType() == SpawnerType.EMERALD) {
                spawnSpawnerItemStand(def, state);
            }
        }
    }

    private void spawnSpawnerItemStand(SpawnerDefinition def, ArenaState state) {
        ArmorStand existing = state.getSpawnerItemStand(def.getKey());
        if (existing != null) {
            if (isItemStandAlive(existing)) {
                return;
            }
            state.removeSpawnerItemStand(def.getKey());
        }

        Location loc = def.getLocation().clone().add(0.5D, 2.8D, 0.5D);
        if (!isChunkLoaded(loc)) {
            return;
        }
        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.setSmall(true);
        stand.setBasePlate(false);
        stand.setCanPickupItems(false);
        stand.setHelmet(new ItemStack(def.getType() == SpawnerType.DIAMOND ? Material.DIAMOND_BLOCK : Material.EMERALD_BLOCK));
        state.setSpawnerItemStand(def.getKey(), stand);
        state.setSpawnerItemRotation(def.getKey(), 0);
    }

    public void updateSpawnerHolograms(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                       ArenaState state) {
        if (state == null || context == null) {
            return;
        }

        boolean needsRespawn = false;
        for (SpawnerDefinition def : state.getSpawnerDefinitions()) {
            if (!def.isHologramEnabled()) {
                continue;
            }
            Hologram<Location> hologram = state.getSpawnerHologram(def.getKey());
            if (hologram == null || !isHologramAlive(hologram)) {
                needsRespawn = true;
                break;
            }

            double multiplier = state.getSpawnerMultiplier(def.getTeamId(), def.getType());
            int effectiveTicks = (int) Math.max(1, def.getIntervalTicks() / multiplier);
            int currentTicks = state.getSpawnerTicks(def.getKey());
            int remainingTicks = effectiveTicks - currentTicks;
            int remainingSeconds = Math.max(0, (remainingTicks + 19) / 20);

            hologram.setLines(buildSpawnerHologramLines(def, remainingSeconds, state));
        }

        if (needsRespawn) {
            spawnSpawnerHolograms(context, state);
        }
    }

    private boolean isHologramAlive(Hologram<Location> hologram) {
        if (hologram == null) {
            return false;
        }
        UUID id = hologram.getId();
        if (id == null) {
            return false;
        }
        Entity entity = Bukkit.getEntity(id);
        return entity != null && entity.isValid() && !entity.isDead();
    }

    private boolean isItemStandAlive(ArmorStand stand) {
        return stand != null && stand.isValid() && !stand.isDead();
    }

    private boolean isChunkLoaded(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        return loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    private List<String> buildSpawnerHologramLines(SpawnerDefinition def, int remainingSeconds, ArenaState state) {
        List<String> lines = new ArrayList<>();


        String typeLabelKey = "messages.spawner.hologram_" + def.getType().name().toLowerCase(Locale.ROOT);
        String typeLabel = moduleConfig.getTranslation(null, typeLabelKey);
        if (typeLabel == null) {
            typeLabel = moduleConfig.getTranslation(null, "messages.spawner.hologram_label");
        }
        if (typeLabel == null) {
            typeLabel = "<white>{type}</white>";
        }
        typeLabel = typeLabel.replace("{type}", def.getType().getDisplayName());


        double multiplier = state != null ? state.getSpawnerMultiplier(def.getTeamId(), def.getType()) : 1.0;
        String tierTemplate = moduleConfig.getTranslation(null, "messages.spawner.hologram_tier");
        if (tierTemplate == null) {
            tierTemplate = "<yellow>Tier {tier}</yellow>";
        }
        int tier = Math.max(1, (int) Math.round(multiplier));
        String tierLabel = tierTemplate.replace("{tier}", toRomanNumeral(tier));

        String timeUntilNextTier = formatTimeUntilNextTier(def.getType(), state);
        String nextUpgradeLine = null;
        if (timeUntilNextTier != null) {
            String nextUpgradeTemplate = moduleConfig.getTranslation(null, "messages.spawner.hologram_next_upgrade");
            if (nextUpgradeTemplate == null) {
                nextUpgradeTemplate = "<gray>(Next upgrade in <white>{time}</white>)</gray>";
            }
            nextUpgradeLine = nextUpgradeTemplate.replace("{time}", timeUntilNextTier);
        }


        String countdownLine = null;
        if (remainingSeconds >= 0) {
            String countdownTemplate = moduleConfig.getTranslation(null, "messages.spawner.hologram_countdown");
            if (countdownTemplate == null) {
                countdownTemplate = "<gray>Next drop in <white>{seconds}s</white></gray>";
            }
            countdownLine = countdownTemplate.replace("{seconds}", formatCountdownTime(remainingSeconds));
        }

        lines.add(typeLabel);
        if (tierLabel != null) {
            lines.add(tierLabel);
        }
        if (nextUpgradeLine != null) {
            lines.add(nextUpgradeLine);
        }
        if (countdownLine != null) {
            lines.add(countdownLine);
        }

        return lines;
    }

    private String formatTimeUntilNextTier(SpawnerType type, ArenaState state) {
        if (state == null || type == null) {
            return null;
        }
        int matchSeconds = state.getMatchSeconds();
        List<ArenaState.ScheduledEvent> events = state.getScheduledEvents();
        for (int i = 0; i < events.size(); i++) {
            if (state.isEventFired(i)) {
                continue;
            }
            ArenaState.ScheduledEvent event = events.get(i);
            if (event.getTriggerSeconds() <= matchSeconds) {
                continue;
            }
            if (type.name().equalsIgnoreCase(event.getType())) {
                int remainingSeconds = event.getTriggerSeconds() - matchSeconds;
                return formatDuration(remainingSeconds);
            }
        }
        return null;
    }

    private String formatDuration(int seconds) {
        if (seconds < 0) {
            return null;
        }
        int safeSeconds = Math.max(0, seconds);
        return String.format("%02d:%02d", safeSeconds / 60, safeSeconds % 60);
    }

    private String toRomanNumeral(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    public boolean handleSpawnerBlockBreak(ArenaState state, Location blockLocation) {
        if (state == null || blockLocation == null) {
            return false;
        }
        SpawnerDefinition toRemove = null;
        for (SpawnerDefinition def : state.getSpawnerDefinitions()) {
            if (locationsMatch(def.getLocation(), blockLocation)) {
                toRemove = def;
                break;
            }
        }
        if (toRemove != null) {
            state.removeSpawnerDefinition(toRemove.getKey());
            state.removeSpawnerHologram(toRemove.getKey());
            state.removeSpawnerItemStand(toRemove.getKey());
            return true;
        }
        return false;
    }

    public boolean isSpawnerLocation(ArenaState state, Location location) {
        if (state == null || location == null) {
            return false;
        }
        for (SpawnerDefinition def : state.getSpawnerDefinitions()) {
            if (locationsMatch(def.getLocation(), location)) {
                return true;
            }
        }
        return false;
    }

    public void clearSpawnerHolograms(ArenaState state) {
        if (state == null) {
            return;
        }
        for (String key : state.getSpawnerHologramKeys()) {
            state.removeSpawnerHologram(key);
        }
        for (String key : state.getSpawnerItemStandKeys()) {
            state.removeSpawnerItemStand(key);
        }
    }

    private int getDefaultInterval(SpawnerType type) {
        return switch (type) {
            case IRON -> moduleConfig.getInt("spawners.defaults.iron_interval_ticks", 40);
            case GOLD -> moduleConfig.getInt("spawners.defaults.gold_interval_ticks", 160);
            case DIAMOND -> moduleConfig.getInt("spawners.defaults.diamond_interval_ticks", 600);
            case EMERALD -> moduleConfig.getInt("spawners.defaults.emerald_interval_ticks", 1200);
        };
    }

    private boolean getDefaultHologram(SpawnerType type) {
        return switch (type) {
            case IRON, GOLD -> false;
            case DIAMOND, EMERALD -> true;
        };
    }

    private boolean locationsMatch(Location a, Location b) {
        if (a == null || b == null) {
            return false;
        }
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private String resolveDataBasePath(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                       String section) {
        if (context.getDataAccess().hasGameData("game.play_area." + section)) {
            return "game.play_area." + section;
        }
        return "game." + section;
    }

    private String resolveTeamForSpawnerByBed(Location spawnerLoc, ArenaState state) {
        if (spawnerLoc == null || state == null) {
            return null;
        }
        List<net.blueva.arcade.modules.bed_wars.support.bed.BedDefinition> beds = state.getBedDefinitions();
        if (beds == null || beds.isEmpty()) {
            return null;
        }
        String closestTeam = null;
        double closestDistance = Double.MAX_VALUE;
        for (net.blueva.arcade.modules.bed_wars.support.bed.BedDefinition bed : beds) {
            Location bedLoc = bed.getLocation();
            if (bedLoc == null || bedLoc.getWorld() == null || spawnerLoc.getWorld() == null) {
                continue;
            }
            if (!bedLoc.getWorld().equals(spawnerLoc.getWorld())) {
                continue;
            }
            double distance = spawnerLoc.distance(bedLoc);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestTeam = bed.getTeamId();
            }
        }
        return closestTeam;
    }

    private static String formatCountdownTime(int seconds) {
        int safeSeconds = Math.max(0, seconds);
        return String.format("%02d:%02d", safeSeconds / 60, safeSeconds % 60);
    }

}
