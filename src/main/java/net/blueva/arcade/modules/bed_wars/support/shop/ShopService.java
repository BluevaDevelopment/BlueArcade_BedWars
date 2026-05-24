package net.blueva.arcade.modules.bed_wars.support.shop;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.ui.MenuAPI;
import net.blueva.arcade.modules.bed_wars.game.BedWarsGame;
import net.blueva.arcade.modules.bed_wars.state.ArenaState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class ShopService {

    private final ShopServiceImpl impl;

    public ShopService(ModuleConfigAPI moduleConfig, MenuAPI<Player, Material> menuAPI, BedWarsGame game) {
        this.impl = new ShopServiceImpl(moduleConfig, menuAPI, game);
    }

    public void loadShop() {
        impl.loadShop();
    }

    public void openShop(Player player, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, ArenaState state) {
        impl.openShop(player, context, state);
    }

    public void openQuickBuy(Player player, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, ArenaState state) {
        impl.openQuickBuy(player, context, state);
    }

    public void openCategory(Player player, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, ArenaState state, String categoryId) {
        impl.openCategory(player, context, state, categoryId);
    }

    public boolean handleAction(Player player, String payload, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, ArenaState state) {
        return impl.handleAction(player, payload, context, state);
    }

    public boolean isPlayerInShop(Player player) {
        return impl.isPlayerInShop(player);
    }

    public void onPlayerCloseShop(Player player) {
        impl.onPlayerCloseShop(player);
    }

    public boolean handleShopShiftClick(Player player, int slot, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, ArenaState state) {
        return impl.handleShopShiftClick(player, slot, context, state);
    }

    public ShopCache getCache(int arenaId, UUID playerId) {
        return impl.getCache(arenaId, playerId);
    }

    public void removePlayer(int arenaId, Player player) {
        impl.removePlayer(arenaId, player);
    }

    public void clearArena(int arenaId) {
        impl.clearArena(arenaId);
    }

    public void restoreOnRespawn(Player player, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, ArenaState state) {
        impl.restoreOnRespawn(player, context, state);
    }

    public static boolean isPermanentShopItem(ItemStack item) {
        return ShopServiceImpl.isPermanentShopItem(item);
    }

    public static int calculateMoney(Player player, Material currency) {
        return ShopServiceImpl.calculateMoney(player, currency);
    }

    public static void takeMoney(Player player, Material currency, int amount) {
        ShopServiceImpl.takeMoney(player, currency, amount);
    }
}
