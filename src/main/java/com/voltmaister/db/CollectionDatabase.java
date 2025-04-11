package com.voltmaister.db;

import com.voltmaister.data.CollectionItem;
import com.voltmaister.data.CollectionResponse;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CollectionDatabase {

    private static final String DB_URL = "jdbc:h2:file:./runelite-collections;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1";

    public static Connection getConnection() throws SQLException {

        return DriverManager.getConnection(DB_URL, "sa", "");
    }

    public static void init(){

        try(Connection conn = getConnection(); Statement stmt = conn.createStatement()){
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS collection_log(" +
                    "id IDENTITY PRIMARY KEY, " +
                    "category VARCHAR(255), " +
                    "item_id INT, " +
                    "item_count INT, " +
                    "item_name VARCHAR(255)," +
                    "collected_date TIMESTAMP" +
                    ")");
        }
        catch(SQLException e){
            e.printStackTrace();
        }
    }

    public static void insertItemsBatch(String category, java.util.List<CollectionResponse.ItemEntry> items) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // batch = no auto-commit

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO collection_log (category, item_id, item_name, item_count, collected_date) VALUES (?, ?, ?, ?, ?)")) {

                for (CollectionResponse.ItemEntry item : items) {
                    ps.setString(1, category);
                    ps.setInt(2, item.id);
                    ps.setString(3, item.name);
                    ps.setInt(4, item.count);
                    ps.setTimestamp(5, java.sql.Timestamp.valueOf(item.date));
                    ps.addBatch();
                }

                ps.executeBatch();
            }

            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public static void insertItem(String category, int itemId,String itemName, int count, String date){

        try(Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO collection_log (category, item_id, item_name, item_count, collected_date) VALUES(?,?,?,?,?)"
        )){
            ps.setString(1, category);
            ps.setInt(2, itemId);
            ps.setString(3, itemName);
            ps.setInt(4, count);
            ps.setTimestamp(5, Timestamp.valueOf(date));
            ps.executeUpdate();
        }
        catch (SQLException e){
            e.printStackTrace();
        }
    }

    public static List<CollectionItem> getAllItems() {
        List<CollectionItem> items = new ArrayList<>();

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT category, item_id, item_name, item_count, collected_date FROM collection_log")) {

            while (rs.next()) {
                String category = rs.getString("category");
                int itemId = rs.getInt("item_id");
                String name = rs.getString("item_name");
                int count = rs.getInt("item_count");
                Timestamp date = rs.getTimestamp("collected_date");

                items.add(new CollectionItem(category, itemId, name, count, date));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return items;
    }

    public static void clearAll() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM collection_log");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public static List<CollectionItem> getItemsByCategory(String category) {
        List<CollectionItem> items = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT item_id, item_name, item_count, collected_date FROM collection_log WHERE category = ?")) {

            ps.setString(1, category);

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
            e.printStackTrace();
        }

        return items;
    }

}
