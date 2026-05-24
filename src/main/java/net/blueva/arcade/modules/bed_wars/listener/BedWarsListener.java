package net.blueva.arcade.modules.bed_wars.listener;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.api.team.TeamInfo;
import net.blueva.arcade.api.team.TeamsAPI;
import net.blueva.arcade.modules.bed_wars.game.BedWarsGame;
import net.blueva.arcade.modules.bed_wars.state.ArenaState;
import net.blueva.arcade.modules.bed_wars.support.bed.BedDefinition;
import net.blueva.arcade.modules.bed_wars.support.npc.ShopNpcDefinition;
import net.blueva.arcade.modules.bed_wars.support.npc.ShopNpcType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import net.blueva.arcade.modules.bed_wars.support.armory.ArenaEnderChestHolder;

public class BedWarsListener implements Listener {

    private final BedWarsGame game;

    public BedWarsListener(BedWarsGame game) {
        this.game = game;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                game.getContext(player);

        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        if (context.getPhase() != GamePhase.PLAYING) {
            return;
        }

        if (game.isInRestrictedZone(context, player, event.getTo())) {
            event.setCancelled(true);
            context.getMessagesAPI().sendRaw(player,
                    game.getModuleConfig().getStringFrom("language.yml", "messages.restricted_zone"));
            return;
        }

        if (!context.isInsideBounds(event.getTo())) {
            game.handleNonCombatDeath(context, player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                game.getContext(player);
        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        if (context.getPhase() != GamePhase.PLAYING) {
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block clickedBlock = event.getClickedBlock();
            Material type = clickedBlock.getType();

            if (type == Material.ENDER_CHEST) {
                event.setCancelled(true);
                game.openArenaEnderChest(player, context);
                return;
            }

            org.bukkit.block.BlockState blockState = clickedBlock.getState();
            if (blockState instanceof Container container) {
                event.setCancelled(true);
                player.openInventory(container.getInventory());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                game.getContext(player);
        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        if (context.getPhase() != GamePhase.PLAYING) {
            return;
        }

        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof Villager)) {
            return;
        }

        ArenaState state = game.getArenaState(context);
        if (state == null) {
            return;
        }

        ShopNpcDefinition npcDef = game.getShopNpcService().getNpcDefinition(state, clicked);
        if (npcDef == null) {
            return;
        }

        event.setCancelled(true);
        openShopMenu(player, context, npcDef);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                game.getContext(player);
        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        if (context.getPhase() != GamePhase.PLAYING) {
            return;
        }

        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof Villager)) {
            return;
        }

        ArenaState state = game.getArenaState(context);
        if (state == null) {
            return;
        }

        ShopNpcDefinition npcDef = game.getShopNpcService().getNpcDefinition(state, clicked);
        if (npcDef == null) {
            return;
        }

        event.setCancelled(true);
        openShopMenu(player, context, npcDef);
    }

    private void openShopMenu(Player player,
                              GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                              ShopNpcDefinition npcDef) {
        ArenaState state = game.getArenaState(context);
        if (state == null) return;

        if (npcDef.getType() == ShopNpcType.STORE) {
            game.getShopService().openShop(player, context, state);
        } else {
            game.getUpgradeService().openUpgradesMenu(player, context, state);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                game.getContext(player);

        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        if (context.getPhase() != GamePhase.PLAYING) {
            event.setCancelled(true);
            return;
        }

        Location blockLoc = event.getBlock().getLocation();

        if (game.isBedLocation(context, blockLoc)) {
            ArenaState state = game.getArenaState(context);
            BedDefinition bedDef = game.getBedService().findBedAtLocation(state, blockLoc);
            if (bedDef != null) {
                TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();


                if (teamsAPI != null && teamsAPI.isEnabled()) {
                    boolean hasPlayers = false;
                    for (Player p : context.getPlayers()) {
                        TeamInfo<Player, Material> pTeam = teamsAPI.getTeam(p);
                        if (pTeam != null && pTeam.getId().equalsIgnoreCase(bedDef.getTeamId())) {
                            hasPlayers = true;
                            break;
                        }
                    }
                    if (!hasPlayers) {
                        event.setCancelled(true);
                        return;
                    }
                }

                TeamInfo<Player, Material> breakerTeam = teamsAPI != null ? teamsAPI.getTeam(player) : null;
                if (breakerTeam != null && breakerTeam.getId().equalsIgnoreCase(bedDef.getTeamId())) {
                    event.setCancelled(true);
                    String msg = game.getModuleConfig().getStringFrom("language.yml", "messages.bed.cannot_break_own");
                    if (msg != null) {
                        context.getMessagesAPI().sendRaw(player, msg);
                    }
                    return;
                }

                event.setCancelled(true);
                game.handleBedBreak(context, player, event.getBlock());
            }
            return;
        }

        if (game.isSpawnerLocation(context, blockLoc)) {
            event.setCancelled(true);
            return;
        }

        if (!context.isInsideBounds(blockLoc) || !game.canBreakBlock(context, event.getBlock())) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(false);
        game.untrackPlacedBlock(context, blockLoc);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                game.getContext(player);

        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        if (context.getPhase() != GamePhase.PLAYING) {
            event.setCancelled(true);
            return;
        }

        Location blockLoc = event.getBlock().getLocation();

        if (!context.isInsideBounds(blockLoc)) {
            event.setCancelled(true);
            return;
        }

        if (game.isBedLocation(context, blockLoc)) {
            event.setCancelled(true);
            return;
        }

        if (game.isSpawnerLocation(context, blockLoc)) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(false);
        game.trackPlacedBlock(context, blockLoc);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {

        if (event.getDamager() instanceof Fireball fireball) {
            if (fireball.getShooter() instanceof Player player) {
                GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> ctx = game.getContext(player);
                if (ctx != null && ctx.isPlayerPlaying(player)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (!(event.getEntity() instanceof Player target)) {
            ArenaState state = findArenaStateForEntity(event.getEntity());
            if (state != null && game.getShopNpcService().isShopNpc(state, event.getEntity())) {
                event.setCancelled(true);

                Player attacker = resolveAttacker(event.getDamager());
                if (attacker != null) {
                    GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                            game.getContext(attacker);
                    if (context != null && context.getPhase() == GamePhase.PLAYING) {
                        ShopNpcDefinition npcDef = game.getShopNpcService().getNpcDefinition(state, event.getEntity());
                        if (npcDef != null) {
                            openShopMenu(attacker, context, npcDef);
                        }
                    }
                }
            }
            return;
        }

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                game.getContext(target);
        if (context == null || !context.isPlayerPlaying(target)) {
            return;
        }

        if (context.getPhase() != GamePhase.PLAYING) {
            event.setCancelled(true);
            return;
        }

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || !context.isPlayerPlaying(attacker)) {
            event.setCancelled(true);
            return;
        }

        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI != null && teamsAPI.isEnabled()) {
            TeamInfo<Player, Material> attackerTeam = teamsAPI.getTeam(attacker);
            TeamInfo<Player, Material> targetTeam = teamsAPI.getTeam(target);
            if (attackerTeam != null && targetTeam != null && attackerTeam.getId().equalsIgnoreCase(targetTeam.getId())) {
                event.setCancelled(true);
                return;
            }
        }

        double finalHealth = target.getHealth() - event.getFinalDamage();
        if (finalHealth > 0) {
            return;
        }

        event.setCancelled(true);
        game.handleKill(context, attacker, target);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGenericDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }

        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                game.getContext(target);
        if (context == null || !context.isPlayerPlaying(target)) {
            return;
        }

        if (context.getPhase() != GamePhase.PLAYING) {
            event.setCancelled(true);
            return;
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            ArenaState state = game.getArenaState(context);
            if (state != null && state.hasFallProtection(target.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
        }

        double finalHealth = target.getHealth() - event.getFinalDamage();
        if (finalHealth > 0) {
            return;
        }

        event.setCancelled(true);
        game.handleNonCombatDeath(context, target);
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }

        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractSpecial(PlayerInteractEvent event) {
        game.getSpecialItemHandler().onPlayerInteract(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileHit(ProjectileHitEvent event) {
        game.getSpecialItemHandler().onProjectileHit(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Fireball fireball)) return;
        if (!(fireball.getShooter() instanceof Player player)) return;
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> ctx = game.getContext(player);
        if (ctx != null && ctx.isPlayerPlaying(player)) {
            event.setCancelled(true);
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                game.getContext(player);
        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }


        game.getShopService().onPlayerCloseShop(player);

        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof ArenaEnderChestHolder ecHolder) {
            game.saveArenaEnderChest(ecHolder.getTeamId(), ecHolder.getArenaId(), event.getInventory().getContents());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                game.getContext(player);
        if (context == null || !context.isPlayerPlaying(player)) return;


        if (event.getCurrentItem() != null && game.getShopService().isPermanentShopItem(event.getCurrentItem())) {
            if (event.getInventory().getType() != InventoryType.PLAYER) {
                event.setCancelled(true);
                return;
            }
        }


        if (event.isShiftClick() && game.getShopService().isPlayerInShop(player)) {
            ArenaState state = game.getArenaState(context);
            if (state != null && game.getShopService().handleShopShiftClick(player, event.getRawSlot(), context, state)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                game.getContext(player);
        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }
        if (game.getShopService().isPermanentShopItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemSpawn(ItemSpawnEvent event) {

        Material material = event.getEntity().getItemStack().getType();
        if (!isBedMaterial(material)) return;


        Location loc = event.getLocation();
        if (loc.getWorld() == null) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                    game.getContext(player);
            if (context != null && context.isPlayerPlaying(player)) {
                if (context.getArenaAPI() != null && loc.getWorld().equals(context.getArenaAPI().getWorld())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    private boolean isBedMaterial(Material material) {
        return switch (material) {
            case WHITE_BED, ORANGE_BED, MAGENTA_BED, LIGHT_BLUE_BED, YELLOW_BED,
                    LIME_BED, PINK_BED, GRAY_BED, LIGHT_GRAY_BED, CYAN_BED,
                    PURPLE_BED, BLUE_BED, BROWN_BED, GREEN_BED, RED_BED, BLACK_BED -> true;
            default -> false;
        };
    }

    private ArenaState findArenaStateForEntity(Entity entity) {
        if (entity == null || entity.getLocation() == null) {
            return null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                    game.getContext(player);
            if (context != null) {
                ArenaState state = game.getArenaState(context);
                if (state != null) {
                    return state;
                }
            }
        }
        return null;
    }

}
