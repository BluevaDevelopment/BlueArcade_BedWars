package net.blueva.arcade.modules.bed_wars.setup;

import net.blueva.arcade.api.setup.SetupContext;
import net.blueva.arcade.modules.bed_wars.BedWarsModule;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Set;

final class BedWarsNpcSetupHandler {

    private final BedWarsModule module;

    BedWarsNpcSetupHandler(BedWarsModule module) {
        this.module = module;
    }

    boolean handle(SetupContext<Player, CommandSender, Location> context) {
        if (!context.hasHandlerArgs(1)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), message("npc.usage"));
            return true;
        }

        String action = context.getHandlerArg(0);
        if (action == null) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), message("npc.usage"));
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

        context.getMessagesAPI().sendRaw(context.getPlayer(), message("npc.usage"));
        return true;
    }

    private boolean add(SetupContext<Player, CommandSender, Location> context) {
        if (!context.hasHandlerArgs(2)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), message("npc.add_usage"));
            return true;
        }

        String typeName = context.getHandlerArg(1);
        if (typeName == null || typeName.isBlank()) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), message("npc.add_usage"));
            return true;
        }

        String typeUpper = typeName.toUpperCase(Locale.ROOT);
        if (!typeUpper.equals("STORE") && !typeUpper.equals("UPGRADE")) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), message("npc.invalid_type")
                    .replace("{type}", typeName));
            return true;
        }

        Player player = context.getPlayer();
        if (player == null) {
            return true;
        }

        Location loc = player.getLocation();
        String registryRaw = context.getData().getString("game.play_area.npc_registry");
        if (registryRaw == null) {
            registryRaw = context.getData().getString("game.npc_registry");
        }
        Set<String> registry = SetupSupport.parseRegistry(registryRaw);
        int nextId = registry.size() + 1;
        String npcId = String.valueOf(nextId);
        registry.add(npcId);

        String basePath = "game.play_area.npcs." + npcId;
        context.getData().setString(basePath + ".type", typeUpper);
        context.getData().setLocation(basePath + ".location", loc);
        context.getData().setString("game.play_area.npc_registry", String.join(",", registry));
        context.getData().save();

        context.getMessagesAPI().sendRaw(player, message("npc.added")
                .replace("{id}", npcId)
                .replace("{type}", typeUpper));
        return true;
    }

    private boolean list(SetupContext<Player, CommandSender, Location> context) {
        String registryRaw = context.getData().getString("game.play_area.npc_registry");
        if (registryRaw == null || registryRaw.isBlank()) {
            registryRaw = context.getData().getString("game.npc_registry");
        }
        if (registryRaw == null || registryRaw.isBlank()) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), message("npc.list_empty"));
            return true;
        }

        Set<String> entries = SetupSupport.parseRegistry(registryRaw);
        if (entries.isEmpty()) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), message("npc.list_empty"));
            return true;
        }

        context.getMessagesAPI().sendRaw(context.getPlayer(), message("npc.list_header"));
        for (String npcId : entries) {
            String basePath = "game.play_area.npcs." + npcId;
            String type = context.getData().getString(basePath + ".type");
            if (type == null || type.isBlank()) {
                continue;
            }
            String line = message("npc.list_line")
                    .replace("{id}", npcId)
                    .replace("{type}", type);
            context.getMessagesAPI().sendRaw(context.getPlayer(), line);
        }
        return true;
    }

    private boolean remove(SetupContext<Player, CommandSender, Location> context) {
        if (!context.hasHandlerArgs(2)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), message("npc.remove_usage"));
            return true;
        }

        String npcId = context.getHandlerArg(1);
        if (npcId == null || npcId.isBlank()) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), message("npc.remove_usage"));
            return true;
        }

        String registryRaw = context.getData().getString("game.play_area.npc_registry");
        if (registryRaw == null || registryRaw.isBlank()) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), message("npc.not_found")
                    .replace("{id}", npcId));
            return true;
        }

        Set<String> registry = SetupSupport.parseRegistry(registryRaw);
        if (!registry.contains(npcId)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), message("npc.not_found")
                    .replace("{id}", npcId));
            return true;
        }

        context.getData().remove("game.play_area.npcs." + npcId);
        registry.remove(npcId);
        context.getData().setString("game.play_area.npc_registry", String.join(",", registry));
        context.getData().save();

        context.getMessagesAPI().sendRaw(context.getPlayer(), message("npc.removed")
                .replace("{id}", npcId));
        return true;
    }

    private String message(String key) {
        return SetupSupport.message(module, key);
    }
}
