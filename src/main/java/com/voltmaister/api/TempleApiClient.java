package com.voltmaister.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.StringReader;
import java.util.logging.Logger;

public class TempleApiClient {

    private static final Logger logger = Logger.getLogger(TempleApiClient.class.getName());
    private static final String BASE_URL = "https://templeosrs.com/api/collection-log/player_collection_log.php";
    private static Gson gson;
    private static OkHttpClient httpClient;

    // Injected from plugin
    public static void setGson(Gson injectedGson) {
        TempleApiClient.gson = injectedGson;
    }

    public static Gson getGson() {
        return gson;
    }

    public static void setHttpClient(OkHttpClient injectedClient) {
        TempleApiClient.httpClient = injectedClient;
    }

    public static String fetchLog(String username) {
        return fetchLog(username, true);
    }

    public static String fetchLogForChat(String username) {
        return fetchLog(username, false);
    }

    private static String fetchLog(String username, boolean includeNames) {
        String urlString = BASE_URL + "?player=" + username + "&categories=all" +
                (includeNames ? "&includenames=1" : "");

        try {
            Request request = new Request.Builder()
                    .url(urlString)
                    .header("User-Agent", "CollectionTrackerPlugin/1.0 RuneLite")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warning("HTTP error fetching collection log for " + username + ": " + response.code());
                    return null;
                }

                String body = response.body().string();

                if (body.contains("\"Code\":402") && body.contains("has not synced")) {
                    return "error:unsynced";
                }

                return body;
            }
        } catch (Exception e) {
            logger.severe("❌ Exception while fetching log for " + username + ": " + e.getMessage());
            return null;
        }
    }

    public static String getLastChanged(String username) {
        String urlString = "https://templeosrs.com/api/player_info.php?player=" + username + "&cloginfo=1";

        try {
            Request request = new Request.Builder()
                    .url(urlString)
                    .header("User-Agent", "CollectionTrackerPlugin/1.0 RuneLite")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warning("HTTP error fetching last_changed for " + username + ": " + response.code());
                    return null;
                }

                String body = response.body().string();

                if (body == null || body.isEmpty()) {
                    logger.warning("Empty response body when fetching last_changed for: " + username);
                    return null;
                }

                JsonReader reader = new JsonReader(new StringReader(body));
                reader.setLenient(true);

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
            }
        } catch (Exception e) {
            logger.severe("❌ Failed to get last_changed for " + username + ": " + e.getMessage());
        }
        return null;
    }
}
