package net.blueva.arcade.modules.bed_wars.support.special;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.team.TeamInfo;
import net.blueva.arcade.api.team.TeamsAPI;
import net.blueva.arcade.modules.bed_wars.game.BedWarsGame;
import net.blueva.foundation.entities.Entities;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpecialItemHandler {

    private static final String SPECIAL_BRIDGE_EGG = "bridge-egg";
    private static final String SPECIAL_BEDBUG = "bedbug";

    private final BedWarsGame game;
    private final ModuleConfigAPI moduleConfig;
    private final Map<UUID, Long> fireballCooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> trackedFireballs = ConcurrentHashMap.newKeySet();

    public SpecialItemHandler(BedWarsGame game, ModuleConfigAPI moduleConfig) {
        this.game = game;
        this.moduleConfig = moduleConfig;
    }

    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = game.getContext(player);
        if (context == null || !context.isPlayerPlaying(player)) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        String special = getSpecialTag(item);
        if (special == null) return;

        switch (special.toLowerCase()) {
            case "fireball" -> handleFireball(player, context, event);
            case "tnt" -> handleTNT(player, context, event);
            case SPECIAL_BRIDGE_EGG -> handleBridgeEgg(player, context, event);
            case "magic-milk" -> handleMagicMilk(player, context, event);
            case SPECIAL_BEDBUG -> handleBedbug(player, context, event);
            case "dream-defender" -> handleDreamDefender(player, context, event);
            case "sponge" -> handleSponge(player, context, event);
            case "tower" -> handleTower(player, context, event);
            default -> handleConfiguredSpecial(player, context, event, special.toLowerCase(Locale.ROOT));
        }
    }

    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player player)) return;

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = game.getContext(player);
        if (context == null || !context.isPlayerPlaying(player)) return;

        if (projectile instanceof Fireball && trackedFireballs.remove(projectile.getUniqueId())) {
            event.setCancelled(true);
            handleFireballHit(player, context, projectile, event);
            return;
        }

        String special = projectile.getCustomName();
        if (special == null) return;

        switch (special.toLowerCase()) {
            case SPECIAL_BRIDGE_EGG -> {
                Location loc = event.getHitBlock() != null ? event.getHitBlock().getLocation() : projectile.getLocation();
                Material bridgeMaterial = moduleConfig.getBoolean("special_items.bridge_egg.use_team_wool", true)
                        ? getTeamWool(player, context)
                        : parseMaterial(moduleConfig.getString("special_items.bridge_egg.fallback_material", "WHITE_WOOL"), Material.WHITE_WOOL);
                buildBridge(loc, bridgeMaterial);
            }
            case SPECIAL_BEDBUG -> {
                Location loc = event.getHitBlock() != null ? event.getHitBlock().getLocation() : projectile.getLocation();
                loc.getWorld().spawnEntity(loc, EntityType.SILVERFISH);
            }
        }
    }

    private void handleFireball(Player player, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        long now = System.currentTimeMillis();
        long last = fireballCooldowns.getOrDefault(player.getUniqueId(), 0L);
        int cooldownMs = moduleConfig.getInt("special_items.fireball.cooldown_seconds", 1) * 1000;
        if (cooldownMs > 0 && now - last < cooldownMs) {
            return;
        }
        fireballCooldowns.put(player.getUniqueId(), now);

        event.setCancelled(true);
        Fireball fireball = player.launchProjectile(Fireball.class);
        Vector direction = player.getEyeLocation().getDirection();
        fireball.setVelocity(direction.multiply(moduleConfig.getDouble("special_items.fireball.speed", 1.5)));
        fireball.setYield((float) moduleConfig.getDouble("special_items.fireball.explosion_size", 3.0));
        fireball.setIsIncendiary(false);
        fireball.setShooter(player);
        trackedFireballs.add(fireball.getUniqueId());

        consumeOneItem(player);
        Sound launchSound = parseSound(moduleConfig.getString("special_items.fireball.launch_sound", "ENTITY_GHAST_SHOOT"), Sound.ENTITY_GHAST_SHOOT);
        float volume = (float) moduleConfig.getDouble("special_items.fireball.launch_volume", 1.0);
        float pitch = (float) moduleConfig.getDouble("special_items.fireball.launch_pitch", 1.0);
        player.playSound(player.getLocation(), launchSound, volume, pitch);
    }

    private void handleFireballHit(Player player, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Projectile projectile, ProjectileHitEvent event) {
        Location loc = projectile.getLocation();
        World world = loc.getWorld();
        double explosionSize = moduleConfig.getDouble("special_items.fireball.explosion_size", 3.0);
        double horizontalKb = moduleConfig.getDouble("special_items.fireball.knockback_horizontal", 1.0);
        double verticalKb = moduleConfig.getDouble("special_items.fireball.knockback_vertical", 0.65);
        double damageSelf = moduleConfig.getDouble("special_items.fireball.damage_self", 2.0);
        double damageEnemy = moduleConfig.getDouble("special_items.fireball.damage_enemy", 2.0);
        double damageTeammates = moduleConfig.getDouble("special_items.fireball.damage_teammates", 0.0);


        world.createExplosion(loc, (float) explosionSize, false, false);


        net.blueva.arcade.modules.bed_wars.state.ArenaState state = game.getArenaState(context);
        int radius = (int) Math.ceil(explosionSize);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
                    Block b = world.getBlockAt(loc.getBlockX() + dx, loc.getBlockY() + dy, loc.getBlockZ() + dz);
                    if (b.getType() != org.bukkit.Material.AIR && state != null && state.isPlayerPlacedBlock(b.getLocation())) {
                        b.setType(org.bukkit.Material.AIR);
                        state.untrackPlacedBlock(b.getLocation());
                    }
                }
            }
        }

        Collection<Entity> nearby = world.getNearbyEntities(loc, explosionSize, explosionSize, explosionSize);
        for (Entity e : nearby) {
            if (!(e instanceof Player target)) continue;
            if (!context.isPlayerPlaying(target)) continue;


            Vector toTarget = target.getLocation().toVector().subtract(loc.toVector());
            Vector horizontal = toTarget.clone().setY(0).normalize().multiply(-horizontalKb);
            double y = toTarget.getY();
            if (y < 0) y += 1.5;
            if (y <= 0.5) {
                y = verticalKb * 1.5;
            } else {
                y = y * verticalKb * 1.5;
            }
            target.setVelocity(horizontal.setY(y));


            if (target.equals(player)) {
                if (damageSelf > 0) target.damage(damageSelf);
            } else {
                TeamsAPI<Player, Material> teamsAPI = context.getTeamsAPI();
                boolean sameTeam = false;
                if (teamsAPI != null && teamsAPI.isEnabled()) {
                    TeamInfo<Player, Material> t1 = teamsAPI.getTeam(player);
                    TeamInfo<Player, Material> t2 = teamsAPI.getTeam(target);
                    sameTeam = t1 != null && t2 != null && t1.getId().equalsIgnoreCase(t2.getId());
                }
                if (sameTeam) {
                    if (damageTeammates > 0) target.damage(damageTeammates);
                } else {
                    if (damageEnemy > 0) target.damage(damageEnemy);
                }
            }
        }
    }

    private void handleTNT(Player player, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;

        event.setCancelled(true);
        Location loc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);

        // EntityType.PRIMED_TNT was renamed to EntityType.TNT in 1.20.5
        TNTPrimed tnt = Entities.spawn(loc, TNTPrimed.class, "TNT", "PRIMED_TNT");
        if (tnt == null) return;
        tnt.setFuseTicks(moduleConfig.getInt("special_items.tnt.fuse_ticks", 40));

        consumeOneItem(player);
    }

    private void handleBridgeEgg(Player player, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);
        Location loc = player.getEyeLocation();
        Vector direction = loc.getDirection();

        org.bukkit.entity.Egg egg = (org.bukkit.entity.Egg) loc.getWorld().spawnEntity(loc, EntityType.EGG);
        egg.setVelocity(direction.multiply(moduleConfig.getDouble("special_items.bridge_egg.projectile_speed", 1.5)));
        egg.setShooter(player);
        egg.setCustomName(SPECIAL_BRIDGE_EGG);
        egg.setCustomNameVisible(false);

        consumeOneItem(player);
    }

    private void handleMagicMilk(Player player, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);
        consumeOneItem(player);
        int seconds = moduleConfig.getInt("special_items.magic_milk.trap_immunity_seconds", 30);
        String message = moduleConfig.getTranslation(player, "messages.special_items.magic_milk_used");
        context.getMessagesAPI().sendRaw(player, message.replace("{seconds}", String.valueOf(seconds)));
    }

    private void handleBedbug(Player player, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);
        Location loc = player.getEyeLocation();
        Vector direction = loc.getDirection();

        org.bukkit.entity.Snowball snowball = (org.bukkit.entity.Snowball) loc.getWorld().spawnEntity(loc, EntityType.SNOWBALL);
        snowball.setVelocity(direction.multiply(moduleConfig.getDouble("special_items.bedbug.projectile_speed", 1.5)));
        snowball.setShooter(player);
        snowball.setCustomName(SPECIAL_BEDBUG);
        snowball.setCustomNameVisible(false);

        consumeOneItem(player);
    }

    private void handleDreamDefender(Player player, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        event.setCancelled(true);
        Location loc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);


        org.bukkit.entity.IronGolem golem = (org.bukkit.entity.IronGolem) loc.getWorld().spawnEntity(loc, EntityType.IRON_GOLEM);
        golem.setCustomName(moduleConfig.getString("special_items.dream_defender.name", "Dream Defender"));
        golem.setCustomNameVisible(moduleConfig.getBoolean("special_items.dream_defender.name_visible", true));

        consumeOneItem(player);
    }

    private void handleSponge(Player player, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        event.setCancelled(true);

        Location center = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
        int radius = Math.max(0, moduleConfig.getInt("special_items.sponge.radius_blocks", 2));
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = center.clone().add(x, y, z).getBlock();
                    if (b.getType() == Material.WATER) {
                        b.setType(Material.AIR);
                    }
                }
            }
        }
        consumeOneItem(player);
    }

    private void handleTower(Player player, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        event.setCancelled(true);
        Location base = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
        Material wool = getTeamWool(player, context);
        int height = Math.max(1, moduleConfig.getInt("special_items.tower.height_blocks", 5));
        for (int i = 0; i < height; i++) {
            Block b = base.clone().add(0, i, 0).getBlock();
            if (b.getType() == Material.AIR) {
                b.setType(wool);
                game.trackPlacedBlock(context, b.getLocation());
            }
        }
        consumeOneItem(player);
    }

    private void handleConfiguredSpecial(Player player,
                                         GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                         PlayerInteractEvent event,
                                         String special) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        String basePath = "shop.specials." + special;
        List<String> actions = moduleConfig.getStringListFrom("menus/java/bed_wars_shop.yml", basePath + ".actions");
        if (actions.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        for (String rawAction : actions) {
            runConfiguredSpecialAction(player, context, special, rawAction);
        }
        if (moduleConfig.getBooleanFrom("menus/java/bed_wars_shop.yml", basePath + ".consume", true)) {
            consumeOneItem(player);
        }
    }

    private void runConfiguredSpecialAction(Player player,
                                            GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                            String special,
                                            String rawAction) {
        if (rawAction == null || rawAction.isBlank()) {
            return;
        }
        String action = applySpecialPlaceholders(rawAction, player, context, special);
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
            case "console", "console-command" -> org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), data);
            case "player", "player-command" -> player.performCommand(data.startsWith("/") ? data.substring(1) : data);
            case "sound" -> playConfiguredSound(player, data);
            case "effect", "potion-effect" -> applyConfiguredEffect(player, data);
            case "give" -> giveConfiguredItem(player, data);
            default -> {
            }
        }
    }

    private String applySpecialPlaceholders(String input,
                                            Player player,
                                            GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                            String special) {
        return input
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{arena}", String.valueOf(context.getArenaId()))
                .replace("{arena_id}", String.valueOf(context.getArenaId()))
                .replace("{team}", resolveTeamId(player, context))
                .replace("{special}", special);
    }

    private String resolveTeamId(Player player, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
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
        Sound sound = parseSound(parts[0], null);
        if (sound == null) {
            return;
        }
        try {
            float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0F;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0F;
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (NumberFormatException ignored) {}
    }

    private void applyConfiguredEffect(Player player, String data) {
        String[] parts = data.split(":");
        if (parts.length < 3) {
            return;
        }
        org.bukkit.potion.PotionEffectType type = org.bukkit.potion.PotionEffectType.getByName(parts[0].trim().toUpperCase(Locale.ROOT));
        if (type == null) {
            return;
        }
        try {
            int duration = Integer.parseInt(parts[1].trim());
            int amplifier = Integer.parseInt(parts[2].trim());
            boolean ambient = parts.length > 3 && Boolean.parseBoolean(parts[3].trim());
            boolean particles = parts.length <= 4 || Boolean.parseBoolean(parts[4].trim());
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(type, duration, amplifier, ambient, particles), true);
        } catch (NumberFormatException ignored) {}
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
            } catch (NumberFormatException ignored) {}
        }
        player.getInventory().addItem(new ItemStack(material, amount));
    }

    private Material getTeamWool(Player player, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        var teamsAPI = context.getTeamsAPI();
        if (teamsAPI == null || !teamsAPI.isEnabled()) return Material.WHITE_WOOL;
        var team = teamsAPI.getTeam(player);
        if (team == null) return Material.WHITE_WOOL;
        return switch (team.getId().toLowerCase()) {
            case "red" -> Material.RED_WOOL;
            case "blue" -> Material.BLUE_WOOL;
            case "green" -> Material.GREEN_WOOL;
            case "yellow" -> Material.YELLOW_WOOL;
            case "aqua", "cyan" -> Material.CYAN_WOOL;
            case "white" -> Material.WHITE_WOOL;
            case "black" -> Material.BLACK_WOOL;
            case "gray", "grey", "dark_gray", "dark_grey" -> Material.GRAY_WOOL;
            case "light_purple", "magenta" -> Material.MAGENTA_WOOL;
            case "pink" -> Material.PINK_WOOL;
            case "orange", "gold" -> Material.ORANGE_WOOL;
            case "lime" -> Material.LIME_WOOL;
            case "brown" -> Material.BROWN_WOOL;
            case "light_blue" -> Material.LIGHT_BLUE_WOOL;
            case "purple" -> Material.PURPLE_WOOL;
            default -> Material.WHITE_WOOL;
        };
    }

    private void buildBridge(Location hitLocation, Material material) {
        int length = Math.max(1, moduleConfig.getInt("special_items.bridge_egg.length_blocks", 10));
        int halfWidth = Math.max(0, moduleConfig.getInt("special_items.bridge_egg.half_width_blocks", 1));
        for (int i = 0; i < length; i++) {
            for (int j = -halfWidth; j <= halfWidth; j++) {
                Location blockLoc = hitLocation.clone().add(i, j, 0);
                if (blockLoc.getBlock().getType() == Material.AIR) {
                    blockLoc.getBlock().setType(material);
                }
            }
        }
    }

    private void consumeOneItem(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    private String getSpecialTag(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        if (meta.hasLore()) {
            for (String line : meta.getLore()) {
                String stripped = org.bukkit.ChatColor.stripColor(line);
                if (stripped.startsWith("[SPECIAL:")) {
                    return stripped.substring(9, stripped.length() - 1);
                }
            }
        }
        return switch (item.getType()) {
            case FIRE_CHARGE -> "fireball";
            case TNT -> "tnt";
            case EGG -> SPECIAL_BRIDGE_EGG;
            case MILK_BUCKET -> "magic-milk";
            case SNOWBALL -> SPECIAL_BEDBUG;
            case HORSE_SPAWN_EGG -> "dream-defender";
            case SPONGE -> "sponge";
            case CHEST -> "tower";
            default -> null;
        };
    }

    private Material parseMaterial(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return Material.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private Sound parseSound(String name, Sound fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return Sound.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
