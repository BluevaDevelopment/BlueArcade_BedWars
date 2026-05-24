package net.blueva.arcade.modules.bed_wars.state;

import net.blueva.arcade.api.ui.Hologram;
import net.blueva.arcade.modules.bed_wars.support.bed.BedDefinition;
import net.blueva.arcade.modules.bed_wars.support.bed.BedState;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class ArenaBedData {

    private List<BedDefinition> definitions = new ArrayList<>();
    private final Map<String, BedState> states = new ConcurrentHashMap<>();
    private final Map<String, Hologram<Location>> holograms = new ConcurrentHashMap<>();

    void setDefinitions(List<BedDefinition> definitions) {
        this.definitions = definitions != null ? new ArrayList<>(definitions) : new ArrayList<>();
        for (BedDefinition def : this.definitions) {
            states.putIfAbsent(def.getKey(), BedState.INTACT);
        }
    }

    List<BedDefinition> getDefinitions() {
        return List.copyOf(definitions);
    }

    void removeDefinition(String teamKey) {
        if (teamKey == null || teamKey.isBlank()) {
            return;
        }
        definitions.removeIf(def -> def.getKey().equals(teamKey));
        states.remove(teamKey);
        removeHologram(teamKey);
    }

    BedState getState(String teamKey) {
        return states.getOrDefault(teamKey, BedState.INTACT);
    }

    void setState(String teamKey, BedState state) {
        if (teamKey != null && state != null) {
            states.put(teamKey, state);
        }
    }

    void setHologram(String teamKey, Hologram<Location> hologram) {
        if (teamKey == null || teamKey.isBlank()) {
            return;
        }
        if (hologram == null) {
            holograms.remove(teamKey);
            return;
        }
        holograms.put(teamKey, hologram);
    }

    Hologram<Location> getHologram(String teamKey) {
        if (teamKey == null || teamKey.isBlank()) {
            return null;
        }
        return holograms.get(teamKey);
    }

    void removeHologram(String teamKey) {
        if (teamKey == null || teamKey.isBlank()) {
            return;
        }
        Hologram<Location> hologram = holograms.remove(teamKey);
        if (hologram != null) {
            hologram.delete();
        }
    }

    Set<String> getHologramKeys() {
        return Set.copyOf(holograms.keySet());
    }
}
