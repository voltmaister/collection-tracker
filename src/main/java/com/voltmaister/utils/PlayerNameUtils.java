package com.voltmaister.utils;

public class PlayerNameUtils {

    // Normalize player name by removing Ironman prefixes and formatting
    public static String normalizePlayerName(String playerName) {
        String normalizedName = playerName.trim();

        // Remove known Ironman prefixes
        String[] ironmanPrefixes = {"Ultimate Ironman", "Hardcore Ironman", "Ironman"};
        for (String prefix : ironmanPrefixes) {
            if (normalizedName.startsWith(prefix)) {
                normalizedName = normalizedName.replaceFirst(prefix, "").trim();
                break; // No need to check other prefixes once one is removed
            }
        }

        // Remove any <img=xxx> tags from the player name
        normalizedName = normalizedName.replaceAll("<img=\\d+>", "").trim();

        // Replace spaces with underscores
        normalizedName = normalizedName.replace(' ', '_');

        return normalizedName.toLowerCase();
    }
}
