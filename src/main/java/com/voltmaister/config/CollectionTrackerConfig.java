package com.voltmaister.config;

import net.runelite.client.config.*;

@ConfigGroup("collectiontracker")
public interface CollectionTrackerConfig extends Config
{
    @Range(min = 50, max = 200)
    @ConfigItem(
            keyName = "maxCachedPlayers",
            name = "Max Cached Players",
            description = "Maximum number of players to keep in the database (excluding yourself)." +
                    " The more players the more MB kept on database. " +
                    "Default number of players in database is 50"
    )
    default int maxCachedPlayers() { return 50; }
}
