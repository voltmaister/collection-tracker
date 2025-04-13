package com.voltmaister.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
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

    public static String getLastChanged(String username) {
        try {
            String urlString = "https://templeosrs.com/api/player_info.php?player=" + username;
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            String response = readResponse(conn);


            if (response == null || response.isEmpty()) {
                logger.warning("Empty last_changed response for: " + username);
                return null;
            }

            // 👇 Use JsonReader in lenient mode
            JsonReader reader = new JsonReader(new StringReader(response));
            reader.setLenient(true);

            JsonElement element = new JsonParser().parse(reader);
            if (element.isJsonObject()) {
                JsonObject root = element.getAsJsonObject();

                if (root.has("data")) {
                    JsonObject data = root.getAsJsonObject("data");

                    if (data.has("Last changed")) {
                        String lastChanged = data.get("Last changed").getAsString();
                        return lastChanged;
                    }
                }
            }
        } catch (Exception e) {
            logger.severe("❌ Failed to get last_changed for " + username + ": " + e.getMessage());
        }
        return null;
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

