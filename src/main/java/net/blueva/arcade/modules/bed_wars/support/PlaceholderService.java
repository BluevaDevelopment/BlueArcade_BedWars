package net.blueva.arcade.modules.bed_wars.support;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.team.TeamInfo;
import net.blueva.arcade.api.team.TeamsAPI;
import net.blueva.arcade.modules.bed_wars.game.BedWarsGame;
import net.blueva.arcade.modules.bed_wars.state.ArenaState;
import net.blueva.arcade.modules.bed_wars.support.bed.BedDefinition;
import net.blueva.arcade.modules.bed_wars.support.bed.BedState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlaceholderService {

    private final ModuleConfigAPI moduleConfig;
    private final BedWarsGame game;

    public PlaceholderService(ModuleConfigAPI moduleConfig, BedWarsGame game) {
        this.moduleConfig = moduleConfig;
        this.game = game;
    }

    public Map<String, String> buildPlaceholders(Player player) {
        Map<String, String> placeholders = new HashMap<>();

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = game.getContext(player);
        if (context != null) {
            placeholders.put("alive", String.valueOf(context.getAlivePlayers().size()));
            placeholders.put("spectators", String.valueOf(context.getSpectators().size()));
            placeholders.put("kills", String.valueOf(game.getPlayerKills(context, player)));
            placeholders.put("deaths", String.valueOf(game.getPlayerDeaths(context, player)));
            placeholders.put("alive_teams", String.valueOf(game.getAliveTeamIds(context).size()));

            ArenaState state = game.getArenaState(context);

            TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
            if (teamsAPI != null && teamsAPI.isEnabled()) {
                TeamInfo<Player, Material> team = teamsAPI.getTeam(player);
                placeholders.put("team", team != null ? team.getDisplayName() : "-");

                if (state != null && team != null && team.getId() != null) {
                    boolean bedIntact = game.getBedService().isTeamBedIntact(state, team.getId());
                    String yesText = moduleConfig.getTranslation(player, "messages.common.boolean_true");
                    String noText = moduleConfig.getTranslation(player, "messages.common.boolean_false");
                    placeholders.put("bed_status", bedIntact ? ("<green>" + yesText + "</green>") : ("<red>" + noText + "</red>"));
                } else {
                    placeholders.put("bed_status", "-");
                }

                if (state != null) {
                    int index = 1;
                    for (Object teamObj : teamsAPI.getTeams()) {
                        if (!(teamObj instanceof TeamInfo teamInfo) || teamInfo.getId() == null) {
                            continue;
                        }

                        if (!hasTeamAnyPlayers(context, teamsAPI, teamInfo.getId())) {
                            continue;
                        }
                        String teamId = teamInfo.getId().toLowerCase();
                        String bedStatusLine = buildBedStatusLine(state, context, teamInfo, player);
                        placeholders.put("bed_status_" + teamId, bedStatusLine);
                        placeholders.put("bed_status_team_" + index, bedStatusLine);
                        index++;
                    }

                    for (int i = index; i <= 8; i++) {
                        placeholders.put("bed_status_team_" + i, " ");
                    }

                    buildTeamSummaryPlaceholders(context, state, teamsAPI, placeholders, team, player);
                }
            } else {
                placeholders.put("team", "-");
                placeholders.put("bed_status", "-");
            }
        }

        return placeholders;
    }

    private boolean hasTeamAnyPlayers(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                      TeamsAPI<Player, Material> teamsAPI,
                                      String teamId) {
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            return true;
        }
        for (Player p : context.getPlayers()) {
            TeamInfo<Player, Material> pTeam = teamsAPI.getTeam(p);
            if (pTeam != null && pTeam.getId().equalsIgnoreCase(teamId)) {
                return true;
            }
        }
        return false;
    }

    private String buildBedStatusLine(ArenaState state, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, TeamInfo<Player, Material> teamInfo, Player viewer) {
        String teamId = teamInfo.getId();
        String teamDisplay = teamInfo.getDisplayName() != null ? teamInfo.getDisplayName() : teamId;
        BedState bedState = state.getBedState(teamId.toLowerCase());

        boolean hasAlivePlayers = false;
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI != null) {
            for (Player p : context.getAlivePlayers()) {
                TeamInfo<Player, Material> pTeam = teamsAPI.getTeam(p);
                if (pTeam != null && pTeam.getId().equalsIgnoreCase(teamId)) {
                    hasAlivePlayers = true;
                    break;
                }
            }
        }

        String status;
        if (bedState == BedState.INTACT) {
            status = lang(viewer, "messages.team_status.bed_intact", "<green>✔</green>");
        } else if (hasAlivePlayers) {
            int aliveCount = 0;
            for (Player p : context.getAlivePlayers()) {
                TeamInfo<Player, Material> pTeam = teamsAPI != null ? teamsAPI.getTeam(p) : null;
                if (pTeam != null && pTeam.getId().equalsIgnoreCase(teamId)) {
                    aliveCount++;
                }
            }
            status = lang(viewer, "messages.team_status.alive_count", "<yellow>{count}</yellow>")
                    .replace("{count}", String.valueOf(aliveCount));
        } else {
            status = lang(viewer, "messages.team_status.eliminated", "<red>✘</red>");
        }


        boolean isOwnTeam = viewer != null && teamsAPI != null
                && teamsAPI.getTeam(viewer) != null
                && teamsAPI.getTeam(viewer).getId().equalsIgnoreCase(teamId);
        String ownTeamSuffix = isOwnTeam ? lang(viewer, "messages.team_status.own_team_suffix", " <green><bold>YOU</bold></green>") : "";

        return lang(viewer, "messages.team_status.bed_line", "<white>{team}</white> <gray>-</gray> {status}{own_team_suffix}")
                .replace("{team}", teamDisplay)
                .replace("{status}", status)
                .replace("{own_team_suffix}", ownTeamSuffix);
    }

    private String buildBedStatusLine(ArenaState state, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, TeamInfo<Player, Material> teamInfo) {
        return buildBedStatusLine(state, context, teamInfo, null);
    }

    private void buildTeamSummaryPlaceholders(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                              ArenaState state,
                                              TeamsAPI<Player, Material> teamsAPI,
                                              Map<String, String> placeholders,
                                              TeamInfo<Player, Material> playerTeam,
                                              Player viewer) {
        Map<String, Integer> teamKills = game.getTeamKills(context);
        Map<String, Integer> teamDeaths = game.getTeamDeaths(context);

        int index = 1;
        for (Object teamObj : teamsAPI.getTeams()) {
            if (!(teamObj instanceof TeamInfo teamInfo) || teamInfo.getId() == null) {
                continue;
            }


            if (!hasTeamAnyPlayers(context, teamsAPI, teamInfo.getId())) {
                continue;
            }

            String teamId = teamInfo.getId();
            boolean isPlayerTeam = playerTeam != null
                    && playerTeam.getId() != null
                    && playerTeam.getId().equalsIgnoreCase(teamId);

            BedState bedState = state.getBedState(teamId.toLowerCase());
            int kills = teamKills.getOrDefault(teamId, 0);
            int deaths = teamDeaths.getOrDefault(teamId, 0);

            String bedIcon = bedState == BedState.INTACT
                    ? lang(viewer, "messages.team_status.bed_intact", "<green>✔</green>")
                    : lang(viewer, "messages.team_status.eliminated", "<red>✘</red>");
            String ownTeamSuffix = isPlayerTeam
                    ? lang(viewer, "messages.team_status.own_team_header_suffix", " <green><bold><- You</bold></green>")
                    : "";
            placeholders.put("team_header_" + index,
                    lang(viewer, "messages.team_status.header", "<white>{team}{own_team_suffix}</white>")
                            .replace("{team}", teamInfo.getDisplayName())
                            .replace("{own_team_suffix}", ownTeamSuffix)
                            + uniqueSuffix(index, 0));
            placeholders.put("team_summary_" + index,
                    lang(viewer, "messages.team_status.summary", "<white>{team}</white> <gray>Bed {bed_status} • K {kills} • D {deaths}</gray>")
                            .replace("{team}", teamInfo.getDisplayName())
                            .replace("{bed_status}", bedIcon)
                            .replace("{kills}", String.valueOf(kills))
                            .replace("{deaths}", String.valueOf(deaths)));
            index++;
        }

        for (int i = index; i <= 8; i++) {
            placeholders.put("team_header_" + i, "<black>.</black>" + uniqueSuffix(i, 0));
            placeholders.put("team_summary_" + i, " ");
        }
    }

    private String uniqueSuffix(int teamIndex, int slotIndex) {
        int count = Math.max(1, (teamIndex * 3) + slotIndex);
        return "\u200B".repeat(count);
    }

    private String lang(Player player, String path, String fallback) {
        String value = moduleConfig.getTranslation(player, path);
        return value != null ? value : fallback;
    }

    public List<Player> getPlayersSortedByKills(
            GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
            List<Player> players,
            int limit) {
        Map<Player, Integer> killCounts = new HashMap<>();
        for (Player player : players) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            killCounts.put(player, game.getPlayerKills(context, player));
        }

        List<Map.Entry<Player, Integer>> sorted = new java.util.ArrayList<>(killCounts.entrySet());
        sorted.sort((a, b) -> {
            int compare = Integer.compare(b.getValue(), a.getValue());
            if (compare != 0) {
                return compare;
            }
            return a.getKey().getName().compareToIgnoreCase(b.getKey().getName());
        });

        List<Player> orderedPlayers = new java.util.ArrayList<>();
        for (Map.Entry<Player, Integer> entry : sorted) {
            orderedPlayers.add(entry.getKey());
            if (orderedPlayers.size() >= limit) {
                break;
            }
        }

        return orderedPlayers;
    }

    public ModuleConfigAPI getModuleConfig() {
        return moduleConfig;
    }
}
