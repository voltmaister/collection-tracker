package com.voltmaister.utils;

public class PlayerNameUtils {

    // Normalize player name by removing Ironman prefixes and formatting
    public static String normalizePlayerName(String playerName) {
        if (playerName == null) return "";

        // Replace non-breaking space and other unusual whitespace with normal space
        String normalizedName = playerName.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        // Remove known Ironman prefixes
        String[] ironmanPrefixes = {"Ultimate Ironman", "Hardcore Ironman", "Ironman"};
        for (String prefix : ironmanPrefixes) {
            if (normalizedName.startsWith(prefix)) {
                normalizedName = normalizedName.replaceFirst(prefix, "").trim();
                break;
            }
        }

        // Remove <img=xx> tags
        normalizedName = normalizedName.replaceAll("<img=\\d+>", "").trim();

        // Replace spaces with underscores and lowercase
        normalizedName = normalizedName.replace(' ', '_').toLowerCase();

        return normalizedName;
    }

}
