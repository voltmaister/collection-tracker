package com.voltmaister.data;

import java.sql.Timestamp;

public class CollectionItem {
    private final String category;
    private final int itemId;
    private int count;
    private final Timestamp date;
    private final String name;

    public void setCount(int count) {
        this.count = count;
    }


    public CollectionItem(String category, int itemId,String name, int count, Timestamp date) {

        this.category = category;
        this.itemId = itemId;
        this.name = name;
        this.count = count;
        this.date = date;
    }

    public String getName() { return name; }
    public String getCategory() { return category; }
    public int getItemId() { return itemId; }
    public int getCount() { return count; }
    public Timestamp getDate() { return date; }
}