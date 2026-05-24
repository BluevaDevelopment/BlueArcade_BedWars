package net.blueva.arcade.modules.bed_wars.state;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class ArenaBlockData {

    private final Map<String, Material> cageBlocks = new ConcurrentHashMap<>();
    private final Set<UUID> cagedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<String> cagedSpawnKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> playerPlacedBlocks = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<String>> permanentItems = new ConcurrentHashMap<>();
    private final Map<UUID, Long> fallProtectionUntil = new ConcurrentHashMap<>();

    void setFallProtection(UUID playerId, long untilMillis) {
        if (playerId == null) {
            return;
        }
        fallProtectionUntil.put(playerId, untilMillis);
    }

    boolean hasFallProtection(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        Long until = fallProtectionUntil.get(playerId);
        return until != null && System.currentTimeMillis() <= until;
    }

    void trackCageBlock(Location location, Material previousMaterial) {
        if (location == null || previousMaterial == null) {
            return;
        }
        cageBlocks.put(toKey(location), previousMaterial);
    }

    Map<String, Material> getCageBlocks() {
        return new ConcurrentHashMap<>(cageBlocks);
    }

    void clearCageBlocks() {
        cageBlocks.clear();
    }

    boolean hasCage(UUID playerId) {
        return playerId != null && cagedPlayers.contains(playerId);
    }

    void markCageBuilt(UUID playerId) {
        if (playerId != null) {
            cagedPlayers.add(playerId);
        }
    }

    int getCagedPlayerCount() {
        return cagedPlayers.size();
    }

    void clearCagedPlayers() {
        cagedPlayers.clear();
    }

    boolean isSpawnCaged(Location location) {
        return location != null && cagedSpawnKeys.contains(toKey(location));
    }

    void markSpawnCaged(Location location) {
        if (location != null) {
            cagedSpawnKeys.add(toKey(location));
        }
    }

    void clearCagedSpawns() {
        cagedSpawnKeys.clear();
    }

    void trackPlacedBlock(Location location) {
        if (location != null) {
            playerPlacedBlocks.add(toKey(location));
        }
    }

    boolean isPlayerPlacedBlock(Location location) {
        return location != null && playerPlacedBlocks.contains(toKey(location));
    }

    void untrackPlacedBlock(Location location) {
        if (location != null) {
            playerPlacedBlocks.remove(toKey(location));
        }
    }

    boolean hasPermanentItem(UUID playerId, String itemId) {
        if (playerId == null || itemId == null) return false;
        Set<String> items = permanentItems.get(playerId);
        return items != null && items.contains(itemId.toLowerCase());
    }

    void markPermanentItem(UUID playerId, String itemId) {
        if (playerId == null || itemId == null) return;
        permanentItems.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(itemId.toLowerCase());
    }

    private String toKey(Location location) {
        return location.getWorld().getName() + ":" +
                location.getBlockX() + ":" +
                location.getBlockY() + ":" +
                location.getBlockZ();
    }
}
