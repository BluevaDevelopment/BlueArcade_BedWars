package net.blueva.arcade.modules.bed_wars.game;

import net.blueva.arcade.api.ModuleAPI;
import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.stats.StatsAPI;
import net.blueva.arcade.api.store.StoreAPI;
import net.blueva.arcade.api.ui.MenuAPI;
import net.blueva.arcade.api.team.TeamInfo;
import net.blueva.arcade.api.team.TeamsAPI;
import net.blueva.arcade.api.utils.PlayerUtil;
import net.blueva.arcade.modules.bed_wars.state.ArenaState;
import net.blueva.arcade.modules.bed_wars.state.VoteState;
import net.blueva.arcade.modules.bed_wars.support.DescriptionService;
import net.blueva.arcade.modules.bed_wars.support.PlaceholderService;
import net.blueva.arcade.modules.bed_wars.support.armory.ArmoryService;
import net.blueva.arcade.modules.bed_wars.support.bed.BedDefinition;
import net.blueva.arcade.modules.bed_wars.support.bed.BedService;
import net.blueva.arcade.modules.bed_wars.support.combat.CombatService;
import net.blueva.arcade.modules.bed_wars.support.loadout.PlayerLoadoutService;
import net.blueva.arcade.modules.bed_wars.support.npc.ShopNpcService;
import net.blueva.arcade.modules.bed_wars.support.outcome.OutcomeService;
import net.blueva.arcade.modules.bed_wars.support.shop.ShopService;
import net.blueva.arcade.modules.bed_wars.support.spawn.SpawnCageService;
import net.blueva.arcade.modules.bed_wars.support.special.SpecialItemHandler;
import net.blueva.arcade.modules.bed_wars.support.spawner.SpawnerDefinition;
import net.blueva.arcade.modules.bed_wars.support.spawner.SpawnerService;
import net.blueva.arcade.modules.bed_wars.support.npc.ShopNpcDefinition;
import net.blueva.arcade.modules.bed_wars.support.upgrades.TeamUpgradeService;
import net.blueva.arcade.modules.bed_wars.support.vote.BedWarsVoteService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.blueva.arcade.modules.bed_wars.support.armory.ArenaEnderChestHolder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BedWarsGame {

    final ModuleInfo moduleInfo;
    final ModuleConfigAPI moduleConfig;
    final CoreConfigAPI coreConfig;
    final StatsAPI statsAPI;

    private final Map<Integer, ArenaState> arenas = new ConcurrentHashMap<>();
    private final Map<Player, Integer> playerArena = new ConcurrentHashMap<>();
    private final Map<Integer, Set<UUID>> countdownPreparedByArena = new ConcurrentHashMap<>();
    private final Map<Integer, Map<String, ItemStack[]>> arenaEnderChests = new ConcurrentHashMap<>();

    final DescriptionService descriptionService;
    final PlayerLoadoutService loadoutService;
    final PlaceholderService placeholderService;
    final OutcomeService outcomeService;
    final CombatService combatService;
    final BedService bedService;
    final SpawnerService spawnerService;
    final ShopNpcService shopNpcService;
    final ArmoryService armoryService;
    final ShopService shopService;
    final SpawnCageService spawnCageService;
    final TeamUpgradeService upgradeService;
    final SpecialItemHandler specialItemHandler;
    final BedWarsVoteService voteService;

    final MenuAPI<Player, Material> menuAPI;
    final BedWarsArenaDataLoader arenaDataLoader;
    final BedWarsGameplayService gameplayService;
    final BedWarsRuntimeService runtimeService;

    public BedWarsGame(ModuleInfo moduleInfo,
                       ModuleConfigAPI moduleConfig,
                       CoreConfigAPI coreConfig,
                       StatsAPI statsAPI,
                       MenuAPI<Player, Material> menuAPI,
                       StoreAPI storeAPI,
                       BedWarsVoteService voteService) {
        this.moduleInfo = moduleInfo;
        this.moduleConfig = moduleConfig;
        this.coreConfig = coreConfig;
        this.statsAPI = statsAPI;
        this.menuAPI = menuAPI;
        this.voteService = voteService;
        this.descriptionService = new DescriptionService(moduleConfig);
        this.loadoutService = new PlayerLoadoutService(moduleConfig);
        this.placeholderService = new PlaceholderService(moduleConfig, this);
        this.outcomeService = new OutcomeService(moduleInfo, statsAPI, this, placeholderService);
        this.combatService = new CombatService(moduleConfig, coreConfig, statsAPI, this, loadoutService);
        this.bedService = new BedService(moduleConfig);
        this.spawnerService = new SpawnerService(moduleConfig);
        this.shopNpcService = new ShopNpcService(moduleConfig);
        this.armoryService = new ArmoryService(moduleConfig);
        this.shopService = new ShopService(moduleConfig, menuAPI, this);
        this.spawnCageService = new SpawnCageService(moduleConfig, storeAPI);
        this.upgradeService = new TeamUpgradeService(moduleConfig, menuAPI, this);
        this.specialItemHandler = new SpecialItemHandler(this, moduleConfig);
        this.arenaDataLoader = new BedWarsArenaDataLoader();
        this.gameplayService = new BedWarsGameplayService(this);
        this.runtimeService = new BedWarsRuntimeService(this);
    }

    public void startGame(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();

        context.getSummarySettings().setRewardsEnabled(true);

        context.getSchedulerAPI().cancelArenaTasks(arenaId);
        ArenaState state = new ArenaState(context);
        state.setVoteState(voteService != null ? voteService.createVoteState() : null);
        if (voteService != null) {
            voteService.applyPendingVotes(state, context.getPlayers());
        }
        applyVoteDefaults(state);
        arenas.put(arenaId, state);

        List<BedDefinition> bedDefs = bedService.loadBedDefinitions(context);
        state.setBedDefinitions(bedDefs);

        List<SpawnerDefinition> spawnerDefs = spawnerService.loadSpawnerDefinitions(context, state);
        state.setSpawnerDefinitions(spawnerDefs);

        List<ArenaState.ScheduledEvent> upgradeEvents = spawnerService.loadGeneratorUpgradeEvents();
        state.setScheduledEvents(upgradeEvents);

        List<ShopNpcDefinition> npcDefs = shopNpcService.loadNpcDefinitions(context);
        state.setNpcDefinitions(npcDefs);

        arenaDataLoader.loadTeamSpawns(context, state);
        arenaDataLoader.loadTeamRestrictedZones(context, state);

        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        for (Player player : context.getPlayers()) {
            playerArena.put(player, arenaId);
            state.initializePlayer(player.getUniqueId());
            if (teamsAPI != null && teamsAPI.isEnabled() && teamsAPI.getTeam(player) == null) {
                teamsAPI.autoAssignPlayer(player);
            }
        }

        if (spawnCageService.isEnabled()) {
            for (Player player : context.getPlayers()) {
                if (player != null && player.isOnline()) {
                    teleportToTeamSpawn(context, state, player);
                }
            }
            gameplayService.scheduleSpawnCages(context, state);
            gameplayService.scheduleCageGuard(context, state);
        }

        descriptionService.sendDescription(context);
    }

    private void applyVoteDefaults(ArenaState state) {
        state.setSelectedHearts(moduleConfig.getInt("votes.defaults.hearts", 10));
        state.setSelectedTime(moduleConfig.getString("votes.defaults.time", "day"));
        state.setSelectedWeather(moduleConfig.getString("votes.defaults.weather", "sunny"));
    }

    public void handleCountdownTick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                    int secondsLeft) {
        ArenaState state = getArenaState(context);
        if (state == null) {
            return;
        }

        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) {
                continue;
            }

            if (markCountdownPrepared(context.getArenaId(), player.getUniqueId())) {
                context.getSchedulerAPI().runAtEntity(player, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    teleportToTeamSpawn(context, state, player);
                    if (!spawnCageService.isEnabled()) {
                        context.setPlayerSpectating(player, true);
                    }
                });
            }

            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.starting_game.countdown"));

            String title = coreConfig.getLanguage(player, "titles.starting_game.title")
                    .replace("{game_display_name}", moduleInfo.getName())
                    .replace("{time}", String.valueOf(secondsLeft));

            String subtitle = coreConfig.getLanguage(player, "titles.starting_game.subtitle")
                    .replace("{game_display_name}", moduleInfo.getName())
                    .replace("{time}", String.valueOf(secondsLeft));

            context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 5);
        }
    }

    public void handleCountdownFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) {
                continue;
            }

            String title = coreConfig.getLanguage(player, "titles.game_started.title")
                    .replace("{game_display_name}", moduleInfo.getName());

            String subtitle = coreConfig.getLanguage(player, "titles.game_started.subtitle")
                    .replace("{game_display_name}", moduleInfo.getName());

            context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 20);
            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.starting_game.start"));
        }
    }

    public void beginPlaying(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        ArenaState state = getArenaState(context);
        if (state == null) {
            return;
        }

        if (context.getAlivePlayers().isEmpty() && !context.getPlayers().isEmpty()) {
            context.setPlayers(context.getPlayers());
        }

        runtimeService.startGameTimer(context, state);

        context.getSchedulerAPI().cancelTask("arena_" + context.getArenaId() + "_bed_wars_cage_guard");
        spawnCageService.removeCages(context, state);
        bedService.removeEmptyTeamBeds(context, state);

        bedService.spawnBedHolograms(context, state);
        spawnerService.spawnSpawnerHolograms(context, state);
        shopNpcService.spawnNpcs(context, state);

        for (Player player : context.getPlayers()) {
            teleportToTeamSpawn(context, state, player);
            player.setGameMode(GameMode.SURVIVAL);
            loadoutService.restoreVitals(player);
            loadoutService.giveStartingItems(context, player);
            loadoutService.applyStartingEffects(player);
            runtimeService.registerFallProtection(state, player);
            context.getScoreboardAPI().showScoreboard(player, getScoreboardPath(context));
        }
        if (voteService != null) {
            voteService.applyVotes(context, state);
            voteService.broadcastVoteResults(context, state);
        }
    }

    public void finishGame(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();
        context.getSchedulerAPI().cancelArenaTasks(arenaId);

        ArenaState state = arenas.remove(arenaId);
        countdownPreparedByArena.remove(arenaId);
        arenaEnderChests.remove(arenaId);
        if (state != null) {
            spawnCageService.removeCages(context, state);
            bedService.clearBedHolograms(state);
            spawnerService.clearSpawnerHolograms(state);
            shopNpcService.despawnNpcs(state);
        }
        shopService.clearArena(arenaId);
        upgradeService.clearArena(arenaId);
        resetWorldDefaults(context);
        resetPlayerHearts(context.getPlayers());
        removePlayersFromArena(arenaId, context.getPlayers());

        if (statsAPI != null) {
            for (Player player : context.getPlayers()) {
                statsAPI.addModuleStat(player, moduleInfo.getId(), "games_played", 1);
            }
        }
    }

    public void shutdown() {
        Set<ArenaState> states = Set.copyOf(arenas.values());
        for (ArenaState state : states) {
            state.getContext().getSchedulerAPI().cancelModuleTasks("bed_wars");
            spawnCageService.removeCages(state.getContext(), state);
            bedService.clearBedHolograms(state);
            spawnerService.clearSpawnerHolograms(state);
            shopNpcService.despawnNpcs(state);
            resetWorldDefaults(state.getContext());
            resetPlayerHearts(state.getContext().getPlayers());
        }

        arenas.clear();
        playerArena.clear();
        countdownPreparedByArena.clear();
        arenaEnderChests.clear();
        shopService.clearArena(-1);
        upgradeService.clearArena(-1);
    }

    private boolean markCountdownPrepared(int arenaId, UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return countdownPreparedByArena
                .computeIfAbsent(arenaId, ignored -> ConcurrentHashMap.newKeySet())
                .add(playerId);
    }

    public Map<String, String> getPlaceholders(Player player) {
        return placeholderService.buildPlaceholders(player);
    }

    public GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> getContext(Player player) {
        Integer arenaId = playerArena.get(player);

        if (arenaId == null) {
            for (ArenaState state : arenas.values()) {
                if (state.getContext() != null && state.getContext().getPlayers().contains(player)) {
                    arenaId = state.getContext().getArenaId();
                    playerArena.put(player, arenaId);
                    break;
                }
            }
        }

        if (arenaId == null) {
            return null;
        }
        ArenaState state = arenas.get(arenaId);
        return state != null ? state.getContext() : null;
    }

    public ArenaState getArenaState(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        if (context == null) {
            return null;
        }
        return arenas.get(context.getArenaId());
    }

    public int getPlayerKills(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player) { return gameplayService.getPlayerKills(context, player); }

    public void addPlayerKill(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player) { gameplayService.addPlayerKill(context, player); }

    public int getPlayerDeaths(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player) { return gameplayService.getPlayerDeaths(context, player); }

    public void addPlayerDeath(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player) { gameplayService.addPlayerDeath(context, player); }

    public void healKiller(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player killer) { gameplayService.healKiller(context, killer); }

    public void handleKill(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player attacker, Player victim) { gameplayService.handleKill(context, attacker, victim); }

    public void handleNonCombatDeath(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player victim) { gameplayService.handleNonCombatDeath(context, victim); }

    public boolean handleBedBreak(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player breaker, Block block) { return gameplayService.handleBedBreak(context, breaker, block); }

    public boolean isBedLocation(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Location location) { return gameplayService.isBedLocation(context, location); }

    public boolean isSpawnerLocation(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Location location) { return gameplayService.isSpawnerLocation(context, location); }

    public boolean canPlayerRespawn(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player) { return gameplayService.canPlayerRespawn(context, player); }

    public void respawnPlayer(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player) { gameplayService.respawnPlayer(context, player); }

    void teleportToTeamSpawn(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, ArenaState state, Player player) {
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            throw new IllegalStateException("BedWars requires TeamsAPI enabled to teleport players to team spawns.");
        }
        TeamInfo<Player, Material> team = teamsAPI.getTeam(player);
        if (team == null) {
            throw new IllegalStateException("Player '" + player.getName() + "' has no assigned team. Team spawn teleport cannot continue.");
        }
        Location spawn = state.getTeamSpawn(team.getId());
        if (spawn == null) {
            throw new IllegalStateException("Missing configured team spawn for team '" + team.getId() + "' (player '" + player.getName() + "'). Refusing fallback to legacy spawn.");
        }
        player.teleport(centerSpawnLocation(spawn));
    }

    Location centerSpawnLocation(Location spawn) {
        return new Location(spawn.getWorld(), Math.floor(spawn.getX()) + 0.5, spawn.getY(), Math.floor(spawn.getZ()) + 0.5, spawn.getYaw(), spawn.getPitch());
    }

    public boolean isInRestrictedZone(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player, Location location) {
        return gameplayService.isInRestrictedZone(context, player, location);
    }

    public ArmoryService getArmoryService() {
        return armoryService;
    }

    public BedService getBedService() {
        return bedService;
    }

    public SpawnerService getSpawnerService() {
        return spawnerService;
    }

    public ShopNpcService getShopNpcService() {
        return shopNpcService;
    }

    public ShopService getShopService() {
        return shopService;
    }

    public TeamUpgradeService getUpgradeService() {
        return upgradeService;
    }

    public boolean handleMenuAction(Player player, String payload) {
        if (payload.startsWith("vote")) {
            String[] args = payload.trim().split("\\s+");
            return handleVoteCommand(player, args);
        }
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getContext(player);
        if (context == null || !context.isPlayerPlaying(player)) {
            return false;
        }
        ArenaState state = getArenaState(context);
        if (state == null) return false;

        if (payload.startsWith("shop:")) {
            return shopService.handleAction(player, payload, context, state);
        }
        if (payload.startsWith("upgrade:")) {
            return upgradeService.handleAction(player, payload, context, state);
        }
        return false;
    }

    public void onPlayerQuit(Player player) {
        if (player == null) {
            return;
        }

        @SuppressWarnings("unchecked")
        PlayerUtil<Player> playerUtil = (PlayerUtil<Player>) ModuleAPI.getPlayerUtil();
        Integer waitingArenaId = playerUtil != null ? playerUtil.getPlayerArena(player) : null;
        if (waitingArenaId == null) {
            waitingArenaId = playerArena.get(player);
        }
        if (voteService != null && waitingArenaId != null) {
            voteService.clearWaitingVote(waitingArenaId, player.getUniqueId());
        }

        Integer activeArenaId = playerArena.get(player);
        if (activeArenaId != null) {
            ArenaState state = arenas.get(activeArenaId);
            if (state != null) {
                VoteState voteState = state.getVoteState();
                if (voteState != null) {
                    voteState.clearPlayerVotes(player.getUniqueId());
                }
            }
        }

        playerArena.remove(player);
    }

    public boolean handleVoteCommand(Player player, String[] args) {
        if (voteService == null || player == null) {
            return false;
        }

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getContext(player);
        ArenaState state = context != null ? getArenaState(context) : null;
        if (context == null || state == null) {
            return voteService.handleVoteCommandWithoutContext(player, args);
        }

        GamePhase phase = context.getPhase();
        if (phase == GamePhase.PLAYING || phase == GamePhase.ENDING || phase == GamePhase.FINISHED) {
            return false;
        }
        return voteService.handleVoteCommand(player, context, state, args != null ? args : new String[0]);
    }

    public SpecialItemHandler getSpecialItemHandler() {
        return specialItemHandler;
    }

    public void openArenaEnderChest(Player player,
                                    GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        String teamId = "solo";
        if (teamsAPI != null && teamsAPI.isEnabled()) {
            TeamInfo<Player, Material> team = teamsAPI.getTeam(player);
            if (team != null && team.getId() != null) {
                teamId = team.getId().toLowerCase();
            }
        }

        Map<String, ItemStack[]> chests = arenaEnderChests.computeIfAbsent(arenaId, k -> new ConcurrentHashMap<>());
        ItemStack[] contents = chests.get(teamId);

        String configuredTitle = moduleConfig.getString("inventories.ender_chest_title", "<dark_purple>Ender Chest</dark_purple>");
        String title = context.getItemAPI().formatInventoryTitle(configuredTitle);
        Inventory inv = Bukkit.createInventory(new ArenaEnderChestHolder(arenaId, teamId), 27, title);

        if (contents != null) {
            inv.setContents(contents.clone());
        }

        player.openInventory(inv);
    }

    public void saveArenaEnderChest(String teamId, int arenaId, ItemStack[] contents) {
        Map<String, ItemStack[]> chests = arenaEnderChests.get(arenaId);
        if (chests == null) {
            return;
        }
        if (contents == null) {
            chests.remove(teamId);
        } else {
            chests.put(teamId, contents.clone());
        }
    }

    public void endGame(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        ArenaState state = getArenaState(context);
        if (state == null) {
            return;
        }

        outcomeService.endGame(context, state);
    }

    public ModuleConfigAPI getModuleConfig() {
        return moduleConfig;
    }

    public ModuleInfo getModuleInfo() {
        return moduleInfo;
    }

    public void removePlayersFromArena(int arenaId, List<Player> players) {
        for (Player player : players) {
            playerArena.remove(player);
            shopService.removePlayer(arenaId, player);
        }
    }

    private void resetWorldDefaults(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        if (context == null || context.getArenaAPI() == null) {
            return;
        }
        World world = context.getArenaAPI().getWorld();
        if (world == null) {
            return;
        }
        world.setTime(1000L);
        world.setStorm(false);
        world.setThundering(false);
    }

    private void resetPlayerHearts(List<Player> players) {
        if (players == null) {
            return;
        }
        Object maxHealthAttribute = resolveMaxHealthAttribute();
        for (Player player : players) {
            if (player == null) {
                continue;
            }
            if (maxHealthAttribute != null) {
                try {
                    java.lang.reflect.Method getAttribute = player.getClass().getMethod("getAttribute", maxHealthAttribute.getClass());
                    Object attributeInstance = getAttribute.invoke(player, maxHealthAttribute);
                    if (attributeInstance != null) {
                        attributeInstance.getClass().getMethod("setBaseValue", double.class).invoke(attributeInstance, 20.0);
                    }
                } catch (ReflectiveOperationException | LinkageError ignored) {
                }
            }
            player.setHealth(Math.min(player.getHealth(), 20.0));
        }
    }

    private Object resolveMaxHealthAttribute() {
        Object attr = resolveAttributeConstant("MAX_HEALTH");
        return attr != null ? attr : resolveAttributeConstant("GENERIC_MAX_HEALTH");
    }

    private Object resolveAttributeConstant(String fieldName) {
        try {
            Class<?> attributeClass = Class.forName("org.bukkit.attribute.Attribute");
            Object value = attributeClass.getField(fieldName).get(null);
            if (attributeClass.isInstance(value)) {
                return value;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return null;
    }

    public String getScoreboardPath(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) { return runtimeService.getScoreboardPath(context); }

    public boolean isSoloMode(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) { return runtimeService.isSoloMode(context); }

    public List<String> getAliveTeamIds(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) { return runtimeService.getAliveTeamIds(context); }

    public Map<String, Integer> getTeamKills(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) { return runtimeService.getTeamKills(context); }

    public Map<String, Integer> getTeamDeaths(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) { return runtimeService.getTeamDeaths(context); }

    public List<Player> getTeamPlayers(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, String teamId) { return runtimeService.getTeamPlayers(context, teamId); }

    public void checkForVictory(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) { runtimeService.checkForVictory(context); }

    public void trackPlacedBlock(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Location location) { runtimeService.trackPlacedBlock(context, location); }

    public boolean canBreakBlock(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Block block) { return runtimeService.canBreakBlock(context, block); }

    public void untrackPlacedBlock(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Location location) { runtimeService.untrackPlacedBlock(context, location); }


    private static String formatCountdownTime(int seconds) {
        int safeSeconds = Math.max(0, seconds);
        return String.format("%02d:%02d", safeSeconds / 60, safeSeconds % 60);
    }

}
