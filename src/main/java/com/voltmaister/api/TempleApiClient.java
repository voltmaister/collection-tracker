package com.voltmaister.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.Gson;
import lombok.Getter;
import lombok.Setter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.StringReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TempleApiClient {

    private static final Logger log = LoggerFactory.getLogger(TempleApiClient.class);
    private static final String BASE_URL = "https://templeosrs.com/api/collection-log/player_collection_log.php";

    @Getter @Setter
    private static Gson gson;

    @Setter
    private static OkHttpClient httpClient;

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
                    log.warn("HTTP error fetching collection log for " + username + ": " + response.code());
                    return null;
                }

                String body = response.body().string();

                if (body.contains("\"Code\":402") && body.contains("has not synced")) {
                    return "error:unsynced";
                }

                return body;
            }
        } catch (Exception e) {
            log.error("❌ Exception while fetching log for " + username + ": " + e.getMessage());
            return null;
        }
    }

    public static String getLastChanged(String username) {
        String urlString = "https://templeosrs.com/api/player_info.php?player=" + username + "&cloginfo=1";

        try {
            Request request = new Request.Builder()
                    .url(urlString)
                    .header("User-Agent", "CollectionTrackerPlugin/1.1 RuneLite")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("HTTP error fetching last_changed for " + username + ": " + response.code());
                    return null;
                }

                String body = response.body().string();

                if (body == null || body.isEmpty()) {
                    log.warn("Empty response body when fetching last_changed for: " + username);
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
            log.error("❌ Failed to get last_changed for " + username + ": " + e.getMessage());
        }
        return null;
    }
}
