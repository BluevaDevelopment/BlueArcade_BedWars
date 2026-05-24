package net.blueva.arcade.modules.bed_wars.support.bed;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.team.TeamInfo;
import net.blueva.arcade.api.team.TeamsAPI;
import net.blueva.arcade.api.ui.Hologram;
import net.blueva.arcade.api.ui.HologramAPI;
import net.blueva.arcade.modules.bed_wars.state.ArenaState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BedService {

    private final ModuleConfigAPI moduleConfig;

    public BedService(ModuleConfigAPI moduleConfig) {
        this.moduleConfig = moduleConfig;
    }

    public List<BedDefinition> loadBedDefinitions(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        List<BedDefinition> definitions = new ArrayList<>();
        if (context == null || context.getDataAccess() == null) {
            return definitions;
        }

        World runtimeWorld = null;
        if (context.getArenaAPI() != null) {
            runtimeWorld = context.getArenaAPI().getWorld();
        }

        String bedsBase = resolveDataBasePath(context, "beds");

        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            return definitions;
        }

        int teamIndex = 1;
        for (TeamInfo<Player, Material> team : teamsAPI.getTeams()) {
            String teamId = team.getId();
            if (teamId == null || teamId.isBlank()) {
                teamIndex++;
                continue;
            }
            teamId = teamId.toLowerCase(Locale.ROOT);

            String canonicalPath = bedsBase + "." + teamId;
            String numericPath = bedsBase + "." + teamIndex;
            String resolvedPath = null;

            if (context.getDataAccess().hasGameData(canonicalPath)) {
                resolvedPath = canonicalPath;
            } else if (context.getDataAccess().hasGameData(numericPath)) {
                resolvedPath = numericPath;
            }

            if (resolvedPath != null) {
                Location bedLoc = context.getDataAccess().getGameLocation(resolvedPath);
                if (bedLoc != null) {
                    if (runtimeWorld != null) {
                        bedLoc.setWorld(runtimeWorld);
                    }
                    definitions.add(new BedDefinition(teamId, bedLoc));
                }
            }
            teamIndex++;
        }

        return definitions;
    }

    public boolean handleBedBreak(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                  ArenaState state,
                                  Player breaker,
                                  Block block) {
        if (state == null || breaker == null || block == null) {
            return false;
        }

        BedDefinition bedDef = findBedAtLocation(state, block.getLocation());
        if (bedDef == null) {
            return false;
        }

        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            return false;
        }

        TeamInfo<Player, Material> breakerTeam = teamsAPI.getTeam(breaker);
        if (breakerTeam == null) {
            return false;
        }

        if (breakerTeam.getId().equalsIgnoreCase(bedDef.getTeamId())) {
            return false;
        }

        BedState currentState = state.getBedState(bedDef.getKey());
        if (currentState == BedState.DESTROYED) {
            return false;
        }

        state.setBedState(bedDef.getKey(), BedState.DESTROYED);

        removeBedBlocks(block);

        state.removeBedHologram(bedDef.getKey());

        String breakerTeamDisplay = breakerTeam.getDisplayName();
        String victimTeamDisplay = bedDef.getTeamId();
        if (teamsAPI.isEnabled()) {
            for (TeamInfo<Player, Material> team : teamsAPI.getTeams()) {
                String tid = team.getId();
                if (tid != null && tid.equalsIgnoreCase(bedDef.getTeamId())) {
                    victimTeamDisplay = team.getDisplayName() != null ? team.getDisplayName() : victimTeamDisplay;
                    break;
                }
            }
        }

        String messageTemplate = moduleConfig.getStringFrom("language.yml", "messages.bed.destroyed");
        if (messageTemplate != null) {
            String message = messageTemplate
                    .replace("{player}", breaker.getName())
                    .replace("{team}", victimTeamDisplay);
            for (Player p : context.getPlayers()) {
                context.getMessagesAPI().sendRaw(p, message);
            }
        }

        String titleTemplate = moduleConfig.getStringFrom("language.yml", "titles.bed_destroyed.title");
        String subtitleTemplate = moduleConfig.getStringFrom("language.yml", "titles.bed_destroyed.subtitle");
        if (titleTemplate != null && subtitleTemplate != null) {
            for (Player p : context.getPlayers()) {
                TeamInfo<Player, Material> pTeam = teamsAPI.getTeam(p);
                if (pTeam != null && pTeam.getId().equalsIgnoreCase(bedDef.getTeamId())) {
                    context.getTitlesAPI().sendRaw(p, titleTemplate, subtitleTemplate, 0, 60, 20);
                }
            }
        }

        return true;
    }

    public boolean isTeamBedIntact(ArenaState state, String teamId) {
        if (state == null || teamId == null) {
            return false;
        }
        return state.getBedState(teamId.toLowerCase(Locale.ROOT)) == BedState.INTACT;
    }

    public boolean isBedLocation(ArenaState state, Location location) {
        if (state == null || location == null) {
            return false;
        }
        for (BedDefinition def : state.getBedDefinitions()) {
            if (locationsMatch(def.getLocation(), location)) {
                return true;
            }
            Location otherHalf = getOtherBedHalf(def.getLocation());
            if (otherHalf != null && locationsMatch(otherHalf, location)) {
                return true;
            }
        }
        return false;
    }

    public BedDefinition findBedAtLocation(ArenaState state, Location location) {
        if (state == null || location == null) {
            return null;
        }
        for (BedDefinition def : state.getBedDefinitions()) {
            if (locationsMatch(def.getLocation(), location)) {
                return def;
            }
            Location otherHalf = getOtherBedHalf(def.getLocation());
            if (otherHalf != null && locationsMatch(otherHalf, location)) {
                return def;
            }
        }
        return null;
    }

    public void removeEmptyTeamBeds(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                    ArenaState state) {
        if (state == null || context == null) {
            return;
        }
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            return;
        }

        for (BedDefinition def : new ArrayList<>(state.getBedDefinitions())) {
            if (!hasTeamAnyPlayers(context, teamsAPI, def.getTeamId())) {
                removeBedAndResidue(def.getLocation());
                state.removeBedDefinition(def.getKey());
            }
        }
    }

    private void removeBedAndResidue(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        Block center = location.getBlock();


        if (center.getBlockData() instanceof Bed) {
            removeBedBlocks(center);
            return;
        }



        for (org.bukkit.block.BlockFace face : new org.bukkit.block.BlockFace[]{
                org.bukkit.block.BlockFace.NORTH,
                org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST,
                org.bukkit.block.BlockFace.WEST
        }) {
            Block relative = center.getRelative(face);
            if (relative.getBlockData() instanceof Bed bedData) {
                Block itsOtherHalf = (bedData.getPart() == Bed.Part.HEAD)
                        ? relative.getRelative(bedData.getFacing().getOppositeFace())
                        : relative.getRelative(bedData.getFacing());
                if (itsOtherHalf.getLocation().equals(center.getLocation())) {
                    removeBedBlocks(relative);
                    break;
                }
            }
        }
    }

    public void spawnBedHolograms(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                  ArenaState state) {
        if (state == null || context == null) {
            return;
        }
        HologramAPI<Location> hologramAPI = context.getHologramAPI();
        if (hologramAPI == null) {
            return;
        }

        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();

        for (BedDefinition def : state.getBedDefinitions()) {
            if (state.getBedState(def.getKey()) != BedState.INTACT) {
                continue;
            }
            if (state.getBedHologram(def.getKey()) != null) {
                continue;
            }


            if (teamsAPI != null && teamsAPI.isEnabled() && !hasTeamAnyPlayers(context, teamsAPI, def.getTeamId())) {
                continue;
            }


            repairBedIfBroken(def.getLocation());


            Block bedBlock = def.getLocation().getBlock();
            if (!(bedBlock.getBlockData() instanceof Bed)) {
                continue;
            }

            String teamDisplay = resolveTeamDisplay(teamsAPI, def.getTeamId());

            Location holoLoc = def.getLocation().clone().add(0.5D, 2.0D, 0.5D);
            List<String> lines = buildBedHologramLines(teamDisplay);
            Hologram<Location> hologram = hologramAPI.spawn(state.getArenaId(), holoLoc, lines);
            if (hologram != null) {
                state.setBedHologram(def.getKey(), hologram);
            }
        }
    }

    private List<String> buildBedHologramLines(String teamDisplay) {
        List<String> lines = new ArrayList<>();

        String titleLine = moduleConfig.getStringFrom("language.yml", "messages.bed.hologram_title");
        if (titleLine == null) {
            titleLine = "<red>❤ BED ❤</red>";
        }

        String teamLine = moduleConfig.getStringFrom("language.yml", "messages.bed.hologram_label");
        if (teamLine == null) {
            teamLine = "<white>{team}</white>";
        }
        teamLine = teamLine.replace("{team}", teamDisplay);

        String statusLine = moduleConfig.getStringFrom("language.yml", "messages.bed.hologram_status");
        if (statusLine == null) {
            statusLine = "<green>✔ Protected</green>";
        }

        lines.add(titleLine);
        lines.add(teamLine);
        lines.add(statusLine);
        return lines;
    }

    private boolean hasTeamAnyPlayers(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                      TeamsAPI<Player, Material> teamsAPI,
                                      String teamId) {
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            return true;
        }
        for (Player p : context.getPlayers()) {
            TeamInfo<Player, Material> pTeam = teamsAPI.getTeam(p);
            if (pTeam != null && pTeam.getId().equalsIgnoreCase(teamId)) {
                return true;
            }
        }
        return false;
    }

    private String resolveTeamDisplay(TeamsAPI<Player, Material> teamsAPI, String teamId) {
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            return teamId;
        }
        for (TeamInfo<Player, Material> team : teamsAPI.getTeams()) {
            String tid = team.getId();
            if (tid != null && tid.equalsIgnoreCase(teamId)) {
                return team.getDisplayName() != null ? team.getDisplayName() : teamId;
            }
        }
        return teamId;
    }

    public void clearBedHolograms(ArenaState state) {
        if (state == null) {
            return;
        }
        for (String key : state.getBedHologramKeys()) {
            state.removeBedHologram(key);
        }
    }

    public int getAliveTeamCount(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                 ArenaState state) {
        if (context == null || state == null) {
            return 0;
        }
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            return 0;
        }

        int count = 0;
        for (TeamInfo<Player, Material> team : teamsAPI.getTeams()) {
            String teamId = team.getId();
            if (teamId == null || teamId.isBlank()) {
                continue;
            }
            if (isTeamAlive(context, state, teamId)) {
                count++;
            }
        }
        return count;
    }

    public boolean isTeamAlive(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                               ArenaState state,
                               String teamId) {
        TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();

        if (teamsAPI != null && teamsAPI.isEnabled() && !hasTeamAnyPlayers(context, teamsAPI, teamId)) {
            return false;
        }
        if (isTeamBedIntact(state, teamId)) {
            return true;
        }
        if (teamsAPI == null || !teamsAPI.isEnabled()) {
            return false;
        }
        for (Player player : context.getAlivePlayers()) {
            TeamInfo<Player, Material> pTeam = teamsAPI.getTeam(player);
            if (pTeam != null && pTeam.getId().equalsIgnoreCase(teamId)) {
                return true;
            }
        }
        return false;
    }

    private void repairBedIfBroken(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        Block block = location.getBlock();
        if (!(block.getBlockData() instanceof Bed bedData)) {
            return;
        }

        Block otherHalf = (bedData.getPart() == Bed.Part.HEAD)
                ? block.getRelative(bedData.getFacing().getOppositeFace())
                : block.getRelative(bedData.getFacing());

        if (!(otherHalf.getBlockData() instanceof Bed otherBedData)) {

            Bed replacement = (Bed) Bukkit.createBlockData(block.getType());
            replacement.setPart(bedData.getPart() == Bed.Part.HEAD ? Bed.Part.FOOT : Bed.Part.HEAD);
            replacement.setFacing(bedData.getFacing());
            otherHalf.setBlockData(replacement);
        } else if (otherBedData.getPart() == bedData.getPart()) {

            Bed fixed = (Bed) otherHalf.getBlockData().clone();
            fixed.setPart(bedData.getPart() == Bed.Part.HEAD ? Bed.Part.FOOT : Bed.Part.HEAD);
            otherHalf.setBlockData(fixed);
        }
    }

    private void removeBedBlocks(Block block) {
        if (block == null) {
            return;
        }
        BlockData data = block.getBlockData();
        if (data instanceof Bed bedData) {
            Block otherHalf;
            if (bedData.getPart() == Bed.Part.HEAD) {
                otherHalf = block.getRelative(bedData.getFacing().getOppositeFace());
            } else {
                otherHalf = block.getRelative(bedData.getFacing());
            }
            block.setType(Material.AIR);
            if (otherHalf != null) {
                otherHalf.setType(Material.AIR);
            }
        } else {
            block.setType(Material.AIR);
        }
    }

    private Location getOtherBedHalf(Location bedLoc) {
        if (bedLoc == null || bedLoc.getWorld() == null) {
            return null;
        }
        Block block = bedLoc.getBlock();
        BlockData data = block.getBlockData();
        if (data instanceof Bed bedData) {
            Block otherHalf;
            if (bedData.getPart() == Bed.Part.HEAD) {
                otherHalf = block.getRelative(bedData.getFacing().getOppositeFace());
            } else {
                otherHalf = block.getRelative(bedData.getFacing());
            }
            return otherHalf.getLocation();
        }
        return null;
    }

    private boolean locationsMatch(Location a, Location b) {
        if (a == null || b == null) {
            return false;
        }
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private String resolveDataBasePath(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                       String section) {
        if (context.getDataAccess().hasGameData("game.play_area." + section)) {
            return "game.play_area." + section;
        }
        return "game." + section;
    }

}
