package net.blueva.arcade.modules.bed_wars;

import net.blueva.arcade.api.ModuleAPI;
import net.blueva.arcade.api.achievements.AchievementsAPI;
import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.events.CustomEventRegistry;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GameModule;
import net.blueva.arcade.api.game.GameResult;
import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.setup.SetupRequirement;
import net.blueva.arcade.api.stats.StatDefinition;
import net.blueva.arcade.api.stats.StatScope;
import net.blueva.arcade.api.stats.StatsAPI;
import net.blueva.arcade.api.store.StoreAPI;
import net.blueva.arcade.api.ui.ItemAPI;
import net.blueva.arcade.api.ui.MenuAPI;
import net.blueva.arcade.api.ui.VoteMenuAPI;
import net.blueva.arcade.modules.bed_wars.game.BedWarsGame;
import net.blueva.arcade.modules.bed_wars.listener.BedWarsListener;
import net.blueva.arcade.modules.bed_wars.listener.BedWarsVoteListener;
import net.blueva.arcade.modules.bed_wars.setup.BedWarsSetup;
import net.blueva.arcade.modules.bed_wars.support.store.BedWarsStoreService;
import net.blueva.arcade.modules.bed_wars.support.vote.BedWarsVoteService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;

public class BedWarsModule implements GameModule<Player, Location, World, Material, ItemStack, Sound, Block, Entity, Listener, EventPriority> {

    private ModuleConfigAPI moduleConfig;
    private CoreConfigAPI coreConfig;
    private ModuleInfo moduleInfo;
    private StatsAPI statsAPI;
    private MenuAPI<Player, Material> menuAPI;
    private ItemAPI<Player, ItemStack, Material> itemAPI;
    private BedWarsGame game;

    @Override
    public void onLoad() {
        moduleInfo = ModuleAPI.getModuleInfo("bed_wars");
        if (moduleInfo == null) {
            throw new IllegalStateException("ModuleInfo not available for BedWars module");
        }

        moduleConfig = ModuleAPI.getModuleConfig(moduleInfo.getId());
        coreConfig = ModuleAPI.getCoreConfig();
        statsAPI = ModuleAPI.getStatsAPI();
        menuAPI = ModuleAPI.getMenuAPI();
        @SuppressWarnings("unchecked")
        ItemAPI<Player, ItemStack, Material> resolvedItemAPI = (ItemAPI<Player, ItemStack, Material>) ModuleAPI.getItemAPI();
        itemAPI = resolvedItemAPI;

        registerConfigs();
        registerStats();
        registerAchievements();

        StoreAPI storeAPI = ModuleAPI.getStoreAPI();
        BedWarsStoreService storeService = new BedWarsStoreService(moduleConfig, storeAPI, moduleInfo);
        storeService.registerStoreItems();

        BedWarsVoteService voteService = new BedWarsVoteService(moduleConfig, menuAPI, itemAPI, moduleInfo.getId());
        game = new BedWarsGame(moduleInfo, moduleConfig, coreConfig, statsAPI, menuAPI, storeAPI, voteService);
        voteService.setGame(game);
        registerMenuActions();
        voteService.registerWaitingItem();
        voteService.registerClickHandler(game);
        ModuleAPI.getSetupAPI().registerHandler(moduleInfo.getId(), new BedWarsSetup(this));

        VoteMenuAPI voteMenu = ModuleAPI.getVoteMenuAPI();
        if (voteMenu != null) {
            voteMenu.registerGame(
                    moduleInfo.getId(),
                    Material.valueOf(moduleConfig.getString("menus.vote.item")),
                    moduleConfig.getStringFrom("language.yml", "vote_menu.name"),
                    moduleConfig.getStringListFrom("language.yml", "vote_menu.lore")
            );
        }
    }

    @Override
    public void onStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        game.startGame(context);
    }

    @Override
    public void onCountdownTick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                int secondsLeft) {
        game.handleCountdownTick(context, secondsLeft);
    }

    @Override
    public void onCountdownFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        game.handleCountdownFinish(context);
    }

    @Override
    public boolean freezePlayersOnCountdown() {
        return false;
    }

    @Override
    public boolean allowJoinInProgress() {
        return true;
    }

    @Override
    public Set<SetupRequirement> getDisabledRequirements() {
        return Set.of(SetupRequirement.SPAWNS, SetupRequirement.TIME);
    }

    @Override
    public void onGameStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        game.beginPlaying(context);
    }

    @Override
    public void onEnd(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                      GameResult<Player> result) {
        game.finishGame(context);
    }

    @Override
    public void onDisable() {
        if (game != null) {
            game.shutdown();
        }
        if (menuAPI != null && moduleInfo != null) {
            menuAPI.unregisterModuleMenuAPI(moduleInfo.getId());
            menuAPI.unregisterModuleMenuAPI("bed");
        }
        if (itemAPI != null) {
            itemAPI.unregisterWaitingItem("bed_wars_vote_settings");
            itemAPI.unregisterClickHandler("bed_wars_vote_settings");
        }
    }

    @Override
    public void registerEvents(CustomEventRegistry<Listener, EventPriority> registry) {
        registry.register(new BedWarsListener(game));
        registry.register(new BedWarsVoteListener(game));
    }

    @Override
    public Map<String, String> getCustomPlaceholders(Player player) {
        return game.getPlaceholders(player);
    }

    public ModuleConfigAPI getModuleConfig() {
        return moduleConfig;
    }

    public CoreConfigAPI getCoreConfig() {
        return coreConfig;
    }

    public ModuleInfo getModuleInfo() {
        return moduleInfo;
    }

    private void registerConfigs() {
        moduleConfig.register("language.yml", 1);
        moduleConfig.register("settings.yml", 1);
        moduleConfig.register("achievements.yml", 1);
        moduleConfig.register("store.yml", 1);
        moduleConfig.registerCopyOnly("cage.yml");
        moduleConfig.register("menus/java/bed_wars_shop.yml", 1);
        moduleConfig.register("menus/java/bed_wars_upgrades.yml", 1);
        moduleConfig.register("menus/bedrock/bed_wars_shop.yml", 2);
        moduleConfig.register("menus/bedrock/bed_wars_upgrades.yml", 2);
        moduleConfig.register("menus/java/bed_wars_vote_main.yml", 1);
        moduleConfig.register("menus/java/bed_wars_vote_hearts.yml", 1);
        moduleConfig.register("menus/java/bed_wars_vote_time.yml", 1);
        moduleConfig.register("menus/java/bed_wars_vote_weather.yml", 1);
        moduleConfig.register("menus/bedrock/bed_wars_vote_main.yml", 1);
        moduleConfig.register("menus/bedrock/bed_wars_vote_hearts.yml", 1);
        moduleConfig.register("menus/bedrock/bed_wars_vote_time.yml", 1);
        moduleConfig.register("menus/bedrock/bed_wars_vote_weather.yml", 1);
    }

    private void registerMenuActions() {
        if (menuAPI == null) {
            return;
        }
        menuAPI.registerModuleActionHandler(moduleInfo.getId(), (player, payload) -> {
            if (payload == null || payload.isBlank()) {
                return false;
            }
            return game.handleMenuAction(player, payload);
        });
    }

    private void registerStats() {
        if (statsAPI == null) {
            return;
        }

        statsAPI.registerModuleStat(moduleInfo.getId(), new StatDefinition("wins", moduleConfig.getStringFrom("language.yml", "stats.labels.wins", "Wins"), moduleConfig.getStringFrom("language.yml", "stats.descriptions.wins", "BedWars victories"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(), new StatDefinition("games_played", moduleConfig.getStringFrom("language.yml", "stats.labels.games_played", "Games Played"), moduleConfig.getStringFrom("language.yml", "stats.descriptions.games_played", "BedWars matches played"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(), new StatDefinition("kills", moduleConfig.getStringFrom("language.yml", "stats.labels.kills", "Eliminations"), moduleConfig.getStringFrom("language.yml", "stats.descriptions.kills", "Opponents eliminated in BedWars"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(), new StatDefinition("deaths", moduleConfig.getStringFrom("language.yml", "stats.labels.deaths", "Deaths"), moduleConfig.getStringFrom("language.yml", "stats.descriptions.deaths", "Times eliminated in BedWars"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(), new StatDefinition("beds_broken", moduleConfig.getStringFrom("language.yml", "stats.labels.beds_broken", "Beds Broken"), moduleConfig.getStringFrom("language.yml", "stats.descriptions.beds_broken", "Enemy beds destroyed in BedWars"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(), new StatDefinition("final_kills", moduleConfig.getStringFrom("language.yml", "stats.labels.final_kills", "Final Kills"), moduleConfig.getStringFrom("language.yml", "stats.descriptions.final_kills", "Opponents eliminated without bed in BedWars"), StatScope.MODULE));
    }

    private void registerAchievements() {
        AchievementsAPI achievementsAPI = ModuleAPI.getAchievementsAPI();
        if (achievementsAPI != null) {
            achievementsAPI.registerModuleAchievements(moduleInfo.getId(), "achievements.yml");
        }
    }
}
