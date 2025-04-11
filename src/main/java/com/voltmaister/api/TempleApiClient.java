package com.voltmaister.api;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.stream.Collectors;

public class TempleApiClient {

    // Common base URL
    private static final String BASE_URL = "https://templeosrs.com/api/collection-log/player_collection_log.php";

    // Original method (used for initial sync, includes item names)
    public static String fetchLog(String username) {
        return fetchLog(username, true); // always includes item names
    }

    // New method (used specifically for chat command responses)
    public static String fetchLogForChat(String username) {
        return fetchLog(username, false); // can exclude item names if desired
    }

    // Private helper to avoid repeating HTTP logic
    private static String fetchLog(String username, boolean includeNames) {
        try {
            // URL constructed dynamically based on the includeNames parameter
            String urlString = BASE_URL + "?player=" + username + "&categories=all" +
                    (includeNames ? "&includenames=1" : "");

            URL url = new URL(urlString);

            // HTTP connection setup
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            // Read the API response
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                return in.lines().collect(Collectors.joining());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null; // handle exceptions gracefully
        }
    }
}
