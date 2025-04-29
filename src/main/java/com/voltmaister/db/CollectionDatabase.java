package com.voltmaister.db;

import com.voltmaister.data.CollectionItem;
import com.voltmaister.data.CollectionResponse;

import net.runelite.client.RuneLite;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CollectionDatabase {
    private static final Logger log = LoggerFactory.getLogger(CollectionDatabase.class);

    private static final String DB_URL = "jdbc:h2:file:" + RuneLite.RUNELITE_DIR + "/collection-tracker/runelite-collections;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1";

    static {
        File pluginDir = new File(RuneLite.RUNELITE_DIR, "collection-tracker");
        if (!pluginDir.exists()) {
            if (!pluginDir.mkdirs()) {
                log.warn("⚠️ Failed to create plugin directory at {}", pluginDir.getAbsolutePath());
            }
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public static void init() {
        try {
            // 🚨 Required for Plugin Hub: explicitly load the H2 JDBC driver
            Class.forName("org.h2.Driver");

            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS collection_log(" +
                        "id IDENTITY PRIMARY KEY, " +
                        "category VARCHAR(255), " +
                        "item_id INT, " +
                        "item_count INT, " +
                        "item_name VARCHAR(255)," +
                        "collected_date TIMESTAMP" +
                        ")");

                addColumnIfNotExists(conn, "collection_log", "player_name", "VARCHAR(255)");
                addColumnIfNotExists(conn, "collection_log", "last_accessed", "TIMESTAMP");
            }
        } catch (ClassNotFoundException e) {
            log.warn("H2 Driver class not found: " + e.getMessage());
        } catch (SQLException e) {
            log.warn("Database initialization failed: " + e.getMessage());
        }
    }


    private static void addColumnIfNotExists(Connection conn, String table, String column, String type) throws SQLException {
        String checkQuery = "SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(checkQuery)) {
            ps.setString(1, table.toUpperCase());
            ps.setString(2, column.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
                    }
                }
            }
        }
    }

    public static boolean hasPlayerData(String playerName) {
        String sql = "SELECT 1 FROM collection_log WHERE player_name = ? LIMIT 1";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerName);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            log.warn("Error checking player data: " + e.getMessage());
            return false;
        }
    }

    public static void insertItemsBatch(String playerName, String category, List<CollectionResponse.ItemEntry> items) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO collection_log (player_name, category, item_id, item_name, item_count, collected_date, last_accessed) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                for (CollectionResponse.ItemEntry item : items) {
                    ps.setString(1, playerName);
                    ps.setString(2, category);
                    ps.setInt(3, item.id);
                    ps.setString(4, item.name);
                    ps.setInt(5, item.count);
                    ps.setTimestamp(6, Timestamp.valueOf(item.date));
                    ps.setTimestamp(7, new Timestamp(System.currentTimeMillis()));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            log.warn("Error inserting items batch: " + e.getMessage());
        }
    }

    public static void insertItem(String playerName, String category, int itemId, String itemName, int count, String date) {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO collection_log (player_name, category, item_id, item_name, item_count, collected_date, last_accessed) VALUES(?,?,?,?,?,?,?)")) {
            ps.setString(1, playerName);
            ps.setString(2, category);
            ps.setInt(3, itemId);
            ps.setString(4, itemName);
            ps.setInt(5, count);
            ps.setTimestamp(6, Timestamp.valueOf(date));
            ps.setTimestamp(7, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("Error inserting item: " + e.getMessage());
        }
    }

    public static List<CollectionItem> getAllItems(String playerName) {
        List<CollectionItem> items = new ArrayList<>();

        String sql = "SELECT category, item_id, item_name, item_count, collected_date FROM collection_log WHERE player_name = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerName.toLowerCase());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String category = rs.getString("category");
                int itemId = rs.getInt("item_id");
                String name = rs.getString("item_name");
                int count = rs.getInt("item_count");
                Timestamp date = rs.getTimestamp("collected_date");

                items.add(new CollectionItem(category, itemId, name, count, date));
            }
        } catch (SQLException e) {
            log.warn("Error fetching items for player: " + e.getMessage());
        }

        return items;
    }

    public static Timestamp getLatestTimestamp(String playerName) {
        String sql = "SELECT MAX(collected_date) FROM collection_log WHERE player_name = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerName.toLowerCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getTimestamp(1);
            }
        } catch (SQLException e) {
            log.warn("Error fetching latest timestamp: " + e.getMessage());
        }
        return null;
    }


    public static void clearAll() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM collection_log");
        } catch (SQLException e) {
            log.warn("Error clearing all items: " + e.getMessage());
        }
    }

    public static List<CollectionItem> getItemsByCategory(String playerName, String category) {
        List<CollectionItem> items = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT item_id, item_name, item_count, collected_date FROM collection_log WHERE category = ? AND player_name = ?")) {
            ps.setString(1, category);
            ps.setString(2, playerName.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int itemId = rs.getInt("item_id");
                    String name = rs.getString("item_name");
                    int count = rs.getInt("item_count");
                    Timestamp date = rs.getTimestamp("collected_date");

                    items.add(new CollectionItem(category, itemId, name, count, date));
                }
            }
        } catch (SQLException e) {
            log.warn("Error fetching items by category: " + e.getMessage());
        }

        return items;
    }

    public static void pruneOldPlayers(String yourUsername, int maxPlayers) {
        try (Connection conn = getConnection();
             PreparedStatement ps1 = conn.prepareStatement(
                     "SELECT player_name, MIN(last_accessed) as oldest " +
                             "FROM collection_log " +
                             "WHERE player_name != ? " +
                             "GROUP BY player_name " +
                             "ORDER BY oldest ASC"
             );
             PreparedStatement deleteStmt = conn.prepareStatement(
                     "DELETE FROM collection_log WHERE player_name = ?"
             )
        ) {
            ps1.setString(1, yourUsername);
            ResultSet rs = ps1.executeQuery();

            int count = 0;
            List<String> playersToRemove = new ArrayList<>();

            while (rs.next()) {
                count++;
                if (count > maxPlayers) {
                    playersToRemove.add(rs.getString("player_name"));
                }
            }

            for (String name : playersToRemove) {
                log.debug("🧹 Pruning cached player: " + name);
                deleteStmt.setString(1, name);
                deleteStmt.executeUpdate();
            }
        } catch (SQLException e) {
            log.warn("Error pruning old players: " + e.getMessage());
        }
    }
}
