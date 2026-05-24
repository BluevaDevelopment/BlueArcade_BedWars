package net.blueva.arcade.modules.bed_wars.support.shop;

import org.bukkit.Material;

import java.util.List;

public record ContentTier(
    int tierIndex,
    Material material,
    int amount,
    boolean enchanted,
    int price,
    Material currency,
    List<BuyItem> buyItems,
    List<String> actions
) {}
