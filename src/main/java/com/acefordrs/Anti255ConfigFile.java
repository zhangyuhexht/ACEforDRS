package com.acefordrs;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

/**
 * Rewrites plugins/ACEforDRS/config.yml so that every key shipped in the jar's
 * default config.yml exists. Existing values and comments are kept; missing
 * top-level sections and nested keys are inserted at their default position.
 * Obsolete exempt keys are removed. The previous file is backed up.
 */
final class Anti255ConfigFile {
   private Anti255ConfigFile() {
   }

   static void update(Anti255Plugin plugin) {
      File configFile = new File(plugin.getDataFolder(), "config.yml");
      if (!configFile.isFile()) {
         plugin.saveDefaultConfig();
         return;
      }

      List<String> defaults;
      try (InputStream in = plugin.getResource("config.yml")) {
         if (in == null) {
            return;
         }
         defaults = readAllLines(in);
      } catch (IOException e) {
         plugin.getLogger().log(Level.WARNING, "[ACE for DRS] 读取内置 config.yml 失败，跳过配置补写", e);
         return;
      }

      List<String> userLines;
      try {
         userLines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
      } catch (IOException e) {
         plugin.getLogger().log(Level.WARNING, "[ACE for DRS] 读取 config.yml 失败，跳过配置补写", e);
         return;
      }

      if (!userLines.isEmpty() && userLines.get(0).startsWith("\uFEFF")) {
         userLines.set(0, userLines.get(0).substring(1));
      }

      boolean removedObsolete = removeConfigBlock(userLines, "exempt-permission");
      removedObsolete |= removeConfigBlock(userLines, "exempt-players");

      Map<Integer, List<String>> inserts = new TreeMap<>();
      collectMissingConfigBlocks(defaults, 0, defaults.size(), userLines, 0, userLines.size(), 0, true, inserts);
      if (inserts.isEmpty() && !removedObsolete) {
         return;
      }

      File backup = new File(plugin.getDataFolder(), "config.yml.bak");
      try {
         Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException e) {
         plugin.getLogger().log(Level.WARNING, "[ACE for DRS] 备份旧 config.yml 失败，继续补写", e);
      }

      List<Integer> indices = new ArrayList<>(inserts.keySet());
      indices.sort(Comparator.reverseOrder());
      for (int index : indices) {
         List<String> block = new ArrayList<>(inserts.get(index));
         int at = Math.min(index, userLines.size());
         if (at >= userLines.size() && !userLines.isEmpty() && !userLines.get(userLines.size() - 1).isBlank()) {
            block.add(0, "");
         }
         userLines.addAll(at, block);
      }

      try {
         Files.write(configFile.toPath(), userLines, StandardCharsets.UTF_8);
         if (removedObsolete) {
            plugin.getLogger().info("[ACE for DRS] config.yml 已移除废弃的豁免配置并补写缺失项，旧文件备份为 config.yml.bak");
         } else {
            plugin.getLogger().info("[ACE for DRS] config.yml 已补写缺失配置项，旧文件备份为 config.yml.bak");
         }
      } catch (IOException e) {
         plugin.getLogger().log(Level.WARNING, "[ACE for DRS] 写入 config.yml 失败", e);
      }
   }

   private static void collectMissingConfigBlocks(
      List<String> defaults,
      int defStart,
      int defEnd,
      List<String> user,
      int userStart,
      int userEnd,
      int indent,
      boolean includeComments,
      Map<Integer, List<String>> inserts
   ) {
      List<int[]> defBlocks = childBlocks(defaults, defStart, defEnd, indent);
      for (int index = 0; index < defBlocks.size(); index++) {
         int[] defBlock = defBlocks.get(index);
         String key = configKey(defaults.get(defBlock[0]));
         if (key == null) {
            continue;
         }

         int[] userBlock = findBlock(user, userStart, userEnd, indent, key);
         if (userBlock == null) {
            int from = defBlock[0];
            if (includeComments) {
               while (from > defStart && isCommentLine(defaults.get(from - 1))) {
                  from--;
               }
            }

            // Trailing blank lines and comments belong to the next sibling
            // key, so they must not travel with the copied block.
            int to = trimTail(defaults, from, defBlock[1]);

            // Insert before the first following default sibling that already
            // exists in the user file. This keeps missing keys in their
            // original position instead of appending them after unrelated
            // comments at the end of the section.
            int insertAt = findNextInsertIndex(defaults, defBlocks, index, user, userStart, userEnd, indent);
            List<String> block = inserts.computeIfAbsent(insertAt, k -> new ArrayList<>());
            for (int i = from; i < to; i++) {
               block.add(defaults.get(i));
            }
         } else {
            int childIndent = detectChildIndent(defaults, defBlock[0] + 1, defBlock[1], indent);
            if (childIndent > indent) {
               collectMissingConfigBlocks(
                  defaults, defBlock[0] + 1, defBlock[1], user, userBlock[0] + 1, userBlock[1], childIndent, true, inserts
               );
            }
         }
      }
   }

   private static int findNextInsertIndex(
      List<String> defaults,
      List<int[]> defBlocks,
      int currentIndex,
      List<String> user,
      int userStart,
      int userEnd,
      int indent
   ) {
      for (int i = currentIndex + 1; i < defBlocks.size(); i++) {
         String nextKey = configKey(defaults.get(defBlocks.get(i)[0]));
         int[] nextUser = findBlock(user, userStart, userEnd, indent, nextKey);
         if (nextUser != null) {
            return commentStart(user, userStart, nextUser[0]);
         }
      }

      return trimTail(user, userStart, userEnd);
   }

   private static int commentStart(List<String> lines, int start, int keyLine) {
      int from = keyLine;
      while (from > start && isCommentLine(lines.get(from - 1))) {
         from--;
      }
      return from;
   }

   private static boolean removeConfigBlock(List<String> lines, String key) {
      boolean removed = false;
      while (true) {
         int[] block = findBlock(lines, 0, lines.size(), 0, key);
         if (block == null) {
            return removed;
         }

         int from = block[0];
         while (from > 0 && isCommentLine(lines.get(from - 1))) {
            from--;
         }

         int to = trimTail(lines, from, block[1]);
         lines.subList(from, to).clear();
         removed = true;
      }
   }

   private static List<int[]> childBlocks(List<String> lines, int start, int end, int indent) {
      List<int[]> blocks = new ArrayList<>();
      int i = Math.max(0, start);
      int limit = Math.min(end, lines.size());
      while (i < limit) {
         if (configKey(lines.get(i)) != null && indentOf(lines.get(i)) == indent) {
            int j = i + 1;
            while (j < limit) {
               if (configKey(lines.get(j)) != null && indentOf(lines.get(j)) <= indent) {
                  break;
               }
               j++;
            }
            blocks.add(new int[]{i, j});
            i = j;
         } else {
            i++;
         }
      }
      return blocks;
   }

   private static int[] findBlock(List<String> lines, int start, int end, int indent, String key) {
      for (int[] block : childBlocks(lines, start, end, indent)) {
         if (key.equals(configKey(lines.get(block[0])))) {
            return block;
         }
      }
      return null;
   }

   private static int detectChildIndent(List<String> lines, int start, int end, int parentIndent) {
      for (int i = start; i < end && i < lines.size(); i++) {
         if (configKey(lines.get(i)) != null) {
            int ind = indentOf(lines.get(i));
            if (ind > parentIndent) {
               return ind;
            }
         }
      }
      return -1;
   }

   private static String configKey(String line) {
      if (line == null) {
         return null;
      }

      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("-")) {
         return null;
      }

      int colon = trimmed.indexOf(':');
      return colon <= 0 ? null : trimmed.substring(0, colon).trim();
   }

   private static int indentOf(String line) {
      int i = 0;
      while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
         i++;
      }
      return i;
   }

   private static boolean isCommentLine(String line) {
      return line != null && line.trim().startsWith("#");
   }

   private static int trimTail(List<String> lines, int start, int end) {
      int to = Math.min(end, lines.size());
      while (to > start) {
         String line = lines.get(to - 1);
         if (line.isBlank() || isCommentLine(line)) {
            to--;
         } else {
            break;
         }
      }
      return to;
   }

   private static List<String> readAllLines(InputStream in) throws IOException {
      List<String> lines = new ArrayList<>();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
         String line;
         while ((line = reader.readLine()) != null) {
            lines.add(line);
         }
      }
      return lines;
   }
}
