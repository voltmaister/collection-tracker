package com.voltmaister.data;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
public class CollectionItem {
    private String category;
    private int itemId;
    private String name;
    private int count;
    private Timestamp date;
}
