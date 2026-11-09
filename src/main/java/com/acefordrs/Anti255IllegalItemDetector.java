package com.acefordrs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class Anti255IllegalItemDetector {
   private static final Set<String> BUILT_IN_ILLEGAL = Set.of(
      "DRAGON_EGG",
      "END_PORTAL_FRAME",
      "BEDROCK",
      "COMMAND_BLOCK",
      "CHAIN_COMMAND_BLOCK",
      "REPEATING_COMMAND_BLOCK",
      "COMMAND_BLOCK_MINECART",
      "STRUCTURE_BLOCK",
      "JIGSAW",
      "BARRIER",
      "LIGHT"
   );
   private static final Map<String, String> MATERIAL_ZH = new HashMap<>();
   private final Anti255Config config;
   private Set<Material> allIllegalMaterials;
   private final Anti255MaliciousNBTDetector deepDetector;
   private static final Set<String> VANILLA_EFFECTS = Set.of(
      "speed",
      "slowness",
      "haste",
      "mining_fatigue",
      "strength",
      "instant_health",
      "instant_damage",
      "jump_boost",
      "nausea",
      "regeneration",
      "resistance",
      "fire_resistance",
      "water_breathing",
      "invisibility",
      "blindness",
      "night_vision",
      "hunger",
      "weakness",
      "poison",
      "wither",
      "health_boost",
      "absorption",
      "saturation",
      "glowing",
      "levitation",
      "luck",
      "unluck",
      "slow_falling",
      "conduit_power",
      "dolphins_grace",
      "bad_omen",
      "hero_of_the_village",
      "darkness",
      "trial_omen",
      "wind_charged",
      "weaving",
      "oozing",
      "raid_omen",
      "infested",
      "breath_of_the_nautilus"
   );
   /**
    * Per-effect amplifier ceilings for vanilla potion items. The strongest
    * potion the vanilla game can create is Turtle Master II, which applies
    * Slowness VI (amplifier 5) and Resistance IV (amplifier 3). The
    * configurable potion-max-potency remains a global ceiling for unknown
    * or custom effects.
    */
   private static final Map<String, Integer> VANILLA_EFFECT_MAX_AMPLIFIER = createPotionEffectLimits();
   /**
    * Absolute ceiling for attribute amounts on whitelisted items. Legit addon
    * gear will never come close to this, but a spoofed whitelist marker (the
    * PDC namespace is player-controlled NBT) must not hide Integer.MAX_VALUE
    * attribute hacks.
    */
   private static final double HARD_MAX_ATTRIBUTE_AMOUNT = 1_000_000.0;
   /**
    * Per-attribute sanity limits for non-whitelisted items. Values are still
    * generous compared to vanilla, but +256 max health or +64 interaction
    * range are not legitimate survival items. The configurable
    * max-attribute-amount acts as an additional ceiling.
    */
   private static final Map<String, Double> ATTRIBUTE_SANE_LIMITS = createAttributeLimits();

   public static String getZhName(String materialName) {
      String zh = MATERIAL_ZH.get(materialName);
      if (zh != null) {
         return zh;
      } else if (materialName.endsWith("_SPAWN_EGG")) {
         String mob = materialName.replace("_SPAWN_EGG", "");
         return mob + "刷怪蛋";
      } else {
         return materialName;
      }
   }

   public Anti255IllegalItemDetector(Anti255Config config) {
      this.config = config;
      this.deepDetector = new Anti255MaliciousNBTDetector(config);
      this.rebuildIllegalSet();
   }

   public Anti255MaliciousNBTDetector getDeepDetector() {
      return this.deepDetector;
   }

   /**
    * Vanilla per-effect amplifier ceiling, or Integer.MAX_VALUE for effects
    * that are not part of the vanilla effect list (custom/plugin effects).
    */
   public static int getVanillaPotionMaxAmplifier(String effectKey) {
      Integer limit = VANILLA_EFFECT_MAX_AMPLIFIER.get(effectKey);
      return limit != null ? limit : Integer.MAX_VALUE;
   }

   /**
    * Vanilla amplifier ceiling for an effect, or null for custom/plugin
    * effects. Shared by the potion-item and player-effect checks.
    */
   public static Integer getVanillaEffectMaxAmplifier(String effectKey) {
      return VANILLA_EFFECT_MAX_AMPLIFIER.get(effectKey);
   }

   private int maxPotionAmplifier(String effectKey) {
      return Math.min(this.config.potionMaxPotency, getVanillaPotionMaxAmplifier(effectKey));
   }

   public void rebuildIllegalSet() {
      // Resolve names to Material once per reload. The hot path then does a
      // single enum-set lookup instead of name() + endsWith("_SPAWN_EGG").
      Set<Material> materials = new HashSet<>();
      for (String name : BUILT_IN_ILLEGAL) {
         Material material = Material.matchMaterial(name);
         if (material != null) {
            materials.add(material);
         }
      }

      for (String name : this.config.customIllegalItems) {
         Material material = Material.matchMaterial(name);
         if (material != null) {
            materials.add(material);
         }
      }

      for (Material material : Material.values()) {
         if (material.name().endsWith("_SPAWN_EGG")) {
            materials.add(material);
         }
      }

      this.allIllegalMaterials = materials;
   }

   public static String zhMaterial(Material mat) {
      return MATERIAL_ZH.getOrDefault(mat.name(), mat.name());
   }

   public static String zhMaterial(String key) {
      return MATERIAL_ZH.getOrDefault(key, key);
   }

   public boolean isIllegalMaterial(ItemStack item) {
      return item != null && !item.getType().isAir() && this.allIllegalMaterials.contains(item.getType());
   }

   public boolean isPotionType(ItemStack item) {
      if (item == null) {
         return false;
      } else {
         Material mat = item.getType();
         return mat == Material.POTION
            || mat == Material.SPLASH_POTION
            || mat == Material.LINGERING_POTION
            || mat == Material.TIPPED_ARROW;
      }
   }

   private static ItemMeta readMeta(ItemStack item) {
      return item != null && item.hasItemMeta() ? item.getItemMeta() : null;
   }

   public String detectAbnormalPotion(ItemStack item) {
      return item == null ? null : this.detectAbnormalPotion(item, readMeta(item));
   }

   private String detectAbnormalPotion(ItemStack item, ItemMeta meta) {
      if (!this.isPotionType(item)) {
         return null;
      } else if (meta instanceof PotionMeta potionMeta) {
         StringBuilder sb = new StringBuilder();
         int effectCount = 0;

         for (PotionEffect effect : potionMeta.getAllEffects()) {
            effectCount++;
            PotionEffectType type = effect.getType();
            int amplifier = effect.getAmplifier();
            int duration = effect.getDuration();
            String effectKey = type.getKey().getKey();
            if (!VANILLA_EFFECTS.contains(effectKey)) {
               if (sb.length() > 0) {
                  sb.append(", ");
               }

               sb.append("custom:").append(effectKey);
            } else {
               int maxAmplifier = this.maxPotionAmplifier(effectKey);
               if (amplifier > maxAmplifier) {
                  if (sb.length() > 0) {
                     sb.append(", ");
                  }

                  sb.append(effectKey).append(" amplifier=").append(amplifier + 1).append(" > ").append(maxAmplifier + 1);
               }

               if (duration > this.config.potionMaxDuration) {
                  if (sb.length() > 0) {
                     sb.append(", ");
                  }

                  sb.append(effectKey).append(" duration=").append(duration / 20).append("s");
               }
            }
         }

         if (effectCount > this.config.potionMaxEffects) {
            if (sb.length() > 0) {
               sb.append(", ");
            }

            sb.append("effect_count=").append(effectCount).append(" > ").append(this.config.potionMaxEffects);
         }

         return sb.length() == 0 ? null : sb.toString();
      } else {
         return null;
      }
   }

   private String detectAbnormalAttributes(ItemMeta meta, double limit, boolean useSaneLimits) {
      if (meta != null && meta.hasAttributeModifiers()) {
         StringBuilder sb = new StringBuilder();

         for (Entry<Attribute, AttributeModifier> entry : meta.getAttributeModifiers().entries()) {
            AttributeModifier mod = entry.getValue();
            if (mod != null) {
               double amount = mod.getAmount();
               double max = limit;
               String attributeKey = entry.getKey().getKey().getKey();
               if (useSaneLimits) {
                  Double sane = ATTRIBUTE_SANE_LIMITS.get(attributeKey);
                  if (sane != null) {
                     max = Math.min(max, sane);
                  }
               }

               if (Double.isNaN(amount) || Double.isInfinite(amount) || Math.abs(amount) > max) {
                  if (sb.length() > 0) {
                     sb.append(", ");
                  }

                  sb.append(zhAttr(attributeKey)).append(" ").append(fmtAmount(amount));
               }
            }
         }

         return sb.length() == 0 ? null : sb.toString();
      } else {
         return null;
      }
   }

   public Anti255IllegalItemDetector.DetectionResult detect(ItemStack item) {
      if (item == null || item.getType().isAir()) {
         return null;
      }

      ItemMeta meta = readMeta(item);
      return this.detect(item, this.deepDetector.isWhitelisted(item, meta), meta);
   }

   /**
    * Same as {@link #detect(ItemStack)} but reuses an already computed
    * whitelist result. Callers that scan many items also need the whitelist
    * flag for System 1, so passing it here avoids a second PDC/name lookup.
    */
   Anti255IllegalItemDetector.DetectionResult detect(ItemStack item, boolean whitelisted) {
      if (item == null || item.getType().isAir()) {
         return null;
      }

      return this.detect(item, whitelisted, readMeta(item));
   }

   /**
    * Full detection with a pre-read ItemMeta. This is the hot path used by
    * inventory scans; reading the meta once avoids 3-5 copies per item.
    */
   Anti255IllegalItemDetector.DetectionResult detect(ItemStack item, boolean whitelisted, ItemMeta meta) {
      if (item == null || item.getType().isAir()) {
         return null;
      }

      // Fast path for plain items: no meta means no attributes, enchants or
      // potion effects, so only the material check can match.
      if (meta == null) {
         return !whitelisted && this.isIllegalMaterial(item)
            ? new Anti255IllegalItemDetector.DetectionResult("illegal-material", item.getType().name())
            : null;
      }

      // Attributes are checked even for whitelisted items. Whitelist markers
      // are just PDC/custom-data NBT and can be spoofed, so an impossible
      // Integer.MAX_VALUE attribute must never be trusted. Whitelisted addon
      // gear only has to stay below the much higher hard ceiling.
      String abnormal = whitelisted
         ? this.detectAbnormalAttributes(meta, Math.max(this.config.maxAttributeAmount, HARD_MAX_ATTRIBUTE_AMOUNT), false)
         : this.detectAbnormalAttributes(meta, this.config.maxAttributeAmount, true);
      if (abnormal != null) {
         return new Anti255IllegalItemDetector.DetectionResult("abnormal-attribute", abnormal);
      }

      if (whitelisted) {
         return null;
      } else if (this.isIllegalMaterial(item)) {
         return new Anti255IllegalItemDetector.DetectionResult("illegal-material", item.getType().name());
      } else {
         String potionAbnormal = this.detectAbnormalPotion(item, meta);
         return potionAbnormal != null ? new Anti255IllegalItemDetector.DetectionResult("abnormal-potion", potionAbnormal) : null;
      }
   }

   /**
    * Full check of a crossbow's charged projectiles. Each projectile is
    * treated like a normal item (illegal material, attributes, potion
    * effects/count/duration) and removed when abnormal. Returns a readable
    * summary, or null when every projectile is valid.
    */
   public String scanCrossbowProjectiles(ItemStack crossbow, ItemMeta meta) {
      if (crossbow == null || crossbow.getType() != Material.CROSSBOW || !(meta instanceof CrossbowMeta crossbowMeta)) {
         return null;
      }

      if (!crossbowMeta.hasChargedProjectiles()) {
         return null;
      }

      List<ItemStack> projectiles = crossbowMeta.getChargedProjectiles();
      List<ItemStack> cleaned = new ArrayList<>(projectiles.size());
      List<String> details = new ArrayList<>();
      for (int i = 0; i < projectiles.size(); i++) {
         ItemStack projectile = projectiles.get(i);
         if (projectile == null || projectile.getType().isAir()) {
            continue;
         }

         ItemMeta projectileMeta = readMeta(projectile);
         boolean whitelisted = this.deepDetector.isWhitelisted(projectile, projectileMeta);
         Anti255IllegalItemDetector.DetectionResult result = this.detect(projectile, whitelisted, projectileMeta);
         if (result != null) {
            details.add("槽位 " + i + ": " + result.type() + " " + result.detail());
         } else {
            cleaned.add(projectile);
         }
      }

      if (details.isEmpty()) {
         return null;
      }

      crossbowMeta.setChargedProjectiles(cleaned);
      crossbow.setItemMeta(crossbowMeta);
      return String.join("; ", details);
   }

   public static ItemStack createRollingLog() {
      ItemStack item = new ItemStack(Material.OAK_LOG, 1);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         Component name = Component.text("滚木", NamedTextColor.RED, TextDecoration.BOLD)
            .append(Component.text("/Rolling Log", NamedTextColor.GRAY));
         meta.displayName(name);
         item.setItemMeta(meta);
      }

      return item;
   }

   private static String zhAttr(String attributeKey) {
      return switch (attributeKey) {
         case "max_health" -> "最大生命值";
         case "attack_damage" -> "攻击伤害";
         case "attack_speed" -> "攻击速度";
         case "armor" -> "护甲值";
         case "armor_toughness" -> "盔甲韧性";
         case "knockback_resistance" -> "击退抗性";
         case "explosion_knockback_resistance" -> "爆炸击退抗性";
         case "movement_speed" -> "移动速度";
         case "mining_efficiency" -> "挖掘效率";
         case "water_movement_efficiency" -> "水中移动效率";
         case "oxygen_bonus" -> "额外氧气";
         case "max_absorption" -> "最大伤害吸收值";
         case "block_interaction_range" -> "方块交互距离";
         case "entity_interaction_range" -> "实体交互距离";
         case "jump_strength" -> "跳跃力度";
         case "burning_time" -> "燃烧时间";
         case "camera_distance" -> "视距";
         case "waypoint_transmit_range" -> "路径点传输范围";
         case "sneaking_speed" -> "潜行速度";
         case "submerged_mining_speed" -> "水下挖掘速度";
         case "sweeping_damage_ratio" -> "横扫伤害比例";
         case "attack_knockback" -> "攻击击退";
         case "luck" -> "幸运";
         case "fall_damage_multiplier" -> "摔落伤害倍率";
         case "safe_fall_distance" -> "安全摔落距离";
         case "scale" -> "体型比例";
         case "gravity" -> "重力";
         case "step_height" -> "步高";
         default -> attributeKey;
      };
   }

   private static String fmtAmount(double amount) {
      if (Double.isNaN(amount)) {
         return "NaN";
      } else {
         return Double.isInfinite(amount) ? "∞" : String.valueOf(amount);
      }
   }

   private static Map<String, Integer> createPotionEffectLimits() {
      Map<String, Integer> limits = new HashMap<>();
      limits.put("speed", 1);
      // Turtle Master II applies Slowness VI (amplifier 5).
      limits.put("slowness", 5);
      limits.put("haste", 1);
      limits.put("mining_fatigue", 2);
      limits.put("strength", 1);
      limits.put("instant_health", 1);
      limits.put("instant_damage", 1);
      limits.put("jump_boost", 1);
      limits.put("nausea", 0);
      limits.put("regeneration", 1);
      // Turtle Master I gives Resistance III, Turtle Master II gives IV.
      limits.put("resistance", 3);
      limits.put("fire_resistance", 0);
      limits.put("water_breathing", 0);
      limits.put("invisibility", 0);
      limits.put("blindness", 0);
      limits.put("night_vision", 0);
      limits.put("hunger", 2);
      limits.put("weakness", 0);
      limits.put("poison", 1);
      limits.put("wither", 1);
      limits.put("health_boost", 3);
      limits.put("absorption", 3);
      limits.put("saturation", 0);
      limits.put("glowing", 0);
      limits.put("levitation", 0);
      limits.put("luck", 0);
      limits.put("unluck", 0);
      limits.put("slow_falling", 0);
      limits.put("conduit_power", 0);
      limits.put("dolphins_grace", 0);
      limits.put("bad_omen", 4);
      limits.put("hero_of_the_village", 4);
      limits.put("darkness", 0);
      limits.put("trial_omen", 4);
      limits.put("wind_charged", 0);
      limits.put("weaving", 0);
      limits.put("oozing", 0);
      limits.put("raid_omen", 4);
      limits.put("infested", 0);
      limits.put("breath_of_the_nautilus", 0);
      return Map.copyOf(limits);
   }

   private static Map<String, Double> createAttributeLimits() {
      Map<String, Double> limits = new HashMap<>();
      limits.put("attack_damage", 100.0);
      limits.put("attack_speed", 10.0);
      limits.put("armor", 100.0);
      limits.put("armor_toughness", 100.0);
      limits.put("knockback_resistance", 10.0);
      limits.put("explosion_knockback_resistance", 10.0);
      limits.put("max_health", 100.0);
      limits.put("movement_speed", 10.0);
      limits.put("mining_efficiency", 100.0);
      limits.put("water_movement_efficiency", 10.0);
      limits.put("oxygen_bonus", 1000.0);
      limits.put("safe_fall_distance", 1000.0);
      limits.put("max_absorption", 1000.0);
      limits.put("block_interaction_range", 16.0);
      limits.put("entity_interaction_range", 16.0);
      limits.put("jump_strength", 10.0);
      limits.put("step_height", 10.0);
      limits.put("gravity", 10.0);
      limits.put("scale", 16.0);
      limits.put("fall_damage_multiplier", 10.0);
      limits.put("luck", 100.0);
      limits.put("burning_time", 1000.0);
      limits.put("camera_distance", 100.0);
      limits.put("waypoint_transmit_range", 100000.0);
      limits.put("sneaking_speed", 10.0);
      limits.put("submerged_mining_speed", 10.0);
      limits.put("sweeping_damage_ratio", 10.0);
      limits.put("attack_knockback", 10.0);
      return Map.copyOf(limits);
   }

   static {
      MATERIAL_ZH.put("DRAGON_EGG", "龙蛋");
      MATERIAL_ZH.put("END_PORTAL_FRAME", "末地传送门框架");
      MATERIAL_ZH.put("BEDROCK", "基岩");
      MATERIAL_ZH.put("COMMAND_BLOCK", "命令方块");
      MATERIAL_ZH.put("CHAIN_COMMAND_BLOCK", "链式命令方块");
      MATERIAL_ZH.put("REPEATING_COMMAND_BLOCK", "循环命令方块");
      MATERIAL_ZH.put("COMMAND_BLOCK_MINECART", "命令方块矿车");
      MATERIAL_ZH.put("STRUCTURE_BLOCK", "结构方块");
      MATERIAL_ZH.put("JIGSAW", "拼图方块");
      MATERIAL_ZH.put("BARRIER", "屏障");
      MATERIAL_ZH.put("LIGHT", "光源方块");
      MATERIAL_ZH.put("SPAWNER", "刷怪笼");
      MATERIAL_ZH.put("PLAYER_HEAD", "玩家头颅");
      MATERIAL_ZH.put("SKELETON_SKULL", "骷髅头颅");
      MATERIAL_ZH.put("WITHER_SKELETON_SKULL", "凋灵骷髅头颅");
      MATERIAL_ZH.put("ZOMBIE_HEAD", "僵尸头颅");
      MATERIAL_ZH.put("CREEPER_HEAD", "苦力怕头颅");
      MATERIAL_ZH.put("DRAGON_HEAD", "龙首");
      MATERIAL_ZH.put("PIGLIN_HEAD", "猪灵头颅");
      MATERIAL_ZH.put("TOTEM_OF_UNDYING", "不死图腾");
      MATERIAL_ZH.put("ENCHANTED_GOLDEN_APPLE", "附魔金苹果");
      MATERIAL_ZH.put("NETHERITE_BLOCK", "下界合金块");
      MATERIAL_ZH.put("ANCIENT_DEBRIS", "远古残骸");
      MATERIAL_ZH.put("BUNDLE", "收纳袋");
      MATERIAL_ZH.put("OAK_LOG", "橡木原木");
      MATERIAL_ZH.put("STONE", "石头");
      MATERIAL_ZH.put("DIRT", "泥土");
      MATERIAL_ZH.put("GRASS_BLOCK", "草方块");
      MATERIAL_ZH.put("COBBLESTONE", "圆石");
      MATERIAL_ZH.put("DIAMOND", "钻石");
      MATERIAL_ZH.put("EMERALD", "绿宝石");
      MATERIAL_ZH.put("GOLD_INGOT", "金锭");
      MATERIAL_ZH.put("IRON_INGOT", "铁锭");
      MATERIAL_ZH.put("NETHERITE_INGOT", "下界合金锭");
      MATERIAL_ZH.put("DIAMOND_SWORD", "钻石剑");
      MATERIAL_ZH.put("DIAMOND_PICKAXE", "钻石镐");
      MATERIAL_ZH.put("DIAMOND_AXE", "钻石斧");
      MATERIAL_ZH.put("DIAMOND_SHOVEL", "钻石锹");
      MATERIAL_ZH.put("DIAMOND_HOE", "钻石锄");
      MATERIAL_ZH.put("DIAMOND_HELMET", "钻石头盔");
      MATERIAL_ZH.put("DIAMOND_CHESTPLATE", "钻石胸甲");
      MATERIAL_ZH.put("DIAMOND_LEGGINGS", "钻石护腿");
      MATERIAL_ZH.put("DIAMOND_BOOTS", "钻石靴子");
      MATERIAL_ZH.put("NETHERITE_SWORD", "下界合金剑");
      MATERIAL_ZH.put("NETHERITE_PICKAXE", "下界合金镐");
      MATERIAL_ZH.put("NETHERITE_AXE", "下界合金斧");
      MATERIAL_ZH.put("NETHERITE_SHOVEL", "下界合金锹");
      MATERIAL_ZH.put("NETHERITE_HOE", "下界合金锄");
      MATERIAL_ZH.put("NETHERITE_HELMET", "下界合金头盔");
      MATERIAL_ZH.put("NETHERITE_CHESTPLATE", "下界合金胸甲");
      MATERIAL_ZH.put("NETHERITE_LEGGINGS", "下界合金护腿");
      MATERIAL_ZH.put("NETHERITE_BOOTS", "下界合金靴子");
      MATERIAL_ZH.put("TRIDENT", "三叉戟");
      MATERIAL_ZH.put("ELYTRA", "鞘翅");
      MATERIAL_ZH.put("SHULKER_BOX", "潜影盒");
      MATERIAL_ZH.put("ENDER_CHEST", "末影箱");
      MATERIAL_ZH.put("CHEST", "箱子");
      MATERIAL_ZH.put("POTION", "药水");
      MATERIAL_ZH.put("SPLASH_POTION", "喷溅药水");
      MATERIAL_ZH.put("LINGERING_POTION", "滞留药水");
      MATERIAL_ZH.put("ENCHANTED_BOOK", "附魔书");
      MATERIAL_ZH.put("BOOK", "书");
      MATERIAL_ZH.put("WHEAT", "小麦");
      MATERIAL_ZH.put("CARROT", "胡萝卜");
      MATERIAL_ZH.put("POTATO", "马铃薯");
      MATERIAL_ZH.put("BEETROOT", "甜菜根");
      MATERIAL_ZH.put("BREAD", "面包");
      MATERIAL_ZH.put("COOKED_BEEF", "牛排");
      MATERIAL_ZH.put("COOKED_PORKCHOP", "熟猪排");
      MATERIAL_ZH.put("COOKED_MUTTON", "熟羊肉");
      MATERIAL_ZH.put("COOKED_CHICKEN", "熟鸡肉");
      MATERIAL_ZH.put("COOKED_RABBIT", "熟兔肉");
      MATERIAL_ZH.put("COOKED_COD", "熟鳕鱼");
      MATERIAL_ZH.put("COOKED_SALMON", "熟鲑鱼");
      MATERIAL_ZH.put("GOLDEN_APPLE", "金苹果");
      MATERIAL_ZH.put("GOLDEN_CARROT", "金胡萝卜");
      MATERIAL_ZH.put("ZOMBIE_SPAWN_EGG", "僵尸刷怪蛋");
      MATERIAL_ZH.put("SKELETON_SPAWN_EGG", "骷髅刷怪蛋");
      MATERIAL_ZH.put("CREEPER_SPAWN_EGG", "苦力怕刷怪蛋");
      MATERIAL_ZH.put("SPIDER_SPAWN_EGG", "蜘蛛刷怪蛋");
      MATERIAL_ZH.put("ENDERMAN_SPAWN_EGG", "末影人刷怪蛋");
      MATERIAL_ZH.put("BLAZE_SPAWN_EGG", "烈焰人刷怪蛋");
      MATERIAL_ZH.put("GHAST_SPAWN_EGG", "恶魂刷怪蛋");
      MATERIAL_ZH.put("WITHER_SKELETON_SPAWN_EGG", "凋灵骷髅刷怪蛋");
      MATERIAL_ZH.put("GUARDIAN_SPAWN_EGG", "守卫者刷怪蛋");
      MATERIAL_ZH.put("ELDER_GUARDIAN_SPAWN_EGG", "远古守卫者刷怪蛋");
      MATERIAL_ZH.put("SHULKER_SPAWN_EGG", "潜影贝刷怪蛋");
      MATERIAL_ZH.put("PIGLIN_SPAWN_EGG", "猪灵刷怪蛋");
      MATERIAL_ZH.put("ZOMBIFIED_PIGLIN_SPAWN_EGG", "僵尸猪灵刷怪蛋");
      MATERIAL_ZH.put("HOGLIN_SPAWN_EGG", "疣猪兽刷怪蛋");
      MATERIAL_ZH.put("STRIDER_SPAWN_EGG", "炽足兽刷怪蛋");
      MATERIAL_ZH.put("VEX_SPAWN_EGG", "恼鬼刷怪蛋");
      MATERIAL_ZH.put("WARDEN_SPAWN_EGG", "监守者刷怪蛋");
      MATERIAL_ZH.put("ALLAY_SPAWN_EGG", "悦灵刷怪蛋");
   }

   public record DetectionResult(String type, String detail) {
   }
}
