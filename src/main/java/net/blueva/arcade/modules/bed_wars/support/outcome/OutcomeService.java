package net.blueva.arcade.modules.bed_wars.support.outcome;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.stats.StatsAPI;
import net.blueva.arcade.api.team.TeamInfo;
import net.blueva.arcade.api.team.TeamsAPI;
import net.blueva.arcade.modules.bed_wars.game.BedWarsGame;
import net.blueva.arcade.modules.bed_wars.state.ArenaState;
import net.blueva.arcade.modules.bed_wars.support.PlaceholderService;
import net.blueva.arcade.modules.bed_wars.support.bed.BedState;
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
import java.util.Map;
import java.util.UUID;

public class OutcomeService {

    private final ModuleInfo moduleInfo;
    private final StatsAPI statsAPI;
    private final BedWarsGame game;
    private final PlaceholderService placeholderService;

    public OutcomeService(ModuleInfo moduleInfo,
                          StatsAPI statsAPI,
                          BedWarsGame game,
                          PlaceholderService placeholderService) {
        this.moduleInfo = moduleInfo;
        this.statsAPI = statsAPI;
        this.game = game;
        this.placeholderService = placeholderService;
    }

    public void endGame(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                        ArenaState state) {
        if (state.markEnded()) {
            return;
        }

        context.getSchedulerAPI().cancelArenaTasks(context.getArenaId());

        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI != null && teamsAPI.isEnabled()) {
            for (TeamInfo<Player, Material> team : teamsAPI.getTeams()) {
                String teamId = team.getId();
                if (teamId == null || teamId.isBlank()) {
                    continue;
                }
                if (game.getBedService().isTeamAlive(context, state, teamId)) {
                    declareWinningTeam(context, state, teamId);
                    context.endGame();
                    return;
                }
            }
        }

        declareTopTeamByKills(context, state);
        context.endGame();
    }

    private void declareWinningTeam(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                    ArenaState state,
                                    String teamId) {
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        List<Player> winners = game.getTeamPlayers(context, teamId);
        if (teamsAPI != null && teamsAPI.isEnabled()) {
            teamsAPI.setWinningTeam(teamId);
        }
        if (!winners.isEmpty()) {
            context.markSharedFirstPlace(winners);
        }
        showFinalScoreboard(context, state, teamId);
        handleWinStats(state, winners);
    }

    private void declareTopTeamByKills(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                       ArenaState state) {
        Map<String, Integer> teamKills = game.getTeamKills(context);
        if (teamKills.isEmpty()) {
            handleNoWinner(context);
            return;
        }

        int maxKills = teamKills.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<String> topTeams = teamKills.entrySet().stream()
                .filter(entry -> entry.getValue() == maxKills)
                .map(Map.Entry::getKey)
                .toList();

        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI != null && teamsAPI.isEnabled()) {
            teamsAPI.setWinningTeams(topTeams);
        }

        List<Player> winners = new ArrayList<>();
        for (String teamId : topTeams) {
            winners.addAll(game.getTeamPlayers(context, teamId));
        }
        if (!winners.isEmpty()) {
            context.markSharedFirstPlace(winners);
        }
        String winnerTeam = topTeams.isEmpty() ? "-" : topTeams.get(0);
        showFinalScoreboard(context, state, winnerTeam);
        handleWinStats(state, winners);
    }

    private void showFinalScoreboard(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                     ArenaState state,
                                     String winningTeamId) {
        if (context == null || state == null) {
            return;
        }

        Map<String, String> placeholders = new java.util.HashMap<>();
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        String winningDisplay = winningTeamId;
        if (teamsAPI != null && teamsAPI.isEnabled() && winningTeamId != null) {
            for (TeamInfo<Player, Material> team : teamsAPI.getTeams()) {
                if (team != null && team.getId() != null && team.getId().equalsIgnoreCase(winningTeamId)) {
                    winningDisplay = team.getDisplayName();
                    break;
                }
            }
        }

        placeholders.put("winning_team", winningDisplay == null ? "-" : winningDisplay);
        populateTopKillsPlaceholders(context, placeholders);

        List<Player> players = context.getPlayers();
        for (Player player : players) {
            Map<String, String> finalPlaceholders = new java.util.HashMap<>(placeholders);
            finalPlaceholders.putAll(game.getPlaceholders(player));
            context.getScoreboardAPI().showModuleFinalScoreboard(player, "scoreboard.final.winner", finalPlaceholders);
        }
    }

    private void populateTopKillsPlaceholders(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                              Map<String, String> placeholders) {
        if (context == null || placeholders == null) {
            return;
        }

        List<Player> topKillers = placeholderService.getPlayersSortedByKills(
                context,
                new ArrayList<>(context.getPlayers()),
                5
        );

        for (int i = 1; i <= 5; i++) {
            if (topKillers.size() >= i) {
                Player killer = topKillers.get(i - 1);
                placeholders.put("top_kills_" + i, killer.getName());
                placeholders.put("top_kills_value_" + i, String.valueOf(game.getPlayerKills(context, killer)));
            } else {
                placeholders.put("top_kills_" + i, "-");
                placeholders.put("top_kills_value_" + i, "0");
            }
        }
    }

    private void handleNoWinner(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        List<Player> sortedByKills = placeholderService.getPlayersSortedByKills(
                context, new ArrayList<>(context.getPlayers()), context.getPlayers().size());
        if (sortedByKills.isEmpty()) {
            return;
        }
        context.markSharedFirstPlace(List.of(sortedByKills.get(0)));
    }

    private void handleWinStats(ArenaState state, List<Player> winners) {
        if (statsAPI == null || winners.isEmpty()) {
            return;
        }

        UUID winnerId = state.getWinnerId();
        if (winnerId != null) {
            return;
        }

        state.setWinner(winners.get(0).getUniqueId());
        for (Player winner : winners) {
            statsAPI.addModuleStat(winner, moduleInfo.getId(), "wins", 1);
            statsAPI.addGlobalStat(winner, "wins", 1);
        }
    }
}
