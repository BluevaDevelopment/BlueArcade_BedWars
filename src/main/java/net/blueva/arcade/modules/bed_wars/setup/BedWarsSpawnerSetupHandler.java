package net.blueva.arcade.modules.bed_wars.setup;

import net.blueva.arcade.api.setup.SetupContext;
import net.blueva.arcade.modules.bed_wars.BedWarsModule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Set;

final class BedWarsSpawnerSetupHandler {

    private final BedWarsModule module;

    BedWarsSpawnerSetupHandler(BedWarsModule module) {
        this.module = module;
    }

    boolean handle(SetupContext<Player, CommandSender, Location> context) {
        if (!context.hasHandlerArgs(1)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), message(context.getPlayer(), "spawner.usage"));
            return true;
        }

        String action = context.getHandlerArg(0);
        if (action == null) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), message(context.getPlayer(), "spawner.usage"));
            return true;
        }

        if ("list".equalsIgnoreCase(action)) {
            return list(context);
        }

        if ("remove".equalsIgnoreCase(action)) {
            return remove(context);
        }

        if ("add".equalsIgnoreCase(action)) {
            return add(context);
        }

        context.getMessagesAPI().sendRaw(context.getPlayer(), message(context.getPlayer(), "spawner.usage"));
        return true;
    }

    private boolean add(SetupContext<Player, CommandSender, Location> context) {
        if (!context.hasHandlerArgs(3)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), message(context.getPlayer(), "spawner.add_usage"));
            return true;
        }

        String typeName = context.getHandlerArg(1);
        if (typeName == null || typeName.isBlank()) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), message(context.getPlayer(), "spawner.add_usage"));
            return true;
        }

        String typeUpper = typeName.toUpperCase(Locale.ROOT);
        if (!typeUpper.equals("IRON") && !typeUpper.equals("GOLD") && !typeUpper.equals("DIAMOND") && !typeUpper.equals("EMERALD")) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), message(context.getPlayer(), "spawner.invalid_type")
                    .replace("{type}", typeName));
            return true;
        }

        String hologramRaw = context.getHandlerArg(2);
        if (hologramRaw == null || (!hologramRaw.equalsIgnoreCase("true") && !hologramRaw.equalsIgnoreCase("false"))) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), message(context.getPlayer(), "spawner.invalid_hologram")
                    .replace("{value}", hologramRaw == null ? "" : hologramRaw));
            return true;
        }

        boolean hologram = hologramRaw.equalsIgnoreCase("true");
        Player player = context.getPlayer();
        if (player == null) {
            return true;
        }

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || targetBlock.getType() == Material.AIR) {
            context.getMessagesAPI().sendRaw(player, message(context.getPlayer(), "spawner.must_look_at_block"));
            return true;
        }

        String registryRaw = context.getData().getString("game.play_area.spawner_registry");
        if (registryRaw == null) {
            registryRaw = context.getData().getString("game.spawner_registry");
        }
        Set<String> registry = SetupSupport.parseRegistry(registryRaw);
        int nextId = registry.size() + 1;
        String spawnerId = String.valueOf(nextId);
        registry.add(spawnerId);

        String basePath = "game.play_area.spawners." + spawnerId;
        context.getData().setString(basePath + ".type", typeUpper);
        context.getData().setLocation(basePath + ".location", targetBlock.getLocation());
        context.getData().setString(basePath + ".hologram", String.valueOf(hologram));
        context.getData().setString("game.play_area.spawner_registry", String.join(",", registry));
        context.getData().save();

        context.getMessagesAPI().sendRaw(player, message(context.getPlayer(), "spawner.added")
                .replace("{id}", spawnerId)
                .replace("{type}", typeUpper)
                .replace("{hologram}", String.valueOf(hologram)));
        return true;
    }

    private boolean list(SetupContext<Player, CommandSender, Location> context) {
        String registryRaw = context.getData().getString("game.play_area.spawner_registry");
        if (registryRaw == null || registryRaw.isBlank()) {
            registryRaw = context.getData().getString("game.spawner_registry");
        }
        if (registryRaw == null || registryRaw.isBlank()) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), message(context.getPlayer(), "spawner.list_empty"));
            return true;
        }

        Set<String> entries = SetupSupport.parseRegistry(registryRaw);
        if (entries.isEmpty()) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), message(context.getPlayer(), "spawner.list_empty"));
            return true;
        }

        context.getMessagesAPI().sendRaw(context.getPlayer(), message(context.getPlayer(), "spawner.list_header"));
        for (String spawnerId : entries) {
            String basePath = "game.play_area.spawners." + spawnerId;
            String type = context.getData().getString(basePath + ".type");
            String hologram = context.getData().getString(basePath + ".hologram");
            if (type == null || type.isBlank()) {
                continue;
            }
            String line = message(context.getPlayer(), "spawner.list_line")
                    .replace("{id}", spawnerId)
                    .replace("{type}", type)
                    .replace("{hologram}", hologram == null ? "-" : hologram);
            context.getMessagesAPI().sendRaw(context.getPlayer(), line);
        }
        return true;
    }

    private boolean remove(SetupContext<Player, CommandSender, Location> context) {
        Player player = context.getPlayer();
        if (player == null) {
            return true;
        }

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || targetBlock.getType() == Material.AIR) {
            context.getMessagesAPI().sendRaw(player, message(context.getPlayer(), "spawner.must_look_at_block"));
            return true;
        }

        Location targetLoc = targetBlock.getLocation();
        String registryRaw = context.getData().getString("game.play_area.spawner_registry");
        if (registryRaw == null || registryRaw.isBlank()) {
            context.getMessagesAPI().sendRaw(player, message(context.getPlayer(), "spawner.not_found"));
            return true;
        }

        Set<String> registry = SetupSupport.parseRegistry(registryRaw);
        String foundId = null;
        for (String spawnerId : registry) {
            String basePath = "game.play_area.spawners." + spawnerId;
            Location loc = context.getData().getLocation(basePath + ".location");
            if (loc != null && loc.getBlockX() == targetLoc.getBlockX()
                    && loc.getBlockY() == targetLoc.getBlockY()
                    && loc.getBlockZ() == targetLoc.getBlockZ()) {
                foundId = spawnerId;
                break;
            }
        }

        if (foundId == null) {
            context.getMessagesAPI().sendRaw(player, message(context.getPlayer(), "spawner.not_found"));
            return true;
        }

        context.getData().remove("game.play_area.spawners." + foundId);
        registry.remove(foundId);
        context.getData().setString("game.play_area.spawner_registry", String.join(",", registry));
        context.getData().save();

        context.getMessagesAPI().sendRaw(player, message(context.getPlayer(), "spawner.removed")
                .replace("{id}", foundId));
        return true;
    }

    private String message(Player player, String key) {
        return SetupSupport.message(module, player, key);
    }
}
