package com.enderdevelopment.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class Main extends JavaPlugin implements Listener {
    private NamespacedKey gearKey;
    private NamespacedKey weaponKey;
    private BukkitTask flightTask;
    private final Map<String, ArmorSet> armorSets = Map.of(
            "t45", new ArmorSet("T-45 Combat Armor", Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS, NamedTextColor.GOLD, 1.0, 0.0),
            "t60", new ArmorSet("T-60 Advanced Armor", Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS, NamedTextColor.AQUA, 1.15, 0.25));

    @Override
    public void onEnable() {
        gearKey = new NamespacedKey(this, "wasteland_gear");
        weaponKey = new NamespacedKey(this, "wasteland_weapon");
        Bukkit.getPluginManager().registerEvents(this, this);
        flightTask = Bukkit.getScheduler().runTaskTimer(this, this::updateArmorEffects, 20L, 20L);
        getLogger().info("WastelandVaults enabled.");
    }

    @Override
    public void onDisable() {
        if (flightTask != null) flightTask.cancel();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getAllowFlight()) player.setAllowFlight(false);
            player.setFlying(false);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        if (!player.hasPermission("wasteland.use")) {
            player.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("Wasteland: /wasteland give <t45|t60>, weapon <minigun|fatman>, structure <bunker|vault>, enemy <ghoul|mutant|deathclaw>", NamedTextColor.YELLOW));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> giveArmor(player, args);
            case "weapon" -> giveWeapon(player, args);
            case "structure" -> placeStructure(player, args);
            case "enemy" -> spawnEnemy(player, args);
            default -> player.sendMessage(Component.text("Unknown option. Try /wasteland", NamedTextColor.RED));
        }
        return true;
    }

    private void giveArmor(Player player, String[] args) {
        if (args.length < 2 || !armorSets.containsKey(args[1].toLowerCase(Locale.ROOT))) {
            player.sendMessage(Component.text("Usage: /wasteland give <t45|t60>", NamedTextColor.YELLOW));
            return;
        }
        ArmorSet set = armorSets.get(args[1].toLowerCase(Locale.ROOT));
        ItemStack[] pieces = {gearPiece(set.helmet(), set, "helmet"), gearPiece(set.chest(), set, "chest"), gearPiece(set.legs(), set, "legs"), gearPiece(set.boots(), set, "boots")};
        for (ItemStack piece : pieces) player.getInventory().addItem(piece).values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.getInventory().addItem(createWeapon("minigun"), createWeapon("fatman"));
        player.sendMessage(Component.text(set.name() + " issued. Full set grants protection and powered flight. Weapons added.", set.color()));
    }

    private ItemStack gearPiece(Material material, ArmorSet set, String piece) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(Component.text(set.name() + " - " + piece, set.color()));
        meta.getPersistentDataContainer().set(gearKey, PersistentDataType.STRING, set.id());
        if (set.protection() > 0) {
            Enchantment protection = org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft("protection"));
            if (protection != null) meta.addEnchant(protection, (int) Math.ceil(set.protection()), true);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void giveWeapon(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /wasteland weapon <minigun|fatman>", NamedTextColor.YELLOW));
            return;
        }
        String type = args[1].toLowerCase(Locale.ROOT);
        if (!type.equals("minigun") && !type.equals("fatman")) {
            player.sendMessage(Component.text("Weapon must be minigun or fatman.", NamedTextColor.RED));
            return;
        }
        player.getInventory().addItem(createWeapon(type));
        if (type.equals("fatman")) player.getInventory().addItem(namedItem(Material.FIREWORK_ROCKET, "Mini-nuke charge", type, NamedTextColor.RED));
        player.sendMessage(Component.text(type.equals("minigun") ? "Minigun issued. Right-click to fire." : "Fat Man and one mini-nuke charge issued. Right-click to launch.", NamedTextColor.YELLOW));
    }

    private ItemStack createWeapon(String type) {
        Material material = type.equals("minigun") ? Material.IRON_HORSE_ARMOR : Material.CROSSBOW;
        return namedItem(material, type.equals("minigun") ? "Wasteland Minigun" : "Fat Man Launcher", type, type.equals("minigun") ? NamedTextColor.GRAY : NamedTextColor.RED);
    }

    private ItemStack namedItem(Material material, String name, String id, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color));
            meta.getPersistentDataContainer().set(weaponKey, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onWeaponUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        String type = item.getItemMeta().getPersistentDataContainer().get(weaponKey, PersistentDataType.STRING);
        if (type == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (type.equals("minigun")) fireMinigun(player);
        if (type.equals("fatman")) fireFatMan(player);
    }

    private void fireMinigun(Player player) {
        if (player.getCooldown(player.getInventory().getItemInMainHand().getType()) > 0) return;
        Vector direction = player.getEyeLocation().getDirection().normalize();
        Location origin = player.getEyeLocation().clone();
        World world = player.getWorld();
        for (int i = 0; i < 28; i++) {
            double spread = 0.025;
            Vector shot = direction.clone().add(new Vector((ThreadLocalRandom.current().nextDouble() - .5) * spread, (ThreadLocalRandom.current().nextDouble() - .5) * spread, (ThreadLocalRandom.current().nextDouble() - .5) * spread)).normalize();
            Location point = origin.clone();
            for (double distance = 0; distance < 32; distance += .5) {
                point.add(shot.clone().multiply(.5));
                if (point.getBlock().getType().isSolid()) break;
                for (Entity entity : world.getNearbyEntities(point, .45, .45, .45)) {
                    if (entity instanceof org.bukkit.entity.LivingEntity target && !entity.equals(player)) {
                        target.damage(2.0, player);
                        distance = 32;
                        break;
                    }
                }
            }
        }
        player.setCooldown(Material.IRON_HORSE_ARMOR, 12);
        world.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, .5f, 1.7f);
        world.spawnParticle(Particle.CRIT, origin.add(direction.multiply(1.5)), 8, .08, .08, .08, .02);
    }

    private void fireFatMan(Player player) {
        ItemStack ammo = findFatManAmmo(player);
        if (ammo == null) {
            player.sendMessage(Component.text("You need a mini-nuke charge.", NamedTextColor.RED));
            return;
        }
        ammo.setAmount(ammo.getAmount() - 1);
        Location target = player.getTargetBlockExact(35) == null ? player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(24)) : player.getTargetBlockExact(35).getLocation().add(.5, 1, .5);
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1, .5f);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            world.spawnParticle(Particle.EXPLOSION, target, 1);
            world.spawnParticle(Particle.FLAME, target, 70, 2.2, 1.5, 2.2, .04);
            world.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 2, .6f);
            for (Entity entity : world.getNearbyEntities(target, 6, 4, 6)) {
                if (entity instanceof org.bukkit.entity.LivingEntity living && !entity.equals(player)) living.damage(24.0, player);
            }
        }, 18L);
    }

    private ItemStack findFatManAmmo(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            String id = item.getItemMeta().getPersistentDataContainer().get(weaponKey, PersistentDataType.STRING);
            if ("fatman".equals(id) && item.getType() == Material.FIREWORK_ROCKET) return item;
        }
        return null;
    }

    private void spawnEnemy(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /wasteland enemy <ghoul|mutant|deathclaw>", NamedTextColor.YELLOW));
            return;
        }
        String kind = args[1].toLowerCase(Locale.ROOT);
        EntityType type;
        String name;
        switch (kind) {
            case "ghoul" -> { type = EntityType.HUSK; name = "Feral Ghoul"; }
            case "mutant" -> { type = EntityType.IRON_GOLEM; name = "Super Mutant"; }
            case "deathclaw" -> { type = EntityType.RAVAGER; name = "Deathclaw"; }
            default -> {
                player.sendMessage(Component.text("Enemy must be ghoul, mutant, or deathclaw.", NamedTextColor.RED));
                return;
            }
        }
        Location spawn = player.getLocation().add(player.getLocation().getDirection().multiply(3));
        LivingEntity enemy = (LivingEntity) player.getWorld().spawnEntity(spawn, type);
        enemy.customName(Component.text(name, NamedTextColor.DARK_RED));
        enemy.setCustomNameVisible(true);
        enemy.setPersistent(true);
        if (kind.equals("ghoul")) enemy.setMaxHealth(24.0);
        if (kind.equals("mutant")) enemy.setMaxHealth(60.0);
        if (kind.equals("deathclaw")) enemy.setMaxHealth(100.0);
        enemy.setHealth(enemy.getMaxHealth());
        player.sendMessage(Component.text(name + " spawned nearby.", NamedTextColor.GREEN));
    }

    private void placeStructure(Player player, String[] args) {
        if (args.length < 2 || (!args[1].equalsIgnoreCase("bunker") && !args[1].equalsIgnoreCase("vault"))) {
            player.sendMessage(Component.text("Usage: /wasteland structure <bunker|vault>", NamedTextColor.YELLOW));
            return;
        }
        Location center = player.getLocation().getBlock().getRelative(player.getFacing()).getLocation();
        int radius = args[1].equalsIgnoreCase("vault") ? 7 : 5;
        if (!player.hasPermission("wasteland.structure")) {
            player.sendMessage(Component.text("You do not have permission to place structures.", NamedTextColor.RED));
            return;
        }
        if (center.getY() < player.getWorld().getMinHeight() + 4 || center.getY() + 7 > player.getWorld().getMaxHeight()) {
            player.sendMessage(Component.text("There is not enough vertical space here.", NamedTextColor.RED));
            return;
        }
        buildStructure(player, center, radius, args[1].equalsIgnoreCase("vault"));
    }

    private void buildStructure(Player player, Location center, int radius, boolean vault) {
        World world = center.getWorld();
        if (world == null) return;
        int y = center.getBlockY();
        int x0 = center.getBlockX();
        int z0 = center.getBlockZ();
        Material wall = vault ? Material.LIGHT_GRAY_CONCRETE : Material.POLISHED_ANDESITE;
        Material trim = vault ? Material.BLUE_CONCRETE : Material.IRON_BLOCK;
        Material floor = vault ? Material.BLUE_CONCRETE : Material.DEEPSLATE_TILES;
        List<BlockState> changed = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = 0; dy <= 5; dy++) {
                    boolean edge = Math.abs(dx) == radius || Math.abs(dz) == radius || dy == 0 || dy == 5;
                    Material material = edge ? wall : Material.AIR;
                    if (dy == 0 && !edge) material = floor;
                    if (dy == 0 && (Math.abs(dx) == radius || Math.abs(dz) == radius)) material = trim;
                    if (dy == 1 && Math.abs(dx) == radius && dz == 0) material = Material.AIR;
                    if (dy == 2 && Math.abs(dx) == radius && dz == 0) material = Material.AIR;
                    if (dy == 3 && Math.abs(dx) == radius && dz == 0) material = Material.AIR;
                    Block block = world.getBlockAt(x0 + dx, y + dy, z0 + dz);
                    if (block.getType() != Material.AIR && block.getType() != Material.CAVE_AIR && block.getType() != Material.VOID_AIR) changed.add(new BlockState(block, block.getBlockData().clone()));
                    block.setType(material, false);
                }
            }
        }
        BlockFace facing = player.getFacing();
        Location inside = center.clone().add(0, 1, 0);
        if (vault) {
            world.getBlockAt(x0, y + 1, z0).setType(Material.IRON_DOOR, false);
            world.getBlockAt(x0, y + 2, z0).setType(Material.IRON_DOOR, false);
            world.getBlockAt(x0 - 2, y + 1, z0 - 2).setType(Material.LANTERN, false);
            world.getBlockAt(x0 + 2, y + 1, z0 + 2).setType(Material.LANTERN, false);
            world.getBlockAt(x0, y + 5, z0).setType(Material.SEA_LANTERN, false);
            world.getBlockAt(x0, y + 1, z0 + 2).setType(Material.CRAFTING_TABLE, false);
            world.getBlockAt(x0, y + 1, z0 - 2).setType(Material.BARREL, false);
        } else {
            world.getBlockAt(x0, y + 1, z0).setType(Material.IRON_DOOR, false);
            world.getBlockAt(x0, y + 2, z0).setType(Material.IRON_DOOR, false);
            world.getBlockAt(x0, y + 1, z0 + 2).setType(Material.FURNACE, false);
            world.getBlockAt(x0, y + 1, z0 - 2).setType(Material.CHEST, false);
            world.getBlockAt(x0, y + 5, z0).setType(Material.LANTERN, false);
        }
        if (facing == BlockFace.EAST || facing == BlockFace.WEST) inside.setX(x0 + 2); else inside.setZ(z0 + 2);
        player.teleport(inside);
        player.sendMessage(Component.text((vault ? "Vault-Tec shelter" : "Wasteland bunker") + " built. Existing blocks displaced: " + changed.size() + ".", NamedTextColor.GREEN));
    }

    private void updateArmorEffects() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ArmorSet set = equippedSet(player);
            boolean canFly = set != null;
            if (canFly) {
                player.setAllowFlight(true);
                if (set.id().equals("t60")) player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 50, 0, true, false, true));
                if (player.isFlying()) player.setFallDistance(0);
            } else {
                if (player.isFlying()) player.setFlying(false);
                if (player.getAllowFlight() && !player.getGameMode().name().equals("CREATIVE") && !player.getGameMode().name().equals("SPECTATOR")) player.setAllowFlight(false);
            }
        }
    }

    private ArmorSet equippedSet(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        if (armor.length != 4) return null;
        String id = gearId(armor[0]);
        if (id == null) return null;
        ArmorSet set = armorSets.get(id);
        if (set == null || !id.equals(gearId(armor[1])) || !id.equals(gearId(armor[2])) || !id.equals(gearId(armor[3]))) return null;
        return set;
    }

    private String gearId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(gearKey, PersistentDataType.STRING);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, this::updateArmorEffects, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().isFlying()) event.getPlayer().setFlying(false);
    }

    @EventHandler
    public void onArmorChange(PlayerItemHeldEvent event) {
        Bukkit.getScheduler().runTaskLater(this, this::updateArmorEffects, 1L);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ArmorSet set = equippedSet(player);
        if (set != null && event.getCause() == EntityDamageEvent.DamageCause.FALL) event.setCancelled(true);
        if (set != null && set.id().equals("t60") && event.getCause() != EntityDamageEvent.DamageCause.FALL) event.setDamage(event.getDamage() * .75);
    }

    private record ArmorSet(String id, String name, Material helmet, Material chest, Material legs, Material boots, NamedTextColor color, double protection, double bonus) {
        private ArmorSet(String name, Material helmet, Material chest, Material legs, Material boots, NamedTextColor color, double protection, double bonus) {
            this(name.startsWith("T-45") ? "t45" : "t60", name, helmet, chest, legs, boots, color, protection, bonus);
        }
    }

    private record BlockState(Block block, org.bukkit.block.data.BlockData oldData) {}
}
