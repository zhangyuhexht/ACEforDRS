package com.acefordrs;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

public final class Anti255Plugin extends JavaPlugin implements Listener {
   private static final Class<?>[] GROUND_ITEM_CLASSES = new Class[]{Item.class};
   private final Anti255Config cfg = new Anti255Config();
   private Anti255Scanner scanner;
   private Anti255Messages messages;
   private Anti255ArrowSpeedLimiter arrowSpeedLimiter;
   // Clicking fires events very often; a full inventory re-scan on every
   // click would be wasteful. 500 ms is short enough that a moved stack is
   // still checked, and the periodic scan remains the safety net.
   private final Map<UUID, Long> clickScanThrottle = new HashMap<>();

   public void onEnable() {
      this.saveDefaultConfig();
      Anti255ConfigFile.update(this);
      this.messages = new Anti255Messages(this);
      this.messages.load();
      this.reloadSettings();
      this.scanner = new Anti255Scanner(this, this.cfg);
      this.arrowSpeedLimiter = new Anti255ArrowSpeedLimiter(this, this.cfg);
      this.getServer().getPluginManager().registerEvents(this, this);
      this.getServer().getPluginManager().registerEvents(this.arrowSpeedLimiter, this);
      this.scheduleTasks();
      PluginCommand anti255Command = this.getCommand("anti255");
      if (anti255Command != null) {
         anti255Command.setTabCompleter(new Anti255Plugin.Anti255TabCompleter());
      }

      PluginCommand check255Command = this.getCommand("check255");
      if (check255Command != null) {
         check255Command.setTabCompleter(new Anti255Plugin.Anti255TabCompleter());
      }

      this.getLogger().info(this.messages.get("plugin-enabled", Map.of("interval", String.valueOf(this.cfg.onlineIntervalSeconds))));
   }

   public void onDisable() {
      Bukkit.getScheduler().cancelTasks(this);
      if (this.scanner != null) {
         this.scanner.closeLogWriter();
      }

      if (this.messages != null) {
         this.getLogger().info(this.messages.get("plugin-disabled"));
      } else {
         this.getLogger().info("[ACE for DRS] Plugin unloaded");
      }
   }

   private void reloadSettings() {
      this.reloadConfig();
      this.cfg.load(this.getConfig());
      if (this.messages != null) {
         this.messages.load();
      }

      Anti255Scanner.clearCache();
      if (this.scanner != null) {
         this.scanner.getIllegalDetector().rebuildIllegalSet();
      }
   }

   public Anti255Messages getMessages() {
      return this.messages;
   }

   private void scheduleTasks() {
      // Config values are clamped to 86400 seconds, so * 20L cannot overflow.
      long onlineTicks = this.cfg.onlineIntervalSeconds * 20L;
      this.getServer().getScheduler().runTaskTimer(this, this::scanOnlinePlayers, onlineTicks, onlineTicks);
      if (this.cfg.groundItemScanSeconds > 0L) {
         long groundTicks = this.cfg.groundItemScanSeconds * 20L;
         this.getServer().getScheduler().runTaskTimer(this, this::scanGroundItems, groundTicks, groundTicks);
      }

      if (this.cfg.arrowSpeedEnabled && this.cfg.arrowScanSeconds > 0L) {
         long arrowTicks = this.cfg.arrowScanSeconds * 20L;
         this.getServer().getScheduler().runTaskTimer(this, this.arrowSpeedLimiter::scanFlyingArrows, arrowTicks, arrowTicks);
      }

      long effectTicks = this.cfg.effectCheckSeconds * 20L;
      this.getServer().getScheduler().runTaskTimer(this, this::scanPlayerEffects, effectTicks, effectTicks);
   }

   private void scanOnlinePlayers() {
      List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
      if (players.isEmpty()) {
         return;
      }

      // Keep the 180s per-player interval, but spread this cycle across the
      // interval instead of scanning every player in a single tick. This
      // avoids a large TPS spike on servers with many online players.
      long intervalTicks = this.cfg.onlineIntervalSeconds * 20L;
      long step = Math.max(1L, intervalTicks / players.size());
      for (int i = 0; i < players.size(); i++) {
         Player player = players.get(i);
         long delay = i * step;
         if (delay <= 0L) {
            this.scanOnlinePlayer(player);
         } else {
            this.getServer().getScheduler().runTaskLater(this, () -> this.scanOnlinePlayer(player), delay);
         }
      }
   }

   private void scanOnlinePlayer(Player player) {
      if (player == null || !player.isOnline()) {
         return;
      }

      try {
         int n = this.scanner.scanPlayerInventory(player);
         if (n > 0) {
            this.getLogger().info("[ACE for DRS] 在线检查: 玩家 " + player.getName() + " 共处理 " + n + " 件物品");
         }

         int m = this.scanner.scanPlayerEnderChest(player);
         if (m > 0) {
            this.getLogger().info("[ACE for DRS] 在线检查(末影箱): 玩家 " + player.getName() + " 共处理 " + m + " 件物品");
         }
      } catch (Throwable t) {
         // One broken item must not stop the whole scan round.
         this.getLogger().log(Level.WARNING, "[ACE for DRS] 在线检查玩家 " + player.getName() + " 时出错", t);
      }
   }

   private void scanPlayerEffects() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         try {
            if (player.isOnline()) {
               int fixed = 0;

               // Copy first: removing/adding effects while iterating the live
               // collection can throw ConcurrentModificationException.
               for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
                  String key = effect.getType().getKey().getKey();
                  Integer maxAmplifier = Anti255IllegalItemDetector.getVanillaEffectMaxAmplifier(key);
                  if (maxAmplifier != null && effect.getAmplifier() > maxAmplifier) {
                     player.removePotionEffect(effect.getType());
                     // withAmplifier() keeps duration (including infinite),
                     // ambient/particles/icon and the hidden-effect link.
                     player.addPotionEffect(effect.withAmplifier(maxAmplifier), true);
                     fixed++;
                     String detail = key + " " + (effect.getAmplifier() + 1) + " -> " + (maxAmplifier + 1);
                     this.getLogger().info("[ACE for DRS] 玩家 " + player.getName() + " 的违规效果 " + detail + " 已降级");
                     player.sendMessage(
                        this.messages
                           .get(
                              "effect-downgraded",
                              Map.of("effect", key, "from", String.valueOf(effect.getAmplifier() + 1), "to", String.valueOf(maxAmplifier + 1))
                           )
                     );
                  }
               }

               if (fixed > 0) {
                  this.getLogger().info("[ACE for DRS] 效果检查: 玩家 " + player.getName() + " 修正 " + fixed + " 个违规效果");
               }
            }
         } catch (Throwable t) {
            this.getLogger().log(Level.WARNING, "[ACE for DRS] 检查玩家 " + player.getName() + " 的状态效果时出错", t);
         }
      }
   }

   private void scanGroundItems() {
      for (World world : Bukkit.getWorlds()) {
         for (Entity entity : world.getEntitiesByClasses(GROUND_ITEM_CLASSES)) {
            try {
               Item itemEntity = (Item)entity;
               ItemStack stack = itemEntity.getItemStack();
               if (stack == null || stack.getType().isAir()) {
                  continue;
               }

               // Plain drops (cobblestone, dirt, ...) cannot carry attributes
               // or potion effects; skip them without touching ItemMeta.
               if (!stack.hasItemMeta() && !this.scanner.getIllegalDetector().isIllegalMaterial(stack)) {
                  continue;
               }

               ItemMeta meta = stack.hasItemMeta() ? stack.getItemMeta() : null;
               boolean whitelisted = this.scanner.getIllegalDetector().getDeepDetector().isWhitelisted(stack, meta);

               List<String> details = new ArrayList<>();

               // System 2: forbidden materials / abnormal potions. detect()
               // still applies the absolute attribute ceiling to whitelisted
               // items, so a forged whitelist marker cannot hide impossible
               // attribute values. Only runs when deletion is enabled.
               if (this.cfg.deleteIllegalItems) {
                  Anti255IllegalItemDetector.DetectionResult result = this.scanner.getIllegalDetector().detect(stack, whitelisted, meta);
                  if (result != null) {
                     this.handleGroundIllegalItem(itemEntity, stack, world);
                     continue;
                  }

                  String projectileResult = this.scanner.getIllegalDetector().scanCrossbowProjectiles(stack, meta);
                  if (projectileResult != null) {
                     details.add("弩装填物: " + projectileResult);
                  }
               }

               if (whitelisted) {
                  if (!details.isEmpty()) {
                     this.handleGroundFixedItem(itemEntity, stack, world, String.join("; ", details));
                  }
                  continue;
               }

               // System 2 NBT repair (potion effects, attributes, ...) must
               // also run for ground items when deletion is disabled, just
               // like it does for inventories.
               String deepResult = this.scanner.getIllegalDetector().getDeepDetector().deepScanAndFix(stack, whitelisted, meta);
               if (deepResult != null) {
                  details.add(deepResult);
               }

               // System 1: ground items previously never had their 255
               // enchantments downgraded at all.
               String enchantResult = this.scanner.fixEnchantsOnGroundItem(stack, meta);
               if (enchantResult != null) {
                  details.add(enchantResult);
               }

               if (!details.isEmpty()) {
                  this.handleGroundFixedItem(itemEntity, stack, world, String.join("; ", details));
               }
            } catch (Throwable t) {
               this.getLogger().log(Level.WARNING, "[ACE for DRS] 扫描地面物品时出错", t);
            }
         }
      }
   }

   private void handleGroundIllegalItem(Item itemEntity, ItemStack stack, World world) {
      Location loc = itemEntity.getLocation();
      String itemName = Anti255IllegalItemDetector.getZhName(stack.getType().name());
      itemEntity.remove();
      String coord = formatCoord(world, loc);
      String msg = this.messages.get("ground-item-removed", Map.of("location", coord, "item", itemName + "(" + stack.getType().name() + ")"));
      Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));
      this.getLogger().info("[ACE for DRS] 地面违禁品: " + itemName + "(" + stack.getType().name() + ") at " + coord + " 已清除");
      this.scanner.incrementReplaced();
   }

   private void handleGroundFixedItem(Item itemEntity, ItemStack stack, World world, String deepResult) {
      Location loc = itemEntity.getLocation();
      String itemName = Anti255IllegalItemDetector.getZhName(stack.getType().name());
      String coord = formatCoord(world, loc);
      String msg = this.messages
         .get("ground-item-fixed", Map.of("location", coord, "item", itemName + "(" + stack.getType().name() + ")", "detail", deepResult));
      Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));
      this.getLogger().info("[ACE for DRS] 地面物品修正: " + itemName + "(" + stack.getType().name() + ") at " + coord + " - " + deepResult);
      itemEntity.setItemStack(stack);
   }

   private static String formatCoord(World world, Location loc) {
      return world.getName() + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
   }

   @EventHandler(
      priority = EventPriority.NORMAL,
      ignoreCancelled = true
   )
   public void onInventoryOpen(InventoryOpenEvent event) {
      if (this.cfg.checkContainerOpen) {
         if (event.getPlayer() instanceof Player player) {
            Inventory top = event.getView().getTopInventory();
            if (event.getView().getType() == InventoryType.ENDER_CHEST) {
               // The ender chest holder is not a BlockState, so the regular
               // container check would skip it.
               this.scanner.scanPlayerEnderChest(player);
            } else if (top != null && isContainer(top)) {
               this.scanner.scanContainer(top, player);
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.NORMAL,
      ignoreCancelled = true
   )
   public void onInventoryClick(InventoryClickEvent event) {
      if (!(event.getWhoClicked() instanceof Player player) || !player.isOnline()) {
         return;
      }

      InventoryAction action = event.getAction();
      if (action == InventoryAction.NOTHING || this.shouldThrottleClickScan(player)) {
         return;
      }

      InventoryView view = event.getView();
      int rawSlot = event.getRawSlot();
      int hotbarButton = event.getHotbarButton();
      boolean shiftMove = action == InventoryAction.MOVE_TO_OTHER_INVENTORY;
      Bukkit.getScheduler().runTaskLater(this, () -> {
         if (!player.isOnline()) {
            return;
         }

         // Only the clicked slot changed for normal clicks. The cursor is
         // checked separately because a picked-up stack no longer exists in
         // its source slot after the click.
         if (rawSlot >= 0) {
            this.scanViewSlot(view, player, rawSlot);
         }

         if (hotbarButton >= 0) {
            this.scanner.scanPlayerSlot(player, hotbarButton);
         }

         try {
            this.scanner.scanCursor(player, view);
         } catch (Throwable t) {
            this.getLogger().log(Level.WARNING, "[ACE for DRS] 光标扫描出错", t);
         }

         // Shift-click has an unknown destination slot, so fall back to a
         // full destination scan for that action only.
         if (shiftMove) {
            Inventory top = view.getTopInventory();
            if (top != null && !top.equals(player.getInventory()) && isContainer(top)) {
               this.scanner.scanContainer(top, player);
            }
            this.scanner.scanPlayerInventory(player);
         }
      }, 1L);
   }

   @EventHandler(
      priority = EventPriority.NORMAL,
      ignoreCancelled = true
   )
   public void onInventoryDrag(InventoryDragEvent event) {
      if (!(event.getWhoClicked() instanceof Player player) || !player.isOnline()) {
         return;
      }

      if (this.shouldThrottleClickScan(player)) {
         return;
      }

      InventoryView view = event.getView();
      List<Integer> rawSlots = new ArrayList<>(event.getRawSlots());
      Bukkit.getScheduler().runTaskLater(this, () -> {
         if (!player.isOnline()) {
            return;
         }

         // Dragging only affects the slots reported by the event, so there is
         // no need to rescan the whole open container and player inventory.
         for (int rawSlot : rawSlots) {
            this.scanViewSlot(view, player, rawSlot);
         }

         try {
            this.scanner.scanCursor(player, view);
         } catch (Throwable t) {
            this.getLogger().log(Level.WARNING, "[ACE for DRS] 光标扫描出错", t);
         }
      }, 1L);
   }

   private void scanViewSlot(InventoryView view, Player player, int rawSlot) {
      try {
         if (rawSlot < 0 || rawSlot >= view.countSlots()) {
            return;
         }

         Inventory inventory = view.getInventory(rawSlot);
         if (inventory == null) {
            return;
         }

         int slot = view.convertSlot(rawSlot);
         if (slot < 0 || slot >= inventory.getSize()) {
            return;
         }

         if (view.getType() == InventoryType.ENDER_CHEST && rawSlot < view.getTopInventory().getSize()) {
            this.scanner.scanPlayerEnderChestSlot(player, slot);
         } else if (inventory.equals(player.getInventory())) {
            this.scanner.scanPlayerSlot(player, slot);
         } else if (isContainer(inventory)) {
            this.scanner.scanContainerSlot(inventory, slot, player);
         }
      } catch (RuntimeException e) {
         // The view may have been closed between the event and this task.
      }
   }

   @EventHandler(
      priority = EventPriority.NORMAL
   )
   public void onPlayerJoin(PlayerJoinEvent event) {
      if (this.cfg.checkOnJoin) {
         Player player = event.getPlayer();
         this.getServer().getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
               try {
                  int n = this.scanner.scanPlayerInventory(player);
                  if (this.cfg.checkEnderChestOnJoin) {
                     n += this.scanner.scanPlayerEnderChest(player);
                  }

                  if (n > 0) {
                     this.getLogger().info("[ACE for DRS] 上线检查: 玩家 " + player.getName() + " 共处理 " + n + " 件物品");
                  }
               } catch (Throwable t) {
                  this.getLogger().log(Level.WARNING, "[ACE for DRS] 上线检查玩家 " + player.getName() + " 时出错", t);
               }
            }
         }, this.cfg.joinCheckDelayTicks);
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onPlayerQuit(PlayerQuitEvent event) {
      this.clickScanThrottle.remove(event.getPlayer().getUniqueId());
      if (this.scanner != null) {
         this.scanner.clearNotification(event.getPlayer().getUniqueId());
      }
   }

   private boolean shouldThrottleClickScan(Player player) {
      long now = System.currentTimeMillis();
      Long last = this.clickScanThrottle.get(player.getUniqueId());
      if (last != null && now - last < 500L) {
         return true;
      }

      this.clickScanThrottle.put(player.getUniqueId(), now);
      return false;
   }

   @EventHandler(
      priority = EventPriority.NORMAL
   )
   public void onItemHeld(PlayerItemHeldEvent event) {
      Player player = event.getPlayer();
      if (player.isOnline() && !this.shouldThrottleClickScan(player)) {
         this.scanner.scanPlayerSlot(player, event.getNewSlot());
      }
   }

   @EventHandler(
      priority = EventPriority.NORMAL
   )
   public void onArmorChange(PlayerArmorChangeEvent event) {
      Player player = event.getPlayer();
      // Equipping a full set fires this event once per slot. The first full
      // inventory scan already covers every armor slot, so throttle the rest
      // just like hotbar switches.
      if (player.isOnline() && !this.shouldThrottleClickScan(player)) {
         this.scanner.scanPlayerInventory(player);
      }
   }

   @EventHandler(
      priority = EventPriority.NORMAL,
      ignoreCancelled = true
   )
   public void onSwapHands(PlayerSwapHandItemsEvent event) {
      Player player = event.getPlayer();
      if (!player.isOnline()) {
         return;
      }

      // F-key swaps do not fire PlayerItemHeldEvent, so scan both hands on
      // the next tick once the swap has been applied.
      this.getServer().getScheduler().runTask(this, () -> {
         if (!player.isOnline()) {
            return;
         }

         this.scanner.scanPlayerSlot(player, player.getInventory().getHeldItemSlot());
         this.scanner.scanPlayerSlot(player, 40);
      });
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onConsume(PlayerItemConsumeEvent event) {
      if (this.blockIllegalUse(event.getPlayer(), event.getItem(), event.getHand())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOW,
      ignoreCancelled = true
   )
   public void onInteract(PlayerInteractEvent event) {
      Action action = event.getAction();
      if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
         return;
      }

      ItemStack item = event.getItem();
      if (item == null || item.getType().isAir()) {
         return;
      }

      // Plain building blocks have no meta and cannot be illegal materials;
      // skip them so right-clicking while building stays cheap.
      if (!item.hasItemMeta() && !this.scanner.getIllegalDetector().isIllegalMaterial(item)) {
         return;
      }

      if (this.blockIllegalUse(event.getPlayer(), item, event.getHand())) {
         event.setCancelled(true);
      }
   }

   private boolean blockIllegalUse(Player player, ItemStack item, EquipmentSlot hand) {
      if (player == null || item == null || item.getType().isAir() || this.isAdmin(player)) {
         return false;
      }

      Anti255IllegalItemDetector.DetectionResult result;
      try {
         result = this.scanner.getIllegalDetector().detect(item);
      } catch (Throwable t) {
         this.getLogger().log(Level.WARNING, "[ACE for DRS] 使用前检测出错", t);
         return false;
      }

      if (result == null) {
         return false;
      }

      // Repair/replace the item in the hand slot before the effect happens.
      this.scanner.scanPlayerSlot(player, this.handSlot(player, hand));
      String detail = result.type() + ": " + result.detail();
      player.sendMessage(this.messages.get("illegal-item-use", Map.of("detail", detail)));
      this.getLogger().warning("[ACE for DRS] 已阻止 " + player.getName() + " 使用异常物品: " + detail);
      return true;
   }

   private int handSlot(Player player, EquipmentSlot hand) {
      return hand == EquipmentSlot.OFF_HAND ? 40 : player.getInventory().getHeldItemSlot();
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onPickup(EntityPickupItemEvent event) {
      if (!(event.getEntity() instanceof Player player) || this.isAdmin(player)) {
         return;
      }

      Item itemEntity = event.getItem();
      ItemStack stack = itemEntity.getItemStack();
      Anti255Scanner.PickupResult result;
      try {
         result = this.scanner.scanPickupItem(stack);
      } catch (Throwable t) {
         this.getLogger().log(Level.WARNING, "[ACE for DRS] 拾取扫描出错，已跳过该物品", t);
         return;
      }

      if (result == null) {
         return;
      }

      if (result.fixes() != null) {
         itemEntity.setItemStack(stack);
         this.handleGroundFixedItem(itemEntity, stack, itemEntity.getWorld(), result.fixes());
      }

      if (result.illegal() != null && this.cfg.deleteIllegalItems) {
         event.setCancelled(true);
         this.handleGroundIllegalItem(itemEntity, stack, itemEntity.getWorld());
      }
   }

   @EventHandler(
      priority = EventPriority.LOW,
      ignoreCancelled = true
   )
   public void onMeleeDamage(EntityDamageByEntityEvent event) {
      if (!this.cfg.meleeCheckEnabled) {
         return;
      }

      if (event.getDamage() < this.cfg.meleeCheckMinDamage) {
         return;
      }

      if (!(event.getDamager() instanceof Player player)) {
         return;
      }

      // Admins may use admin-only items, so System 2's hit guard skips them.
      if (this.isAdmin(player)) {
         return;
      }

      ItemStack weapon = player.getInventory().getItemInMainHand();
      if (weapon == null || weapon.getType().isAir()) {
         return;
      }

      Anti255IllegalItemDetector.DetectionResult result;
      try {
         result = this.scanner.getIllegalDetector().detect(weapon);
      } catch (Throwable t) {
         this.getLogger().log(Level.WARNING, "[ACE for DRS] 近战武器检测出错", t);
         return;
      }

      if (result == null) {
         return;
      }

      // Block the hit immediately, then repair/replace the weapon on the spot.
      event.setCancelled(true);
      this.scanner.scanPlayerSlot(player, player.getInventory().getHeldItemSlot());
      String detail = result.type() + ": " + result.detail();
      player.sendMessage(this.messages.get("illegal-item-hit", Map.of("detail", detail)));
      this.getLogger().warning("[ACE for DRS] 已拦截 " + player.getName() + " 的异常近战攻击: " + detail);
   }

   private static boolean isContainer(Inventory inventory) {
      if (inventory == null) {
         return false;
      }

      Object holder = inventory.getHolder();
      // BlockState covers chests/barrels/furnaces, DoubleChest covers double
      // chests, and Entity covers storage minecarts, chest boats and hopper
      // minecarts (which are not BlockState holders).
      return holder instanceof BlockState || holder instanceof DoubleChest || holder instanceof Entity;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (command.getName().equalsIgnoreCase("check255")) {
         return this.onCheck255(sender, args);
      } else if (args.length != 0 && !args[0].equalsIgnoreCase("help")) {
         String var5 = args[0].toLowerCase(Locale.ROOT);

         return switch (var5) {
            case "reload" -> this.onReload(sender);
            case "scan" -> this.onScan(sender, args);
            case "inspect" -> this.onInspect(sender, args);
            case "stats" -> this.onStats(sender);
            default -> false;
         };
      } else {
         sender.sendMessage(this.messages.get("help"));
         return true;
      }
   }

   private boolean onReload(CommandSender sender) {
      Anti255ConfigFile.update(this);
      this.reloadSettings();
      Bukkit.getScheduler().cancelTasks(this);
      this.scheduleTasks();
      sender.sendMessage(this.messages.get("reload-success"));
      this.getLogger().info("[ACE for DRS] 配置已重载");
      return true;
   }

   private boolean onScan(CommandSender sender, String[] args) {
      if (args.length >= 2 && !args[1].equalsIgnoreCase("all")) {
         Player target = Bukkit.getPlayerExact(args[1]);
         if (target == null) {
            sender.sendMessage(this.messages.get("player-not-online", Map.of("player", args[1])));
            return true;
         }

         int total = this.scanner.scanPlayerInventory(target) + this.scanner.scanPlayerEnderChest(target);
         sender.sendMessage(this.messages.get("scan-complete", Map.of("player", target.getName(), "count", String.valueOf(total))));
      } else {
         int total = 0;

         for (Player p : Bukkit.getOnlinePlayers()) {
            try {
               total += this.scanner.scanPlayerInventory(p) + this.scanner.scanPlayerEnderChest(p);
            } catch (Throwable t) {
               // Keep scanning the remaining players even if one inventory
               // is broken.
               this.getLogger().log(Level.WARNING, "[ACE for DRS] 手动扫描玩家 " + p.getName() + " 时出错", t);
            }
         }

         sender.sendMessage(this.messages.get("scan-complete-all", Map.of("count", String.valueOf(total))));
      }

      return true;
   }

   private boolean onInspect(CommandSender sender, String[] args) {
      if (args.length < 2) {
         sender.sendMessage(this.messages.get("usage-inspect"));
         return true;
      } else {
         Player target = Bukkit.getPlayerExact(args[1]);
         if (target == null) {
            sender.sendMessage(this.messages.get("player-not-online", Map.of("player", args[1])));
            return true;
         } else {
            this.scanner.inspectPlayer(target, sender);
            return true;
         }
      }
   }

   private boolean onCheck255(CommandSender sender, String[] args) {
      if (args.length < 1) {
         sender.sendMessage(this.messages.get("usage-check255"));
         return true;
      } else {
         Player target = Bukkit.getPlayerExact(args[0]);
         if (target == null) {
            sender.sendMessage(this.messages.get("player-not-online", Map.of("player", args[0])));
            return true;
         } else {
            int n = this.scanner.scanPlayerInventory(target) + this.scanner.scanPlayerEnderChest(target);
            sender.sendMessage(this.messages.get("scan-complete", Map.of("player", target.getName(), "count", String.valueOf(n))));
            if (n > 0) {
               this.getLogger().info("[ACE for DRS] 手动检查: 玩家 " + target.getName() + " 共处理 " + n + " 件物品");
            }

            return true;
         }
      }
   }

   private boolean onStats(CommandSender sender) {
      String msg = this.messages
         .get(
            "stats-detail",
            Map.of(
               "players",
               String.valueOf(Bukkit.getOnlinePlayers().size()),
               "scans",
               String.valueOf(this.scanner.getTotalScanned()),
               "fixed",
               String.valueOf(this.scanner.getTotalFixed()),
               "replaced",
               String.valueOf(this.scanner.getTotalReplaced())
            )
         );
      sender.sendMessage(msg);
      return true;
   }

   public boolean isAdmin(Player player) {
      return player.isOp() ? true : this.cfg.adminPermission != null && !this.cfg.adminPermission.isBlank() && player.hasPermission(this.cfg.adminPermission);
   }

   public Anti255Scanner getScanner() {
      return this.scanner;
   }

   private final class Anti255TabCompleter implements TabCompleter {
      public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
         if (!sender.hasPermission("anti255.admin")) {
            return List.of();
         } else if (command.getName().equalsIgnoreCase("check255")) {
            return args.length == 1 ? this.playerNames(args[0]) : List.of();
         } else {
            return switch (args.length) {
               case 1 -> this.filter(new ArrayList<>(List.of("reload", "scan", "inspect", "stats", "help")), args[0]);
               case 2 -> {
                  String var5 = args[0].toLowerCase(Locale.ROOT);
                  switch (var5) {
                     case "scan":
                        List<String> subs = new ArrayList<>(List.of("all"));
                        subs.addAll(this.playerNames(args[1]));
                        yield this.filter(subs, args[1]);
                     case "inspect":
                        yield this.playerNames(args[1]);
                     default:
                        yield List.of();
                  }
               }
               default -> List.of();
            };
         }
      }

      private List<String> playerNames(String prefix) {
         String lower = prefix.toLowerCase(Locale.ROOT);
         List<String> names = new ArrayList<>();

         for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
               names.add(p.getName());
            }
         }

         return names;
      }

      private List<String> filter(List<String> list, String prefix) {
         String p = prefix.toLowerCase(Locale.ROOT);
         list.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(p));
         return list;
      }
   }
}
