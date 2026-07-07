package net.blueva.arcade.modules.bed_wars.setup;

import net.blueva.arcade.api.setup.GameSetupHandler;
import net.blueva.arcade.api.setup.SetupContext;
import net.blueva.arcade.api.setup.TabCompleteContext;
import net.blueva.arcade.api.setup.TabCompleteResult;
import net.blueva.arcade.modules.bed_wars.BedWarsModule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public class BedWarsSetup implements GameSetupHandler {

    private final BedWarsModule module;
    private final BedWarsSpawnerSetupHandler spawnerHandler;
    private final BedWarsNpcSetupHandler npcHandler;

    public BedWarsSetup(BedWarsModule module) {
        this.module = module;
        this.spawnerHandler = new BedWarsSpawnerSetupHandler(module);
        this.npcHandler = new BedWarsNpcSetupHandler(module);
    }

    @Override
    public boolean handle(SetupContext context) {
        return handleInternal(castSetupContext(context));
    }

    private boolean handleInternal(SetupContext<Player, CommandSender, Location> context) {
        String subcommand = context.getArg(context.getStartIndex() - 1);
        if ("bed".equalsIgnoreCase(subcommand)) {
            return handleBed(context);
        }
        if ("spawner".equalsIgnoreCase(subcommand)) {
            return spawnerHandler.handle(context);
        }
        if ("npc".equalsIgnoreCase(subcommand)) {
            return npcHandler.handle(context);
        }
        if ("region".equalsIgnoreCase(subcommand)) {
            return handleRegion(context);
        }
        if ("team".equalsIgnoreCase(subcommand)) {
            String teamSubcommand = context.getHandlerArg(0);
            if ("spawn".equalsIgnoreCase(teamSubcommand)) {
                return handleTeamSpawn(context);
            }
            return handleTeamConfig(context);
        }
        return handleTeamConfig(context);
    }

    @Override
    public TabCompleteResult tabComplete(TabCompleteContext context) {
        return tabCompleteInternal(castTabContext(context));
    }

    private TabCompleteResult tabCompleteInternal(TabCompleteContext<Player, CommandSender> context) {
        if (context.getRelativeArgIndex() == 0
                && "team".equalsIgnoreCase(context.getArg(context.getStartIndex() - 1))) {
            return TabCompleteResult.of("count", "size", "spawn");
        }
        if (context.getRelativeArgIndex() == 0 && "region".equalsIgnoreCase(context.getArg(context.getStartIndex() - 1))) {
            return TabCompleteResult.of("set", "clear");
        }
        if (context.getRelativeArgIndex() == 0 && "bed".equalsIgnoreCase(context.getArg(context.getStartIndex() - 1))) {
            return TabCompleteResult.of("set");
        }
        if (context.getRelativeArgIndex() == 0 && "spawner".equalsIgnoreCase(context.getArg(context.getStartIndex() - 1))) {
            return TabCompleteResult.of("add", "list", "remove");
        }
        if (context.getRelativeArgIndex() == 1
                && "spawner".equalsIgnoreCase(context.getArg(context.getStartIndex() - 1))
                && "add".equalsIgnoreCase(context.getArg(context.getStartIndex()))) {
            return TabCompleteResult.of("iron", "gold", "diamond", "emerald");
        }
        if (context.getRelativeArgIndex() == 2
                && "spawner".equalsIgnoreCase(context.getArg(context.getStartIndex() - 1))
                && "add".equalsIgnoreCase(context.getArg(context.getStartIndex()))) {
            return TabCompleteResult.of("true", "false");
        }
        if (context.getRelativeArgIndex() == 0 && "npc".equalsIgnoreCase(context.getArg(context.getStartIndex() - 1))) {
            return TabCompleteResult.of("add", "list", "remove");
        }
        if (context.getRelativeArgIndex() == 1
                && "npc".equalsIgnoreCase(context.getArg(context.getStartIndex() - 1))
                && "add".equalsIgnoreCase(context.getArg(context.getStartIndex()))) {
            return TabCompleteResult.of("store", "upgrade");
        }
        return TabCompleteResult.empty();
    }

    @Override
    public List<String> getSubcommands() {
        return List.of("team", "region", "bed", "spawner", "npc");
    }



    private boolean handleTeamConfig(SetupContext<Player, CommandSender, Location> context) {
        if (!context.hasHandlerArgs(2)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(),
                    getSetupMessage(context.getPlayer(), "team.usage"));
            return true;
        }

        String setting = context.getHandlerArg(0);
        if (setting == null || (!setting.equalsIgnoreCase("count") && !setting.equalsIgnoreCase("size"))) {
            context.getMessagesAPI().sendRaw(context.getPlayer(),
                    getSetupMessage(context.getPlayer(), "team.usage"));
            return true;
        }

        String valueRaw = context.getHandlerArg(1);
        if (valueRaw == null || !isNumber(valueRaw)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(),
                    module.getCoreConfig().getLanguage(context.getPlayer(), "admin_commands.errors.invalid_number")
                            .replace("{value}", valueRaw == null ? "" : valueRaw));
            return true;
        }

        int value = Integer.parseInt(valueRaw);
        if (value <= 0) {
            context.getMessagesAPI().sendRaw(context.getPlayer(),
                    getSetupMessage(context.getPlayer(), "team.invalid_value")
                            .replace("{setting}", setting));
            return true;
        }

        if (setting.equalsIgnoreCase("count") && value < 2) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage(context.getPlayer(), "team.invalid_count"));
            return true;
        }
        if (setting.equalsIgnoreCase("size") && value < 1) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage(context.getPlayer(), "team.invalid_size"));
            return true;
        }

        int teamCount = context.getData().getInt("teams.count", 0);
        int teamSize = context.getData().getInt("teams.size", 0);
        if (setting.equalsIgnoreCase("count")) {
            teamCount = value;
        } else {
            teamSize = value;
        }

        int maxPlayers = context.getData().getArenaInt("arena.basic.max_players", 0);
        if (teamCount > 0 && teamSize > 0 && maxPlayers > 0 && teamCount * teamSize > maxPlayers) {
            context.getMessagesAPI().sendRaw(context.getPlayer(),
                    getSetupMessage(context.getPlayer(), "team.invalid_limit")
                            .replace("{max_players}", String.valueOf(maxPlayers)));
            return true;
        }

        context.getData().setTeamConfig(teamCount, teamSize);
        context.getData().save();

        context.getMessagesAPI().sendRaw(context.getPlayer(),
                getSetupMessage(context.getPlayer(), "team.success")
                        .replace("{game}", context.getGameId())
                        .replace("{arena_id}", String.valueOf(context.getArenaId()))
                        .replace("{setting}", setting.toLowerCase())
                        .replace("{value}", String.valueOf(value)));
        return true;
    }



    private boolean handleBed(SetupContext<Player, CommandSender, Location> context) {
        if (!context.hasHandlerArgs(1)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage(context.getPlayer(), "bed.usage"));
            return true;
        }

        String action = context.getHandlerArg(0);
        if (!"set".equalsIgnoreCase(action)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage(context.getPlayer(), "bed.usage"));
            return true;
        }

        if (!context.hasHandlerArgs(2)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage(context.getPlayer(), "bed.set_usage"));
            return true;
        }

        String teamId = normalizeTeamId(context.getHandlerArg(1));
        if (teamId == null) {
            sendTeamIdRangeMessage(context);
            return true;
        }

        if (!isExistingTeam(context, teamId)) {
            sendTeamIdRangeMessage(context);
            return true;
        }

        Player player = context.getPlayer();
        if (player == null) {
            return true;
        }

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || targetBlock.getType() == Material.AIR) {
            context.getMessagesAPI().sendRaw(player, getSetupMessage(context.getPlayer(), "bed.must_look_at_block"));
            return true;
        }

        Location bedLoc = targetBlock.getLocation();
        String basePath = "game.play_area.beds." + teamId;
        context.getData().setLocation(basePath, bedLoc);
        context.getData().save();

        context.getMessagesAPI().sendRaw(player, getSetupMessage(context.getPlayer(), "bed.set")
                .replace("{team}", teamId));
        return true;
    }




    private boolean handleTeamSpawn(SetupContext<Player, CommandSender, Location> context) {
        if (!context.hasHandlerArgs(2)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage(context.getPlayer(), "team_spawn.usage"));
            return true;
        }

        String teamId = normalizeTeamId(context.getHandlerArg(1));
        if (teamId == null) {
            context.getMessagesAPI().sendRaw(context.getPlayer(), getSetupMessage(context.getPlayer(), "team_spawn.usage"));
            sendTeamIdRangeMessage(context);
            return true;
        }

        if (!isExistingTeam(context, teamId)) {
            sendTeamIdRangeMessage(context);
            return true;
        }

        Player player = context.getPlayer();
        if (player == null) {
            return true;
        }

        Location location = player.getLocation();
        String path = "game.play_area.team_spawns." + teamId.toLowerCase();
        context.getData().setLocation(path, location);
        context.getData().save();

        context.getMessagesAPI().sendRaw(player, getSetupMessage(context.getPlayer(), "team_spawn.set")
                .replace("{team}", teamId));
        return true;
    }



    private boolean handleRegion(SetupContext<Player, CommandSender, Location> context) {
        if (!context.hasHandlerArgs(1)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(),
                    getSetupMessage(context.getPlayer(), "region.usage"));
            return true;
        }

        String action = context.getHandlerArg(0);
        if (action == null) {
            context.getMessagesAPI().sendRaw(context.getPlayer(),
                    getSetupMessage(context.getPlayer(), "region.usage"));
            return true;
        }

        if ("clear".equalsIgnoreCase(action)) {
            context.getData().remove("game.play_area");
            context.getData().remove("regeneration.regions");
            context.getData().save();
            context.getMessagesAPI().sendRaw(context.getPlayer(),
                    getSetupMessage(context.getPlayer(), "region.cleared"));
            return true;
        }

        if (!"set".equalsIgnoreCase(action)) {
            context.getMessagesAPI().sendRaw(context.getPlayer(),
                    getSetupMessage(context.getPlayer(), "region.usage"));
            return true;
        }

        Player player = context.getPlayer();
        if (player == null) {
            return true;
        }

        if (!context.getSelection().hasCompleteSelection(player)) {
            context.getMessagesAPI().sendRaw(player,
                    getSetupMessage(context.getPlayer(), "region.must_use_stick"));
            return true;
        }

        Location pos1 = context.getSelection().getPosition1(player);
        Location pos2 = context.getSelection().getPosition2(player);

        context.getData().registerRegenerationRegion("game.play_area", pos1, pos2);
        context.getData().save();

        int x = (int) Math.abs(pos2.getX() - pos1.getX()) + 1;
        int y = (int) Math.abs(pos2.getY() - pos1.getY()) + 1;
        int z = (int) Math.abs(pos2.getZ() - pos1.getZ()) + 1;
        int blocks = x * y * z;

        context.getMessagesAPI().sendRaw(player,
                getSetupMessage(context.getPlayer(), "region.set")
                        .replace("{blocks}", String.valueOf(blocks))
                        .replace("{x}", String.valueOf(x))
                        .replace("{y}", String.valueOf(y))
                        .replace("{z}", String.valueOf(z)));
        return true;
    }



    private String getSetupMessage(Player player, String key) {
        return SetupSupport.message(module, player, key);
    }

    private boolean isNumber(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isExistingTeam(SetupContext<Player, CommandSender, Location> context, String teamId) {
        return SetupSupport.isExistingTeam(context, teamId);
    }

    private String normalizeTeamId(String teamRaw) {
        if (teamRaw == null) {
            return null;
        }
        String value = teamRaw.trim().toLowerCase(Locale.ROOT);
        return isNumericId(value) ? value : null;
    }

    private boolean isNumericId(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        for (int i = 0; i < raw.length(); i++) {
            if (!Character.isDigit(raw.charAt(i))) {
                return false;
            }
        }
        try {
            return Integer.parseInt(raw) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }


    private void sendTeamIdRangeMessage(SetupContext<Player, CommandSender, Location> context) {
        SetupSupport.sendTeamIdRangeMessage(module, context);
    }

    @SuppressWarnings("unchecked")
    private SetupContext<Player, CommandSender, Location> castSetupContext(SetupContext context) {
        return (SetupContext<Player, CommandSender, Location>) context;
    }

    @SuppressWarnings("unchecked")
    private TabCompleteContext<Player, CommandSender> castTabContext(TabCompleteContext context) {
        return (TabCompleteContext<Player, CommandSender>) context;
    }
}
