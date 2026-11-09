package com.acefordrs;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class Anti255Config {
   private static final Set<String> DEFAULT_WHITELIST_NAMESPACES = Set.of(
      "slimefun",
      "sf_addon",
      "infinityexpansion",
      "infinitylib",
      "infinity_expansion"
   );

   public long onlineIntervalSeconds = 180L;
   public boolean checkContainerOpen = true;
   public boolean respectServerMaxLevel = true;
   public boolean logToFile = true;
   public boolean removeInvalidEnchants = true;
   public boolean broadcastOnFix = true;
   public boolean checkOnJoin = true;
   public boolean keepUnbreaking255 = true;
   public int shulkerMaxDepth = 3;
   public long joinCheckDelayTicks = 20L;
   public boolean checkEnderChestOnJoin = true;
   public boolean deleteIllegalItems = true;
   public boolean broadcastOnDelete = true;
   public double maxAttributeAmount = 1000.0;
   public boolean backupBeforeDelete = true;
   public Set<String> customIllegalItems = new HashSet<>();
   public int potionMaxEffects = 6;
   public int potionMaxPotency = 9;
   public int potionMaxDuration = 12000;
   public long notificationCooldownSeconds = 3L;
   public long groundItemScanSeconds = 40L;
   public long effectCheckSeconds = 15L;
   public boolean arrowSpeedEnabled = true;
   public double arrowMaxSpeed = 5.0;
   public double arrowMaxDamage = 30.0;
   public long arrowScanSeconds = 5L;
   public boolean meleeCheckEnabled = true;
   public double meleeCheckMinDamage = 30.0;
   public String adminPermission = "anti255.admin";
   public String language = "both";

   // Slimefun / addon whitelist. Items matching any rule are treated as trusted
   // and skipped by both System 1 (enchant downgrade) and System 2 (illegal item).
   public boolean whitelistEnabled = true;
   public Set<String> whitelistPdcNamespaces = new HashSet<>(DEFAULT_WHITELIST_NAMESPACES);
   public Set<String> whitelistMaterials = new HashSet<>();
   public Set<String> whitelistNameContains = new HashSet<>();
   // Enchant key -> maximum level that should be treated as legitimate.
   // e.g. unbreaking: 25 keeps Infinity Expansion's Unbreaking XXV.
   public Map<String, Integer> whitelistEnchants = new HashMap<>();

   public void load(FileConfiguration cfg) {
      this.onlineIntervalSeconds = Math.max(5L, Math.min(86400L, cfg.getLong("check-online-seconds", 180L)));
      this.checkContainerOpen = cfg.getBoolean("check-on-container-open", true);
      this.respectServerMaxLevel = cfg.getBoolean("respect-server-max-level", true);
      this.logToFile = cfg.getBoolean("log-to-file", true);
      this.removeInvalidEnchants = cfg.getBoolean("remove-invalid-enchants", true);
      this.broadcastOnFix = cfg.getBoolean("broadcast-on-fix", true);
      this.checkOnJoin = cfg.getBoolean("check-on-join", true);
      this.keepUnbreaking255 = cfg.getBoolean("keep-unbreaking-255", true);
      this.shulkerMaxDepth = Math.max(0, Math.min(6, cfg.getInt("shulker-check-depth", 3)));
      this.joinCheckDelayTicks = Math.max(5L, Math.min(200L, cfg.getLong("join-check-delay-ticks", 20L)));
      this.checkEnderChestOnJoin = cfg.getBoolean("check-ender-chest-on-join", true);
      this.deleteIllegalItems = cfg.getBoolean("delete-illegal-items", true);
      this.broadcastOnDelete = cfg.getBoolean("broadcast-on-delete", true);
      this.maxAttributeAmount = cfg.getDouble("max-attribute-amount", 1000.0);
      if (!Double.isFinite(this.maxAttributeAmount) || this.maxAttributeAmount < 0.0) {
         this.maxAttributeAmount = 1000.0;
      }

      this.backupBeforeDelete = cfg.getBoolean("backup-before-delete", true);
      this.customIllegalItems = new HashSet<>();

      for (String name : cfg.getStringList("custom-illegal-items")) {
         if (name != null && !name.isBlank()) {
            this.customIllegalItems.add(name.trim().toUpperCase(Locale.ROOT));
         }
      }

      this.potionMaxEffects = Math.max(1, Math.min(16, cfg.getInt("potion-max-effects", 6)));
      this.potionMaxPotency = Math.max(0, Math.min(255, cfg.getInt("potion-max-potency", 9)));
      this.potionMaxDuration = Math.max(0, cfg.getInt("potion-max-duration", 12000));
      this.notificationCooldownSeconds = Math.max(0L, Math.min(86400L, cfg.getLong("notification-cooldown-seconds", 3L)));
      this.groundItemScanSeconds = Math.max(0L, Math.min(86400L, cfg.getLong("ground-item-scan-seconds", 40L)));
      this.effectCheckSeconds = Math.max(1L, Math.min(86400L, cfg.getLong("effect-check-seconds", 15L)));
      this.arrowSpeedEnabled = cfg.getBoolean("arrow-speed-enabled", true);
      this.arrowMaxSpeed = cfg.getDouble("arrow-max-speed", 5.0);
      if (!Double.isFinite(this.arrowMaxSpeed) || this.arrowMaxSpeed < 1.0) {
         this.arrowMaxSpeed = 5.0;
      }

      this.arrowMaxDamage = cfg.getDouble("arrow-max-damage", 30.0);
      if (!Double.isFinite(this.arrowMaxDamage) || this.arrowMaxDamage < 1.0) {
         this.arrowMaxDamage = 30.0;
      }
      this.arrowScanSeconds = Math.max(1L, Math.min(86400L, cfg.getLong("arrow-scan-seconds", 5L)));
      this.meleeCheckEnabled = cfg.getBoolean("melee-check-enabled", true);
      this.meleeCheckMinDamage = cfg.getDouble("melee-check-min-damage", 30.0);
      if (Double.isNaN(this.meleeCheckMinDamage) || Double.isInfinite(this.meleeCheckMinDamage) || this.meleeCheckMinDamage < 0.0) {
         this.meleeCheckMinDamage = 30.0;
      }
      this.adminPermission = cfg.getString("admin-permission", "anti255.admin");
      this.language = cfg.getString("language", "zh");
      if (this.language == null || this.language.isBlank()) {
         this.language = "zh";
      }

      this.whitelistEnabled = cfg.getBoolean("whitelist.enabled", true);
      this.whitelistPdcNamespaces = new HashSet<>();
      for (String ns : cfg.getStringList("whitelist.pdc-namespaces")) {
         if (ns != null && !ns.isBlank()) {
            this.whitelistPdcNamespaces.add(ns.trim().toLowerCase(Locale.ROOT));
         }
      }
      if (this.whitelistPdcNamespaces.isEmpty()) {
         this.whitelistPdcNamespaces.addAll(DEFAULT_WHITELIST_NAMESPACES);
      }

      this.whitelistMaterials = new HashSet<>();
      for (String material : cfg.getStringList("whitelist.materials")) {
         if (material != null && !material.isBlank()) {
            this.whitelistMaterials.add(material.trim().toUpperCase(Locale.ROOT));
         }
      }

      this.whitelistNameContains = new HashSet<>();
      for (String keyword : cfg.getStringList("whitelist.name-contains")) {
         if (keyword != null && !keyword.isBlank()) {
            this.whitelistNameContains.add(keyword.trim().toLowerCase(Locale.ROOT));
         }
      }

      this.whitelistEnchants = new HashMap<>();
      ConfigurationSection enchantSection = cfg.getConfigurationSection("whitelist.enchants");
      if (enchantSection == null) {
         // Backwards compatibility: existing config.yml files have no
         // whitelist section, but addon gear still needs its over-level
         // Unbreaking to survive.
         this.whitelistEnchants.put("unbreaking", 25);
      } else {
         for (String key : enchantSection.getKeys(false)) {
            int max = enchantSection.getInt(key, 0);
            if (max > 0) {
               this.whitelistEnchants.put(key.trim().toLowerCase(Locale.ROOT), max);
            }
         }
      }
   }

   public int getWhitelistedEnchantMax(String enchantKey) {
      if (!this.whitelistEnabled || enchantKey == null) {
         return 0;
      }
      Integer max = this.whitelistEnchants.get(enchantKey.toLowerCase(Locale.ROOT));
      return max != null ? max : 0;
   }
}
