package net.blueva.arcade.modules.bed_wars.game;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.team.TeamInfo;
import net.blueva.arcade.api.team.TeamsAPI;
import net.blueva.arcade.modules.bed_wars.state.ArenaState;
import net.blueva.arcade.modules.bed_wars.support.spawner.SpawnerType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class BedWarsRuntimeService {

    private final BedWarsGame game;

    BedWarsRuntimeService(BedWarsGame game) {
        this.game = game;
    }

    String getScoreboardPath(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        int activeTeamCount = 0;
        if (teamsAPI != null && teamsAPI.isEnabled()) {
            for (TeamInfo<Player, Material> team : teamsAPI.getTeams()) {
                String teamId = team.getId();
                if (teamId == null || teamId.isBlank()) {
                    continue;
                }
                boolean hasPlayers = false;
                for (Player p : context.getPlayers()) {
                    TeamInfo<Player, Material> pTeam = teamsAPI.getTeam(p);
                    if (pTeam != null && pTeam.getId().equalsIgnoreCase(teamId)) {
                        hasPlayers = true;
                        break;
                    }
                }
                if (hasPlayers) {
                    activeTeamCount++;
                }
            }
        }
        if (activeTeamCount == 0 && teamsAPI != null && teamsAPI.isEnabled()) {
            activeTeamCount = teamsAPI.getTeams().size();
        }
        if (activeTeamCount <= 2) return "scoreboard.team_size_2";
        if (activeTeamCount == 3) return "scoreboard.team_size_3";
        if (activeTeamCount == 4) return "scoreboard.team_size_4";
        if (activeTeamCount == 5) return "scoreboard.team_size_5";
        if (activeTeamCount == 6) return "scoreboard.team_size_6";
        if (activeTeamCount == 7) return "scoreboard.team_size_7";
        return "scoreboard.team_size_8";
    }

    boolean isSoloMode(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        if (context == null) {
            return true;
        }
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            return true;
        }
        if (context.getDataAccess() == null) {
            return false;
        }
        Integer teamSize = context.getDataAccess().getGameData("teams.size", Integer.class);
        Integer teamCount = context.getDataAccess().getGameData("teams.count", Integer.class);
        if (teamSize != null && teamSize <= 1) {
            return true;
        }
        return teamCount != null && teamCount <= 1;
    }

    List<String> getAliveTeamIds(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            List<String> ids = new ArrayList<>();
            if (!context.getAlivePlayers().isEmpty()) {
                ids.add("solo");
            }
            return ids;
        }

        ArenaState state = game.getArenaState(context);
        if (state == null) {
            return new ArrayList<>();
        }
        Set<String> teamIds = new HashSet<>();
        for (TeamInfo<Player, Material> team : teamsAPI.getTeams()) {
            String teamId = team.getId();
            if (teamId == null || teamId.isBlank()) {
                continue;
            }
            if (game.bedService.isTeamAlive(context, state, teamId)) {
                teamIds.add(teamId);
            }
        }
        return new ArrayList<>(teamIds);
    }

    Map<String, Integer> getTeamKills(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        Map<String, Integer> teamKills = new HashMap<>();
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        for (Player player : context.getPlayers()) {
            int kills = game.getPlayerKills(context, player);
            String teamId = "solo";
            if (teamsAPI != null && teamsAPI.isEnabled()) {
                TeamInfo<Player, Material> team = teamsAPI.getTeam(player);
                if (team != null) {
                    teamId = team.getId();
                }
            }
            teamKills.merge(teamId, kills, Integer::sum);
        }
        return teamKills;
    }

    Map<String, Integer> getTeamDeaths(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        Map<String, Integer> teamDeaths = new HashMap<>();
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        for (Player player : context.getPlayers()) {
            int deaths = game.getPlayerDeaths(context, player);
            String teamId = "solo";
            if (teamsAPI != null && teamsAPI.isEnabled()) {
                TeamInfo<Player, Material> team = teamsAPI.getTeam(player);
                if (team != null) {
                    teamId = team.getId();
                }
            }
            teamDeaths.merge(teamId, deaths, Integer::sum);
        }
        return teamDeaths;
    }

    List<Player> getTeamPlayers(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                String teamId) {
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        List<Player> players = new ArrayList<>();
        for (Player player : context.getPlayers()) {
            if (teamsAPI == null || !teamsAPI.isEnabled()) {
                players.add(player);
                continue;
            }
            TeamInfo<Player, Material> team = teamsAPI.getTeam(player);
            if (team != null && team.getId().equalsIgnoreCase(teamId)) {
                players.add(player);
            }
        }
        return players;
    }

    void checkForVictory(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        ArenaState state = game.getArenaState(context);
        if (state == null || state.isEnded()) {
            return;
        }
        if (shouldEndForVictory(context, state)) {
            game.endGame(context);
        }
    }

    void startGameTimer(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                        ArenaState state) {
        int arenaId = context.getArenaId();
        int fallProtectionSeconds = Math.max(0, game.moduleConfig.getInt("spawn_protection.fall_damage_seconds", 5));
        String taskId = "arena_" + arenaId + "_bed_wars_timer";
        String spawnerTaskId = "arena_" + arenaId + "_bed_wars_spawners";
        String itemRotationTaskId = "arena_" + arenaId + "_bed_wars_item_rot";

        context.getSchedulerAPI().runTimer(spawnerTaskId, () -> {
            if (state.isEnded()) {
                context.getSchedulerAPI().cancelTask(spawnerTaskId);
                return;
            }
            game.spawnerService.spawnResources(context, state);
        }, 0L, 1L);

        context.getSchedulerAPI().runTimer(itemRotationTaskId, () -> {
            if (state.isEnded()) {
                context.getSchedulerAPI().cancelTask(itemRotationTaskId);
                return;
            }
            rotateSpawnerItems(state);
        }, 0L, 1L);

        context.getSchedulerAPI().runTimer(taskId, () -> {
            if (state.isEnded()) {
                context.getSchedulerAPI().cancelTask(taskId);
                return;
            }

            state.incrementMatchSeconds();
            refreshFallProtection(state, context.getPlayers(), fallProtectionSeconds);
            processScheduledEvents(context, state);
            game.spawnerService.updateSpawnerHolograms(context, state);

            List<Player> allPlayers = context.getPlayers();
            if (shouldEndForVictory(context, state)) {
                game.endGame(context);
                return;
            }

            String actionBarTemplate = game.moduleConfig.getStringFrom("language.yml", "messages.action_bar.in_game");
            for (Player player : allPlayers) {
                if (!player.isOnline()) {
                    continue;
                }

                Map<String, String> customPlaceholders = game.placeholderService.buildPlaceholders(player);
                context.getMessagesAPI().sendActionBar(player, actionBarTemplate
                        .replace("{team}", customPlaceholders.getOrDefault("team", "-"))
                        .replace("{kills}", customPlaceholders.getOrDefault("kills", "0"))
                        .replace("{deaths}", customPlaceholders.getOrDefault("deaths", "0"))
                        .replace("{bed_status}", customPlaceholders.getOrDefault("bed_status", "-"))
                        .replace("{elapsed}", String.valueOf(state.getMatchSeconds())));

                context.getScoreboardAPI().update(player, getScoreboardPath(context), customPlaceholders);
            }
        }, 0L, 20L);
    }

    void registerFallProtection(ArenaState state, Player player) {
        if (state == null || player == null) {
            return;
        }
        int protectionSeconds = Math.max(0, game.moduleConfig.getInt("spawn_protection.fall_damage_seconds", 5));
        if (protectionSeconds <= 0) {
            return;
        }
        state.setFallProtection(player.getUniqueId(), System.currentTimeMillis() + (protectionSeconds * 1000L));
    }

    void trackPlacedBlock(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                          Location location) {
        ArenaState state = game.getArenaState(context);
        if (state != null) {
            state.trackPlacedBlock(location);
        }
    }

    boolean canBreakBlock(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                          Block block) {
        ArenaState state = game.getArenaState(context);
        return state != null && state.isPlayerPlacedBlock(block.getLocation());
    }

    void untrackPlacedBlock(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                            Location location) {
        ArenaState state = game.getArenaState(context);
        if (state != null) {
            state.untrackPlacedBlock(location);
        }
    }

    private void processScheduledEvents(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                        ArenaState state) {
        int matchSeconds = state.getMatchSeconds();
        List<ArenaState.ScheduledEvent> events = state.getScheduledEvents();
        for (int i = 0; i < events.size(); i++) {
            if (state.isEventFired(i)) {
                continue;
            }
            ArenaState.ScheduledEvent event = events.get(i);
            if (matchSeconds >= event.getTriggerSeconds()) {
                state.markEventFired(i);
                applyGeneratorUpgradeEvent(context, state, event);
            }
        }
    }

    private void applyGeneratorUpgradeEvent(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                            ArenaState state,
                                            ArenaState.ScheduledEvent event) {
        SpawnerType type = SpawnerType.fromString(event.getType());
        if (type != null) {
            state.setGlobalSpawnerMultiplier(type, event.getMultiplier());
        }

        String template = game.moduleConfig.getStringFrom("language.yml", "messages.generator_upgrade",
                "<gold><bold>⚡ GENERATOR UPGRADE</bold></gold> <gray>-</gray> <yellow>{label}</yellow> <gray>spawners are now</gray> <green>{multiplier}x</green> <gray>faster!</gray>");
        String multiplierFormatted = formatMultiplier(event.getMultiplier());
        String message = template
                .replace("{label}", event.getLabel())
                .replace("{type}", event.getType())
                .replace("{multiplier}", multiplierFormatted);

        for (Player player : context.getPlayers()) {
            if (player.isOnline()) {
                context.getMessagesAPI().sendRaw(player, message);
            }
        }
    }

    private void rotateSpawnerItems(ArenaState state) {
        if (state == null) return;
        for (String key : state.getSpawnerItemStandKeys()) {
            org.bukkit.entity.ArmorStand stand = state.getSpawnerItemStand(key);
            if (stand == null || stand.isDead()) continue;
            int rotation = state.getSpawnerItemRotation(key);
            rotation = (rotation + 4) % 360;
            state.setSpawnerItemRotation(key, rotation);
            stand.setHeadPose(new org.bukkit.util.EulerAngle(0, Math.toRadians(rotation), 0));
        }
    }

    private boolean shouldEndForVictory(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                        ArenaState state) {
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            return false;
        }
        int aliveTeams = game.bedService.getAliveTeamCount(context, state);
        return aliveTeams <= 1;
    }

    private void refreshFallProtection(ArenaState state, List<Player> players, int protectionSeconds) {
        if (state == null || protectionSeconds <= 0) {
            return;
        }
        for (Player player : players) {
            if (player == null) {
                continue;
            }
            if (state.hasFallProtection(player.getUniqueId())) {
                continue;
            }
            if (state.getMatchSeconds() == 1) {
                state.setFallProtection(player.getUniqueId(),
                        System.currentTimeMillis() + (protectionSeconds * 1000L));
            }
        }
    }

    private String formatMultiplier(double multiplier) {
        if (multiplier == Math.floor(multiplier)) {
            return String.valueOf((int) multiplier);
        }
        return String.valueOf(multiplier);
    }
}
