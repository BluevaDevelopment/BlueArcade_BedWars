package net.blueva.arcade.modules.bed_wars.support.shop;

import org.bukkit.Material;

import java.util.List;

public record BuyItem(
    String id,
    Material material,
    int amount,
    String name,
    List<String> lore,
    List<String> enchantments,
    List<String> effects,
    boolean autoEquip,
    String special,
    String potionColor,
    List<String> actions
) {}
