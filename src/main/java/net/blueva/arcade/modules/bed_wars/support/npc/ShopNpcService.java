package net.blueva.arcade.modules.bed_wars.support.npc;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.ui.Hologram;
import net.blueva.arcade.api.ui.HologramAPI;
import net.blueva.arcade.modules.bed_wars.state.ArenaState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ShopNpcService {

    private static final int NPC_SCAN_LIMIT = 64;

    private final ModuleConfigAPI moduleConfig;

    public ShopNpcService(ModuleConfigAPI moduleConfig) {
        this.moduleConfig = moduleConfig;
    }

    public List<ShopNpcDefinition> loadNpcDefinitions(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        List<ShopNpcDefinition> definitions = new ArrayList<>();
        if (context == null || context.getDataAccess() == null) {
            return definitions;
        }

        World runtimeWorld = null;
        if (context.getArenaAPI() != null) {
            runtimeWorld = context.getArenaAPI().getWorld();
        }

        String npcBase = resolveDataBasePath(context, "npcs");
        String registryRaw = context.getDataAccess().getGameData(npcBase.replace(".npcs", "") + ".npc_registry", String.class);
        if (registryRaw == null || registryRaw.isBlank()) {
            registryRaw = context.getDataAccess().getGameData("game.npc_registry", String.class);
        }

        Set<String> npcIds = new LinkedHashSet<>();
        if (registryRaw != null && !registryRaw.isBlank()) {
            for (String id : registryRaw.split(",")) {
                String trimmed = id.trim();
                if (!trimmed.isEmpty()) {
                    npcIds.add(trimmed);
                }
            }
        }

        if (npcIds.isEmpty()) {
            for (int i = 1; i <= NPC_SCAN_LIMIT; i++) {
                String path = npcBase + "." + i + ".type";
                if (context.getDataAccess().hasGameData(path)) {
                    npcIds.add(String.valueOf(i));
                }
            }
        }

        for (String npcId : npcIds) {
            String basePath = npcBase + "." + npcId;
            String typeName = context.getDataAccess().getGameData(basePath + ".type", String.class);
            if (typeName == null || typeName.isBlank()) {
                continue;
            }

            ShopNpcType type = ShopNpcType.fromString(typeName);
            if (type == null) {
                continue;
            }

            Location loc = context.getDataAccess().getGameLocation(basePath + ".location");
            if (loc == null) {
                continue;
            }
            if (runtimeWorld != null) {
                loc.setWorld(runtimeWorld);
            }

            definitions.add(new ShopNpcDefinition(npcId, type, loc));
        }

        return definitions;
    }

    public void spawnNpcs(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                          ArenaState state) {
        if (state == null || context == null) {
            return;
        }

        for (ShopNpcDefinition def : state.getNpcDefinitions()) {
            if (state.getNpcEntity(def.getKey()) != null) {
                continue;
            }

            Location loc = def.getLocation();
            if (loc == null || loc.getWorld() == null) {
                continue;
            }

            Villager villager = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setSilent(true);
            villager.setCollidable(false);
            villager.setCanPickupItems(false);
            villager.setProfession(Villager.Profession.NONE);

            state.setNpcEntity(def.getKey(), villager.getUniqueId());

            spawnNpcHologram(context, state, def);
        }
    }

    public void despawnNpcs(ArenaState state) {
        if (state == null) {
            return;
        }

        for (ShopNpcDefinition def : state.getNpcDefinitions()) {
            UUID entityId = state.getNpcEntity(def.getKey());
            if (entityId != null) {
                org.bukkit.entity.Entity entity = org.bukkit.Bukkit.getEntity(entityId);
                if (entity != null && entity.isValid()) {
                    entity.remove();
                }
            }
            state.removeNpcHologram(def.getKey());
        }
    }

    public boolean isShopNpc(ArenaState state, Entity entity) {
        if (state == null || entity == null) {
            return false;
        }
        UUID entityId = entity.getUniqueId();
        for (ShopNpcDefinition def : state.getNpcDefinitions()) {
            UUID npcEntityId = state.getNpcEntity(def.getKey());
            if (npcEntityId != null && npcEntityId.equals(entityId)) {
                return true;
            }
        }
        return false;
    }

    public ShopNpcDefinition getNpcDefinition(ArenaState state, Entity entity) {
        if (state == null || entity == null) {
            return null;
        }
        UUID entityId = entity.getUniqueId();
        for (ShopNpcDefinition def : state.getNpcDefinitions()) {
            UUID npcEntityId = state.getNpcEntity(def.getKey());
            if (npcEntityId != null && npcEntityId.equals(entityId)) {
                return def;
            }
        }
        return null;
    }

    private void spawnNpcHologram(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                  ArenaState state,
                                  ShopNpcDefinition def) {
        HologramAPI<Location> hologramAPI = context.getHologramAPI();
        if (hologramAPI == null) {
            return;
        }

        Location holoLoc = def.getLocation().clone().add(0.0D, 2.5D, 0.0D);
        List<String> lines = new ArrayList<>();

        String label;
        if (def.getType() == ShopNpcType.STORE) {
            label = moduleConfig.getStringFrom("language.yml", "messages.npc.store_hologram");
        } else {
            label = moduleConfig.getStringFrom("language.yml", "messages.npc.upgrade_hologram");
        }
        if (label == null) {
            label = "<white>" + def.getType().getDisplayName() + "</white>";
        }
        lines.add(label);

        Hologram<Location> hologram = hologramAPI.spawn(state.getArenaId(), holoLoc, lines);
        if (hologram != null) {
            state.setNpcHologram(def.getKey(), hologram);
        }
    }

    private String resolveDataBasePath(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                       String section) {
        if (context.getDataAccess().hasGameData("game.play_area." + section)) {
            return "game.play_area." + section;
        }
        return "game." + section;
    }
}
