package com.acefordrs;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

public final class Anti255ArrowSpeedLimiter implements Listener {
   private final Anti255Plugin plugin;
   private final Anti255Config config;
   private final Map<UUID, UUID> arrowShooter = new ConcurrentHashMap<>();
   private static final Class<?>[] TRACKED_ARROW_CLASSES = new Class[]{Arrow.class, SpectralArrow.class};

   public Anti255ArrowSpeedLimiter(Anti255Plugin plugin, Anti255Config config) {
      this.plugin = plugin;
      this.config = config;
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onEntityShootBow(EntityShootBowEvent event) {
      if (this.config.arrowSpeedEnabled) {
         if (event.getEntity() instanceof Player player) {
            if (event.getProjectile() instanceof AbstractArrow arrow && isTrackedArrow(arrow)) {
               this.arrowShooter.put(arrow.getUniqueId(), player.getUniqueId());
               double speed = arrow.getVelocity().length();
               if (speed > this.config.arrowMaxSpeed) {
                  Vector dir = arrow.getVelocity().normalize();
                  arrow.setVelocity(dir.multiply(this.config.arrowMaxSpeed));
                  this.logAndNotify(player, "弓箭初速度 " + String.format("%.2f", speed) + " 超限，已修正为 " + this.config.arrowMaxSpeed);
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onProjectileLaunch(ProjectileLaunchEvent event) {
      if (this.config.arrowSpeedEnabled) {
         if (event.getEntity() instanceof AbstractArrow arrow && isTrackedArrow(arrow)) {
            if (!this.arrowShooter.containsKey(arrow.getUniqueId())) {
               double speed = arrow.getVelocity().length();
               if (speed > this.config.arrowMaxSpeed) {
                  Vector dir = arrow.getVelocity().normalize();
                  arrow.setVelocity(dir.multiply(this.config.arrowMaxSpeed));
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
      if (this.config.arrowSpeedEnabled) {
         if (event.getEntity() instanceof Player victim) {
            if (event.getDamager() instanceof AbstractArrow arrow && isTrackedArrow(arrow)) {
               Player shooter = this.resolveShooter(arrow);
               double damage = event.getDamage();
               if (damage > this.config.arrowMaxDamage) {
                  String shooterName = shooter != null ? shooter.getName() : "未知";
                  event.setDamage(this.config.arrowMaxDamage);
                  this.logAndNotify(victim, "收到来自 " + shooterName + " 的箭矢伤害 " + String.format("%.1f", damage) + " 超限，已削减为 " + this.config.arrowMaxDamage);
                  if (shooter != null && shooter.isOnline() && !shooter.equals(victim)) {
                     this.logAndNotify(
                        shooter, "你的箭矢对 " + victim.getName() + " 造成伤害 " + String.format("%.1f", damage) + " 超限，已削减为 " + this.config.arrowMaxDamage
                     );
                  }
               }
            }
         }
      }
   }

   public void scanFlyingArrows() {
      if (this.config.arrowSpeedEnabled) {
         for (World world : this.plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntitiesByClasses(TRACKED_ARROW_CLASSES)) {
               AbstractArrow arrow = (AbstractArrow)entity;
               double speed = arrow.getVelocity().length();
               if (speed > this.config.arrowMaxSpeed) {
                  Player shooter = this.resolveShooter(arrow);
                  arrow.remove();
                  if (shooter != null && shooter.isOnline()) {
                     this.logAndNotify(shooter, "你的箭矢在飞行中速度异常（" + String.format("%.2f", speed) + "），已被移除");
                  }

                  this.plugin
                     .getLogger()
                     .info("[ACE for DRS] 箭矢巡检: 移除速度异常箭矢 (速度=" + String.format("%.2f", speed) + ", 射手=" + (shooter != null ? shooter.getName() : "未知") + ")");
               }
            }
         }

         this.cleanUpStaleEntries();
      }
   }

   private void cleanUpStaleEntries() {
      if (!this.arrowShooter.isEmpty()) {
         this.arrowShooter.keySet().removeIf(uuid -> {
            Entity entity = this.plugin.getServer().getEntity(uuid);
            return entity == null || !entity.isValid() || entity.isDead();
         });
      }
   }

   private static boolean isTrackedArrow(Entity entity) {
      // SpectralArrow extends AbstractArrow, not Arrow. Restricting the
      // checks to Arrow let every spectral arrow bypass this limiter.
      return entity instanceof Arrow || entity instanceof SpectralArrow;
   }

   private Player resolveShooter(AbstractArrow arrow) {
      UUID known = this.arrowShooter.get(arrow.getUniqueId());
      if (known != null) {
         Player player = this.plugin.getServer().getPlayer(known);
         if (player != null) {
            return player;
         }
      }

      ProjectileSource source = arrow.getShooter();
      return source instanceof Player player ? player : null;
   }

   private void logAndNotify(Player player, String message) {
      String msg = this.plugin.getMessages().get("arrow-speed-alert", Map.of("player", player.getName(), "detail", message));
      player.sendMessage(msg);
      this.plugin.getLogger().info("[ACE for DRS] 箭矢限速: " + player.getName() + " - " + message);
   }
}
