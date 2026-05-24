package net.blueva.arcade.modules.bed_wars.listener;

import net.blueva.arcade.modules.bed_wars.game.BedWarsGame;
import net.blueva.arcade.modules.bed_wars.support.vote.BedWarsVoteService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Arrays;
import java.util.Locale;

public class BedWarsVoteListener implements Listener {

    private final BedWarsGame game;

    public BedWarsVoteListener(BedWarsGame game) {
        this.game = game;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVoteCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();

        if (message == null || message.isBlank()) {
            return;
        }

        String normalized = message.trim().toLowerCase(Locale.ROOT);
        String commandPrefix = "/" + BedWarsVoteService.COMMAND;
        if (!normalized.startsWith(commandPrefix)) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        String[] parts = message.substring(1).trim().split("\\s+");
        String[] args = parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];

        game.handleVoteCommand(player, args);
    }
}
