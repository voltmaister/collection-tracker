package com.voltmaister.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.Gson;

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
    private static Gson gson;

    // ✅ Fixed method
    public static void setGson(Gson injectedGson) {
        TempleApiClient.gson = injectedGson;
    }

    public static Gson getGson() {
        return gson;
    }

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
            conn.setRequestProperty("User-Agent", "CollectionTrackerPlugin/1.0 RuneLite");

            String response = readResponse(conn);

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
            String urlString = "https://templeosrs.com/api/player_info.php?player=" + username + "&cloginfo=1";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "CollectionTrackerPlugin/1.0 RuneLite");

            String response = readResponse(conn);

            if (response == null || response.isEmpty()) {
                logger.warning("Empty last_changed response for: " + username);
                return null;
            }

            JsonReader reader = new JsonReader(new StringReader(response));
            reader.setLenient(true);

            // ✅ Use injected Gson instead of deprecated JsonParser
            JsonElement element = gson.fromJson(reader, JsonElement.class);

            if (element.isJsonObject()) {
                JsonObject root = element.getAsJsonObject();

                if (root.has("data")) {
                    JsonObject data = root.getAsJsonObject("data");

                    if (data.has("collection_log")) {
                        JsonObject collectionLog = data.getAsJsonObject("collection_log");

                        if (collectionLog.has("last_changed")) {
                            return collectionLog.get("last_changed").getAsString();
                        }
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
