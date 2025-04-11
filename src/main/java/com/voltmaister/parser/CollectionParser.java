package com.voltmaister.parser;

import com.google.gson.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import com.voltmaister.data.CollectionResponse;
import com.voltmaister.db.CollectionDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CollectionParser {

    private static final Logger log = LoggerFactory.getLogger(CollectionParser.class);
    private final Gson gson = new Gson();

    public void parseAndStore(String json) {
        log.info("🧩 Starting parseAndStore()...");

        JsonObject data = new JsonParser()
                .parse(json)
                .getAsJsonObject()
                .getAsJsonObject("data");

        JsonObject items = data.getAsJsonObject("items");

        int categoryCount = 0;
        int itemCount = 0;

        for (Map.Entry<String, JsonElement> category : items.entrySet()) {
            String categoryName = category.getKey();
            JsonArray itemArray = category.getValue().getAsJsonArray();

            log.info("📂 Parsing category: {} ({} items)", categoryName, itemArray.size());
            categoryCount++;

            List<CollectionResponse.ItemEntry> entryList = new ArrayList<>();

            for (JsonElement e : itemArray) {
                CollectionResponse.ItemEntry item = gson.fromJson(e, CollectionResponse.ItemEntry.class);
                log.debug("↪ Queuing: [{}] {} x{} @ {}", categoryName, item.name, item.count, item.date);
                entryList.add(item);
                itemCount++;
            }

            // ✅ Perform batch insert for the whole category
            CollectionDatabase.insertItemsBatch(categoryName, entryList);
        }

        log.info("✅ Parsed {} categories and inserted {} items total.", categoryCount, itemCount);

        // ✅ Manually shut down the database after insert
        try (Connection conn = CollectionDatabase.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SHUTDOWN");
            log.info("🛑 Manually closed H2 database after sync.");
        } catch (SQLException e) {
            log.error("⚠️ Error while trying to shut down the database", e);
        }
    }
}
