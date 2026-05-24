package net.blueva.arcade.modules.bed_wars.support.shop;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ShopCache {

    private final UUID playerId;

    private final Map<String, Integer> contentTiers = new ConcurrentHashMap<>();

    private final Map<String, Integer> categoryWeights = new ConcurrentHashMap<>();

    private final Map<String, Boolean> permanentItems = new ConcurrentHashMap<>();

    public ShopCache(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getTier(String contentId) {
        return contentTiers.getOrDefault(contentId, -1);
    }

    public void setTier(String contentId, int tier) {
        contentTiers.put(contentId, tier);
    }

    public void downgradeTier(String contentId) {
        int current = getTier(contentId);
        if (current > 0) {
            contentTiers.put(contentId, current - 1);
        } else {
            contentTiers.remove(contentId);
        }
    }

    public int getCategoryWeight(String categoryId) {
        return categoryWeights.getOrDefault(categoryId, -1);
    }

    public void setCategoryWeight(String categoryId, int weight) {
        categoryWeights.put(categoryId, Math.max(weight, getCategoryWeight(categoryId)));
    }

    public boolean hasPermanentItem(String contentId) {
        return permanentItems.getOrDefault(contentId, false);
    }

    public void markPermanentItem(String contentId) {
        permanentItems.put(contentId, true);
    }

    public void clear() {
        contentTiers.clear();
        categoryWeights.clear();
        permanentItems.clear();
    }
}
