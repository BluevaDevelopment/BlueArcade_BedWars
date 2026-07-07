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
import net.blueva.arcade.api.setup.ModuleSetupCommand;
import net.blueva.arcade.api.setup.ModuleSetupMetadata;
import net.blueva.arcade.api.setup.ModuleSetupStep;
import net.blueva.arcade.api.setup.ModuleSetupStatusCheck;
import java.util.List;

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
                    moduleConfig.getTranslation(null, "vote_menu.name"),
                    moduleConfig.getTranslationList(null, "vote_menu.lore")
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
        moduleConfig.register("settings.yml");
        moduleConfig.register("achievements.yml");
        moduleConfig.register("store.yml");
        moduleConfig.registerCopyOnly("cage.yml");
        moduleConfig.register("menus/java/bed_wars_shop.yml");
        moduleConfig.register("menus/java/bed_wars_upgrades.yml");
        moduleConfig.register("menus/bedrock/bed_wars_shop.yml");
        moduleConfig.register("menus/bedrock/bed_wars_upgrades.yml");
        moduleConfig.register("menus/java/bed_wars_vote_main.yml");
        moduleConfig.register("menus/java/bed_wars_vote_hearts.yml");
        moduleConfig.register("menus/java/bed_wars_vote_time.yml");
        moduleConfig.register("menus/java/bed_wars_vote_weather.yml");
        moduleConfig.register("menus/bedrock/bed_wars_vote_main.yml");
        moduleConfig.register("menus/bedrock/bed_wars_vote_hearts.yml");
        moduleConfig.register("menus/bedrock/bed_wars_vote_time.yml");
        moduleConfig.register("menus/bedrock/bed_wars_vote_weather.yml");
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

        statsAPI.registerModuleStat(moduleInfo.getId(), new StatDefinition("wins", moduleConfig.getTranslation(null, "stats.labels.wins"), moduleConfig.getTranslation(null, "stats.descriptions.wins"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(), new StatDefinition("games_played", moduleConfig.getTranslation(null, "stats.labels.games_played"), moduleConfig.getTranslation(null, "stats.descriptions.games_played"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(), new StatDefinition("kills", moduleConfig.getTranslation(null, "stats.labels.kills"), moduleConfig.getTranslation(null, "stats.descriptions.kills"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(), new StatDefinition("deaths", moduleConfig.getTranslation(null, "stats.labels.deaths"), moduleConfig.getTranslation(null, "stats.descriptions.deaths"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(), new StatDefinition("beds_broken", moduleConfig.getTranslation(null, "stats.labels.beds_broken"), moduleConfig.getTranslation(null, "stats.descriptions.beds_broken"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(), new StatDefinition("final_kills", moduleConfig.getTranslation(null, "stats.labels.final_kills"), moduleConfig.getTranslation(null, "stats.descriptions.final_kills"), StatScope.MODULE));
    }

    private void registerAchievements() {
        AchievementsAPI achievementsAPI = ModuleAPI.getAchievementsAPI();
        if (achievementsAPI != null) {
            achievementsAPI.registerModuleAchievements(moduleInfo.getId(), "achievements.yml");
        }
    }

    @Override
    public ModuleSetupMetadata getSetupMetadata() {
        return new ModuleSetupMetadata() {

            @Override
            public List<ModuleSetupStep> getSetupSteps() {
                return List.of(
                        new ModuleSetupStep("bed", true, "Configure Bed", "Configure the module-specific bed setup data.", List.of("/baa game <arena> bed_wars bed"), "team bed location"),
                        new ModuleSetupStep("npc", true, "Configure Npc", "Configure the module-specific npc setup data.", List.of("/baa game <arena> bed_wars npc"), "NPC type and location"),
                        new ModuleSetupStep("region", true, "Configure Region", "Configure the module-specific region setup data.", List.of("/baa game <arena> bed_wars region"), "selection region"),
                        new ModuleSetupStep("spawner", true, "Configure Spawner", "Configure the module-specific spawner setup data.", List.of("/baa game <arena> bed_wars spawner"), "spawner type and location"),
                        new ModuleSetupStep("team", true, "Configure Team", "Configure the module-specific team setup data.", List.of("/baa game <arena> bed_wars team"), "team count and team size")
                );
            }

            @Override
            public List<ModuleSetupCommand> getSetupCommands() {
                return List.of(
                        new ModuleSetupCommand("bed", "/baa game <arena> bed_wars bed", "Configure bed setup data.", true),
                        new ModuleSetupCommand("npc", "/baa game <arena> bed_wars npc", "Configure npc setup data.", true),
                        new ModuleSetupCommand("region", "/baa game <arena> bed_wars region", "Configure region setup data.", true),
                        new ModuleSetupCommand("spawner", "/baa game <arena> bed_wars spawner", "Configure spawner setup data.", true),
                        new ModuleSetupCommand("team", "/baa game <arena> bed_wars team", "Configure team setup data.", true)
                );
            }

            @Override
            public List<ModuleSetupStatusCheck<?, ?, ?>> getStatusChecks() {
                return List.of(
                        new ModuleSetupStatusCheck<>("bed", true, "Set at least one team bed.", context -> context.getData().has("game.play_area.beds")),
                        new ModuleSetupStatusCheck<>("npc", true, "Add at least one shop NPC.", context -> context.getData().has("game.play_area.npc_registry") || context.getData().has("game.npc_registry")),
                        new ModuleSetupStatusCheck<>("region", true, "Select the play area region.", context -> (context.getData().has("game.play_area.bounds.min.x") && context.getData().has("game.play_area.bounds.max.x")) || (context.getData().has("game.region.bounds.min.x") && context.getData().has("game.region.bounds.max.x"))),
                        new ModuleSetupStatusCheck<>("spawner", true, "Add at least one resource spawner.", context -> context.getData().has("game.play_area.spawner_registry") || context.getData().has("game.spawner_registry")),
                        new ModuleSetupStatusCheck<>("team", true, "Set team count and team size.", context -> context.getData().getInt("teams.count", 0) > 0 && context.getData().getInt("teams.size", 0) > 0)
                );
            }
        };
    }

}
