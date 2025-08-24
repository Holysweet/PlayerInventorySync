package com.holysweet.playerinventorysync;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(PlayerInventorySync.MODID)
public final class PlayerInventorySync {
    public static final String MODID = "playerinventorysync";

    public PlayerInventorySync() {
        // Instance registration because ServerResyncs keeps per-tick/per-player state in fields
        NeoForge.EVENT_BUS.register(new ServerResyncs());
    }
}
