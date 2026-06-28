package net.blueva.arcade.modules.bed_wars.support.shop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.team.TeamInfo;
import net.blueva.arcade.api.team.TeamsAPI;
import net.blueva.arcade.api.ui.ItemAPI;
import net.blueva.arcade.api.ui.MenuAPI;
import net.blueva.arcade.api.ui.menu.BedrockButtonDefinition;
import net.blueva.arcade.api.ui.menu.BedrockSimpleMenuDefinition;
import net.blueva.arcade.api.ui.menu.JavaItemDefinition;
import net.blueva.arcade.api.ui.menu.JavaMenuItem;
import net.blueva.arcade.api.ui.menu.MenuDefinition;
import net.blueva.arcade.modules.bed_wars.game.BedWarsGame;
import net.blueva.arcade.modules.bed_wars.state.ArenaState;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

final class ShopServiceImpl {
  private static final String SHOP_FILE = "menus/java/bed_wars_shop.yml";
  private static final String BEDROCK_SHOP_FILE = "menus/bedrock/bed_wars_shop.yml";
  private static final String BEDROCK_IMAGE_BASE = "https://blueva.net/api/menu/items/1_21_10";
  private final ModuleConfigAPI moduleConfig;
  private final MenuAPI<Player, Material> menuAPI;
  private final BedWarsGame game;
  private final Map<String, ShopCategory> categories = new ConcurrentHashMap<>();
  private final List<QuickBuyEntry> quickBuyDefaults = new ArrayList<>();
  private final Map<Integer, Map<Player, String>> playerSelectedCategory =
      new ConcurrentHashMap<>();
  private final Map<Integer, Map<UUID, ShopCache>> playerCaches = new ConcurrentHashMap<>();
  private final Set<UUID> playersInShop = ConcurrentHashMap.newKeySet();
  private final Map<UUID, List<QuickBuyEntry>> playerQuickBuys = new ConcurrentHashMap<>();
  private final Map<UUID, Long> lastShiftClick = new ConcurrentHashMap<>();
  private static final long SHIFT_CLICK_GRACE_MS = 500;
  private Material quickBuyButtonMaterial = Material.NETHER_STAR;
  private Material separatorRegular = Material.GRAY_STAINED_GLASS_PANE;
  private Material separatorSelected = Material.GREEN_STAINED_GLASS_PANE;
  private Material quickBuyEmpty = Material.RED_STAINED_GLASS_PANE;
  private int quickBuyButtonSlot = 0;
  private int quickBuyButtonAmount = 1;
  private int separatorRegularAmount = 1;
  private int separatorSelectedAmount = 1;
  private int quickBuyEmptyAmount = 1;
  private int menuSize = 54;
  private int maxIndexScan = 256;
  private List<Integer> categorySlots = defaultCategorySlots();
  private List<Integer> contentSlots = defaultDynamicSlots();
  private List<Integer> quickBuySlots = defaultQuickBuySlots();
  private List<Integer> separatorSlots = defaultSeparatorSlots();

  public ShopServiceImpl(
      ModuleConfigAPI moduleConfig, MenuAPI<Player, Material> menuAPI, BedWarsGame game) {
    this.moduleConfig = moduleConfig;
    this.menuAPI = menuAPI;
    this.game = game;
    loadShop();
  }

  public void loadShop() {
    categories.clear();
    quickBuyDefaults.clear();
    String file = SHOP_FILE;
    loadShopSettings(file);
    for (int qbIndex = 1; qbIndex <= maxIndexScan; qbIndex++) {
      String qbBase = "shop.quick_buy_defaults." + qbIndex;
      String category = moduleConfig.getStringFrom(file, qbBase + ".category", null);
      if (category == null) continue;
      String content = moduleConfig.getStringFrom(file, qbBase + ".content", null);
      int slot = resolveDynamicSlot(file, qbBase + ".slot", qbIndex - 1, quickBuySlots, 0);
      if (content != null) {
        quickBuyDefaults.add(new QuickBuyEntry(category, content, slot));
      }
    }
    for (int catIndex = 1; catIndex <= maxIndexScan; catIndex++) {
      String catBase = "shop.categories." + catIndex;
      String id = moduleConfig.getStringFrom(file, catBase + ".id", null);
      if (id == null) continue;
      Material icon = parseMaterial(moduleConfig.getStringFrom(file, catBase + ".icon", "STONE"));
      String name = moduleConfig.getStringFrom(file, catBase + ".name", id);
      int amount = Math.max(1, moduleConfig.getIntFrom(file, catBase + ".amount", 1));
      int slot = resolveDynamicSlot(file, catBase + ".slot", catIndex - 1, categorySlots, catIndex);
      List<String> categoryLore = moduleConfig.getStringListFrom(file, catBase + ".lore");
      List<ShopContent> contentList = new ArrayList<>();
      for (int contentIndex = 1; contentIndex <= maxIndexScan; contentIndex++) {
        String contentBase = catBase + ".content." + contentIndex;
        String contentId = moduleConfig.getStringFrom(file, contentBase + ".id", null);
        if (contentId == null) continue;
        int contentSlot =
            resolveDynamicSlot(file, contentBase + ".slot", contentIndex - 1, contentSlots, 0);
        String contentName = resolveContentName(file, contentBase, id, contentId);
        List<String> contentLore = moduleConfig.getStringListFrom(file, contentBase + ".lore");
        boolean permanent = moduleConfig.getBooleanFrom(file, contentBase + ".permanent", false);
        boolean downgradable =
            moduleConfig.getBooleanFrom(file, contentBase + ".downgradable", false);
        boolean unbreakable =
            moduleConfig.getBooleanFrom(file, contentBase + ".unbreakable", false);
        int weight = moduleConfig.getIntFrom(file, contentBase + ".weight", 0);
        List<String> contentActions =
            moduleConfig.getStringListFrom(file, contentBase + ".actions");
        List<ContentTier> tiers = new ArrayList<>();
        for (int tierIndex = 1; tierIndex <= maxIndexScan; tierIndex++) {
          String tierBase = contentBase + ".tiers." + tierIndex;
          String materialStr = moduleConfig.getStringFrom(file, tierBase + ".material", null);
          if (materialStr == null) continue;
          Material material = parseMaterial(materialStr);
          int tierAmount = moduleConfig.getIntFrom(file, tierBase + ".amount", 1);
          boolean enchanted = moduleConfig.getBooleanFrom(file, tierBase + ".enchanted", false);
          int price = moduleConfig.getIntFrom(file, tierBase + ".price", 1);
          Material currency =
              parseCurrency(moduleConfig.getStringFrom(file, tierBase + ".currency", "iron"));
          List<String> tierActions = moduleConfig.getStringListFrom(file, tierBase + ".actions");
          List<BuyItem> buyItems = new ArrayList<>();
          for (int biIndex = 1; biIndex <= maxIndexScan; biIndex++) {
            String biBase = tierBase + ".buy_items." + biIndex;
            String biMaterialStr = moduleConfig.getStringFrom(file, biBase + ".material", null);
            if (biMaterialStr == null) continue;
            Material biMaterial = parseMaterial(biMaterialStr);
            int biAmount = moduleConfig.getIntFrom(file, biBase + ".amount", 1);
            String biName = moduleConfig.getStringFrom(file, biBase + ".name", null);
            List<String> biLore = moduleConfig.getStringListFrom(file, biBase + ".lore");
            boolean autoEquip = moduleConfig.getBooleanFrom(file, biBase + ".auto_equip", false);
            String special = moduleConfig.getStringFrom(file, biBase + ".special", null);
            String potionColor = moduleConfig.getStringFrom(file, biBase + ".potion_color", null);
            List<String> biActions = moduleConfig.getStringListFrom(file, biBase + ".actions");
            List<String> enchantments = new ArrayList<>();
            for (int encIndex = 1; encIndex <= maxIndexScan; encIndex++) {
              String enc =
                  moduleConfig.getStringFrom(file, biBase + ".enchantments." + encIndex, null);
              if (enc != null) {
                enchantments.add(enc);
              }
            }
            List<String> effects = new ArrayList<>();
            for (int effIndex = 1; effIndex <= maxIndexScan; effIndex++) {
              String eff = moduleConfig.getStringFrom(file, biBase + ".effects." + effIndex, null);
              if (eff != null) {
                effects.add(eff);
              }
            }
            buyItems.add(
                new BuyItem(
                    contentId + "_tier" + tierIndex + "_item" + biIndex,
                    biMaterial,
                    biAmount,
                    biName,
                    biLore,
                    enchantments,
                    effects,
                    autoEquip,
                    special,
                    potionColor,
                    biActions));
          }
          tiers.add(
              new ContentTier(
                  tierIndex,
                  material,
                  tierAmount,
                  enchanted,
                  price,
                  currency,
                  buyItems,
                  tierActions));
        }
        tiers.sort(Comparator.comparingInt(ContentTier::tierIndex));
        if (!tiers.isEmpty()) {
          contentList.add(
              new ShopContent(
                  contentId,
                  contentSlot,
                  contentName,
                  contentLore,
                  permanent,
                  downgradable,
                  unbreakable,
                  weight,
                  contentActions,
                  tiers));
        }
      }
      contentList.sort(Comparator.comparingInt(ShopContent::slot));
      categories.put(id, new ShopCategory(id, icon, name, amount, slot, categoryLore, contentList));
    }
  }

  private void loadShopSettings(String file) {
    menuSize =
        normalizeMenuSize(
            moduleConfig.getIntFrom(file, "menu_size", moduleConfig.getIntFrom(file, "size", 54)));
    maxIndexScan = Math.max(1, moduleConfig.getIntFrom(file, "shop.settings.max_index_scan", 256));
    quickBuyButtonMaterial =
        parseMaterial(
            firstString(
                file,
                "items.quick_buy.item_stack.material",
                "shop.settings.quick_buy_button.material",
                "shop.quick_buy_button.material",
                "NETHER_STAR"));
    separatorRegular =
        parseMaterial(
            firstString(
                file,
                "templates.navigation.separator_regular.item_stack.material",
                "shop.settings.separator_regular.material",
                "shop.separator_regular.material",
                "GRAY_STAINED_GLASS_PANE"));
    separatorSelected =
        parseMaterial(
            firstString(
                file,
                "templates.navigation.separator_selected.item_stack.material",
                "shop.settings.separator_selected.material",
                "shop.separator_selected.material",
                "GREEN_STAINED_GLASS_PANE"));
    quickBuyEmpty =
        parseMaterial(
            firstString(
                file,
                "templates.quick_buy.empty.item_stack.material",
                "shop.settings.quick_buy_empty.material",
                "shop.quick_buy_empty.material",
                "RED_STAINED_GLASS_PANE"));
    quickBuyButtonSlot =
        Math.max(
            0,
            Math.min(
                menuSize - 1,
                moduleConfig.getIntFrom(
                    file,
                    "items.quick_buy.slot",
                    moduleConfig.getIntFrom(
                        file,
                        "shop.settings.quick_buy_button.slot",
                        moduleConfig.getIntFrom(file, "shop.quick_buy_button.slot", 0)))));
    quickBuyButtonAmount =
        Math.max(
            1,
            moduleConfig.getIntFrom(
                file,
                "items.quick_buy.item_stack.amount",
                moduleConfig.getIntFrom(
                    file,
                    "shop.settings.quick_buy_button.amount",
                    moduleConfig.getIntFrom(file, "shop.quick_buy_button.amount", 1))));
    separatorRegularAmount =
        Math.max(
            1,
            moduleConfig.getIntFrom(
                file,
                "templates.navigation.separator_regular.item_stack.amount",
                moduleConfig.getIntFrom(
                    file,
                    "shop.settings.separator_regular.amount",
                    moduleConfig.getIntFrom(file, "shop.separator_regular.amount", 1))));
    separatorSelectedAmount =
        Math.max(
            1,
            moduleConfig.getIntFrom(
                file,
                "templates.navigation.separator_selected.item_stack.amount",
                moduleConfig.getIntFrom(
                    file,
                    "shop.settings.separator_selected.amount",
                    moduleConfig.getIntFrom(file, "shop.separator_selected.amount", 1))));
    quickBuyEmptyAmount =
        Math.max(
            1,
            moduleConfig.getIntFrom(
                file,
                "templates.quick_buy.empty.item_stack.amount",
                moduleConfig.getIntFrom(
                    file,
                    "shop.settings.quick_buy_empty.amount",
                    moduleConfig.getIntFrom(file, "shop.quick_buy_empty.amount", 1))));
    categorySlots = parseSlotList(file, "templates.category.dynamic_slots", defaultCategorySlots());
    contentSlots = parseSlotList(file, "dynamic_slots", defaultDynamicSlots());
    quickBuySlots =
        parseSlotList(
            file,
            "templates.quick_buy.dynamic_slots",
            parseSlotList(file, "shop.settings.quick_buy_slots", contentSlots));
    separatorSlots = parseSlotList(file, "shop.settings.separator_slots", defaultSeparatorSlots());
  }

  private int resolveDynamicSlot(
      String file, String path, int index, List<Integer> slots, int fallback) {
    String raw = moduleConfig.getStringFrom(file, path, null);
    if (raw != null && !raw.isBlank()) {
      try {
        int configured = Integer.parseInt(raw.trim());
        if (configured >= 0 && configured < menuSize) {
          return configured;
        }
      } catch (NumberFormatException ignored) {
      }
    }
    if (index >= 0 && index < slots.size()) {
      return slots.get(index);
    }
    return fallback;
  }

  private String firstString(
      String file, String preferredPath, String legacyPath, String fallback) {
    return firstString(file, preferredPath, legacyPath, null, fallback);
  }

  private String firstString(
      String file,
      String preferredPath,
      String legacyPath,
      String olderLegacyPath,
      String fallback) {
    String preferred = moduleConfig.getStringFrom(file, preferredPath, null);
    if (preferred != null) {
      return preferred;
    }
    String legacy = moduleConfig.getStringFrom(file, legacyPath, null);
    if (legacy != null) {
      return legacy;
    }
    if (olderLegacyPath != null) {
      return moduleConfig.getStringFrom(file, olderLegacyPath, fallback);
    }
    return fallback;
  }

  private int normalizeMenuSize(int configuredSize) {
    int size = Math.max(9, Math.min(54, configuredSize));
    return size % 9 == 0 ? size : ((size / 9) + 1) * 9;
  }

  private List<Integer> parseSlotList(String file, String path, List<Integer> fallback) {
    List<Integer> slots = new ArrayList<>();
    List<String> rawList = moduleConfig.getStringListFrom(file, path);
    if (!rawList.isEmpty()) {
      for (String raw : rawList) {
        addSlotsFromRaw(raw, slots);
      }
    } else {
      addSlotsFromRaw(moduleConfig.getStringFrom(file, path, ""), slots);
    }
    if (slots.isEmpty()) {
      return new ArrayList<>(fallback);
    }
    return slots;
  }

  private void addSlotsFromRaw(String raw, List<Integer> slots) {
    if (raw == null || raw.isBlank()) {
      return;
    }
    for (String part : raw.split(",")) {
      String trimmed = part.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      try {
        int slot = Integer.parseInt(trimmed);
        if (slot >= 0 && slot < menuSize && !slots.contains(slot)) {
          slots.add(slot);
        }
      } catch (NumberFormatException ignored) {
      }
    }
  }

  private List<Integer> defaultDynamicSlots() {
    return new ArrayList<>(
        List.of(
            19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43));
  }

  private List<Integer> defaultCategorySlots() {
    return new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7));
  }

  private List<Integer> defaultQuickBuySlots() {
    return defaultDynamicSlots();
  }

  private List<Integer> defaultSeparatorSlots() {
    return new ArrayList<>(List.of(9, 10, 11, 12, 13, 14, 15, 16, 17));
  }

  private String resolveContentName(
      String file, String contentBase, String categoryId, String contentId) {
    String name = moduleConfig.getStringFrom(file, contentBase + ".name", null);
    if (name != null && !name.isBlank()) {
      return name;
    }
    return capitalizeItemName(contentId);
  }

  private List<QuickBuyEntry> getPlayerQuickBuy(UUID playerId) {
    return playerQuickBuys.computeIfAbsent(
        playerId,
        id -> {
          List<QuickBuyEntry> copy = new ArrayList<>();
          for (QuickBuyEntry entry : quickBuyDefaults) {
            copy.add(new QuickBuyEntry(entry.categoryId(), entry.contentId(), entry.slot()));
          }
          return copy;
        });
  }

  public void openShop(
      Player player,
      GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
      ArenaState state) {
    if (categories.isEmpty()) {
      context
          .getMessagesAPI()
          .sendRaw(
              player,
              message("messages.shop.not_configured", "<red>Shop is not configured.</red>"));
      return;
    }
    playerSelectedCategory
        .computeIfAbsent(context.getArenaId(), k -> new ConcurrentHashMap<>())
        .remove(player);
    if (isBedrockPlayer(player)) {
      openBedrockCategories(player, context, state);
      return;
    }
    openQuickBuy(player, context, state);
  }

  public void openQuickBuy(
      Player player,
      GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
      ArenaState state) {
    if (menuAPI == null) return;
    List<JavaMenuItem<Material>> items = new ArrayList<>();
    String activeCategory = null;
    buildNavigation(items, activeCategory, player, context, state);
    ShopCache cache = getCache(context.getArenaId(), player.getUniqueId());
    List<QuickBuyEntry> playerQB = getPlayerQuickBuy(player.getUniqueId());
    Map<Integer, QuickBuyEntry> occupied = new HashMap<>();
    for (QuickBuyEntry entry : playerQB) {
      occupied.put(entry.slot(), entry);
    }
    for (int slot : quickBuySlots) {
      QuickBuyEntry entry = occupied.get(slot);
      if (entry != null) {
        ShopCategory cat = categories.get(entry.categoryId());
        if (cat != null) {
          ShopContent content = findContent(cat, entry.contentId());
          if (content != null) {
            JavaItemDefinition<Material> def =
                buildContentDefinition(player, context, state, cache, cat.id(), content);
            List<String> extendedLore = new ArrayList<>(def.lore());
            extendedLore.add(
                menuText(
                    "templates.quick_buy.remove_lore",
                    "<dark_gray>Sneak Click to remove from Quick Buy!</dark_gray>"));
            def =
                new JavaItemDefinition<>(
                    def.material(),
                    def.amount(),
                    def.name(),
                    extendedLore,
                    def.skullValue(),
                    def.actions());
            items.add(JavaMenuItem.of(slot, def));
            continue;
          }
        }
      }
      List<String> emptyLore =
          moduleConfig.getStringListFrom(SHOP_FILE, "templates.quick_buy.empty.lore");
      if (emptyLore.isEmpty()) {
        emptyLore =
            List.of(
                "<red>This is a Quick Buy Slot!</red>",
                "<gray>Sneak Click any item in</gray>",
                "<gray>the shop to add it here.</gray>");
      }
      String emptyName = menuText("templates.quick_buy.empty.name", " ");
      items.add(
          JavaMenuItem.of(
              slot,
              JavaItemDefinition.of(
                  quickBuyEmpty, quickBuyEmptyAmount, emptyName, emptyLore, List.of())));
    }
    String title =
        moduleConfig.getStringFrom(
            "menus/java/bed_wars_shop.yml", "menu_name", "<dark_gray>Item Shop");
    title = title.replace("{view}", menuText("templates.quick_buy.view", "Quick Buy"));
    MenuDefinition<Material> menu = new MenuDefinition<>(title, menuSize, items, null);
    menuAPI.openMenu(player, menu, Map.of());
    playersInShop.add(player.getUniqueId());
  }

  public void openCategory(
      Player player,
      GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
      ArenaState state,
      String categoryId) {
    if (menuAPI == null) return;
    ShopCategory category = categories.get(categoryId);
    if (category == null) return;
    if (isBedrockPlayer(player)) {
      openBedrockCategory(player, context, state, category);
      return;
    }
    List<JavaMenuItem<Material>> items = new ArrayList<>();
    buildNavigation(items, categoryId, player, context, state);
    ShopCache cache = getCache(context.getArenaId(), player.getUniqueId());
    for (ShopContent content : category.content()) {
      JavaItemDefinition<Material> def =
          buildContentDefinition(player, context, state, cache, categoryId, content);
      List<String> extendedLore = new ArrayList<>(def.lore());
      extendedLore.add(
          menuText(
              "templates.quick_buy.add_lore",
              "<dark_gray>Sneak Click to add to Quick Buy</dark_gray>"));
      def =
          new JavaItemDefinition<>(
              def.material(),
              def.amount(),
              def.name(),
              extendedLore,
              def.skullValue(),
              def.actions());
      items.add(JavaMenuItem.of(content.slot(), def));
    }
    String title =
        moduleConfig.getStringFrom(
            "menus/java/bed_wars_shop.yml", "menu_name", "<dark_gray>Item Shop");
    title = title.replace("{view}", category.name());
    MenuDefinition<Material> menu = new MenuDefinition<>(title, menuSize, items, null);
    menuAPI.openMenu(player, menu, Map.of());
    playersInShop.add(player.getUniqueId());
  }

  private void buildNavigation(
      List<JavaMenuItem<Material>> items,
      String activeCategory,
      Player player,
      GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
      ArenaState state) {
    boolean qbActive = activeCategory == null;
    String qbName =
        qbActive
            ? menuText("templates.quick_buy.tab_selected", "<yellow>Quick Buy</yellow>")
            : menuText("templates.quick_buy.tab", "<gray>Quick Buy</gray>");
    List<String> qbLore =
        qbActive
            ? List.of(menuText("templates.navigation.selected_lore", "<green>Selected</green>"))
            : List.of(
                menuText("templates.navigation.click_to_view_lore", "<gray>Click to view</gray>"));
    List<String> qbActions =
        List.of("MODULE;" + game.getModuleInfo().getId() + ";shop:category:quick_buy");
    items.add(
        JavaMenuItem.of(
            quickBuyButtonSlot,
            JavaItemDefinition.of(
                quickBuyButtonMaterial, quickBuyButtonAmount, qbName, qbLore, qbActions)));
    for (ShopCategory cat : categories.values()) {
      boolean selected = cat.id().equals(activeCategory);
      String name =
          (selected
                  ? menuText("templates.category.tab_selected", "<yellow>{category}</yellow>")
                  : menuText("templates.category.tab", "<gray>{category}</gray>"))
              .replace("{category}", cat.name());
      List<String> lore = new ArrayList<>(cat.lore());
      lore.add(
          selected
              ? menuText("templates.navigation.selected_lore", "<green>Selected</green>")
              : menuText("templates.navigation.click_to_view_lore", "<gray>Click to view</gray>"));
      List<String> actions =
          List.of("MODULE;" + game.getModuleInfo().getId() + ";shop:category:" + cat.id());
      items.add(
          JavaMenuItem.of(
              cat.slot(), JavaItemDefinition.of(cat.icon(), cat.amount(), name, lore, actions)));
    }
    for (int i : separatorSlots) {
      boolean isSelectedSep = false;
      if (activeCategory != null) {
        ShopCategory activeCat = categories.get(activeCategory);
        if (activeCat != null && i == activeCat.slot() + 9) {
          isSelectedSep = true;
        }
      } else if (i == quickBuyButtonSlot + 9) {
        isSelectedSep = true;
      }
      Material sepMat = isSelectedSep ? separatorSelected : separatorRegular;
      int amount = isSelectedSep ? separatorSelectedAmount : separatorRegularAmount;
      items.add(
          JavaMenuItem.of(i, JavaItemDefinition.of(sepMat, amount, " ", List.of(), List.of())));
    }
  }

  public boolean handleAction(
      Player player,
      String payload,
      GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
      ArenaState state) {
    if (payload.startsWith("shop:back")) {
      playerSelectedCategory
          .computeIfAbsent(context.getArenaId(), k -> new ConcurrentHashMap<>())
          .remove(player);
      openShop(player, context, state);
      return true;
    }
    if (payload.startsWith("shop:category:")) {
      String categoryId = payload.substring("shop:category:".length());
      if ("quick_buy".equals(categoryId)) {
        playerSelectedCategory
            .computeIfAbsent(context.getArenaId(), k -> new ConcurrentHashMap<>())
            .remove(player);
        if (isBedrockPlayer(player)) {
          openBedrockCategories(player, context, state);
        } else {
          openQuickBuy(player, context, state);
        }
      } else {
        playerSelectedCategory
            .computeIfAbsent(context.getArenaId(), k -> new ConcurrentHashMap<>())
            .put(player, categoryId);
        openCategory(player, context, state, categoryId);
      }
      return true;
    }
    if (payload.startsWith("shop:buy:")) {
      long lastShift = lastShiftClick.getOrDefault(player.getUniqueId(), 0L);
      if (System.currentTimeMillis() - lastShift < SHIFT_CLICK_GRACE_MS) {
        return true;
      }
      String contentId = payload.substring("shop:buy:".length());
      String currentCategory =
          playerSelectedCategory.getOrDefault(context.getArenaId(), Map.of()).get(player);
      ShopCategory cat = null;
      ShopContent content = null;
      if (currentCategory != null) {
        cat = categories.get(currentCategory);
        if (cat != null) {
          content = findContent(cat, contentId);
        }
      } else {
        for (ShopCategory c : categories.values()) {
          content = findContent(c, contentId);
          if (content != null) {
            cat = c;
            break;
          }
        }
      }
      if (content != null && cat != null) {
        boolean purchased = purchaseContent(player, context, state, cat, content);
        if (isBedrockPlayer(player) && !purchased) {
          return true;
        }
        if (currentCategory != null) {
          openCategory(player, context, state, currentCategory);
        } else {
          openQuickBuy(player, context, state);
        }
        return true;
      }
    }
    return false;
  }

  private ShopContent findContent(ShopCategory category, String contentId) {
    for (ShopContent c : category.content()) {
      if (c.id().equals(contentId)) {
        return c;
      }
    }
    return null;
  }

  private boolean purchaseContent(
      Player player,
      GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
      ArenaState state,
      ShopCategory category,
      ShopContent content) {
    ShopCache cache = getCache(context.getArenaId(), player.getUniqueId());
    int currentTierIndex = cache.getTier(content.id());
    int nextTierIndex = currentTierIndex + 1;
    if (content.weight() > 0) {
      int maxWeight = cache.getCategoryWeight(category.id());
      if (maxWeight >= 0 && content.weight() < maxWeight) {
        String msg =
            message("messages.shop.weight_blocked", "<red>You already have better armor!</red>");
        if (msg != null) context.getMessagesAPI().sendRaw(player, msg);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
        return false;
      }
    }
    if (nextTierIndex >= content.tiers().size()) {
      if (content.permanent() && cache.hasPermanentItem(content.id())) {
        String msg =
            message(
                "messages.shop.already_bought", "<red>You already own this permanent item!</red>");
        if (msg != null) context.getMessagesAPI().sendRaw(player, msg);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
        return false;
      }
      nextTierIndex = currentTierIndex;
    }
    ContentTier tier = content.tiers().get(nextTierIndex);
    int money = calculateMoney(player, tier.currency());
    if (money < tier.price()) {
      String msg =
          message(
              "messages.shop.insufficient_money",
              "<red>You don't have enough {currency}! Need {price} more.</red>");
      if (msg != null) {
        msg =
            msg.replace("{price}", String.valueOf(tier.price()))
                .replace("{currency}", formatCurrencyName(tier.currency()));
        context.getMessagesAPI().sendRaw(player, msg);
      }
      player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
      return false;
    }
    if (content.permanent() && cache.hasPermanentItem(content.id())) {
      String msg =
          message(
              "messages.shop.already_bought", "<red>You already own this permanent item!</red>");
      if (msg != null) context.getMessagesAPI().sendRaw(player, msg);
      player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
      return false;
    }
    if (currentTierIndex >= 0 && nextTierIndex > currentTierIndex) {
      ContentTier oldTier = content.tiers().get(currentTierIndex);
      for (BuyItem bi : oldTier.buyItems()) {
        removeItems(player, bi.material(), bi.amount());
      }
    }
    takeMoney(player, tier.currency(), tier.price());
    for (BuyItem bi : tier.buyItems()) {
      giveBuyItem(player, context, state, bi, content, category.id());
      runConfiguredActions(player, context, state, category, content, tier, bi.actions());
    }
    cache.setTier(content.id(), nextTierIndex);
    if (content.permanent()) {
      cache.markPermanentItem(content.id());
    }
    if (content.weight() > 0) {
      cache.setCategoryWeight(category.id(), content.weight());
    }
    String msg = message("messages.shop.purchased", "<green>You purchased {item}!</green>");
    if (msg != null) {
      msg = applyActionPlaceholders(msg, player, context, category, content);
      context.getMessagesAPI().sendRaw(player, msg);
    }
    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
    runConfiguredActions(player, context, state, category, content, tier, content.actions());
    runConfiguredActions(player, context, state, category, content, tier, tier.actions());
    return true;
  }

  private void giveBuyItem(
      Player player,
      GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
      ArenaState state,
      BuyItem buyItem,
      ShopContent content,
      String categoryId) {
    Material material = buyItem.material();
    material = colorizeForTeam(material, player, context);
    ItemStack stack = new ItemStack(material, buyItem.amount());
    ItemAPI<Player, ItemStack, Material> itemAPI = context.getItemAPI();
    String displayName =
        buyItem.name() != null && !buyItem.name().isBlank() ? buyItem.name() : content.name();
    if (displayName == null || displayName.isBlank()) {
      displayName = capitalizeItemName(content.id());
    }
    List<String> lore = new ArrayList<>(buyItem.lore().isEmpty() ? content.lore() : buyItem.lore());
    if (buyItem.special() != null && !buyItem.special().isBlank()) {
      lore.add("[SPECIAL:" + buyItem.special().toLowerCase() + "]");
    }
    if (itemAPI != null && displayName != null) {
      stack = itemAPI.decorate(stack, displayName, lore, player, Map.of());
    }
    ItemMeta meta = stack.getItemMeta();
    if (meta != null) {
      if (content.unbreakable()) {
        meta.setUnbreakable(true);
      }
      if (meta instanceof org.bukkit.inventory.meta.PotionMeta potionMeta
          && buyItem.potionColor() != null) {
        try {
          java.awt.Color color = java.awt.Color.decode(buyItem.potionColor());
          potionMeta.setColor(
              org.bukkit.Color.fromRGB(color.getRed(), color.getGreen(), color.getBlue()));
        } catch (NumberFormatException ignored) {
        }
        stack.setItemMeta(potionMeta);
      }
      stack.setItemMeta(meta);
    }
    for (String enc : buyItem.enchantments()) {
      String[] parts = enc.split(":");
      if (parts.length >= 2) {
        Enchantment e =
            Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(parts[0].toLowerCase()));
        if (e == null) {
          try {
            e = Enchantment.getByName(parts[0].toUpperCase());
          } catch (Exception ignored) {
          }
        }
        if (e != null) {
          int level = 1;
          try {
            level = Integer.parseInt(parts[1]);
          } catch (NumberFormatException ignored) {
          }
          stack.addUnsafeEnchantment(e, level);
        }
      }
    }
    for (String eff : buyItem.effects()) {
      String[] parts = eff.split(":");
      if (parts.length >= 3) {
        PotionEffectType type = PotionEffectType.getByName(parts[0].toUpperCase());
        if (type != null) {
          int duration = Integer.parseInt(parts[1]);
          int amplifier = Integer.parseInt(parts[2]);
          boolean ambient = parts.length > 3 && Boolean.parseBoolean(parts[3]);
          boolean particles = parts.length <= 4 || Boolean.parseBoolean(parts[4]);
          player.addPotionEffect(new PotionEffect(type, duration, amplifier, ambient, particles));
        }
      }
    }
    TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
    String teamId = null;
    if (teamsAPI != null && teamsAPI.isEnabled()) {
      TeamInfo<Player, Material> team = teamsAPI.getTeam(player);
      if (team != null) {
        teamId = team.getId().toLowerCase();
      }
    }
    if (teamId != null && meta != null) {
      if (buyItem.autoEquip()) {
        for (ArenaState.TeamEnchantment enchant : state.getTeamArmorEnchantments(teamId)) {
          Enchantment enc = resolveEnchantment(enchant.getEnchantmentName());
          if (enc != null) {
            meta.addEnchant(enc, enchant.getLevel(), true);
          }
        }
        stack.setItemMeta(meta);
      } else if (material == Material.BOW) {
        for (ArenaState.TeamEnchantment enchant : state.getTeamBowEnchantments(teamId)) {
          Enchantment enc = resolveEnchantment(enchant.getEnchantmentName());
          if (enc != null) {
            meta.addEnchant(enc, enchant.getLevel(), true);
          }
        }
        stack.setItemMeta(meta);
      } else if (material.name().endsWith("_SWORD") || material.name().endsWith("_AXE")) {
        for (ArenaState.TeamEnchantment enchant : state.getTeamSwordEnchantments(teamId)) {
          Enchantment enc = resolveEnchantment(enchant.getEnchantmentName());
          if (enc != null) {
            meta.addEnchant(enc, enchant.getLevel(), true);
          }
        }
        stack.setItemMeta(meta);
      }
    }
    if (content.permanent() && meta != null) {
      try {
        org.bukkit.NamespacedKey key =
            new org.bukkit.NamespacedKey("bluearcade", "bedwars_permanent");
        meta.getPersistentDataContainer()
            .set(key, org.bukkit.persistence.PersistentDataType.STRING, content.id());
        stack.setItemMeta(meta);
      } catch (Exception ignored) {
      }
    }
    boolean wasEquipped = false;
    if (buyItem.autoEquip() && meta != null) {
      String typeName = material.name();
      org.bukkit.inventory.PlayerInventory inv = player.getInventory();
      if (typeName.endsWith("_BOOTS")) {
        inv.setBoots(stack);
        wasEquipped = true;
      } else if (typeName.endsWith("_LEGGINGS")) {
        inv.setLeggings(stack);
        wasEquipped = true;
      } else if (typeName.endsWith("_CHESTPLATE")) {
        inv.setChestplate(stack);
        wasEquipped = true;
      } else if (typeName.endsWith("_HELMET")) {
        inv.setHelmet(stack);
        wasEquipped = true;
      }
    }
    if (!wasEquipped) {
      if (material.name().endsWith("_SWORD")) {
        removeAllSwords(player, material);
      }
      player.getInventory().addItem(stack);
    }
  }

  private void runConfiguredActions(
      Player player,
      GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
      ArenaState state,
      ShopCategory category,
      ShopContent content,
      ContentTier tier,
      List<String> actions) {
    if (actions == null || actions.isEmpty()) {
      return;
    }
    for (String rawAction : actions) {
      if (rawAction == null || rawAction.isBlank()) {
        continue;
      }
      String action = applyActionPlaceholders(rawAction, player, context, category, content, tier);
      String[] parts = action.split(":", 2);
      String type = parts[0].trim().toLowerCase(Locale.ROOT);
      String data = parts.length > 1 ? parts[1].trim() : "";
      switch (type) {
        case "message" -> context.getMessagesAPI().sendRaw(player, data);
        case "broadcast" -> {
          for (Player online : context.getPlayers()) {
            if (online.isOnline()) {
              context.getMessagesAPI().sendRaw(online, data);
            }
          }
        }
        case "console", "console-command" ->
            org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), data);
        case "player", "player-command" ->
            player.performCommand(data.startsWith("/") ? data.substring(1) : data);
        case "sound" -> playConfiguredSound(player, data);
        case "effect", "potion-effect" -> applyConfiguredEffect(player, data);
        case "give" -> giveConfiguredItem(player, data);
        default -> {}
      }
    }
  }

  private String applyActionPlaceholders(
      String input,
      Player player,
      GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
      ShopCategory category,
      ShopContent content) {
    return applyActionPlaceholders(input, player, context, category, content, null);
  }

  private String applyActionPlaceholders(
      String input,
      Player player,
      GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
      ShopCategory category,
      ShopContent content,
      ContentTier tier) {
    String teamId = resolveTeamId(player, context);
    String result =
        input
            .replace("{player}", player.getName())
            .replace("{uuid}", player.getUniqueId().toString())
            .replace("{arena}", String.valueOf(context.getArenaId()))
            .replace("{arena_id}", String.valueOf(context.getArenaId()))
            .replace("{team}", teamId)
            .replace("{category}", category.name())
            .replace("{category_id}", category.id())
            .replace("{item}", content.name())
            .replace("{item_id}", content.id());
    if (tier != null) {
      result =
          result
              .replace("{tier}", String.valueOf(tier.tierIndex()))
              .replace("{price}", String.valueOf(tier.price()))
              .replace("{currency}", formatCurrencyName(tier.currency()));
    }
    return result;
  }

  private String resolveTeamId(
      Player player,
      GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
    TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
    if (teamsAPI == null || !teamsAPI.isEnabled()) {
      return "solo";
    }
    TeamInfo<Player, Material> team = teamsAPI.getTeam(player);
    return team != null && team.getId() != null ? team.getId() : "solo";
  }

  private void playConfiguredSound(Player player, String data) {
    String[] parts = data.split(":");
    if (parts.length == 0 || parts[0].isBlank()) {
      return;
    }
    try {
      Sound sound = Sound.valueOf(parts[0].trim().toUpperCase(Locale.ROOT));
      float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0F;
      float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0F;
      player.playSound(player.getLocation(), sound, volume, pitch);
    } catch (IllegalArgumentException ignored) {
    }
  }

  private void applyConfiguredEffect(Player player, String data) {
    String[] parts = data.split(":");
    if (parts.length < 3) {
      return;
    }
    PotionEffectType type = PotionEffectType.getByName(parts[0].trim().toUpperCase(Locale.ROOT));
    if (type == null) {
      return;
    }
    try {
      int duration = Integer.parseInt(parts[1].trim());
      int amplifier = Integer.parseInt(parts[2].trim());
      boolean ambient = parts.length > 3 && Boolean.parseBoolean(parts[3].trim());
      boolean particles = parts.length <= 4 || Boolean.parseBoolean(parts[4].trim());
      player.addPotionEffect(new PotionEffect(type, duration, amplifier, ambient, particles), true);
    } catch (NumberFormatException ignored) {
    }
  }

  private void giveConfiguredItem(Player player, String data) {
    String[] parts = data.split(":");
    if (parts.length == 0 || parts[0].isBlank()) {
      return;
    }
    Material material = parseMaterial(parts[0], null);
    if (material == null) {
      return;
    }
    int amount = 1;
    if (parts.length > 1) {
      try {
        amount = Math.max(1, Integer.parseInt(parts[1].trim()));
      } catch (NumberFormatException ignored) {
      }
    }
    player.getInventory().addItem(new ItemStack(material, amount));
  }

  private void removeAllSwords(Player player, Material newSword) {
    org.bukkit.inventory.PlayerInventory inv = player.getInventory();
    int newDamage = getSwordDamage(newSword);
    for (int i = 0; i < inv.getSize(); i++) {
      ItemStack item = inv.getItem(i);
      if (item != null && item.getType().name().endsWith("_SWORD")) {
        if (getSwordDamage(item.getType()) <= newDamage) {
          inv.setItem(i, null);
        }
      }
    }
  }

  private int getSwordDamage(Material material) {
    return switch (material) {
      case WOODEN_SWORD -> 4;
      case GOLDEN_SWORD -> 4;
      case STONE_SWORD -> 5;
      case IRON_SWORD -> 6;
      case DIAMOND_SWORD -> 7;
      case NETHERITE_SWORD -> 8;
      default -> 0;
    };
  }

  private Enchantment resolveEnchantment(String name) {
    Enchantment enc = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(name.toLowerCase()));
    if (enc == null) {
      try {
        enc = Enchantment.getByName(name.toUpperCase());
      } catch (Exception ignored) {
      }
    }
    return enc;
  }

  private void removeItems(Player player, Material material, int amount) {
    int remaining = amount;
    for (ItemStack is : player.getInventory().getContents()) {
      if (is == null) continue;
      if (is.getType() == material) {
        if (is.getAmount() <= remaining) {
          remaining -= is.getAmount();
          player.getInventory().remove(is);
        } else {
          is.setAmount(is.getAmount() - remaining);
          remaining = 0;
          break;
        }
      }
    }
  }

  private void openBedrockCategories(
      Player player,
      GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
      ArenaState state) {
    if (menuAPI == null) return;
    List<BedrockButtonDefinition> buttons = new ArrayList<>();
    List<ShopCategory> sortedCategories = new ArrayList<>(categories.values());
    sortedCategories.sort(Comparator.comparingInt(ShopCategory::slot));
    for (ShopCategory category : sortedCategories) {
      String text =
          bedrockText(
                  "templates.category_button.text",
                  "<yellow>{category}</yellow>\n<gray>{items} items</gray>")
              .replace("\\n", "\n")
              .replace("{category}", category.name())
              .replace("{category_id}", category.id())
              .replace("{items}", String.valueOf(category.content().size()));
      String image = bedrockCategoryImage(category);
      List<String> actions =
          List.of("MODULE;" + game.getModuleInfo().getId() + ";shop:category:" + category.id());
      buttons.add(BedrockButtonDefinition.of(text, image, actions));
    }
    String title =
        moduleConfig.getStringFrom(
            BEDROCK_SHOP_FILE,
            "menuName",
            moduleConfig.getStringFrom(
                BEDROCK_SHOP_FILE,
                "menu_name",
                moduleConfig.getStringFrom(SHOP_FILE, "menu_name", "<dark_gray>Item Shop")));
    title = title.replace("{view}", bedrockText("templates.categories_view", "Categories"));
    List<String> content = moduleConfig.getStringListFrom(BEDROCK_SHOP_FILE, "content");
    if (content.isEmpty()) {
      content = List.of("<white>Select a category.</white>");
    }
    BedrockSimpleMenuDefinition bedrockMenu =
        new BedrockSimpleMenuDefinition(title, content, buttons);
    MenuDefinition<Material> menu = new MenuDefinition<>(title, menuSize, List.of(), bedrockMenu);
    menuAPI.openMenu(player, menu, Map.of());
    playersInShop.add(player.getUniqueId());
  }

  private void openBedrockCategory(
      Player player,
      GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
      ArenaState state,
      ShopCategory category) {
    if (menuAPI == null) return;
    List<BedrockButtonDefinition> buttons = new ArrayList<>();
    String backText =
        bedrockText("templates.back_button.text", "<white>← Back to categories</white>")
            .replace("\\n", "\n");
    String backImage =
        moduleConfig.getStringFrom(
            BEDROCK_SHOP_FILE, "templates.back_button.image", materialImage(Material.ARROW));
    buttons.add(
        BedrockButtonDefinition.of(
            backText, backImage, List.of("MODULE;" + game.getModuleInfo().getId() + ";shop:back")));
    ShopCache cache = getCache(context.getArenaId(), player.getUniqueId());
    for (ShopContent content : category.content()) {
      buttons.add(buildBedrockContentButton(player, context, state, cache, category, content));
    }
    String title =
        moduleConfig.getStringFrom(
            BEDROCK_SHOP_FILE,
            "menuName",
            moduleConfig.getStringFrom(
                BEDROCK_SHOP_FILE,
                "menu_name",
                moduleConfig.getStringFrom(SHOP_FILE, "menu_name", "<dark_gray>Item Shop")));
    title = title.replace("{view}", category.name());
    List<String> menuContent = List.of("<white>" + category.name() + "</white>");
    BedrockSimpleMenuDefinition bedrockMenu =
        new BedrockSimpleMenuDefinition(title, menuContent, buttons);
    MenuDefinition<Material> menu = new MenuDefinition<>(title, menuSize, List.of(), bedrockMenu);
    menuAPI.openMenu(player, menu, Map.of());
    playersInShop.add(player.getUniqueId());
  }

  private BedrockButtonDefinition buildBedrockContentButton(
      Player player,
      GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
      ArenaState state,
      ShopCache cache,
      ShopCategory category,
      ShopContent content) {
    int currentTierIndex = cache.getTier(content.id());
    int nextTierIndex = currentTierIndex + 1;
    boolean isMaxedPermanent =
        content.permanent()
            && nextTierIndex >= content.tiers().size()
            && cache.hasPermanentItem(content.id());
    boolean maxed =
        isMaxedPermanent || (nextTierIndex >= content.tiers().size() && content.tiers().size() > 1);
    ContentTier displayTier;
    if (isMaxedPermanent) {
      displayTier = content.tiers().get(currentTierIndex);
    } else if (nextTierIndex >= content.tiers().size() && !content.permanent()) {
      displayTier = content.tiers().get(currentTierIndex);
    } else {
      displayTier = content.tiers().get(nextTierIndex);
    }
    boolean canAfford =
        !maxed && calculateMoney(player, displayTier.currency()) >= displayTier.price();
    String color = maxed ? "<aqua>" : (canAfford ? "<green>" : "<red>");
    String status =
        maxed
            ? bedrockText("templates.content.maxed_line", "<aqua>MAXED OUT</aqua>")
            : (canAfford
                ? bedrockText(
                    "templates.content.click_to_purchase_line", "<green>Click to purchase!</green>")
                : bedrockText(
                    "templates.content.not_enough_resources_line",
                    "<red>Not enough resources!</red>"));
    String text =
        bedrockText(
                "templates.item_button.text",
                "{color}{item}\n"
                    + "<gray>{amount}x - {currency_color}{price} {currency}</gray>\n"
                    + "{status}")
            .replace("\\n", "\n")
            .replace("{color}", color)
            .replace("{item}", content.name())
            .replace("{item_id}", content.id())
            .replace("{amount}", String.valueOf(displayTier.amount()))
            .replace("{currency_color}", getCurrencyColor(displayTier.currency()))
            .replace("{price}", String.valueOf(displayTier.price()))
            .replace("{currency}", formatCurrencyName(displayTier.currency()))
            .replace("{status}", status);
    String image = bedrockContentImage(category, content, displayTier);
    List<String> actions =
        maxed
            ? List.of()
            : List.of("MODULE;" + game.getModuleInfo().getId() + ";shop:buy:" + content.id());
    return BedrockButtonDefinition.of(text, image, actions);
  }

  private boolean isBedrockPlayer(Player player) {
    try {
      return menuAPI != null && menuAPI.isBedrockPlayer(player);
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  private String bedrockText(String path, String fallback) {
    return moduleConfig.getStringFrom(BEDROCK_SHOP_FILE, path, fallback);
  }

  private String bedrockCategoryImage(ShopCategory category) {
    for (int index = 1; index <= maxIndexScan; index++) {
      String base = "shop.categories." + index;
      String id = moduleConfig.getStringFrom(BEDROCK_SHOP_FILE, base + ".id", null);
      if (category.id().equals(id)) {
        String image = moduleConfig.getStringFrom(BEDROCK_SHOP_FILE, base + ".image", null);
        if (image != null && !image.isBlank()) return image;
      }
    }
    return materialImage(category.icon());
  }

  private String bedrockContentImage(ShopCategory category, ShopContent content, ContentTier tier) {
    for (int catIndex = 1; catIndex <= maxIndexScan; catIndex++) {
      String catBase = "shop.categories." + catIndex;
      String catId = moduleConfig.getStringFrom(BEDROCK_SHOP_FILE, catBase + ".id", null);
      if (!category.id().equals(catId)) continue;
      for (int contentIndex = 1; contentIndex <= maxIndexScan; contentIndex++) {
        String contentBase = catBase + ".content." + contentIndex;
        String contentId = moduleConfig.getStringFrom(BEDROCK_SHOP_FILE, contentBase + ".id", null);
        if (!content.id().equals(contentId)) continue;
        String image =
            moduleConfig.getStringFrom(
                BEDROCK_SHOP_FILE, contentBase + ".tiers." + tier.tierIndex() + ".image", null);
        if (image != null && !image.isBlank()) return image;
      }
    }
    return materialImage(tier.material());
  }

  private String materialImage(Material material) {
    return BEDROCK_IMAGE_BASE + "/minecraft_" + material.name().toLowerCase(Locale.ROOT) + ".png";
  }

  private JavaItemDefinition<Material> buildContentDefinition(
      Player player,
      GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
      ArenaState state,
      ShopCache cache,
      String categoryId,
      ShopContent content) {
    int currentTierIndex = cache.getTier(content.id());
    int nextTierIndex = currentTierIndex + 1;
    boolean isMaxedPermanent =
        content.permanent()
            && nextTierIndex >= content.tiers().size()
            && cache.hasPermanentItem(content.id());
    boolean maxed =
        isMaxedPermanent || (nextTierIndex >= content.tiers().size() && content.tiers().size() > 1);
    ContentTier displayTier;
    if (isMaxedPermanent) {
      displayTier = content.tiers().get(currentTierIndex);
    } else if (nextTierIndex >= content.tiers().size() && !content.permanent()) {
      displayTier = content.tiers().get(currentTierIndex);
    } else {
      displayTier = content.tiers().get(nextTierIndex);
    }
    boolean canAfford =
        !maxed && calculateMoney(player, displayTier.currency()) >= displayTier.price();
    String color = maxed ? "<aqua>" : (canAfford ? "<green>" : "<red>");
    String itemName = content.name();
    String displayName = color + itemName;
    List<String> lore = new ArrayList<>(content.lore());
    if (content.tiers().size() > 1) {
      for (int i = 0; i < content.tiers().size(); i++) {
        ContentTier tier = content.tiers().get(i);
        String tierColor;
        if (i < nextTierIndex) {
          tierColor = "<green>";
        } else if (i == nextTierIndex && !maxed) {
          tierColor = "<yellow>";
        } else {
          tierColor = "<gray>";
        }
        String roman = toRoman(i + 1);
        String currencyColor = getCurrencyColor(tier.currency());
        lore.add(
            menuText(
                    "templates.content.tier_line",
                    "{color}Tier {tier} <gray>- {currency_color}{price} {currency}</gray>")
                .replace("{color}", tierColor)
                .replace("{tier}", roman)
                .replace("{currency_color}", currencyColor)
                .replace("{price}", String.valueOf(tier.price()))
                .replace("{currency}", formatCurrencyName(tier.currency())));
      }
    } else {
      ContentTier tier = content.tiers().get(0);
      String currencyColor = getCurrencyColor(tier.currency());
      lore.add(
          menuText(
                  "templates.content.cost_line",
                  "<gray>Cost: {currency_color}{price} {currency}</gray>")
              .replace("{currency_color}", currencyColor)
              .replace("{price}", String.valueOf(tier.price()))
              .replace("{currency}", formatCurrencyName(tier.currency())));
    }
    if (maxed) {
      lore.add(menuText("templates.content.maxed_line", "<aqua>MAXED OUT</aqua>"));
    } else if (canAfford) {
      lore.add(
          menuText(
              "templates.content.click_to_purchase_line", "<green>Click to purchase!</green>"));
    } else {
      lore.add(
          menuText(
              "templates.content.not_enough_resources_line", "<red>Not enough resources!</red>"));
    }
    List<String> actions =
        List.of("MODULE;" + game.getModuleInfo().getId() + ";shop:buy:" + content.id());
    Material displayMaterial = colorizeForTeam(displayTier.material(), player, context);
    JavaItemDefinition<Material> def =
        JavaItemDefinition.of(displayMaterial, displayTier.amount(), displayName, lore, actions);
    return def;
  }

  private Material colorizeForTeam(
      Material material,
      Player player,
      GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
    if (material != Material.WHITE_WOOL && material != Material.WHITE_STAINED_GLASS) {
      return material;
    }
    TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
    if (teamsAPI == null || !teamsAPI.isEnabled()) {
      return material;
    }
    TeamInfo<Player, Material> team = teamsAPI.getTeam(player);
    if (team == null) {
      return material;
    }
    String suffix = material == Material.WHITE_WOOL ? "_WOOL" : "_STAINED_GLASS";
    DyeColor dye = resolveDyeColor(team.getId());
    if (dye == null) {
      return material;
    }
    String coloredName = dye.name() + suffix;
    try {
      return Material.valueOf(coloredName);
    } catch (IllegalArgumentException e) {
      return material;
    }
  }

  private DyeColor resolveDyeColor(String teamId) {
    return switch (teamId.toLowerCase()) {
      case "red" -> DyeColor.RED;
      case "blue" -> DyeColor.BLUE;
      case "green" -> DyeColor.GREEN;
      case "yellow" -> DyeColor.YELLOW;
      case "aqua", "cyan" -> DyeColor.CYAN;
      case "white" -> DyeColor.WHITE;
      case "black" -> DyeColor.BLACK;
      case "gray", "grey" -> DyeColor.GRAY;
      case "dark_gray", "dark_grey" -> DyeColor.GRAY;
      case "light_purple", "magenta" -> DyeColor.MAGENTA;
      case "pink" -> DyeColor.PINK;
      case "orange", "gold" -> DyeColor.ORANGE;
      case "lime" -> DyeColor.LIME;
      case "brown" -> DyeColor.BROWN;
      case "light_blue" -> DyeColor.LIGHT_BLUE;
      case "purple" -> DyeColor.PURPLE;
      default -> null;
    };
  }

  public boolean isPlayerInShop(Player player) {
    return playersInShop.contains(player.getUniqueId());
  }

  public void onPlayerCloseShop(Player player) {
    playersInShop.remove(player.getUniqueId());
  }

  public boolean handleShopShiftClick(
      Player player,
      int slot,
      GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
      ArenaState state) {
    if (!playersInShop.contains(player.getUniqueId())) return false;
    lastShiftClick.put(player.getUniqueId(), System.currentTimeMillis());
    String currentCategory =
        playerSelectedCategory.getOrDefault(context.getArenaId(), Map.of()).get(player);
    List<QuickBuyEntry> playerQB = getPlayerQuickBuy(player.getUniqueId());
    if (currentCategory != null) {
      ShopCategory cat = categories.get(currentCategory);
      if (cat == null) return false;
      for (ShopContent content : cat.content()) {
        if (content.slot() == slot) {
          for (QuickBuyEntry existing : playerQB) {
            if (existing.categoryId().equals(currentCategory)
                && existing.contentId().equals(content.id())) {
              String msg =
                  message(
                      "messages.shop.quick_buy_already_added",
                      "<yellow>That item is already in your Quick Buy!</yellow>");
              if (msg != null) context.getMessagesAPI().sendRaw(player, msg);
              player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
              return true;
            }
          }
          for (int qbSlot : quickBuySlots) {
            boolean occupied = false;
            for (QuickBuyEntry entry : playerQB) {
              if (entry.slot() == qbSlot) {
                occupied = true;
                break;
              }
            }
            if (!occupied) {
              playerQB.add(new QuickBuyEntry(currentCategory, content.id(), qbSlot));
              String msg =
                  message("messages.shop.quick_buy_added", "<green>Added to Quick Buy!</green>");
              if (msg != null) context.getMessagesAPI().sendRaw(player, msg);
              player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
              openCategory(player, context, state, currentCategory);
              return true;
            }
          }
          String msgFull = message("messages.shop.quick_buy_full", "<red>Quick Buy is full!</red>");
          if (msgFull != null) context.getMessagesAPI().sendRaw(player, msgFull);
          return true;
        }
      }
    } else {
      if (!quickBuySlots.contains(slot)) return false;
      for (int i = 0; i < playerQB.size(); i++) {
        QuickBuyEntry entry = playerQB.get(i);
        if (entry.slot() == slot) {
          playerQB.remove(i);
          String msg =
              message("messages.shop.quick_buy_removed", "<red>Removed from Quick Buy.</red>");
          if (msg != null) context.getMessagesAPI().sendRaw(player, msg);
          player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
          openQuickBuy(player, context, state);
          return true;
        }
      }
    }
    return false;
  }

  public static boolean isPermanentShopItem(ItemStack item) {
    if (item == null || !item.hasItemMeta()) return false;
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return false;
    try {
      org.bukkit.NamespacedKey key =
          new org.bukkit.NamespacedKey("bluearcade", "bedwars_permanent");
      return meta.getPersistentDataContainer()
          .has(key, org.bukkit.persistence.PersistentDataType.STRING);
    } catch (Exception e) {
      return false;
    }
  }

  public static int calculateMoney(Player player, Material currency) {
    if (currency == null) {
      return getVaultBalance(player);
    }
    int amount = 0;
    for (ItemStack is : player.getInventory().getContents()) {
      if (is == null) continue;
      if (is.getType() == currency) amount += is.getAmount();
    }
    return amount;
  }

  public static void takeMoney(Player player, Material currency, int amount) {
    if (currency == null) {
      withdrawVault(player, amount);
      return;
    }
    int remaining = amount;
    for (ItemStack is : player.getInventory().getContents()) {
      if (is == null) continue;
      if (is.getType() == currency) {
        if (is.getAmount() <= remaining) {
          remaining -= is.getAmount();
          player.getInventory().remove(is);
        } else {
          is.setAmount(is.getAmount() - remaining);
          remaining = 0;
          break;
        }
      }
    }
  }

  private static int getVaultBalance(Player player) {
    Object economy = resolveEconomy();
    if (economy == null) return 0;
    try {
      Object response =
          economy
              .getClass()
              .getMethod("getBalance", org.bukkit.OfflinePlayer.class)
              .invoke(economy, player);
      if (response instanceof Number n) {
        return n.intValue();
      }
    } catch (Exception ignored) {
    }
    return 0;
  }

  private static void withdrawVault(Player player, int amount) {
    Object economy = resolveEconomy();
    if (economy == null) return;
    try {
      economy
          .getClass()
          .getMethod("withdrawPlayer", org.bukkit.OfflinePlayer.class, double.class)
          .invoke(economy, player, (double) amount);
    } catch (Exception ignored) {
    }
  }

  private static Object resolveEconomy() {
    if (vaultEconomy != null) return vaultEconomy;
    try {
      org.bukkit.plugin.RegisteredServiceProvider<?> rsp =
          org.bukkit.Bukkit.getServer()
              .getServicesManager()
              .getRegistration(Class.forName("net.milkbowl.vault.economy.Economy"));
      if (rsp != null) {
        vaultEconomy = rsp.getProvider();
      }
    } catch (Exception ignored) {
    }
    return vaultEconomy;
  }

  private static Object vaultEconomy = null;

  private String formatCurrencyName(Material currency) {
    if (currency == null) return message("messages.shop.currencies.money", "Money");
    return switch (currency) {
      case IRON_INGOT -> message("messages.shop.currencies.iron", "Iron");
      case GOLD_INGOT -> message("messages.shop.currencies.gold", "Gold");
      case DIAMOND -> message("messages.shop.currencies.diamond", "Diamond");
      case EMERALD -> message("messages.shop.currencies.emerald", "Emerald");
      default -> currency.name();
    };
  }

  private String message(String path, String fallback) {
    return moduleConfig.getStringFrom("language.yml", path, fallback);
  }

  private String menuText(String path, String fallback) {
    return moduleConfig.getStringFrom(SHOP_FILE, path, fallback);
  }

  private Material parseMaterial(String name) {
    return parseMaterial(name, Material.STONE);
  }

  private Material parseMaterial(String name, Material fallback) {
    if (name == null) return fallback;
    try {
      return Material.valueOf(name.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return fallback;
    }
  }

  private Material parseCurrency(String name) {
    if (name == null || name.isBlank()) {
      return Material.IRON_INGOT;
    }
    return switch (name.trim().toLowerCase(Locale.ROOT)) {
      case "iron" -> Material.IRON_INGOT;
      case "gold" -> Material.GOLD_INGOT;
      case "diamond" -> Material.DIAMOND;
      case "emerald" -> Material.EMERALD;
      case "vault" -> null;
      default -> Material.IRON_INGOT;
    };
  }

  private String toRoman(int n) {
    return switch (n) {
      case 1 -> "I";
      case 2 -> "II";
      case 3 -> "III";
      case 4 -> "IV";
      case 5 -> "V";
      default -> String.valueOf(n);
    };
  }

  public ShopCache getCache(int arenaId, UUID playerId) {
    return playerCaches
        .computeIfAbsent(arenaId, k -> new ConcurrentHashMap<>())
        .computeIfAbsent(playerId, ShopCache::new);
  }

  public void removePlayer(int arenaId, Player player) {
    Map<Player, String> map = playerSelectedCategory.get(arenaId);
    if (map != null) map.remove(player);
    Map<UUID, ShopCache> caches = playerCaches.get(arenaId);
    if (caches != null) caches.remove(player.getUniqueId());
    playersInShop.remove(player.getUniqueId());
    playerQuickBuys.remove(player.getUniqueId());
  }

  public void clearArena(int arenaId) {
    playerSelectedCategory.remove(arenaId);
    playerCaches.remove(arenaId);
    playerQuickBuys.clear();
  }

  public void restoreOnRespawn(
      Player player,
      GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
      ArenaState state) {
    ShopCache cache = getCache(context.getArenaId(), player.getUniqueId());
    removeConflictingDefaults(player, cache);
    for (ShopCategory cat : categories.values()) {
      for (ShopContent content : cat.content()) {
        if (!content.permanent() && !content.downgradable()) continue;
        int tierIndex = cache.getTier(content.id());
        if (tierIndex < 0) continue;
        if (content.downgradable() && tierIndex > 0) {
          cache.downgradeTier(content.id());
          tierIndex = cache.getTier(content.id());
        }
        if (tierIndex < 0) continue;
        ContentTier tier = content.tiers().get(tierIndex);
        for (BuyItem bi : tier.buyItems()) {
          giveBuyItem(player, context, state, bi, content, cat.id());
        }
      }
    }
  }

  private void removeConflictingDefaults(Player player, ShopCache cache) {
    for (ShopCategory cat : categories.values()) {
      for (ShopContent content : cat.content()) {
        if (!content.permanent()) continue;
        if (cache.getTier(content.id()) < 0) continue;
        int tierIndex = cache.getTier(content.id());
        if (tierIndex < 0 || tierIndex >= content.tiers().size()) continue;
        ContentTier tier = content.tiers().get(tierIndex);
        for (BuyItem bi : tier.buyItems()) {
          if (bi.autoEquip()) {
            Material mat = bi.material();
            String name = mat.name();
            org.bukkit.inventory.PlayerInventory inv = player.getInventory();
            if (name.endsWith("_BOOTS")) {
              if (inv.getBoots() != null) inv.setBoots(null);
            } else if (name.endsWith("_LEGGINGS")) {
              if (inv.getLeggings() != null) inv.setLeggings(null);
            } else if (name.endsWith("_CHESTPLATE")) {
              if (inv.getChestplate() != null) inv.setChestplate(null);
            } else if (name.endsWith("_HELMET")) {
              if (inv.getHelmet() != null) inv.setHelmet(null);
            }
          }
        }
      }
    }
  }

  private String getCurrencyColor(Material currency) {
    if (currency == null) return "<white>";
    String name = currency.name().toLowerCase();
    if (name.contains("iron")) return "<white>";
    if (name.contains("gold")) return "<gold>";
    if (name.contains("diamond")) return "<aqua>";
    if (name.contains("emerald")) return "<dark_green>";
    return "<white>";
  }

  private String capitalizeItemName(String raw) {
    if (raw == null || raw.isBlank()) return "";
    String[] words = raw.replace('_', ' ').split(" ");
    StringBuilder sb = new StringBuilder();
    for (String word : words) {
      if (word.isEmpty()) continue;
      sb.append(Character.toUpperCase(word.charAt(0)))
          .append(word.substring(1).toLowerCase())
          .append(' ');
    }
    return sb.toString().trim();
  }
}
