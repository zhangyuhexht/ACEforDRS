package com.acefordrs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.potion.PotionEffect;

public final class Anti255MaliciousNBTDetector {
   private final Anti255Config config;

   public Anti255MaliciousNBTDetector(Anti255Config config) {
      this.config = config;
   }

   /**
    * Returns true when the item belongs to a trusted plugin (Slimefun, its
    * addons, or any namespace configured in config.yml). Such items are
    * skipped completely, which keeps addon content like Infinity Expansion's
    * Unbreaking XXV armor intact.
    */
   public boolean isWhitelisted(ItemStack item) {
      return this.isWhitelisted(item, readMeta(item));
   }

   /**
    * Same as {@link #isWhitelisted(ItemStack)} but reuses an already read
    * ItemMeta. The scanner reads the meta once per item and passes it to the
    * whitelist, detection and deep-scan stages.
    */
   boolean isWhitelisted(ItemStack item, ItemMeta meta) {
      if (!this.config.whitelistEnabled || item == null || item.getType().isAir()) {
         return false;
      }

      if (this.config.whitelistMaterials.contains(item.getType().name())) {
         return true;
      }

      if (meta == null) {
         return false;
      }

      if (!this.config.whitelistNameContains.isEmpty() && meta.displayName() != null) {
         String plainName = PlainTextComponentSerializer.plainText().serialize(meta.displayName()).toLowerCase(Locale.ROOT);
         for (String keyword : this.config.whitelistNameContains) {
            if (plainName.contains(keyword)) {
               return true;
            }
         }
      }

      PersistentDataContainer pdc = meta.getPersistentDataContainer();
      if (!pdc.isEmpty()) {
         for (NamespacedKey key : pdc.getKeys()) {
            // NamespacedKey always stores its namespace in lowercase.
            if (this.config.whitelistPdcNamespaces.contains(key.getNamespace())) {
               return true;
            }
         }
      }

      return false;
   }

   public boolean scanAndFixPotionEffects(ItemStack item) {
      return item != null && this.scanAndFixPotionEffects(item, readMeta(item));
   }

   boolean scanAndFixPotionEffects(ItemStack item, ItemMeta meta) {
      if (item == null || !(meta instanceof PotionMeta potionMeta)) {
         return false;
      }

      if (!potionMeta.hasCustomEffects()) {
         return false;
      }

      List<PotionEffect> effects = potionMeta.getCustomEffects();
      List<PotionEffect> cleaned = new ArrayList<>();
      boolean fixed = false;

      for (PotionEffect effect : effects) {
         int amplifier = effect.getAmplifier();
         if (amplifier > this.maxPotionAmplifier(effect)) {
            cleaned.add(effect.withAmplifier(this.maxPotionAmplifier(effect)));
            fixed = true;
         } else {
            cleaned.add(effect);
         }
      }

      if (fixed) {
         potionMeta.clearCustomEffects();

         for (PotionEffect effectx : cleaned) {
            potionMeta.addCustomEffect(effectx, true);
         }

         item.setItemMeta(potionMeta);
      }

      return fixed;
   }

   public String deepScanAndFix(ItemStack item) {
      if (item == null || item.getType().isAir()) {
         return null;
      }

      ItemMeta meta = readMeta(item);
      return this.deepScanAndFix(item, this.isWhitelisted(item, meta), meta);
   }

   String deepScanAndFix(ItemStack item, boolean whitelisted) {
      if (item == null || item.getType().isAir()) {
         return null;
      }

      return this.deepScanAndFix(item, whitelisted, readMeta(item));
   }

   String deepScanAndFix(ItemStack item, boolean whitelisted, ItemMeta meta) {
      if (item == null || item.getType().isAir() || whitelisted || meta == null) {
         return null;
      }

      List<String> issues = new ArrayList<>();
      if (this.scanAndFixPotionEffects(item, meta)) {
         issues.add("药水效果amplifier修正");
      }

      return issues.isEmpty() ? null : String.join("; ", issues);
   }

   public String deepScanCheck(ItemStack item) {
      if (item == null || item.getType().isAir()) {
         return null;
      }

      ItemMeta meta = readMeta(item);
      return this.deepScanCheck(item, this.isWhitelisted(item, meta), meta);
   }

   String deepScanCheck(ItemStack item, boolean whitelisted) {
      if (item == null || item.getType().isAir()) {
         return null;
      }

      return this.deepScanCheck(item, whitelisted, readMeta(item));
   }

   String deepScanCheck(ItemStack item, boolean whitelisted, ItemMeta meta) {
      if (item == null || item.getType().isAir() || whitelisted || meta == null) {
         return null;
      }

      List<String> issues = new ArrayList<>();
      if (item.getType() == Material.CROSSBOW && meta instanceof CrossbowMeta crossbowMeta && crossbowMeta.hasChargedProjectiles()) {
         for (ItemStack proj : crossbowMeta.getChargedProjectiles()) {
            if (proj != null && this.hasAbnormalPotionEffects(proj)) {
               issues.add("弩装填物含异常药水效果");
               break;
            }
         }
      }

      if (this.hasAbnormalPotionEffects(meta)) {
         issues.add("药水效果amplifier异常");
      }

      return issues.isEmpty() ? null : String.join("; ", issues);
   }

   private static ItemMeta readMeta(ItemStack item) {
      return item != null && item.hasItemMeta() ? item.getItemMeta() : null;
   }

   private boolean hasAbnormalPotionEffects(ItemStack item) {
      return item != null && this.hasAbnormalPotionEffects(readMeta(item));
   }

   private boolean hasAbnormalPotionEffects(ItemMeta meta) {
      if (!(meta instanceof PotionMeta potionMeta) || !potionMeta.hasCustomEffects()) {
         return false;
      }

      for (PotionEffect effect : potionMeta.getCustomEffects()) {
         if (effect.getAmplifier() > this.maxPotionAmplifier(effect)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Config value is a global ceiling; vanilla effects additionally use the
    * per-effect limit so e.g. a hacked Strength V is caught while a legit
    * Turtle Master II (Slowness VI) is not.
    */
   private int maxPotionAmplifier(PotionEffect effect) {
      return Math.min(
         this.config.potionMaxPotency,
         Anti255IllegalItemDetector.getVanillaPotionMaxAmplifier(effect.getType().getKey().getKey())
      );
   }
}
