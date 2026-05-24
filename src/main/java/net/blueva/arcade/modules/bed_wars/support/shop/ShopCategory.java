package net.blueva.arcade.modules.bed_wars.support.shop;

import org.bukkit.Material;

import java.util.List;

public record ShopCategory(
    String id,
    Material icon,
    String name,
    int amount,
    int slot,
    List<String> lore,
    List<ShopContent> content
) {}
