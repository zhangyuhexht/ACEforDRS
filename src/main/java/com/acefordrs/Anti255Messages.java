package com.acefordrs;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class Anti255Messages {
   private final Anti255Plugin plugin;
   private FileConfiguration langConfig;
   private String language = "zh";
   private final Map<String, String> cache = new HashMap<>();

   public Anti255Messages(Anti255Plugin plugin) {
      this.plugin = plugin;
   }

   public void load() {
      File file = new File(this.plugin.getDataFolder(), "messages.yml");
      if (!file.exists()) {
         this.plugin.saveResource("messages.yml", false);
      }

      this.langConfig = YamlConfiguration.loadConfiguration(file);
      try (InputStream defStream = this.plugin.getResource("messages.yml")) {
         if (defStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
            this.langConfig.setDefaults(defaults);
         }
      } catch (IOException ignored) {
      }

      // config.yml is the documented place for the language setting. Older
      // installs may still set it in messages.yml, so keep that as an
      // override when present.
      String configured = this.langConfig.getString("language");
      if (configured == null || configured.isBlank()) {
         configured = this.plugin.getConfig().getString("language", "zh");
      }
      this.language = normalizeLanguage(configured);

      this.cache.clear();
   }

   private static String normalizeLanguage(String value) {
      if (value == null) {
         return "zh";
      }

      String normalized = value.trim().toLowerCase(Locale.ROOT);
      return switch (normalized) {
         case "zh", "en", "both" -> normalized;
         default -> "zh";
      };
   }

   public String get(String key, Map<String, String> replacements) {
      String raw = this.getRaw(key);
      if (raw == null) {
         return key;
      } else {
         if (replacements != null) {
            for (Entry<String, String> entry : replacements.entrySet()) {
               raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
            }
         }

         return raw;
      }
   }

   public String get(String key) {
      return this.get(key, null);
   }

   private String getRaw(String key) {
      String cacheKey = this.language + ":" + key;
      String cached = this.cache.get(cacheKey);
      if (cached != null) {
         return cached;
      } else if (this.langConfig == null) {
         return key;
      } else {
         String zh = this.langConfig.getString(key + ".zh");
         String en = this.langConfig.getString(key + ".en");
         String var7 = this.language;

         String result = switch (var7) {
            case "zh" -> zh != null ? zh : key;
            case "en" -> en != null ? en : key;
            default -> {
               if (zh != null && en != null && !zh.equals(en)) {
                  yield zh + "\n" + en;
               } else {
                  yield zh != null ? zh : (en != null ? en : key);
               }
            }
         };
         this.cache.put(cacheKey, result);
         return result;
      }
   }

   public String getLanguage() {
      return this.language;
   }
}
