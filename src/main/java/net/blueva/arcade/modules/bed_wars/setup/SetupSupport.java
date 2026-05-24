package net.blueva.arcade.modules.bed_wars.setup;

import net.blueva.arcade.api.setup.SetupContext;
import net.blueva.arcade.modules.bed_wars.BedWarsModule;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.Set;

final class SetupSupport {

    private SetupSupport() {
    }

    static String message(BedWarsModule module, String key) {
        String message = module.getModuleConfig().getStringFrom("language.yml", "setup_messages." + key);
        if (message == null) {
            return "";
        }
        return message;
    }

    static Set<String> parseRegistry(String raw) {
        Set<String> result = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        String[] split = raw.split(",");
        for (String entry : split) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    static boolean isExistingTeam(SetupContext<Player, CommandSender, Location> context, String teamId) {
        int teamCount = context.getData().getInt("teams.count", 0);
        if (teamCount <= 0) {
            return false;
        }
        for (int i = 1; i <= teamCount; i++) {
            if (String.valueOf(i).equalsIgnoreCase(teamId)) {
                return true;
            }
        }
        return false;
    }

    static void sendTeamIdRangeMessage(BedWarsModule module, SetupContext<Player, CommandSender, Location> context) {
        int teamCount = context.getData().getInt("teams.count", 0);
        String max = teamCount > 0 ? String.valueOf(teamCount) : "N";
        context.getMessagesAPI().sendRaw(context.getPlayer(),
                message(module, "team.numeric_ids_only")
                        .replace("{min}", "1")
                        .replace("{max}", max));
    }
}
