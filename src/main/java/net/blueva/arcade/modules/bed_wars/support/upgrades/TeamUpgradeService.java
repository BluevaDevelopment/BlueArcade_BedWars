package net.blueva.arcade.modules.bed_wars.support.upgrades;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.team.TeamInfo;
import net.blueva.arcade.api.team.TeamsAPI;
import net.blueva.arcade.api.ui.MenuAPI;
import net.blueva.arcade.api.ui.menu.BedrockButtonDefinition;
import net.blueva.arcade.api.ui.menu.BedrockSimpleMenuDefinition;
import net.blueva.arcade.api.ui.menu.JavaItemDefinition;
import net.blueva.arcade.api.ui.menu.JavaMenuItem;
import net.blueva.arcade.api.ui.menu.MenuDefinition;
import net.blueva.arcade.modules.bed_wars.game.BedWarsGame;
import net.blueva.arcade.modules.bed_wars.state.ArenaState;
import net.blueva.arcade.modules.bed_wars.support.shop.ShopService;
import net.blueva.arcade.modules.bed_wars.support.spawner.SpawnerType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TeamUpgradeService {

    private final ModuleConfigAPI moduleConfig;
    private final MenuAPI<Player, Material> menuAPI;
    private final BedWarsGame game;
    private final Map<String, UpgradeDefinition> upgrades = new ConcurrentHashMap<>();
    private final Map<Integer, Map<String, Integer>> teamUpgradeTiers = new ConcurrentHashMap<>();

    private static final String UPGRADES_FILE = "menus/java/bed_wars_upgrades.yml";
    private static final String BEDROCK_UPGRADES_FILE = "menus/bedrock/bed_wars_upgrades.yml";
    private static final String BEDROCK_IMAGE_BASE = "https://blueva.net/api/menu/items/1_21_10";
    private int menuSize = 27;
    private Material emptyMaterial = Material.BLACK_STAINED_GLASS_PANE;
    private int emptyAmount = 1;
    private String emptyName = " ";
    private List<String> emptyLore = List.of();

    public TeamUpgradeService(ModuleConfigAPI moduleConfig, MenuAPI<Player, Material> menuAPI, BedWarsGame game) {
        this.moduleConfig = moduleConfig;
        this.menuAPI = menuAPI;
        this.game = game;
        loadUpgrades();
    }

    public void loadUpgrades() {
        upgrades.clear();
        String file = UPGRADES_FILE;
        menuSize = normalizeMenuSize(moduleConfig.getIntFrom(file, "menu_size", moduleConfig.getIntFrom(file, "size", 27)));
        emptyMaterial = parseMaterial(moduleConfig.getStringFrom(file, "empty_item.item_stack.material", "BLACK_STAINED_GLASS_PANE"));
        emptyAmount = Math.max(1, moduleConfig.getIntFrom(file, "empty_item.item_stack.amount", 1));
        emptyName = moduleConfig.getStringFrom(file, "empty_item.name", " ");
        emptyLore = moduleConfig.getStringListFrom(file, "empty_item.lore");

        int upgIndex = 1;
        while (true) {
            String base = "team_upgrades.upgrades." + upgIndex;
            String id = moduleConfig.getStringFrom(file, base + ".id", null);
            if (id == null) break;

            Material icon = parseMaterial(moduleConfig.getStringFrom(file, base + ".icon", "STONE"));
            int amount = Math.max(1, moduleConfig.getIntFrom(file, base + ".amount", 1));
            String name = moduleConfig.getStringFrom(file, base + ".name", id);
            List<String> lore = moduleConfig.getStringListFrom(file, base + ".lore");
            int slot = Math.max(0, Math.min(menuSize - 1, moduleConfig.getIntFrom(file, base + ".slot", upgIndex - 1)));

            List<UpgradeTier> tiers = new ArrayList<>();
            int tierIndex = 1;
            while (true) {
                String tierBase = base + ".tiers." + tierIndex;
                int cost = moduleConfig.getIntFrom(file, tierBase + ".cost", -1);
                if (cost < 0) break;

                Material currency = parseCurrency(moduleConfig.getStringFrom(file, tierBase + ".currency", "diamond"));
                List<String> actions = new ArrayList<>();
                int actionIndex = 1;
                while (true) {
                    String action = moduleConfig.getStringFrom(file, tierBase + ".actions." + actionIndex, null);
                    if (action == null) break;
                    actions.add(action);
                    actionIndex++;
                }

                tiers.add(new UpgradeTier(tierIndex, cost, currency, actions));
                tierIndex++;
            }

            upgrades.put(id, new UpgradeDefinition(id, icon, amount, name, lore, slot, tiers));
            upgIndex++;
        }
    }

    public void openUpgradesMenu(Player player, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, ArenaState state) {
        if (upgrades.isEmpty() || menuAPI == null) {
            context.getMessagesAPI().sendRaw(player, lang("messages.upgrade.not_configured", "<red>Upgrades are not configured.</red>"));
            return;
        }

        if (isBedrockPlayer(player)) {
            openBedrockUpgradesMenu(player, context, state);
            return;
        }

        List<JavaMenuItem<Material>> items = new ArrayList<>();


        for (int i = 0; i < menuSize; i++) {
            items.add(JavaMenuItem.of(i, JavaItemDefinition.of(emptyMaterial, emptyAmount, emptyName, emptyLore, List.of())));
        }


        for (UpgradeDefinition upgrade : upgrades.values()) {
            JavaItemDefinition<Material> def = buildUpgradeDefinition(player, context, state, upgrade);
            items.add(JavaMenuItem.of(upgrade.slot(), def));
        }

        String title = moduleConfig.getStringFrom(UPGRADES_FILE, "menu_name", "<dark_gray>Team Upgrades");
        MenuDefinition<Material> menu = new MenuDefinition<>(title, menuSize, items, null);
        menuAPI.openMenu(player, menu, Map.of());
    }

    private void openBedrockUpgradesMenu(Player player, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, ArenaState state) {
        List<BedrockButtonDefinition> buttons = new ArrayList<>();
        List<UpgradeDefinition> sortedUpgrades = new ArrayList<>(upgrades.values());
        sortedUpgrades.sort(Comparator.comparingInt(UpgradeDefinition::slot));
        for (UpgradeDefinition upgrade : sortedUpgrades) {
            buttons.add(buildBedrockUpgradeButton(player, context, state, upgrade));
        }

        String title = moduleConfig.getStringFrom(BEDROCK_UPGRADES_FILE, "menuName",
                moduleConfig.getStringFrom(BEDROCK_UPGRADES_FILE, "menu_name",
                        moduleConfig.getStringFrom(UPGRADES_FILE, "menu_name", "<dark_gray>Team Upgrades")));
        List<String> content = moduleConfig.getStringListFrom(BEDROCK_UPGRADES_FILE, "content");
        if (content.isEmpty()) {
            content = List.of("<white>Team upgrades apply to every member of your team.</white>");
        }

        BedrockSimpleMenuDefinition bedrockMenu = new BedrockSimpleMenuDefinition(title, content, buttons);
        MenuDefinition<Material> menu = new MenuDefinition<>(title, menuSize, List.of(), bedrockMenu);
        menuAPI.openMenu(player, menu, Map.of());
    }

    private BedrockButtonDefinition buildBedrockUpgradeButton(Player player, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, ArenaState state, UpgradeDefinition upgrade) {
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        int currentTier = 0;
        if (teamsAPI != null && teamsAPI.isEnabled()) {
            TeamInfo<Player, Material> team = teamsAPI.getTeam(player);
            if (team != null) {
                currentTier = getTeamTier(context.getArenaId(), team.getId().toLowerCase(), upgrade.id());
            }
        }

        boolean maxed = currentTier >= upgrade.tiers().size();
        UpgradeTier next = maxed ? null : upgrade.tiers().get(currentTier);
        boolean canAfford = next != null && ShopService.calculateMoney(player, next.currency()) >= next.cost();
        String color = maxed ? "<aqua>" : (canAfford ? "<green>" : "<red>");
        String status = maxed
                ? bedrockText("templates.content.maxed_line", "<aqua>MAXED OUT</aqua>")
                : canAfford
                ? bedrockText("templates.content.click_to_purchase_line", "<green>Click to purchase!</green>")
                : bedrockText("templates.content.not_enough_resources_line", "<red>Not enough resources!</red>");
        String cost = next == null ? "-" : String.valueOf(next.cost());
        String currency = next == null ? "" : formatCurrencyName(next.currency());

        String text = bedrockText("templates.upgrade_button.text",
                        "{color}{upgrade}\\n<gray>Next: {cost} {currency}</gray>\\n{status}")
                .replace("\\n", "\n")
                .replace("{color}", color)
                .replace("{upgrade}", upgrade.name())
                .replace("{cost}", cost)
                .replace("{currency}", currency)
                .replace("{status}", status);
        String image = bedrockUpgradeImage(upgrade);
        List<String> actions = maxed ? List.of() : List.of("MODULE;" + game.getModuleInfo().getId() + ";upgrade:" + upgrade.id());
        return BedrockButtonDefinition.of(text, image, actions);
    }

    private boolean isBedrockPlayer(Player player) {
        try {
            return menuAPI != null && menuAPI.isBedrockPlayer(player);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public boolean handleAction(Player player, String payload,
                                GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                ArenaState state) {
        if (!payload.startsWith("upgrade:")) return false;
        String upgradeId = payload.substring("upgrade:".length());
        UpgradeDefinition upgrade = upgrades.get(upgradeId);
        if (upgrade == null) return false;
        boolean purchased = purchaseUpgrade(player, context, state, upgrade);
        if (isBedrockPlayer(player) && !purchased) {
            return true;
        }
        openUpgradesMenu(player, context, state);
        return true;
    }

    private boolean purchaseUpgrade(Player player, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, ArenaState state, UpgradeDefinition upgrade) {
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            context.getMessagesAPI().sendRaw(player, lang("messages.upgrade.teams_disabled", "<red>Teams are not enabled.</red>"));
            return false;
        }

        TeamInfo<Player, Material> team = teamsAPI.getTeam(player);
        if (team == null) {
            context.getMessagesAPI().sendRaw(player, lang("messages.upgrade.no_team", "<red>You are not in a team.</red>"));
            return false;
        }

        String teamId = team.getId().toLowerCase();
        int currentTier = getTeamTier(context.getArenaId(), teamId, upgrade.id());

        if (currentTier >= upgrade.tiers().size()) {
            context.getMessagesAPI().sendRaw(player, lang("messages.upgrade.maxed", "<red>This upgrade is already maxed out.</red>"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
            return false;
        }

        UpgradeTier nextTier = upgrade.tiers().get(currentTier);
        int money = ShopService.calculateMoney(player, nextTier.currency());
        if (money < nextTier.cost()) {
            String msg = moduleConfig.getTranslation(player, "messages.shop.insufficient_money");
            if (msg != null) {
                msg = msg.replace("{price}", String.valueOf(nextTier.cost())).replace("{currency}", formatCurrencyName(nextTier.currency()));
                context.getMessagesAPI().sendRaw(player, msg);
            }
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
            return false;
        }

        ShopService.takeMoney(player, nextTier.currency(), nextTier.cost());
        setTeamTier(context.getArenaId(), teamId, upgrade.id(), currentTier + 1);

        for (String action : nextTier.actions()) {
            applyAction(player, context, state, team, action);
        }

        String msg = moduleConfig.getTranslation(player, "messages.upgrade.purchased");
        if (msg != null) {
            msg = msg.replace("{player}", player.getName()).replace("{upgrade}", upgrade.name()).replace("{tier}", String.valueOf(currentTier + 1));
            for (Player p : team.getPlayers()) {
                context.getMessagesAPI().sendRaw(p, msg);
            }
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
        return true;
    }

    private void applyAction(Player buyer, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, ArenaState state, TeamInfo<Player, Material> team, String action) {
        String[] parts = action.split(":");
        if (parts.length < 2) return;

        String type = parts[0].trim().toLowerCase();
        String data = parts[1].trim();
        String teamId = team.getId().toLowerCase();

        switch (type) {
            case "player-effect" -> {
                String[] effData = data.split(",");
                if (effData.length >= 3) {
                    PotionEffectType effectType = resolvePotionEffectType(effData[0]);
                    if (effectType != null) {
                        int amplifier = Integer.parseInt(effData[1]);
                        int duration = Integer.parseInt(effData[2]);
                        if (duration <= 0) duration = Integer.MAX_VALUE;
                        state.getTeamEffects(teamId).add(new ArenaState.TeamPotionEffect(effectType.getName(), duration, amplifier));
                        for (Player p : team.getPlayers()) {
                            p.addPotionEffect(new PotionEffect(effectType, duration, amplifier, true, true), true);
                        }
                    }
                }
            }
            case "generator-speed" -> {
                String[] genData = data.split(",");
                if (genData.length >= 2) {
                    String genType = genData[0].toLowerCase();
                    double multiplier = Double.parseDouble(genData[1]);
                    switch (genType) {
                        case "iron" -> state.setSpawnerMultiplier(teamId, SpawnerType.IRON, multiplier);
                        case "gold" -> state.setSpawnerMultiplier(teamId, SpawnerType.GOLD, multiplier);
                        case "diamond" -> state.setSpawnerMultiplier(teamId, SpawnerType.DIAMOND, multiplier);
                        case "emerald" -> state.setSpawnerMultiplier(teamId, SpawnerType.EMERALD, multiplier);
                    }
                }
            }
            case "enchant-team" -> {
                String[] encData = data.split(",");
                if (encData.length >= 3) {
                    String encName = encData[0].toLowerCase();
                    Enchantment enc = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(encName));
                    if (enc == null) {
                        try { enc = Enchantment.getByName(encData[0].toUpperCase()); } catch (Exception ignored) {}
                    }
                    if (enc != null) {
                        int level = Integer.parseInt(encData[1]);
                        String target = encData[2].toLowerCase();
                        switch (target) {
                            case "sword" -> state.getTeamSwordEnchantments(teamId).add(new ArenaState.TeamEnchantment(encName, level));
                            case "armor" -> state.getTeamArmorEnchantments(teamId).add(new ArenaState.TeamEnchantment(encName, level));
                            case "bow" -> state.getTeamBowEnchantments(teamId).add(new ArenaState.TeamEnchantment(encName, level));
                        }
                        for (Player p : team.getPlayers()) {
                            for (ItemStack item : p.getInventory().getContents()) {
                                if (item == null) continue;
                                boolean shouldEnchant = switch (target) {
                                    case "sword" -> item.getType().name().contains("SWORD");
                                    case "armor" -> item.getType().name().contains("HELMET") || item.getType().name().contains("CHESTPLATE") || item.getType().name().contains("LEGGINGS") || item.getType().name().contains("BOOTS");
                                    case "bow" -> item.getType() == Material.BOW;
                                    case "pickaxe" -> item.getType().name().contains("PICKAXE");
                                    case "axe" -> item.getType().name().contains("AXE");
                                    default -> false;
                                };
                                if (shouldEnchant) {
                                    item.addUnsafeEnchantment(enc, level);
                                }
                            }
                            p.updateInventory();
                        }
                    }
                }
            }
        }
    }

    private JavaItemDefinition<Material> buildUpgradeDefinition(Player player, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, ArenaState state, UpgradeDefinition upgrade) {
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        int currentTier = 0;
        if (teamsAPI != null && teamsAPI.isEnabled()) {
            TeamInfo<Player, Material> team = teamsAPI.getTeam(player);
            if (team != null) {
                currentTier = getTeamTier(context.getArenaId(), team.getId().toLowerCase(), upgrade.id());
            }
        }

        boolean maxed = currentTier >= upgrade.tiers().size();
        boolean canAfford = false;
        if (!maxed) {
            UpgradeTier next = upgrade.tiers().get(currentTier);
            canAfford = ShopService.calculateMoney(player, next.currency()) >= next.cost();
        }

        String color = maxed ? "<aqua>" : (canAfford ? "<green>" : "<red>");
        String displayName = color + upgrade.name();

        List<String> lore = new ArrayList<>(upgrade.lore());
        for (int i = 0; i < upgrade.tiers().size(); i++) {
            UpgradeTier tier = upgrade.tiers().get(i);
            String tierColor = i < currentTier ? "<green>" : (i == currentTier ? "<yellow>" : "<gray>");
            lore.add(menuText("templates.content.tier_line", "{color}Tier {tier} <gray>- {cost} {currency}</gray>")
                    .replace("{color}", tierColor)
                    .replace("{tier}", toRoman(i + 1))
                    .replace("{cost}", String.valueOf(tier.cost()))
                    .replace("{currency}", formatCurrencyName(tier.currency())));
        }

        if (maxed) {
            lore.add(menuText("templates.content.maxed_line", "<aqua>MAXED OUT</aqua>"));
        } else if (canAfford) {
            lore.add(menuText("templates.content.click_to_purchase_line", "<green>Click to purchase!</green>"));
        } else {
            lore.add(menuText("templates.content.not_enough_resources_line", "<red>Not enough resources!</red>"));
        }

        List<String> actions = List.of("MODULE;" + game.getModuleInfo().getId() + ";upgrade:" + upgrade.id());
        return JavaItemDefinition.of(upgrade.icon(), upgrade.amount(), displayName, lore, actions);
    }

    private int getTeamTier(int arenaId, String teamId, String upgradeId) {
        return teamUpgradeTiers
                .computeIfAbsent(arenaId, k -> new ConcurrentHashMap<>())
                .getOrDefault(teamId + ":" + upgradeId, 0);
    }

    private void setTeamTier(int arenaId, String teamId, String upgradeId, int tier) {
        teamUpgradeTiers
                .computeIfAbsent(arenaId, k -> new ConcurrentHashMap<>())
                .put(teamId + ":" + upgradeId, tier);
    }

    private String formatCurrencyName(Material currency) {
        return switch (currency) {
            case IRON_INGOT -> lang("messages.shop.currencies.iron", "Iron");
            case GOLD_INGOT -> lang("messages.shop.currencies.gold", "Gold");
            case DIAMOND -> lang("messages.shop.currencies.diamond", "Diamond");
            case EMERALD -> lang("messages.shop.currencies.emerald", "Emerald");
            default -> currency.name();
        };
    }

    private String lang(String path, String fallback) {
        return moduleConfig.getTranslation(null, path);
    }

    private String menuText(String path, String fallback) {
        return moduleConfig.getStringFrom(UPGRADES_FILE, path, fallback);
    }

    private String bedrockText(String path, String fallback) {
        return moduleConfig.getStringFrom(BEDROCK_UPGRADES_FILE, path, fallback);
    }

    private String bedrockUpgradeImage(UpgradeDefinition upgrade) {
        for (int index = 1; index <= 256; index++) {
            String base = "team_upgrades.upgrades." + index;
            String id = moduleConfig.getStringFrom(BEDROCK_UPGRADES_FILE, base + ".id", null);
            if (upgrade.id().equals(id)) {
                String image = moduleConfig.getStringFrom(BEDROCK_UPGRADES_FILE, base + ".image", null);
                if (image != null && !image.isBlank()) {
                    return image;
                }
            }
        }
        return materialImage(upgrade.icon());
    }

    private String materialImage(Material material) {
        return BEDROCK_IMAGE_BASE + "/minecraft_" + material.name().toLowerCase() + ".png";
    }

    private int normalizeMenuSize(int configuredSize) {
        int size = Math.max(9, Math.min(54, configuredSize));
        return size % 9 == 0 ? size : ((size / 9) + 1) * 9;
    }

    private Material parseMaterial(String name) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.STONE;
        }
    }

    private Material parseCurrency(String name) {
        return switch (name.toLowerCase()) {
            case "iron" -> Material.IRON_INGOT;
            case "gold" -> Material.GOLD_INGOT;
            case "diamond" -> Material.DIAMOND;
            case "emerald" -> Material.EMERALD;
            default -> Material.IRON_INGOT;
        };
    }

    private String toRoman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    public void clearArena(int arenaId) {
        teamUpgradeTiers.remove(arenaId);
    }

    private static PotionEffectType resolvePotionEffectType(String name) {
        if (name == null || name.isBlank()) return null;
        String normalized = name.trim().toUpperCase();


        PotionEffectType type = PotionEffectType.getByName(normalized);
        if (type != null) return type;


        String alias = switch (normalized) {
            case "FAST_DIGGING" -> "HASTE";
            case "HASTE" -> "FAST_DIGGING";
            case "SLOW_DIGGING" -> "MINING_FATIGUE";
            case "MINING_FATIGUE" -> "SLOW_DIGGING";
            case "INCREASE_DAMAGE" -> "STRENGTH";
            case "STRENGTH" -> "INCREASE_DAMAGE";
            case "HEAL" -> "INSTANT_HEALTH";
            case "INSTANT_HEALTH" -> "HEAL";
            case "HARM" -> "INSTANT_DAMAGE";
            case "INSTANT_DAMAGE" -> "HARM";
            case "JUMP" -> "JUMP_BOOST";
            case "JUMP_BOOST" -> "JUMP";
            case "CONFUSION" -> "NAUSEA";
            case "NAUSEA" -> "CONFUSION";
            case "SLOW" -> "SLOWNESS";
            case "SLOWNESS" -> "SLOW";
            default -> null;
        };
        if (alias != null) {
            type = PotionEffectType.getByName(alias);
            if (type != null) return type;
        }


        try {
            return (PotionEffectType) PotionEffectType.class.getField(normalized).get(null);
        } catch (Exception ignored) {}


        if (alias != null) {
            try {
                return (PotionEffectType) PotionEffectType.class.getField(alias).get(null);
            } catch (Exception ignored) {}
        }

        return null;
    }
}
