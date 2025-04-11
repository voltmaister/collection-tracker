package com.voltmaister.data;

import java.util.List;
import java.util.Map;

public class CollectionResponse {
    public Map<String, List<ItemEntry>> items;

    public static class ItemEntry {
        public int id;
        public int count;
        public String date;
        public String name;
    }
}
