package net.blueva.arcade.modules.bed_wars.state;

import net.blueva.arcade.api.ui.Hologram;
import net.blueva.arcade.modules.bed_wars.support.npc.ShopNpcDefinition;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class ArenaNpcData {

    private List<ShopNpcDefinition> definitions = new ArrayList<>();
    private final Map<String, UUID> entities = new ConcurrentHashMap<>();
    private final Map<String, Hologram<Location>> holograms = new ConcurrentHashMap<>();

    void setDefinitions(List<ShopNpcDefinition> definitions) {
        this.definitions = definitions != null ? new ArrayList<>(definitions) : new ArrayList<>();
    }

    List<ShopNpcDefinition> getDefinitions() {
        return List.copyOf(definitions);
    }

    void setEntity(String npcKey, UUID entityId) {
        if (npcKey != null && entityId != null) {
            entities.put(npcKey, entityId);
        }
    }

    UUID getEntity(String npcKey) {
        return entities.get(npcKey);
    }

    void setHologram(String npcKey, Hologram<Location> hologram) {
        if (npcKey == null || npcKey.isBlank()) {
            return;
        }
        if (hologram == null) {
            holograms.remove(npcKey);
            return;
        }
        holograms.put(npcKey, hologram);
    }

    Hologram<Location> getHologram(String npcKey) {
        if (npcKey == null || npcKey.isBlank()) {
            return null;
        }
        return holograms.get(npcKey);
    }

    void removeHologram(String npcKey) {
        if (npcKey == null || npcKey.isBlank()) {
            return;
        }
        Hologram<Location> hologram = holograms.remove(npcKey);
        if (hologram != null) {
            hologram.delete();
        }
    }

    Set<String> getHologramKeys() {
        return Set.copyOf(holograms.keySet());
    }
}
