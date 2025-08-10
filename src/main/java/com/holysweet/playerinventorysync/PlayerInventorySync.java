package com.holysweet.playerinventorysync;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(PlayerInventorySync.MOD_ID)
public class PlayerInventorySync {
    public static final String MOD_ID = "playerinventorysync";

    public PlayerInventorySync(IEventBus modBus) {
        modBus.addListener(this::commonSetup);
        Config.init(); // no path needed
        NeoForge.EVENT_BUS.register(new ServerResyncs());
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // No-op
    }
}
