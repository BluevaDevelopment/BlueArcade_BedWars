package net.blueva.arcade.modules.bed_wars.game;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.team.TeamInfo;
import net.blueva.arcade.api.team.TeamsAPI;
import net.blueva.arcade.modules.bed_wars.state.ArenaState;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

final class BedWarsGameplayService {

    private static final double CAGE_GUARD_MAX_DISTANCE_SQUARED = 2.25;
    private final BedWarsGame game;

    BedWarsGameplayService(BedWarsGame game) {
        this.game = game;
    }

    int getPlayerKills(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player) {
        ArenaState state = game.getArenaState(context);
        return state == null ? 0 : state.getKills(player.getUniqueId());
    }

    void addPlayerKill(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player) {
        ArenaState state = game.getArenaState(context);
        if (state != null) {
            state.addKill(player.getUniqueId());
        }
    }

    int getPlayerDeaths(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player) {
        ArenaState state = game.getArenaState(context);
        return state == null ? 0 : state.getDeaths(player.getUniqueId());
    }

    void addPlayerDeath(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player) {
        ArenaState state = game.getArenaState(context);
        if (state != null) {
            state.addDeath(player.getUniqueId());
        }
    }

    void healKiller(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player killer) {
        game.loadoutService.handleKillRegeneration(context, killer);
        context.getSoundsAPI().play(killer, game.coreConfig.getSound("sounds.in_game.respawn"));
    }

    void handleKill(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                    Player attacker,
                    Player victim) {
        game.combatService.handleKillCredit(context, attacker);
        game.combatService.handleElimination(context, victim, attacker);
    }

    void handleNonCombatDeath(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                              Player victim) {
        game.combatService.handleElimination(context, victim, null);
    }

    boolean handleBedBreak(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                           Player breaker,
                           Block block) {
        ArenaState state = game.getArenaState(context);
        if (state == null) {
            return false;
        }
        boolean broken = game.bedService.handleBedBreak(context, state, breaker, block);
        if (broken) {
            if (game.statsAPI != null) {
                game.statsAPI.addModuleStat(breaker, game.moduleInfo.getId(), "beds_broken", 1);
            }
            game.checkForVictory(context);
        }
        return broken;
    }

    boolean isBedLocation(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                          Location location) {
        return game.bedService.isBedLocation(game.getArenaState(context), location);
    }

    boolean isSpawnerLocation(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                              Location location) {
        return game.spawnerService.isSpawnerLocation(game.getArenaState(context), location);
    }

    boolean canPlayerRespawn(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                             Player player) {
        ArenaState state = game.getArenaState(context);
        if (state == null) {
            return false;
        }
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            return true;
        }
        TeamInfo<Player, Material> team = teamsAPI.getTeam(player);
        if (team == null) {
            return false;
        }
        return game.bedService.isTeamBedIntact(state, team.getId());
    }

    void respawnPlayer(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                       Player player) {
        ArenaState state = game.getArenaState(context);
        if (state == null || player == null || !player.isOnline()) {
            return;
        }

        game.teleportToTeamSpawn(context, state, player);
        player.setGameMode(GameMode.SURVIVAL);
        game.loadoutService.restoreVitals(player);
        game.loadoutService.giveStartingItems(context, player);
        game.loadoutService.applyStartingEffects(player);
        game.loadoutService.applyRespawnEffects(player);
        game.shopService.restoreOnRespawn(player, context, state);
        applyTeamUpgradesOnRespawn(context, state, player);
        game.runtimeService.registerFallProtection(state, player);
    }

    void scheduleSpawnCages(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                            ArenaState state) {
        int arenaId = context.getArenaId();
        String taskId = "arena_" + arenaId + "_bed_wars_cages";
        int maxTicks = 40;
        int[] ticks = {0};
        context.getSchedulerAPI().runTimer(taskId, () -> {
            game.spawnCageService.buildCages(context, state);
            ticks[0]++;
            if (ticks[0] >= maxTicks || state.getCagedPlayerCount() >= context.getPlayers().size()) {
                context.getSchedulerAPI().cancelTask(taskId);
            }
        }, 1L, 1L);
    }

    void scheduleCageGuard(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                           ArenaState state) {
        int arenaId = context.getArenaId();
        String taskId = "arena_" + arenaId + "_bed_wars_cage_guard";
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        context.getSchedulerAPI().runTimer(taskId, () -> {
            if (teamsAPI == null || !teamsAPI.isEnabled()) {
                return;
            }
            for (Player player : context.getPlayers()) {
                if (player == null || !player.isOnline()) {
                    continue;
                }
                TeamInfo<Player, Material> team = teamsAPI.getTeam(player);
                if (team == null) {
                    continue;
                }
                Location spawn = state.getTeamSpawn(team.getId());
                if (spawn == null || spawn.getWorld() == null) {
                    continue;
                }
                Location playerLocation = player.getLocation();
                if (playerLocation.getWorld() == null || !playerLocation.getWorld().equals(spawn.getWorld())) {
                    continue;
                }
                if (playerLocation.distanceSquared(spawn) > CAGE_GUARD_MAX_DISTANCE_SQUARED) {
                    player.teleport(game.centerSpawnLocation(spawn));
                }
            }
        }, 10L, 10L);
    }

    boolean isInRestrictedZone(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                               Player player,
                               Location location) {
        ArenaState state = game.getArenaState(context);
        if (state == null) {
            return false;
        }
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            return false;
        }
        TeamInfo<Player, Material> team = teamsAPI.getTeam(player);
        if (team == null) {
            return false;
        }
        return state.isInRestrictedZone(team.getId(), location);
    }

    private void applyTeamUpgradesOnRespawn(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                            ArenaState state,
                                            Player player) {
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            return;
        }
        TeamInfo<Player, Material> team = teamsAPI.getTeam(player);
        if (team == null) {
            return;
        }
        String teamId = team.getId().toLowerCase();

        for (ArenaState.TeamPotionEffect effect : state.getTeamEffects(teamId)) {
            PotionEffectType type = resolvePotionEffectType(effect.getTypeName());
            if (type != null) {
                player.addPotionEffect(new PotionEffect(type, effect.getDuration(), effect.getAmplifier(), true, true), true);
            }
        }

        for (ArenaState.TeamEnchantment enchant : state.getTeamSwordEnchantments(teamId)) {
            Enchantment enc = resolveEnchantment(enchant.getEnchantmentName());
            if (enc == null) continue;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item == null) continue;
                if (item.getType().name().contains("SWORD") || item.getType().name().contains("AXE")) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.addEnchant(enc, enchant.getLevel(), true);
                        item.setItemMeta(meta);
                    }
                }
            }
        }

        for (ArenaState.TeamEnchantment enchant : state.getTeamBowEnchantments(teamId)) {
            Enchantment enc = resolveEnchantment(enchant.getEnchantmentName());
            if (enc == null) continue;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item == null) continue;
                if (item.getType() == Material.BOW) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.addEnchant(enc, enchant.getLevel(), true);
                        item.setItemMeta(meta);
                    }
                }
            }
        }

        for (ArenaState.TeamEnchantment enchant : state.getTeamArmorEnchantments(teamId)) {
            Enchantment enc = resolveEnchantment(enchant.getEnchantmentName());
            if (enc == null) continue;
            for (ItemStack item : player.getInventory().getArmorContents()) {
                if (item == null) continue;
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.addEnchant(enc, enchant.getLevel(), true);
                    item.setItemMeta(meta);
                }
            }
        }

        player.updateInventory();
    }

    private Enchantment resolveEnchantment(String name) {
        Enchantment enc = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(name.toLowerCase()));
        if (enc == null) {
            try { enc = Enchantment.getByName(name.toUpperCase()); } catch (Exception ignored) {}
        }
        return enc;
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
            return PotionEffectType.getByKey(org.bukkit.NamespacedKey.minecraft(normalized.toLowerCase()));
        } catch (Exception ignored) {
        }
        if (alias != null) {
            try {
                return (PotionEffectType) PotionEffectType.class.getField(alias).get(null);
            } catch (Exception ignored) {}
        }
        return null;
    }
}
