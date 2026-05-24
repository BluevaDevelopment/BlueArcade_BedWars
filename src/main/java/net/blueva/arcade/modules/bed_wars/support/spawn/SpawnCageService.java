package net.blueva.arcade.modules.bed_wars.support.spawn;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.store.StoreAPI;
import net.blueva.arcade.api.team.TeamInfo;
import net.blueva.arcade.api.team.TeamsAPI;
import net.blueva.arcade.modules.bed_wars.state.ArenaState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SpawnCageService {

    private final ModuleConfigAPI moduleConfig;
    private final StoreAPI storeAPI;

    public SpawnCageService(ModuleConfigAPI moduleConfig, StoreAPI storeAPI) {
        this.moduleConfig = moduleConfig;
        this.storeAPI = storeAPI;
    }

    public boolean isEnabled() {
        return moduleConfig.getBoolean("cages.enabled", true);
    }

    public void buildCages(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                           ArenaState state) {
        if (!isEnabled() || context == null || state == null) {
            return;
        }

        List<Player> players = context.getPlayers();
        if (players.isEmpty()) {
            return;
        }

        int interiorRadius = 0;
        int wallOffset = interiorRadius + 1;
        int floorOffset = -1;
        int roofOffset = 3;
        int wallTopOffset = 2;
        Map<String, Location> teamSpawnMap = state.getTeamSpawns();
        List<Location> spawns = !teamSpawnMap.isEmpty()
                ? new ArrayList<>(teamSpawnMap.values())
                : (context.getArenaAPI() != null ? context.getArenaAPI().getSpawns() : List.of());

        for (Player player : players) {
            if (player == null || !player.isOnline() || state.hasCage(player.getUniqueId())) {
                continue;
            }
            Location spawn = resolveSpawnForPlayer(player, spawns, state, context);
            if (spawn == null) {
                continue;
            }
            CageDefinition cage = resolveCageDefinition(player);
            spawn = player.getLocation();
            if (spawn == null || spawn.getWorld() == null) {
                continue;
            }
            if (context.getArenaAPI() != null && !context.getArenaAPI().isInBounds(spawn)) {
                continue;
            }

            int baseX = spawn.getBlockX();
            int baseY = spawn.getBlockY();
            int baseZ = spawn.getBlockZ();

            for (int x = -interiorRadius; x <= interiorRadius; x++) {
                for (int z = -interiorRadius; z <= interiorRadius; z++) {
                    placeBlock(context, state, cage.material(), new Location(spawn.getWorld(), baseX + x, baseY + floorOffset, baseZ + z));
                }
            }

            for (int y = 0; y <= wallTopOffset; y++) {
                if (cage.headClear() && y == 1) {
                    continue;
                }
                for (int x = -interiorRadius; x <= interiorRadius; x++) {
                    placeBlock(context, state, cage.material(), new Location(spawn.getWorld(), baseX + x, baseY + y, baseZ + wallOffset));
                    placeBlock(context, state, cage.material(), new Location(spawn.getWorld(), baseX + x, baseY + y, baseZ - wallOffset));
                }
                for (int z = -interiorRadius; z <= interiorRadius; z++) {
                    placeBlock(context, state, cage.material(), new Location(spawn.getWorld(), baseX + wallOffset, baseY + y, baseZ + z));
                    placeBlock(context, state, cage.material(), new Location(spawn.getWorld(), baseX - wallOffset, baseY + y, baseZ + z));
                }
            }

            placeBlock(context, state, cage.material(), new Location(spawn.getWorld(), baseX, baseY + roofOffset, baseZ));
            markPlayersAtSpawn(players, state, spawn);
        }
    }

    public void removeCages(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                            ArenaState state) {
        if (context == null || state == null) {
            return;
        }

        Map<String, Material> cageBlocks = state.getCageBlocks();
        if (cageBlocks.isEmpty()) {
            return;
        }

        World arenaWorld = context.getArenaAPI() != null ? context.getArenaAPI().getWorld() : null;
        for (String key : cageBlocks.keySet()) {
            Location location = parseLocation(key, arenaWorld);
            if (location == null) {
                continue;
            }
            context.getSchedulerAPI().runAtLocation(location, () -> {
                if (location.getWorld() != null) {
                    location.getBlock().setType(Material.AIR);
                }
            });
        }

        state.clearCageBlocks();
        state.clearCagedPlayers();
        state.clearCagedSpawns();
    }

    private void placeBlock(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                            ArenaState state,
                            Material material,
                            Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        context.getSchedulerAPI().runAtLocation(location, () -> {
            Block block = location.getBlock();
            state.trackCageBlock(location, block.getType());
            block.setType(material);
        });
    }

    private Location parseLocation(String key, World fallbackWorld) {
        if (key == null) {
            return null;
        }
        String[] parts = key.split(":");
        if (parts.length != 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            world = fallbackWorld;
        }
        if (world == null) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Location resolveSpawnForPlayer(Player player,
                                           List<Location> spawns,
                                           ArenaState state,
                                           GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        if (player == null) {
            return null;
        }
        Location playerLocation = player.getLocation();
        if (playerLocation == null || playerLocation.getWorld() == null) {
            return null;
        }
        if (context.getArenaAPI() != null && !context.getArenaAPI().isInBounds(playerLocation)) {
            return null;
        }
        if (spawns == null || spawns.isEmpty()) {
            return playerLocation;
        }

        Location closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Location spawn : spawns) {
            if (spawn == null || state.isSpawnCaged(spawn)) {
                continue;
            }
            double distance = squaredDistanceCoords(spawn, playerLocation);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = spawn;
            }
        }

        if (closest == null || closestDistance > 2.25) {
            return null;
        }
        return closest;
    }

    private void markPlayersAtSpawn(List<Player> players, ArenaState state, Location spawn) {
        if (spawn == null || spawn.getWorld() == null) {
            return;
        }
        state.markSpawnCaged(spawn);
        for (Player candidate : players) {
            if (candidate == null || !candidate.isOnline()) {
                continue;
            }
            if (squaredDistanceCoords(candidate.getLocation(), spawn) <= 2.25) {
                state.markCageBuilt(candidate.getUniqueId());
            }
        }
    }

    private double squaredDistanceCoords(Location a, Location b) {
        if (a == null || b == null) {
            return Double.MAX_VALUE;
        }
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private CageDefinition resolveCageDefinition(Player player) {
        String defaultId = moduleConfig.getStringFrom("cage.yml", "default_cage", "clear_glass");
        String cageId = defaultId;
        if (storeAPI != null && player != null) {
            String selected = storeAPI.resolveSelected(player, getCageCategoryId());
            if (selected != null && moduleConfig.containsFrom("cage.yml", "cages." + selected)) {
                cageId = selected;
            }
        }
        String base = "cages." + cageId;
        Material material = resolveMaterialFrom("cage.yml", base + ".material", Material.GLASS);
        boolean headClear = moduleConfig.getBooleanFrom("cage.yml", base + ".head_clear", false);
        return new CageDefinition(material, headClear);
    }

    private String getCageCategoryId() {
        return moduleConfig.getStringFrom("store.yml", "category_settings.cages.id", "bed_wars_cages");
    }

    private Material resolveMaterialFrom(String file, String path, Material fallback) {
        String raw = moduleConfig.getStringFrom(file, path);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Material.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private record CageDefinition(Material material, boolean headClear) {
    }
}
