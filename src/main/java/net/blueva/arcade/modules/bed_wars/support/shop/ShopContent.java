package net.blueva.arcade.modules.bed_wars.support.shop;

import java.util.List;

public record ShopContent(
    String id,
    int slot,
    String name,
    List<String> lore,
    boolean permanent,
    boolean downgradable,
    boolean unbreakable,
    int weight,
    List<String> actions,
    List<ContentTier> tiers
) {}
