package net.blueva.arcade.modules.bed_wars.game;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.team.TeamInfo;
import net.blueva.arcade.api.team.TeamsAPI;
import net.blueva.arcade.modules.bed_wars.state.ArenaState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class BedWarsArenaDataLoader {

    void loadTeamSpawns(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                        ArenaState state) {
        if (context.getDataAccess() == null) {
            return;
        }

        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            return;
        }

        String spawnBase = resolveDataBasePath(context, "team_spawns");
        int teamIndex = 1;
        for (TeamInfo<Player, Material> team : teamsAPI.getTeams()) {
            String teamId = team.getId();
            if (teamId == null || teamId.isBlank()) {
                teamIndex++;
                continue;
            }
            teamId = teamId.toLowerCase();
            String canonicalPath = spawnBase + "." + teamId;
            String numericPath = spawnBase + "." + teamIndex;

            String resolvedPath = null;
            if (context.getDataAccess().hasGameData(canonicalPath)) {
                resolvedPath = canonicalPath;
            } else if (context.getDataAccess().hasGameData(numericPath)) {
                resolvedPath = numericPath;
            }

            if (resolvedPath != null) {
                Location spawn = context.getDataAccess().getGameLocation(resolvedPath);
                if (spawn != null) {
                    state.setTeamSpawn(teamId, spawn);
                }
            }
            teamIndex++;
        }
    }

    void loadTeamRestrictedZones(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                 ArenaState state) {
        if (context.getDataAccess() == null) {
            return;
        }

        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            return;
        }

        String zonesBase = resolveDataBasePath(context, "restricted_zones");
        int teamIndex = 1;
        for (TeamInfo<Player, Material> team : teamsAPI.getTeams()) {
            String teamId = team.getId();
            if (teamId == null || teamId.isBlank()) {
                teamIndex++;
                continue;
            }
            teamId = teamId.toLowerCase();
            for (int i = 1; i <= 10; i++) {
                String canonicalBasePath = zonesBase + "." + teamId + "." + i;
                String numericBasePath = zonesBase + "." + teamIndex + "." + i;
                String resolvedBasePath;
                if (context.getDataAccess().hasGameData(canonicalBasePath + ".min")) {
                    resolvedBasePath = canonicalBasePath;
                } else if (context.getDataAccess().hasGameData(numericBasePath + ".min")) {
                    resolvedBasePath = numericBasePath;
                } else {
                    continue;
                }

                Location min = context.getDataAccess().getGameLocation(resolvedBasePath + ".min");
                Location max = context.getDataAccess().getGameLocation(resolvedBasePath + ".max");
                if (min != null && max != null) {
                    state.addTeamRestrictedZone(teamId, min, max);
                }
            }
            teamIndex++;
        }
    }

    String resolveDataBasePath(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                               String section) {
        if (context.getDataAccess().hasGameData("game.play_area." + section)) {
            return "game.play_area." + section;
        }
        return "game." + section;
    }
}
