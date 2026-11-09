package com.acefordrs;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

public final class Anti255Scanner {
   private final Anti255Plugin plugin;
   private final Anti255Config config;
   private final Anti255IllegalItemDetector illegalDetector;
   private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
   private static final Map<String, Integer> MAX_LEVEL_CACHE = new ConcurrentHashMap<>();
   private final Map<UUID, Long> notifyCooldown = new ConcurrentHashMap<>();
   private final AtomicLong totalScanned = new AtomicLong(0L);
   private final AtomicLong totalFixed = new AtomicLong(0L);
   private final AtomicLong totalReplaced = new AtomicLong(0L);
   private BufferedWriter logWriter;
   private String logDate;
   private static final Map<String, String> TYPE_ZH_CACHE;
   private static final Map<String, Integer> VANILLA_MAX;
   private static final Map<String, String> ZH_NAMES;
   private static final Map<String, String> SHULKER_ZH;
   private static final Set<Material> SHULKER_BOXES = materialsEndingWith("SHULKER_BOX");
   private static final Set<Material> BUNDLES = materialsEndingWith("_BUNDLE");
   private static final Set<Material> NON_ENCHANTABLE = Set.of(
      Material.STICK,
      Material.BLAZE_ROD,
      Material.BONE,
      // Shears, flint and steel and the two carrot-on-a-stick variants can
      // legitimately receive Unbreaking/Mending/Efficiency via an anvil, so
      // they must NOT be treated as non-enchantable.
      Material.END_ROD,
      Material.TORCH,
      Material.SOUL_TORCH,
      Material.REDSTONE_TORCH,
      Material.LANTERN,
      Material.SOUL_LANTERN,
      Material.CHEST,
      Material.TRAPPED_CHEST,
      Material.ENDER_CHEST,
      Material.BARREL,
      Material.BREWING_STAND,
      Material.CRAFTING_TABLE,
      Material.ANVIL,
      Material.CHIPPED_ANVIL,
      Material.DAMAGED_ANVIL,
      Material.GRINDSTONE,
      Material.SMITHING_TABLE,
      Material.LOOM,
      Material.STONECUTTER,
      Material.BEDROCK,
      Material.BARRIER,
      Material.COMMAND_BLOCK,
      Material.CHAIN_COMMAND_BLOCK,
      Material.REPEATING_COMMAND_BLOCK,
      Material.STRUCTURE_BLOCK,
      Material.JIGSAW,
      Material.DRAGON_EGG,
      Material.END_PORTAL_FRAME,
      Material.BUNDLE,
      Material.OAK_BOAT,
      Material.SPRUCE_BOAT,
      Material.BIRCH_BOAT,
      Material.JUNGLE_BOAT,
      Material.ACACIA_BOAT,
      Material.DARK_OAK_BOAT,
      Material.MANGROVE_BOAT,
      Material.CHERRY_BOAT,
      Material.BAMBOO_RAFT,
      Material.OAK_CHEST_BOAT,
      Material.SPRUCE_CHEST_BOAT,
      Material.BIRCH_CHEST_BOAT,
      Material.JUNGLE_CHEST_BOAT,
      Material.ACACIA_CHEST_BOAT,
      Material.DARK_OAK_CHEST_BOAT,
      Material.MANGROVE_CHEST_BOAT,
      Material.CHERRY_CHEST_BOAT,
      Material.BAMBOO_CHEST_RAFT,
      Material.PAINTING,
      Material.ITEM_FRAME,
      Material.GLOW_ITEM_FRAME,
      Material.MINECART,
      Material.CHEST_MINECART,
      Material.FURNACE_MINECART,
      Material.TNT_MINECART,
      Material.HOPPER_MINECART,
      Material.COMMAND_BLOCK_MINECART,
      Material.OAK_FENCE,
      Material.SPRUCE_FENCE,
      Material.BIRCH_FENCE,
      Material.JUNGLE_FENCE,
      Material.ACACIA_FENCE,
      Material.DARK_OAK_FENCE,
      Material.MANGROVE_FENCE,
      Material.CHERRY_FENCE,
      Material.BAMBOO_FENCE,
      Material.OAK_FENCE_GATE,
      Material.SPRUCE_FENCE_GATE,
      Material.BIRCH_FENCE_GATE,
      Material.JUNGLE_FENCE_GATE,
      Material.ACACIA_FENCE_GATE,
      Material.DARK_OAK_FENCE_GATE,
      Material.MANGROVE_FENCE_GATE,
      Material.CHERRY_FENCE_GATE,
      Material.BAMBOO_FENCE_GATE,
      Material.OAK_DOOR,
      Material.SPRUCE_DOOR,
      Material.BIRCH_DOOR,
      Material.JUNGLE_DOOR,
      Material.ACACIA_DOOR,
      Material.DARK_OAK_DOOR,
      Material.MANGROVE_DOOR,
      Material.CHERRY_DOOR,
      Material.BAMBOO_DOOR,
      Material.OAK_PRESSURE_PLATE,
      Material.SPRUCE_PRESSURE_PLATE,
      Material.BIRCH_PRESSURE_PLATE,
      Material.JUNGLE_PRESSURE_PLATE,
      Material.ACACIA_PRESSURE_PLATE,
      Material.DARK_OAK_PRESSURE_PLATE,
      Material.MANGROVE_PRESSURE_PLATE,
      Material.CHERRY_PRESSURE_PLATE,
      Material.BAMBOO_PRESSURE_PLATE,
      Material.STONE_PRESSURE_PLATE,
      Material.POLISHED_BLACKSTONE_PRESSURE_PLATE,
      Material.HEAVY_WEIGHTED_PRESSURE_PLATE,
      Material.LIGHT_WEIGHTED_PRESSURE_PLATE,
      Material.OAK_BUTTON,
      Material.SPRUCE_BUTTON,
      Material.BIRCH_BUTTON,
      Material.JUNGLE_BUTTON,
      Material.ACACIA_BUTTON,
      Material.DARK_OAK_BUTTON,
      Material.MANGROVE_BUTTON,
      Material.CHERRY_BUTTON,
      Material.BAMBOO_BUTTON,
      Material.STONE_BUTTON,
      Material.POLISHED_BLACKSTONE_BUTTON,
      Material.OAK_SIGN,
      Material.SPRUCE_SIGN,
      Material.BIRCH_SIGN,
      Material.JUNGLE_SIGN,
      Material.ACACIA_SIGN,
      Material.DARK_OAK_SIGN,
      Material.MANGROVE_SIGN,
      Material.CHERRY_SIGN,
      Material.BAMBOO_SIGN,
      Material.OAK_WALL_SIGN,
      Material.SPRUCE_WALL_SIGN,
      Material.BIRCH_WALL_SIGN,
      Material.JUNGLE_WALL_SIGN,
      Material.ACACIA_WALL_SIGN,
      Material.DARK_OAK_WALL_SIGN,
      Material.MANGROVE_WALL_SIGN,
      Material.CHERRY_WALL_SIGN,
      Material.BAMBOO_WALL_SIGN,
      Material.OAK_HANGING_SIGN,
      Material.SPRUCE_HANGING_SIGN,
      Material.BIRCH_HANGING_SIGN,
      Material.JUNGLE_HANGING_SIGN,
      Material.ACACIA_HANGING_SIGN,
      Material.DARK_OAK_HANGING_SIGN,
      Material.MANGROVE_HANGING_SIGN,
      Material.CHERRY_HANGING_SIGN,
      Material.BAMBOO_HANGING_SIGN,
      Material.OAK_WALL_HANGING_SIGN,
      Material.SPRUCE_WALL_HANGING_SIGN,
      Material.BIRCH_WALL_HANGING_SIGN,
      Material.JUNGLE_WALL_HANGING_SIGN,
      Material.ACACIA_WALL_HANGING_SIGN,
      Material.DARK_OAK_WALL_HANGING_SIGN,
      Material.MANGROVE_WALL_HANGING_SIGN,
      Material.CHERRY_WALL_HANGING_SIGN,
      Material.BAMBOO_WALL_HANGING_SIGN,
      Material.TOTEM_OF_UNDYING,
      Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
      Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE,
      Material.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE,
      Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE,
      Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE,
      Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE,
      Material.EYE_ARMOR_TRIM_SMITHING_TEMPLATE,
      Material.VEX_ARMOR_TRIM_SMITHING_TEMPLATE,
      Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE,
      Material.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE,
      Material.RIB_ARMOR_TRIM_SMITHING_TEMPLATE,
      Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE,
      Material.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE,
      Material.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE,
      Material.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE,
      Material.HOST_ARMOR_TRIM_SMITHING_TEMPLATE,
      Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE,
      Material.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE,
      Material.DECORATED_POT,
      Material.PAPER,
      Material.MAP,
      Material.FILLED_MAP,
      Material.BREAD,
      Material.WHEAT,
      Material.COOKIE,
      Material.CARROT,
      Material.GOLDEN_CARROT,
      Material.POTATO,
      Material.BAKED_POTATO,
      Material.POISONOUS_POTATO,
      Material.BEEF,
      Material.COOKED_BEEF,
      Material.PORKCHOP,
      Material.COOKED_PORKCHOP,
      Material.MUTTON,
      Material.COOKED_MUTTON,
      Material.CHICKEN,
      Material.COOKED_CHICKEN,
      Material.RABBIT,
      Material.COOKED_RABBIT,
      Material.COD,
      Material.COOKED_COD,
      Material.SALMON,
      Material.COOKED_SALMON,
      Material.TROPICAL_FISH,
      Material.PUFFERFISH,
      Material.MELON_SLICE,
      Material.DRIED_KELP,
      Material.APPLE,
      Material.GOLDEN_APPLE,
      Material.ENCHANTED_GOLDEN_APPLE,
      Material.MUSHROOM_STEW,
      Material.BEETROOT_SOUP,
      Material.RABBIT_STEW,
      Material.SUSPICIOUS_STEW,
      Material.MILK_BUCKET,
      Material.BUCKET,
      Material.WATER_BUCKET,
      Material.LAVA_BUCKET,
      Material.POWDER_SNOW_BUCKET,
      Material.EXPERIENCE_BOTTLE,
      Material.ENDER_PEARL,
      Material.ENDER_EYE,
      Material.EGG,
      Material.SNOWBALL,
      Material.SLIME_BALL,
      Material.MAGMA_CREAM,
      Material.BLAZE_POWDER,
      Material.FIRE_CHARGE,
      Material.FLINT,
      Material.STRING,
      Material.FEATHER,
      Material.GUNPOWDER,
      Material.LEATHER,
      Material.BOOK,
      Material.NETHER_WART,
      Material.REDSTONE,
      Material.GLOWSTONE_DUST,
      Material.DIAMOND,
      Material.EMERALD,
      Material.LAPIS_LAZULI,
      Material.RAW_IRON,
      Material.RAW_GOLD,
      Material.RAW_COPPER,
      Material.IRON_INGOT,
      Material.GOLD_INGOT,
      Material.COPPER_INGOT,
      Material.NETHERITE_INGOT,
      Material.NETHERITE_SCRAP,
      Material.AMETHYST_SHARD,
      Material.PRISMARINE_CRYSTALS,
      Material.PRISMARINE_SHARD,
      Material.NAUTILUS_SHELL,
      Material.HEART_OF_THE_SEA,
      Material.SHULKER_SHELL,
      Material.PHANTOM_MEMBRANE,
      Material.TURTLE_SCUTE,
      Material.ARMADILLO_SCUTE,
      Material.DISC_FRAGMENT_5,
      Material.PIGLIN_HEAD,
      Material.ZOMBIE_HEAD,
      Material.SKELETON_SKULL,
      Material.WITHER_SKELETON_SKULL,
      Material.CREEPER_HEAD,
      Material.PLAYER_HEAD,
      Material.DRAGON_HEAD
   );

   public Anti255Scanner(Anti255Plugin plugin, Anti255Config config) {
      this.plugin = plugin;
      this.config = config;
      this.illegalDetector = new Anti255IllegalItemDetector(config);
   }

   public Anti255IllegalItemDetector getIllegalDetector() {
      return this.illegalDetector;
   }

   public static void clearCache() {
      MAX_LEVEL_CACHE.clear();
   }

   public long getTotalScanned() {
      return this.totalScanned.get();
   }

   public long getTotalFixed() {
      return this.totalFixed.get();
   }

   public long getTotalReplaced() {
      return this.totalReplaced.get();
   }

   public void incrementReplaced() {
      this.totalReplaced.incrementAndGet();
   }

   public void resetStats() {
      this.totalScanned.set(0L);
      this.totalFixed.set(0L);
      this.totalReplaced.set(0L);
   }

   /**
    * Drops the per-player notification cooldown entry. Called when a player
    * leaves so the map cannot grow for the lifetime of the server.
    */
   public void clearNotification(UUID playerUUID) {
      if (playerUUID != null) {
         this.notifyCooldown.remove(playerUUID);
      }
   }

   /**
    * Applies the normal enchantment downgrade to an item dropped on the
    * ground. Returns a readable summary of the changes, or null when the
    * item was already valid.
    */
   public String fixEnchantsOnGroundItem(ItemStack item) {
      if (item == null || item.getType().isAir()) {
         return null;
      }

      return this.fixEnchantsOnGroundItem(item, readMeta(item));
   }

   String fixEnchantsOnGroundItem(ItemStack item, ItemMeta meta) {
      Anti255Scanner.FixResult result = this.fixItem(item, meta);
      if (result == null) {
         return null;
      }

      this.totalFixed.incrementAndGet();
      return String.join(", ", result.changes);
   }

   private boolean shouldNotify(UUID playerUUID) {
      if (this.config.notificationCooldownSeconds <= 0L) {
         return true;
      } else {
         long now = System.currentTimeMillis();
         Long lastTime = this.notifyCooldown.get(playerUUID);
         if (lastTime != null && now - lastTime < this.config.notificationCooldownSeconds * 1000L) {
            return false;
         } else {
            this.notifyCooldown.put(playerUUID, now);
            return true;
         }
      }
   }

   public int scanPlayerInventory(Player player) {
      return this.scanPlayerInventory(player, true);
   }

   public int scanPlayerEnderChest(Player player) {
      return this.scanPlayerEnderChest(player, true);
   }

   public int scanPlayerInventory(Player player, boolean withSystem2) {
      if (player.isOnline()) {
         int result = this.scan(player.getInventory(), player, "背包", "inventory", withSystem2);
         return result;
      } else {
         return 0;
      }
   }

   /**
    * Scans a single player-inventory slot. Used by the hotbar/armor/melee
    * guards so a suspicious item can be handled immediately instead of
    * waiting for the next periodic scan.
    */
   public int scanPlayerSlot(Player player, int slot) {
      return player == null ? 0 : this.scanInventorySlot(player.getInventory(), slot, player, "背包", "inventory");
   }

   public int scanPlayerEnderChestSlot(Player player, int slot) {
      return player == null ? 0 : this.scanInventorySlot(player.getEnderChest(), slot, player, "末影箱", "ender-chest");
   }

   public int scanContainerSlot(Inventory inventory, int slot, Player player) {
      if (inventory == null) {
         return 0;
      }

      return this.scanInventorySlot(inventory, slot, player, this.containerDesc(inventory), "container");
   }

   private int scanInventorySlot(Inventory inventory, int slot, Player player, String source, String sourceType) {
      if (player == null || !player.isOnline() || inventory == null) {
         return 0;
      }

      if (slot < 0 || slot >= inventory.getSize()) {
         return 0;
      }

      ItemStack item = inventory.getItem(slot);
      if (item == null || item.getType().isAir()) {
         return 0;
      }

      try {
         return this.scanItem(inventory, slot, item, player, source, sourceType, 0, true);
      } catch (Throwable t) {
         this.plugin.getLogger().log(Level.WARNING, "[ACE for DRS] 扫描玩家 " + player.getName() + " 槽位 " + slot + " 时出错", t);
         return 0;
      }
   }

   public int scanPlayerEnderChest(Player player, boolean withSystem2) {
      if (player.isOnline()) {
         int result = this.scan(player.getEnderChest(), player, "末影箱", "ender-chest", withSystem2);
         return result;
      } else {
         return 0;
      }
   }

   public int scanContainer(Inventory inventory, Player player) {
      return this.scanContainer(inventory, player, true);
   }

   public int scanContainer(Inventory inventory, Player player, boolean withSystem2) {
      if (!player.isOnline()) {
         return 0;
      } else if (inventory != null && inventory.getHolder() != null) {
         String desc = this.containerDesc(inventory);
         int result = this.scan(inventory, player, desc, "container", withSystem2);
         return result;
      } else {
         return 0;
      }
   }

   /**
    * Scans the item currently held on the player's cursor. Cursor items are
    * not part of any inventory slot, so the click/drag slot scans cannot see
    * them until they are placed. Replaces the cursor with a Rolling Log when
    * the item is illegal and deletion is enabled.
    */
   public void scanCursor(Player player, InventoryView view) {
      if (player == null || view == null || !player.isOnline()) {
         return;
      }

      ItemStack item;
      try {
         item = view.getCursor();
      } catch (RuntimeException e) {
         return;
      }

      if (item == null || item.getType().isAir()) {
         return;
      }

      this.totalScanned.incrementAndGet();
      ItemMeta meta = readMeta(item);
      boolean whitelisted = this.illegalDetector.getDeepDetector().isWhitelisted(item, meta);
      boolean changed = false;
      if (!whitelisted) {
         Anti255Scanner.FixResult fixResult = this.fixItem(item, meta);
         if (fixResult != null) {
            changed = true;
            this.logFix(player, "光标", item, fixResult);
            if (this.config.broadcastOnFix && fixResult.had255) {
               this.broadcast("broadcast-enchant-fix", player, fixResult.itemDesc);
            }

            if (this.shouldNotify(player.getUniqueId())) {
               this.notifyPlayer(player, "enchant-detail", Map.of("item", fixResult.itemDesc, "location", "光标"));
            }

            this.totalFixed.incrementAndGet();
         }
      }

      if (this.config.deleteIllegalItems && !this.plugin.isAdmin(player)) {
         Anti255IllegalItemDetector.DetectionResult result = this.illegalDetector.detect(item, whitelisted, meta);
         if (result != null) {
            if (this.backupBeforeDelete(player, "光标", -1, item, result.type())) {
               String desc = itemDesc(item);
               try {
                  view.setCursor(Anti255IllegalItemDetector.createRollingLog());
               } catch (RuntimeException e) {
                  return;
               }

               this.logIllegalItemReplace(player, "光标", desc, result);
               if (this.shouldNotify(player.getUniqueId())) {
                  this.notifyIllegalItem(player, desc, "光标", result);
               }

               this.totalReplaced.incrementAndGet();
            } else if (changed) {
               try {
                  view.setCursor(item);
               } catch (RuntimeException e) {
                  // The view was closed between the click and this task.
               }
            }
            return;
         }
      }

      if (this.config.deleteIllegalItems && !this.plugin.isAdmin(player) && meta != null) {
         String projectileResult = this.illegalDetector.scanCrossbowProjectiles(item, meta);
         if (projectileResult != null) {
            changed = true;
            this.log("玩家 " + player.getName() + " 在 [光标] " + itemDesc(item) + " 弩装填物: " + projectileResult);
            if (this.shouldNotify(player.getUniqueId())) {
               this.notifyPlayer(player, "deep-scan-fixed", Map.of("item", itemDesc(item), "location", "光标", "detail", "弩装填物: " + projectileResult));
            }

            this.totalFixed.incrementAndGet();
         }
      }

      if (!whitelisted) {
         String deepResult = this.illegalDetector.getDeepDetector().deepScanAndFix(item, whitelisted, meta);
         if (deepResult != null) {
            changed = true;
            this.log("玩家 " + player.getName() + " 在 [光标] " + itemDesc(item) + " 深度扫描: " + deepResult);
            if (this.shouldNotify(player.getUniqueId())) {
               this.notifyPlayer(player, "deep-scan-fixed", Map.of("item", itemDesc(item), "location", "光标", "detail", deepResult));
            }

            this.totalFixed.incrementAndGet();
         }
      }

      if (changed) {
         try {
            view.setCursor(item);
         } catch (RuntimeException e) {
            // The view was closed between the click and this task.
         }
      }
   }

   /**
    * Scans a stack that is not inside an inventory yet (pickup guard). The
    * caller owns the returned stack, so fixes are applied in place. Returns
    * null when the stack is plain vanilla and needs no further handling.
    */
   public Anti255Scanner.PickupResult scanPickupItem(ItemStack item) {
      if (item == null || item.getType().isAir()) {
         return null;
      }

      // Avoid copying ItemMeta for ordinary drops such as cobblestone.
      if (!item.hasItemMeta() && !this.illegalDetector.isIllegalMaterial(item)) {
         return null;
      }

      return this.scanStandaloneItem(item, 0);
   }

   /**
    * Standalone scan used before pickup. Unlike inventory scans this method
    * also recurses into shulker boxes and bundles, so an illegal item cannot
    * enter the inventory hidden inside a container.
    */
   private Anti255Scanner.PickupResult scanStandaloneItem(ItemStack item, int depth) {
      if (!item.hasItemMeta() && !this.illegalDetector.isIllegalMaterial(item)) {
         return null;
      }

      this.totalScanned.incrementAndGet();
      ItemMeta meta = readMeta(item);
      boolean whitelisted = this.illegalDetector.getDeepDetector().isWhitelisted(item, meta);
      String fixes = null;
      if (!whitelisted) {
         Anti255Scanner.FixResult fixResult = this.fixItem(item, meta);
         if (fixResult != null) {
            fixes = String.join(", ", fixResult.changes);
         }

         String deepResult = this.illegalDetector.getDeepDetector().deepScanAndFix(item, whitelisted, meta);
         if (deepResult != null) {
            fixes = appendDetail(fixes, deepResult);
         }
      }

      if (this.config.deleteIllegalItems && meta != null) {
         String projectileResult = this.illegalDetector.scanCrossbowProjectiles(item, meta);
         if (projectileResult != null) {
            fixes = appendDetail(fixes, "弩装填物: " + projectileResult);
         }
      }

      Anti255IllegalItemDetector.DetectionResult illegal = this.illegalDetector.detect(item, whitelisted, meta);
      if (illegal == null && depth < this.config.shulkerMaxDepth) {
         String nested = this.scanStandaloneContents(item, meta, depth + 1);
         if (nested != null) {
            fixes = appendDetail(fixes, nested);
         }
      }

      return illegal == null && fixes == null ? null : new Anti255Scanner.PickupResult(illegal, fixes);
   }

   private String scanStandaloneContents(ItemStack container, ItemMeta meta, int depth) {
      List<String> details = new ArrayList<>();
      if (meta instanceof BundleMeta bundleMeta && bundleMeta.hasItems()) {
         List<ItemStack> items = bundleMeta.getItems();
         List<ItemStack> modified = new ArrayList<>(items);
         boolean changed = false;
         for (int i = 0; i < modified.size(); i++) {
            ItemStack innerItem = modified.get(i);
            if (innerItem == null || innerItem.getType().isAir()) {
               continue;
            }

            try {
               Anti255Scanner.PickupResult innerResult = this.scanStandaloneItem(innerItem, depth);
               if (innerResult == null) {
                  continue;
               }

               if (innerResult.illegal() != null && this.config.deleteIllegalItems) {
                  modified.set(i, Anti255IllegalItemDetector.createRollingLog());
                  changed = true;
                  details.add("收纳袋槽位 " + i + " 已替换: " + innerResult.illegal().type());
               } else if (innerResult.fixes() != null) {
                  // ItemStack#setItemMeta() mutates a copy when the list came
                  // from BundleMeta#getItems(); write it back explicitly.
                  modified.set(i, innerItem);
                  changed = true;
                  details.add("收纳袋槽位 " + i + ": " + innerResult.fixes());
               }
            } catch (Throwable t) {
               this.plugin.getLogger().log(Level.WARNING, "[ACE for DRS] 扫描收纳袋槽位 " + i + " 时出错", t);
            }
         }

         if (changed) {
            bundleMeta.setItems(modified);
            container.setItemMeta(bundleMeta);
         }
      }

      if (meta instanceof BlockStateMeta blockMeta && blockMeta.getBlockState() instanceof ShulkerBox shulker) {
         Inventory inner = shulker.getInventory();
         ItemStack[] contents = inner.getContents();
         boolean changed = false;
         for (int i = 0; i < contents.length; i++) {
            ItemStack innerItem = contents[i];
            if (innerItem == null || innerItem.getType().isAir()) {
               continue;
            }

            try {
               Anti255Scanner.PickupResult innerResult = this.scanStandaloneItem(innerItem, depth);
               if (innerResult == null) {
                  continue;
               }

               if (innerResult.illegal() != null && this.config.deleteIllegalItems) {
                  inner.setItem(i, Anti255IllegalItemDetector.createRollingLog());
                  changed = true;
                  details.add("潜影盒槽位 " + i + " 已替换: " + innerResult.illegal().type());
               } else if (innerResult.fixes() != null) {
                  // getContents() returns copies; write the repaired stack back.
                  inner.setItem(i, innerItem);
                  changed = true;
                  details.add("潜影盒槽位 " + i + ": " + innerResult.fixes());
               }
            } catch (Throwable t) {
               this.plugin.getLogger().log(Level.WARNING, "[ACE for DRS] 扫描潜影盒槽位 " + i + " 时出错", t);
            }
         }

         if (changed) {
            blockMeta.setBlockState(shulker);
            container.setItemMeta(blockMeta);
         }
      }

      return details.isEmpty() ? null : String.join("; ", details);
   }

   private static String appendDetail(String current, String detail) {
      return current == null ? detail : current + "; " + detail;
   }

   private int scan(Inventory inventory, Player player, String source, String sourceType, boolean withSystem2) {
      int fixed = 0;
      ItemStack[] contents = inventory.getContents();

      for (int i = 0; i < contents.length; i++) {
         ItemStack item = contents[i];
         if (item != null && !item.getType().isAir()) {
            try {
               fixed += this.scanItem(inventory, i, item, player, source, sourceType, 0, withSystem2);
            } catch (Throwable t) {
               // A single malformed item must not abort the rest of the inventory.
               this.plugin.getLogger().log(Level.WARNING, "[ACE for DRS] 扫描 " + source + " 槽位 " + i + " 时出错", t);
            }
         }
      }

      return fixed;
   }

   private int scanItem(Inventory inventory, int slot, ItemStack item, Player player, String source, String sourceType, int depth, boolean withSystem2) {
      if (item == null || item.getType().isAir()) {
         return 0;
      }

      this.totalScanned.incrementAndGet();
      boolean isAdmin = this.plugin.isAdmin(player);
      int fixed = 0;
      // Read the ItemMeta once: whitelist, System 1, System 2 and the deep
      // scan all operate on the same snapshot instead of copying it 3-5
      // times per item. Plain items without meta skip the copy entirely.
      ItemMeta meta = readMeta(item);
      // Whitelisted addon items pass System 1 and the material/potion part of
      // System 2 untouched. They still go through detect(), which applies the
      // absolute attribute ceiling so a forged whitelist marker cannot hide
      // impossible values like Integer.MAX_VALUE attack damage.
      boolean whitelisted = this.illegalDetector.getDeepDetector().isWhitelisted(item, meta);

      if (!whitelisted) {
         Anti255Scanner.FixResult fixResult = this.fixItem(item, meta);
         if (fixResult != null) {
            fixed++;
            String location = this.formatLocation(inventory, slot, source);
            this.logFix(player, location, item, fixResult);
            if (this.config.broadcastOnFix && fixResult.had255) {
               this.broadcast("broadcast-enchant-fix", player, fixResult.itemDesc);
            }

            if (this.shouldNotify(player.getUniqueId())) {
               this.notifyPlayer(player, "enchant-detail", Map.of("item", fixResult.itemDesc, "location", location));
            }

            this.totalFixed.incrementAndGet();
         }
      }

      if (withSystem2 && this.config.deleteIllegalItems && !isAdmin) {
         Anti255IllegalItemDetector.DetectionResult result = this.illegalDetector.detect(item, whitelisted, meta);
         if (result != null) {
            if (!this.backupBeforeDelete(player, source, slot, item, result.type())) {
               if (fixed > 0) {
                  inventory.setItem(slot, item);
               }
               return fixed;
            }

            String desc = itemDesc(item);
            String locationx = this.formatLocation(inventory, slot, source);
            inventory.setItem(slot, Anti255IllegalItemDetector.createRollingLog());
            this.logIllegalItemReplace(player, locationx, desc, result);
            if (this.shouldNotify(player.getUniqueId())) {
               this.notifyIllegalItem(player, desc, locationx, result);
            }

            this.totalReplaced.incrementAndGet();
            return fixed + 1;
         }
      }

      if (withSystem2 && this.config.deleteIllegalItems && !isAdmin && meta != null) {
         String projectileResult = this.illegalDetector.scanCrossbowProjectiles(item, meta);
         if (projectileResult != null) {
            String locationx = this.formatLocation(inventory, slot, source);
            String desc = itemDesc(item);
            this.log("玩家 " + player.getName() + " 在 [" + locationx + "] " + desc + " 弩装填物: " + projectileResult);
            if (this.shouldNotify(player.getUniqueId())) {
               this.notifyPlayer(player, "deep-scan-fixed", Map.of("item", desc, "location", locationx, "detail", "弩装填物: " + projectileResult));
            }

            this.totalFixed.incrementAndGet();
            fixed++;
         }
      }

      if (withSystem2 && !isAdmin && !whitelisted && meta != null) {
         String deepResult = this.illegalDetector.getDeepDetector().deepScanAndFix(item, whitelisted, meta);
         if (deepResult != null) {
            String locationx = this.formatLocation(inventory, slot, source);
            String desc = itemDesc(item);
            this.log("玩家 " + player.getName() + " 在 [" + locationx + "] " + desc + " 深度扫描: " + deepResult);
            if (this.shouldNotify(player.getUniqueId())) {
               this.notifyPlayer(player, "deep-scan-fixed", Map.of("item", desc, "location", locationx, "detail", deepResult));
            }

            this.totalFixed.incrementAndGet();
            fixed++;
         }
      }

      if (depth < this.config.shulkerMaxDepth) {
         // Scan container contents even when the container itself is
         // whitelisted: a forged PDC marker must not hide items inside it.
         Material type = item.getType();
         if (isShulkerBox(type)) {
            fixed += this.scanShulkerContents(item, player, source, sourceType, slot, depth, withSystem2);
         } else if (isBundle(type)) {
            fixed += this.scanBundleContents(item, player, source, sourceType, slot, depth, withSystem2, inventory);
         }
      }

      // fixItem()/deepScanAndFix() mutate the ItemStack instance. Write it
      // back explicitly: some inventory implementations only keep a mirror
      // of the array and would otherwise keep the old stack.
      if (fixed > 0) {
         inventory.setItem(slot, item);
      }

      return fixed;
   }

   private int scanShulkerContents(ItemStack box, Player player, String source, String sourceType, int slot, int depth, boolean withSystem2) {
      if (box.getItemMeta() instanceof BlockStateMeta blockMeta) {
         if (blockMeta.getBlockState() instanceof ShulkerBox shulker) {
            String boxName = SHULKER_ZH.getOrDefault(box.getType().name(), "潜影盒");
            Inventory inner = shulker.getInventory();
            int fixed = 0;
            ItemStack[] innerContents = inner.getContents();
            String nestedSource = source + " → " + boxName;

            for (int i = 0; i < innerContents.length; i++) {
               ItemStack innerItem = innerContents[i];
               if (innerItem != null && !innerItem.getType().isAir()) {
                  try {
                     fixed += this.scanItem(inner, i, innerItem, player, nestedSource, sourceType, depth + 1, withSystem2);
                  } catch (Throwable t) {
                     // Keep scanning the remaining slots when one nested item
                     // contains malformed data.
                     this.plugin.getLogger().log(Level.WARNING, "[ACE for DRS] 扫描潜影盒内槽位 " + i + " 时出错", t);
                  }
               }
            }

            if (fixed > 0) {
               blockMeta.setBlockState(shulker);
               box.setItemMeta(blockMeta);
            }

            return fixed;
         } else {
            return 0;
         }
      } else {
         return 0;
      }
   }

   private int scanBundleContents(
      ItemStack bundle, Player player, String source, String sourceType, int slot, int depth, boolean withSystem2, Inventory outerInventory
   ) {
      if (!(bundle.getItemMeta() instanceof BundleMeta bundleMeta)) {
         return 0;
      } else if (!bundleMeta.hasItems()) {
         return 0;
      } else {
         List<ItemStack> items = bundleMeta.getItems();
         int fixed = 0;
         List<ItemStack> modified = new ArrayList<>(items);
         boolean isAdmin = this.plugin.isAdmin(player);
         String nestedSource = source + " → 收纳袋";

         for (int i = 0; i < modified.size(); i++) {
            ItemStack innerItem = modified.get(i);
            if (innerItem != null && !innerItem.getType().isAir()) {
               try {
                  ItemMeta innerMeta = readMeta(innerItem);
               boolean whitelisted = this.illegalDetector.getDeepDetector().isWhitelisted(innerItem, innerMeta);

               // detect() still enforces the absolute attribute ceiling for
               // whitelisted items (forged whitelist markers must not hide
               // Integer.MAX_VALUE attributes).
               if (withSystem2 && this.config.deleteIllegalItems && !isAdmin) {
                  Anti255IllegalItemDetector.DetectionResult result = this.illegalDetector.detect(innerItem, whitelisted, innerMeta);
                  if (result != null) {
                     if (this.backupBeforeDelete(player, nestedSource, slot, innerItem, result.type())) {
                        modified.set(i, Anti255IllegalItemDetector.createRollingLog());
                        fixed++;
                        String desc = itemDesc(innerItem);
                        String location = this.formatLocation(outerInventory, slot, nestedSource);
                        this.logIllegalItemReplace(player, location, desc, result);
                        if (this.shouldNotify(player.getUniqueId())) {
                           this.notifyIllegalItem(player, desc, location, result);
                        }

                        this.totalReplaced.incrementAndGet();
                     }
                     continue;
                  }
               }

               if (withSystem2 && this.config.deleteIllegalItems && !isAdmin) {
                  String projectileResult = this.illegalDetector.scanCrossbowProjectiles(innerItem, innerMeta);
                  if (projectileResult != null) {
                     fixed++;
                     String location = this.formatLocation(outerInventory, slot, nestedSource);
                     String desc = itemDesc(innerItem);
                     this.log("玩家 " + player.getName() + " 在 [" + location + "] " + desc + " 弩装填物: " + projectileResult);
                     if (this.shouldNotify(player.getUniqueId())) {
                        this.notifyPlayer(
                           player, "deep-scan-fixed", Map.of("item", desc, "location", location, "detail", "弩装填物: " + projectileResult)
                        );
                     }

                     this.totalFixed.incrementAndGet();
                  }
               }

               if (!whitelisted) {
                  if (withSystem2 && !isAdmin) {
                     String deepResult = this.illegalDetector.getDeepDetector().deepScanAndFix(innerItem, whitelisted, innerMeta);
                     if (deepResult != null) {
                        fixed++;
                        String location = this.formatLocation(outerInventory, slot, nestedSource);
                        String desc = itemDesc(innerItem);
                        this.log("玩家 " + player.getName() + " 在 [" + location + "] " + desc + " 深度扫描: " + deepResult);
                        if (this.shouldNotify(player.getUniqueId())) {
                           this.notifyPlayer(player, "deep-scan-fixed", Map.of("item", desc, "location", location, "detail", deepResult));
                        }

                        this.totalFixed.incrementAndGet();
                     }
                  }

                  Anti255Scanner.FixResult fixResult = this.fixItem(innerItem, innerMeta);
                  if (fixResult != null) {
                     fixed++;
                     String location = this.formatLocation(outerInventory, slot, nestedSource);
                     this.logFix(player, location, innerItem, fixResult);
                     if (this.config.broadcastOnFix && fixResult.had255) {
                        this.broadcast("broadcast-enchant-fix", player, fixResult.itemDesc);
                     }

                     if (this.shouldNotify(player.getUniqueId())) {
                        this.notifyPlayer(player, "enchant-detail", Map.of("item", fixResult.itemDesc, "location", location));
                     }

                     this.totalFixed.incrementAndGet();
                  }
               }

               // Recurse into nested containers inside the bundle. Previously
               // this method ignored its depth argument entirely, so
               // bundle -> shulker box -> item was never scanned.
               if (depth + 1 < this.config.shulkerMaxDepth) {
                  if (isShulkerBox(innerItem)) {
                     fixed += this.scanShulkerContents(innerItem, player, nestedSource, sourceType, slot, depth + 1, withSystem2);
                  } else if (isBundle(innerItem)) {
                     fixed += this.scanBundleContents(innerItem, player, nestedSource, sourceType, slot, depth + 1, withSystem2, outerInventory);
                  }
               }
               } catch (Throwable t) {
                  // Keep scanning the remaining bundle items when one nested
                  // item contains malformed data.
                  this.plugin.getLogger().log(Level.WARNING, "[ACE for DRS] 扫描收纳袋内槽位 " + i + " 时出错", t);
               }
            }
         }

         if (fixed > 0) {
            bundleMeta.setItems(modified);
            bundle.setItemMeta(bundleMeta);
         }

         return fixed;
      }
   }

   private Anti255Scanner.FixResult fixItem(ItemStack item, ItemMeta meta) {
      if (item == null || item.getType().isAir() || meta == null) {
         return null;
      }

      Material type = item.getType();

      // Enchanted books store their enchantments in EnchantmentStorageMeta,
      // not in ItemMeta#getEnchants(). Without this branch a Sharpness 255
      // book would pass through untouched.
      EnchantmentStorageMeta storageMeta = meta instanceof EnchantmentStorageMeta storage ? storage : null;
      boolean isBook = storageMeta != null;
      Map<Enchantment, Integer> source = isBook ? storageMeta.getStoredEnchants() : meta.getEnchants();
      if (source.isEmpty()) {
         return null;
      }

      // Copy the map only when there is something to inspect:
      // addStoredEnchant()/removeStoredEnchant() must not be called while
      // iterating the live enchantment map.
      Map<Enchantment, Integer> enchants = new HashMap<>(source);

      boolean isNonEnchantable = !isBook && NON_ENCHANTABLE.contains(type);
      List<String> changes = null;
      boolean anyFixed = false;
      boolean had255 = false;

      for (Entry<Enchantment, Integer> entry : enchants.entrySet()) {
         Enchantment enchant = entry.getKey();
         if (!isVanillaEnchantment(enchant)) {
            continue;
         }

         int level = entry.getValue();
         String key = enchant.getKey().getKey();
         if (level >= 255) {
            had255 = true;
         }

         if (isNonEnchantable && !isCurse(enchant)) {
            removeEnchant(meta, storageMeta, enchant);
            if (changes == null) {
               changes = new ArrayList<>();
            }

            changes.add(zhName(enchant) + " " + level + " → 移除(物品无法附魔)");
            anyFixed = true;
         } else if (level < 1) {
            removeEnchant(meta, storageMeta, enchant);
            if (changes == null) {
               changes = new ArrayList<>();
            }

            changes.add(zhName(enchant) + " " + level + " → 移除(等级无效)");
            anyFixed = true;
         } else if (key.equals("unbreaking") && level == 255 && this.config.keepUnbreaking255) {
            // Preserve the old "keep Unbreaking 255" option.
         } else if (!isBook && !isCurse(enchant) && this.config.removeInvalidEnchants && !canApply(enchant, item)) {
            removeEnchant(meta, storageMeta, enchant);
            if (changes == null) {
               changes = new ArrayList<>();
            }

            changes.add(zhName(enchant) + " " + level + " → 移除(物品不适用)");
            anyFixed = true;
         } else {
            int max = this.maxLevel(enchant);
            if (level > max) {
               setEnchant(meta, storageMeta, enchant, max);
               if (changes == null) {
                  changes = new ArrayList<>();
               }

               changes.add(zhName(enchant) + " " + level + " → " + max);
               anyFixed = true;
            }
         }
      }

      if (!anyFixed) {
         return null;
      }

      item.setItemMeta(meta);
      return new Anti255Scanner.FixResult(itemDesc(item), changes, had255);
   }

   private static void removeEnchant(ItemMeta meta, EnchantmentStorageMeta storageMeta, Enchantment enchant) {
      if (storageMeta != null) {
         storageMeta.removeStoredEnchant(enchant);
      } else {
         meta.removeEnchant(enchant);
      }
   }

   private static void setEnchant(ItemMeta meta, EnchantmentStorageMeta storageMeta, Enchantment enchant, int level) {
      if (storageMeta != null) {
         storageMeta.addStoredEnchant(enchant, level, true);
      } else {
         meta.addEnchant(enchant, level, true);
      }
   }

   private static boolean isVanillaEnchantment(Enchantment enchant) {
      return enchant.getKey().getNamespace().equals("minecraft");
   }

   private void notifyIllegalItem(Player player, String itemDesc, String location, Anti255IllegalItemDetector.DetectionResult result) {
      String reason = this.detectionReason(result.type());
      this.notifyPlayer(player, "illegal-item-detail", Map.of("item", itemDesc, "location", location, "reason", reason));
   }

   private void notifyPlayer(Player player, String key, Map<String, String> replacements) {
      if (player.isOnline()) {
         player.sendMessage(this.plugin.getMessages().get(key, replacements));
      }
   }

   private String detectionReason(String type) {
      String key = "reason-" + type;
      String reason = this.plugin.getMessages().get(key).replace('\n', ' ');
      return reason.equals(key) ? type : reason;
   }

   private void logIllegalItemReplace(Player player, String location, String desc, Anti255IllegalItemDetector.DetectionResult result) {
      String reason = this.detectionReason(result.type());
      this.log("玩家 " + player.getName() + " 在 [" + location + "] 检测到" + reason + " " + desc + " [" + result.detail() + "] 已替换为《滚木》");
      if (this.config.broadcastOnDelete) {
         this.broadcast("broadcast-illegal-item", player, desc);
      }
   }

   private void logFix(Player player, String location, ItemStack item, Anti255Scanner.FixResult result) {
      this.log("玩家 " + player.getName() + " 在 [" + location + "] 检测到违规附魔 " + result.itemDesc + " | " + String.join(", ", result.changes));
   }

   private void broadcast(String key, Player player, String itemDesc) {
      this.plugin.getServer().broadcastMessage(this.plugin.getMessages().get(key, Map.of("player", player.getName(), "item", sanitize(itemDesc))));
   }

   private boolean backupBeforeDelete(Player player, String source, int slot, ItemStack item, String reason) {
      if (!this.config.backupBeforeDelete) {
         return true;
      } else {
         try {
            Path dir = this.plugin.getDataFolder().toPath().resolve("backups");
            Files.createDirectories(dir);
            // A bundle can contain several illegal items at the same slot in
            // the same millisecond, so a plain UUID+time+slot name collides.
            String base = player.getUniqueId()
               + "-"
               + System.currentTimeMillis()
               + "-"
               + UUID.randomUUID().toString().substring(0, 8)
               + "-"
               + slot
               + "-"
               + reason;
            Files.write(dir.resolve(base + ".bin"), item.serializeAsBytes());
            Files.writeString(
               dir.resolve(base + ".txt"),
               "player=" + player.getName() + "\nsource=" + source + "\nslot=" + slot + "\nitem=" + item.getType().name() + "\ntime=" + Instant.now() + "\n"
            );
            return true;
         } catch (Exception var8) {
            this.plugin.getLogger().warning("[ACE for DRS] 备份失败，已跳过: " + player.getName() + " 槽位 " + slot + " (" + var8.getMessage() + ")");
            return false;
         }
      }
   }

   public void inspectPlayer(Player player, CommandSender sender) {
      try {
         boolean isAdmin = this.plugin.isAdmin(player);
         sender.sendMessage(this.plugin.getMessages().get("inspect-header", Map.of("player", player.getName())));
         this.inspectInventory(player.getInventory(), "背包", player, sender, isAdmin, 0);
         sender.sendMessage(this.plugin.getMessages().get("inspect-ender-header", Map.of("player", player.getName())));
         this.inspectInventory(player.getEnderChest(), "末影箱", player, sender, isAdmin, 0);
      } catch (Exception var4) {
         this.plugin.getLogger().log(Level.WARNING, "[ACE for DRS] 检查玩家 " + player.getName() + " 时出错", (Throwable)var4);
         sender.sendMessage("§9[ACE for DRS] §b检查出错，请查看控制台日志");
      }
   }

   private void inspectInventory(Inventory inventory, String name, Player scanned, CommandSender sender, boolean isAdmin, int depth) {
      if (depth <= this.config.shulkerMaxDepth) {
         ItemStack[] contents = inventory.getContents();
         String indent = "  ".repeat(depth);

         for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && !item.getType().isAir()) {
               StringBuilder sb = new StringBuilder(indent + name + " · " + this.slotLabel(inventory, i) + ": ");
               sb.append(Anti255IllegalItemDetector.zhMaterial(item.getType()));
               // Inspect is a manual command, so read meta even for plain
               // items to keep the illegal-material check visible.
               ItemMeta meta = item.getItemMeta();
               if (meta != null) {
                  this.appendInspectMeta(sb, meta, item, isAdmin);
               }

               if (isShulkerBox(item) && depth < this.config.shulkerMaxDepth) {
                  sb.append(" §7[潜影盒内容 ↓]");
                  sender.sendMessage(sb.toString());
                  this.inspectShulkerContents(item, scanned, sender, isAdmin, depth + 1);
               } else if (isBundle(item) && depth < this.config.shulkerMaxDepth) {
                  sb.append(" §7[收纳袋内容 ↓]");
                  sender.sendMessage(sb.toString());
                  this.inspectBundleContents(item, scanned, sender, isAdmin, depth + 1);
               } else {
                  sender.sendMessage(sb.toString());
               }
            }
         }
      }
   }

   private void appendInspectMeta(StringBuilder sb, ItemMeta meta, ItemStack item, boolean isAdmin) {
      String rawName = meta.displayName() == null ? "" : PlainTextComponentSerializer.plainText().serialize(meta.displayName());
      if (!rawName.isEmpty()) {
         sb.append(" (“").append(rawName).append("”)");
      }

      Map<Enchantment, Integer> inspectEnchants = meta instanceof EnchantmentStorageMeta storage
         ? storage.getStoredEnchants()
         : meta.getEnchants();
      if (!inspectEnchants.isEmpty()) {
         sb.append(meta instanceof EnchantmentStorageMeta ? " [存储附魔:" : " [附魔:");

         for (Entry<Enchantment, Integer> entry : inspectEnchants.entrySet()) {
            Enchantment enchant = entry.getKey();
            int level = entry.getValue();
            int max = this.maxLevel(enchant);
            sb.append(" ").append(zhName(enchant)).append(":").append(level);
            // Only vanilla enchantments have a reliable vanilla max level.
            // Addon enchantments would otherwise be flagged as over-level
            // against the fallback value of 5.
            if (isVanillaEnchantment(enchant) && level > max) {
               sb.append("§c(超限").append(max).append(")§r");
            }
         }

         sb.append("]");
      }

      if (!isAdmin) {
         boolean whitelisted = this.illegalDetector.getDeepDetector().isWhitelisted(item, meta);
         Anti255IllegalItemDetector.DetectionResult det = this.illegalDetector.detect(item, whitelisted, meta);
         if (det != null) {
            sb.append(" §c[违禁/异常: ").append(det.detail()).append("]");
            if (this.config.deleteIllegalItems) {
               sb.append(" §a[将替换为滚木]");
            }
         } else if (whitelisted) {
            sb.append(" §7[白名单跳过]");
         }

         if (!whitelisted) {
            String deepResult = this.illegalDetector.getDeepDetector().deepScanCheck(item, whitelisted, meta);
            if (deepResult != null) {
               sb.append(" §6[深度扫描修正: ").append(deepResult).append("]");
            }
         }
      }
   }

   private void inspectShulkerContents(ItemStack box, Player scanned, CommandSender sender, boolean isAdmin, int depth) {
      if (box.getItemMeta() instanceof BlockStateMeta blockMeta) {
         if (blockMeta.getBlockState() instanceof ShulkerBox shulker) {
            this.inspectInventory(shulker.getInventory(), "潜影盒", scanned, sender, isAdmin, depth);
         }
      }
   }

   private void inspectBundleContents(ItemStack bundle, Player scanned, CommandSender sender, boolean isAdmin, int depth) {
      if (bundle.getItemMeta() instanceof BundleMeta bundleMeta) {
         if (bundleMeta.hasItems()) {
            List<ItemStack> items = bundleMeta.getItems();
            String indent = "  ".repeat(depth);

            for (int i = 0; i < items.size(); i++) {
               ItemStack item = items.get(i);
               if (item != null && !item.getType().isAir()) {
                  StringBuilder sb = new StringBuilder(indent + "收纳袋 · 槽位 " + i + ": ");
                  sb.append(Anti255IllegalItemDetector.zhMaterial(item.getType()));
                  ItemMeta innerMeta = item.getItemMeta();
                  if (innerMeta != null) {
                     this.appendInspectMeta(sb, innerMeta, item, isAdmin);
                  }

                  // Keep inspect consistent with the scanner: bundles may
                  // contain shulker boxes and other bundles.
                  if (isShulkerBox(item) && depth < this.config.shulkerMaxDepth) {
                     sb.append(" §7[潜影盒内容 ↓]");
                     sender.sendMessage(sb.toString());
                     this.inspectShulkerContents(item, scanned, sender, isAdmin, depth + 1);
                  } else if (isBundle(item) && depth < this.config.shulkerMaxDepth) {
                     sb.append(" §7[收纳袋内容 ↓]");
                     sender.sendMessage(sb.toString());
                     this.inspectBundleContents(item, scanned, sender, isAdmin, depth + 1);
                  } else {
                     sender.sendMessage(sb.toString());
                  }
               }
            }
         }
      }
   }

   private static Set<Material> materialsEndingWith(String suffix) {
      Set<Material> materials = new HashSet<>();
      for (Material material : Material.values()) {
         if (material.name().endsWith(suffix)) {
            materials.add(material);
         }
      }
      return Set.copyOf(materials);
   }

   private static boolean isShulkerBox(Material type) {
      return type != null && SHULKER_BOXES.contains(type);
   }

   private static boolean isBundle(Material type) {
      return type == Material.BUNDLE || type != null && BUNDLES.contains(type);
   }

   private static boolean isShulkerBox(ItemStack item) {
      return item != null && isShulkerBox(item.getType());
   }

   private static boolean isBundle(ItemStack item) {
      return item != null && isBundle(item.getType());
   }

   /**
    * Returns the meta copy only when the item actually has custom meta.
    * Most scanned items are plain blocks/ingots; avoiding getItemMeta() for
    * them removes an ItemMeta allocation on the hottest scan path.
    */
   private static ItemMeta readMeta(ItemStack item) {
      return item != null && item.hasItemMeta() ? item.getItemMeta() : null;
   }

   private static String itemDesc(ItemStack item) {
      String rawName = getItemDisplayName(item);
      String zh = Anti255IllegalItemDetector.zhMaterial(item.getType());
      return !rawName.isEmpty() ? rawName + "(" + zh + ")" : zh;
   }

   private static String getItemDisplayName(ItemStack item) {
      ItemMeta meta = item.getItemMeta();
      return meta != null && meta.displayName() != null ? PlainTextComponentSerializer.plainText().serialize(meta.displayName()) : "";
   }

   private String formatLocation(Inventory inventory, int slot, String source) {
      return source + " · " + this.slotLabel(inventory, slot);
   }

   private String slotLabel(Inventory inv, int slot) {
      if (inv == null || inv.getType() != InventoryType.PLAYER) {
         return "槽位[" + slot + "]";
      } else if (slot <= 8) {
         return "快捷栏[" + slot + "]";
      } else if (slot <= 35) {
         return "背包[" + slot + "]";
      } else {
         return switch (slot) {
            case 36 -> "头盔";
            case 37 -> "胸甲";
            case 38 -> "护腿";
            case 39 -> "靴子";
            case 40 -> "副手";
            default -> "槽位[" + slot + "]";
         };
      }
   }

   private String containerDesc(Inventory inventory) {
      Object holder = inventory.getHolder();
      if (holder instanceof BlockState state) {
         return TYPE_ZH_CACHE.getOrDefault(state.getType().name(), state.getType().name())
            + " @"
            + state.getWorld().getName()
            + ","
            + state.getX()
            + ","
            + state.getY()
            + ","
            + state.getZ();
      } else if (holder instanceof DoubleChest dc) {
         Location loc = dc.getLocation();
         return loc != null && loc.getWorld() != null
            ? "箱子 @" + loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()
            : "箱子";
      } else {
         return holder instanceof StorageMinecart ? "容器矿车" : inventory.getType().name();
      }
   }

   private static String zhName(Enchantment enchant) {
      return ZH_NAMES.getOrDefault(enchant.getKey().getKey(), enchant.getKey().getKey());
   }

   private static boolean canApply(Enchantment enchant, ItemStack item) {
      try {
         return enchant.canEnchantItem(item);
      } catch (Exception var3) {
         return true;
      }
   }

   /**
    * Curses can legally sit on items that otherwise cannot be enchanted
    * (for example a carved pumpkin or a mob head), so they must never be
    * stripped by the "cannot be enchanted" rule.
    */
   private static boolean isCurse(Enchantment enchant) {
      String key = enchant.getKey().getKey();
      return key.equals("binding_curse") || key.equals("vanishing_curse");
   }

   private int maxLevel(Enchantment enchant) {
      String key = enchant.getKey().getKey();
      Integer cached = MAX_LEVEL_CACHE.get(key);
      if (cached != null) {
         return cached;
      } else {
         int max = VANILLA_MAX.getOrDefault(key, 5);
         if (this.config.respectServerMaxLevel) {
            try {
               int server = enchant.getMaxLevel();
               if (server >= 1 && server <= 10000) {
                  max = server;
               }
            } catch (Exception var6) {
            }
         }

         // Allow config-defined over-level enchants (e.g. Slimefun addon gear
         // with Unbreaking XXV) without touching the rest of the item.
         int whitelisted = this.config.getWhitelistedEnchantMax(key);
         if (whitelisted > max) {
            max = whitelisted;
         }

         MAX_LEVEL_CACHE.put(key, max);
         return max;
      }
   }

   private static String sanitize(String text) {
      if (text != null && !text.isEmpty()) {
         StringBuilder sb = new StringBuilder(text.length());

         for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 167) {
               sb.append('?');
            } else if (c >= ' ' && c != 127) {
               sb.append(c);
            } else {
               sb.append(' ');
            }
         }

         String result = sb.toString().trim();
         return result.isEmpty() ? "物品" : result;
      } else {
         return "物品";
      }
   }

   private void log(String line) {
      this.plugin.getLogger().info("[ACE for DRS] " + line);
      if (this.config.logToFile) {
         this.appendLog(line);
      }
   }

   private void appendLog(String line) {
      try {
         String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
         if (this.logWriter == null || !today.equals(this.logDate)) {
            this.closeLogWriter();
            File dir = new File(this.plugin.getDataFolder(), "logs");
            if (!dir.exists() && !dir.mkdirs()) {
               return;
            }

            File file = new File(dir, today + ".log");
            this.logWriter = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8, true));
            this.logDate = today;
         }

         this.logWriter.write(LocalDateTime.now().format(TS) + " " + line + System.lineSeparator());
         this.logWriter.flush();
      } catch (IOException var5) {
         this.plugin.getLogger().warning("[ACE for DRS] 日志写入失败: " + var5.getMessage());
      }
   }

   public void closeLogWriter() {
      if (this.logWriter != null) {
         try {
            this.logWriter.close();
         } catch (IOException var2) {
         }

         this.logWriter = null;
      }

      // Reset the date too, otherwise a later appendLog() would think the
      // current writer is still open and hit a NullPointerException.
      this.logDate = null;
   }

   static {
      Map<String, Integer> vmax = new HashMap<>();
      vmax.put("sharpness", 5);
      vmax.put("smite", 5);
      vmax.put("bane_of_arthropods", 5);
      vmax.put("efficiency", 5);
      vmax.put("silk_touch", 1);
      vmax.put("unbreaking", 3);
      vmax.put("fortune", 3);
      vmax.put("protection", 4);
      vmax.put("fire_protection", 4);
      vmax.put("feather_falling", 4);
      vmax.put("blast_protection", 4);
      vmax.put("projectile_protection", 4);
      vmax.put("respiration", 3);
      vmax.put("aqua_affinity", 1);
      vmax.put("thorns", 3);
      vmax.put("depth_strider", 3);
      vmax.put("frost_walker", 2);
      vmax.put("binding_curse", 1);
      vmax.put("vanishing_curse", 1);
      vmax.put("looting", 3);
      vmax.put("sweeping_edge", 3);
      vmax.put("mending", 1);
      vmax.put("loyalty", 3);
      vmax.put("channeling", 1);
      vmax.put("riptide", 3);
      vmax.put("impaling", 5);
      vmax.put("knockback", 2);
      vmax.put("fire_aspect", 2);
      vmax.put("power", 5);
      vmax.put("punch", 2);
      vmax.put("flame", 1);
      vmax.put("infinity", 1);
      vmax.put("luck_of_the_sea", 3);
      vmax.put("lure", 3);
      vmax.put("multishot", 1);
      vmax.put("piercing", 4);
      vmax.put("quick_charge", 3);
      vmax.put("soul_speed", 3);
      vmax.put("swift_sneak", 3);
      vmax.put("wind_burst", 3);
      vmax.put("breach", 4);
      vmax.put("density", 5);
      // Added in 1.21.11 / Paper 26.2: Lunge (max III), applies to spears.
      vmax.put("lunge", 3);
      VANILLA_MAX = Map.copyOf(vmax);
      Map<String, String> zhn = new HashMap<>();
      zhn.put("sharpness", "锋利");
      zhn.put("smite", "亡灵杀手");
      zhn.put("bane_of_arthropods", "节肢杀手");
      zhn.put("efficiency", "效率");
      zhn.put("silk_touch", "精准采集");
      zhn.put("unbreaking", "耐久");
      zhn.put("fortune", "时运");
      zhn.put("protection", "保护");
      zhn.put("fire_protection", "火焰保护");
      zhn.put("feather_falling", "摔落保护");
      zhn.put("blast_protection", "爆炸保护");
      zhn.put("projectile_protection", "弹射物保护");
      zhn.put("respiration", "水下呼吸");
      zhn.put("aqua_affinity", "水下速掘");
      zhn.put("thorns", "荆棘");
      zhn.put("depth_strider", "深海探索者");
      zhn.put("frost_walker", "冰霜行者");
      zhn.put("binding_curse", "绑定诅咒");
      zhn.put("vanishing_curse", "消失诅咒");
      zhn.put("looting", "抢夺");
      zhn.put("sweeping_edge", "横扫之刃");
      zhn.put("mending", "经验修补");
      zhn.put("loyalty", "忠诚");
      zhn.put("channeling", "引雷");
      zhn.put("riptide", "激流");
      zhn.put("impaling", "穿刺");
      zhn.put("knockback", "击退");
      zhn.put("fire_aspect", "火焰附加");
      zhn.put("power", "力量");
      zhn.put("punch", "冲击");
      zhn.put("flame", "火矢");
      zhn.put("infinity", "无限");
      zhn.put("luck_of_the_sea", "海之眷顾");
      zhn.put("lure", "饵钓");
      zhn.put("multishot", "多重射击");
      zhn.put("piercing", "穿透");
      zhn.put("quick_charge", "快速装填");
      zhn.put("soul_speed", "灵魂疾行");
      zhn.put("swift_sneak", "迅捷潜行");
      zhn.put("wind_burst", "风爆");
      zhn.put("breach", "破甲");
      zhn.put("density", "致密");
      zhn.put("lunge", "突进");
      ZH_NAMES = Map.copyOf(zhn);
      Map<String, String> tzh = new HashMap<>();
      tzh.put("CHEST", "箱子");
      tzh.put("TRAPPED_CHEST", "陷阱箱");
      tzh.put("BARREL", "木桶");
      tzh.put("SHULKER_BOX", "潜影盒");
      tzh.put("ENDER_CHEST", "末影箱");
      tzh.put("DISPENSER", "发射器");
      tzh.put("DROPPER", "投掷器");
      tzh.put("HOPPER", "漏斗");
      tzh.put("FURNACE", "熔炉");
      tzh.put("BLAST_FURNACE", "高炉");
      tzh.put("SMOKER", "烟熏炉");
      tzh.put("BREWING_STAND", "酿造台");
      tzh.put("CRAFTING_TABLE", "工作台");
      TYPE_ZH_CACHE = Map.copyOf(tzh);
      Map<String, String> szh = new HashMap<>();
      szh.put("WHITE_SHULKER_BOX", "白色潜影盒");
      szh.put("ORANGE_SHULKER_BOX", "橙色潜影盒");
      szh.put("MAGENTA_SHULKER_BOX", "品红色潜影盒");
      szh.put("LIGHT_BLUE_SHULKER_BOX", "淡蓝色潜影盒");
      szh.put("YELLOW_SHULKER_BOX", "黄色潜影盒");
      szh.put("LIME_SHULKER_BOX", "黄绿色潜影盒");
      szh.put("PINK_SHULKER_BOX", "粉红色潜影盒");
      szh.put("GRAY_SHULKER_BOX", "灰色潜影盒");
      szh.put("LIGHT_GRAY_SHULKER_BOX", "淡灰色潜影盒");
      szh.put("CYAN_SHULKER_BOX", "青色潜影盒");
      szh.put("PURPLE_SHULKER_BOX", "紫色潜影盒");
      szh.put("BLUE_SHULKER_BOX", "蓝色潜影盒");
      szh.put("BROWN_SHULKER_BOX", "棕色潜影盒");
      szh.put("GREEN_SHULKER_BOX", "绿色潜影盒");
      szh.put("RED_SHULKER_BOX", "红色潜影盒");
      szh.put("BLACK_SHULKER_BOX", "黑色潜影盒");
      SHULKER_ZH = Map.copyOf(szh);
   }

   private record FixResult(String itemDesc, List<String> changes, boolean had255) {
   }

   public record PickupResult(Anti255IllegalItemDetector.DetectionResult illegal, String fixes) {
   }
}
