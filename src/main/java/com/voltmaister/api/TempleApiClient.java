package com.voltmaister.api;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class TempleApiClient {

    private static final Logger logger = Logger.getLogger(TempleApiClient.class.getName());
    private static final String BASE_URL = "https://templeosrs.com/api/collection-log/player_collection_log.php";

    public static String fetchLog(String username) {
        return fetchLog(username, true);
    }

    public static String fetchLogForChat(String username) {
        return fetchLog(username, false);
    }

    private static String fetchLog(String username, boolean includeNames) {
        try {
            String urlString = BASE_URL + "?player=" + username + "&categories=all" +
                    (includeNames ? "&includenames=1" : "");

            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            String response = readResponse(conn);

            // ✅ Check for TempleOSRS sync error
            if (response.contains("\"Code\":402") && response.contains("has not synced")) {
                return "error:unsynced";
            }

            return response;
        } catch (Exception e) {
            logger.severe("Error fetching log for player: " + username);
            logger.severe("Exception: " + e.getMessage());
            return null;
        }
    }

    private static String readResponse(HttpURLConnection conn) throws Exception {
        BufferedReader in;
        int status = conn.getResponseCode();

        if (status >= 400) {
            in = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
        } else {
            in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        }

        return in.lines().collect(Collectors.joining());
    }
}

