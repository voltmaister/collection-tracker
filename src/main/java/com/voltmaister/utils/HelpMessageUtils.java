package com.voltmaister.utils;

import java.util.*;

public class HelpMessageUtils {

    public static String getHelpMessage() {
        Map<String, String> aliases = CategoryAliases.CATEGORY_ALIASES;

        // Group and sort aliases by their target categories
        Map<String, List<String>> grouped = new TreeMap<>();
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            grouped.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        StringBuilder message = new StringBuilder();
        message.append("📜 Available Chat Commands:\n\n")
                .append("!col boss\n")
                .append("→ Shows your collection for that boss (e.g. !col vorkath)\n\n")
                .append("!col boss_name or mini_game\n")
                .append("→ Shows your collection for that boss (e.g. !col fishing_trawler)\n\n")
                .append("!col boss other player\n")
                .append("→ Shows another player's collection (e.g. !col zulrah <player name>)\n\n")
                .append("✅ Supported aliases:\n");

        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            List<String> keys = entry.getValue();
            Collections.sort(keys);
            for (String alias : keys) {
                message.append(String.format("%-2s → %s\n", alias, entry.getKey()));
            }
        }

        return message.toString();
    }
}
