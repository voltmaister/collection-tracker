package com.voltmaister.services;

import com.voltmaister.api.TempleApiClient;
import com.voltmaister.parser.CollectionParser;
import com.voltmaister.utils.PlayerNameUtils;
import com.voltmaister.db.CollectionDatabase;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

import javax.swing.*;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Slf4j
public class CollectionLogSyncService {

    public static void syncCollectionLog(Client client, Consumer<String> panelLogger) {
        Executors.newSingleThreadExecutor().execute(() -> {
            log.debug("🔄 Starting syncCollectionLog()...");

            CollectionDatabase.init();
            CollectionDatabase.clearAll();

            if (client.getLocalPlayer() == null) {
                log.warn("⚠️ Local player is null — not logged in yet.");
                SwingUtilities.invokeLater(() ->
                        panelLogger.accept("⚠️ Cannot sync — you're not logged in yet.")
                );
                return;
            }

            String username = client.getLocalPlayer().getName().toLowerCase();
            log.debug("👤 Detected username: {}", username);

            SwingUtilities.invokeLater(() ->
                    panelLogger.accept("📡 Fetching collection log for " + username + "...")
            );

            String json = TempleApiClient.fetchLog(username);
            log.debug("📥 Fetched JSON: {} characters", json != null ? json.length() : 0);

            if (json == null || json.isEmpty()) {
                log.error("❌ Empty or null response from Temple API");
                SwingUtilities.invokeLater(() ->
                        panelLogger.accept("❌ Failed to fetch collection log for " + username)
                );
                return;
            }

            log.debug("🧩 Parsing and storing JSON...");
            CollectionParser parser = new CollectionParser(TempleApiClient.getGson());

            parser.parseAndStore(PlayerNameUtils.normalizePlayerName(username), json);
            log.debug("✅ Parsing complete.");

            SwingUtilities.invokeLater(() ->
                    panelLogger.accept("✅ Successfully synced collection log for " + username)
            );
        });
    }
}
