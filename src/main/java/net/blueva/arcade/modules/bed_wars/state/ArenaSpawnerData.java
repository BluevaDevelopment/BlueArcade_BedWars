package net.blueva.arcade.modules.bed_wars.state;

import net.blueva.arcade.api.ui.Hologram;
import net.blueva.arcade.modules.bed_wars.support.spawner.SpawnerDefinition;
import net.blueva.arcade.modules.bed_wars.support.spawner.SpawnerType;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class ArenaSpawnerData {

    private final List<SpawnerDefinition> definitions = new ArrayList<>();
    private final Map<String, Integer> ticks = new ConcurrentHashMap<>();
    private final Map<String, Hologram<Location>> holograms = new ConcurrentHashMap<>();
    private final Map<String, ArmorStand> itemStands = new ConcurrentHashMap<>();
    private final Map<String, Integer> itemRotation = new ConcurrentHashMap<>();
    private final Map<SpawnerType, Double> globalMultipliers = new ConcurrentHashMap<>();
    private final Map<String, Map<SpawnerType, Double>> teamMultipliers = new ConcurrentHashMap<>();
    private final Set<Integer> firedEventIndices = ConcurrentHashMap.newKeySet();

    void setDefinitions(List<SpawnerDefinition> definitions) {
        this.definitions.clear();
        if (definitions != null) {
            this.definitions.addAll(definitions);
        }
    }

    List<SpawnerDefinition> getDefinitions() {
        return List.copyOf(definitions);
    }

    void removeDefinition(String spawnerKey) {
        definitions.removeIf(def -> def.getKey().equals(spawnerKey));
        ticks.remove(spawnerKey);
    }

    int incrementTicks(String spawnerKey) {
        return ticks.merge(spawnerKey, 1, Integer::sum);
    }

    void resetTicks(String spawnerKey) {
        ticks.put(spawnerKey, 0);
    }

    int getTicks(String spawnerKey) {
        if (spawnerKey == null) {
            return 0;
        }
        return ticks.getOrDefault(spawnerKey, 0);
    }

    void setHologram(String spawnerKey, Hologram<Location> hologram) {
        if (spawnerKey == null || spawnerKey.isBlank()) {
            return;
        }
        if (hologram == null) {
            holograms.remove(spawnerKey);
            return;
        }
        holograms.put(spawnerKey, hologram);
    }

    Hologram<Location> getHologram(String spawnerKey) {
        if (spawnerKey == null || spawnerKey.isBlank()) {
            return null;
        }
        return holograms.get(spawnerKey);
    }

    void removeHologram(String spawnerKey) {
        if (spawnerKey == null || spawnerKey.isBlank()) {
            return;
        }
        Hologram<Location> hologram = holograms.remove(spawnerKey);
        if (hologram != null) {
            hologram.delete();
        }
    }

    Set<String> getHologramKeys() {
        return Set.copyOf(holograms.keySet());
    }

    void setItemStand(String spawnerKey, ArmorStand stand) {
        if (spawnerKey == null || spawnerKey.isBlank()) return;
        if (stand == null) {
            ArmorStand old = itemStands.remove(spawnerKey);
            if (old != null && !old.isDead()) old.remove();
            return;
        }
        itemStands.put(spawnerKey, stand);
    }

    ArmorStand getItemStand(String spawnerKey) {
        if (spawnerKey == null || spawnerKey.isBlank()) return null;
        return itemStands.get(spawnerKey);
    }

    void removeItemStand(String spawnerKey) {
        if (spawnerKey == null || spawnerKey.isBlank()) return;
        ArmorStand stand = itemStands.remove(spawnerKey);
        if (stand != null && !stand.isDead()) stand.remove();
    }

    int getItemRotation(String spawnerKey) {
        return itemRotation.getOrDefault(spawnerKey, 0);
    }

    void setItemRotation(String spawnerKey, int rotation) {
        itemRotation.put(spawnerKey, rotation);
    }

    Set<String> getItemStandKeys() {
        return Set.copyOf(itemStands.keySet());
    }

    double getMultiplier(String teamId, SpawnerType type) {
        if (type == null) {
            return 1.0;
        }
        if (teamId != null) {
            double teamMultiplier = teamMultipliers
                    .getOrDefault(teamId.toLowerCase(), Map.of())
                    .getOrDefault(type, 1.0);
            if (teamMultiplier > 1.0) {
                return teamMultiplier;
            }
        }
        return globalMultipliers.getOrDefault(type, 1.0);
    }

    void setMultiplier(String teamId, SpawnerType type, double multiplier) {
        if (type != null && teamId != null) {
            teamMultipliers.computeIfAbsent(teamId.toLowerCase(), k -> new ConcurrentHashMap<>())
                    .put(type, Math.max(1.0, multiplier));
        }
    }

    void setGlobalMultiplier(SpawnerType type, double multiplier) {
        if (type != null) {
            globalMultipliers.put(type, Math.max(1.0, multiplier));
        }
    }

    boolean markEventFired(int index) {
        return firedEventIndices.add(index);
    }

    boolean isEventFired(int index) {
        return firedEventIndices.contains(index);
    }
}
