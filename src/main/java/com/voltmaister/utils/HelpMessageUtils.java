package com.voltmaister.utils;

public class HelpMessageUtils {

    public static String getHelpMessage() {
        return
                "📜 Available Chat Commands:\n\n" +
                        "!col boss\n" +
                        "→ Shows your collection for that boss (e.g. !col vorkath)\n\n" +
                        "!col boss other_player\n" +
                        "→ Shows another player's collection (e.g. !col zulrah <player name>)\n\n" +
                        "✅ Supported aliases:\n" +
                        "- toa → tombs_of_amascut\n" +
                        "- tob → theatre_of_blood\n" +
                        "- cox → chambers_of_xeric\n" +
                        "- hydra → alchemical_hydra\n" +
                        "- sire → abyssal_sire\n" +
                        "- arma → kree_arra\n" +
                        "- kril → kril_tsutsaroth\n" +
                        "- zilyana → commander_zilyana\n" +
                        "...and more!";
    }
}
