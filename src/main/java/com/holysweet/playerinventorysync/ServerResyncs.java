package com.holysweet.playerinventorysync;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.UUID;

/**
 * Server-side only: immediately resends authoritative container + carried stacks
 * when interactions FAIL (canceled), plus a tiny watchdog follow-up.
 * Clean & surgical: no resync on successful actions.
 */
final class ServerResyncs {

    private final Object2IntOpenHashMap<UUID> suspectUntilTick = new Object2IntOpenHashMap<>();
    private final Object2IntOpenHashMap<UUID> resyncsThisTick = new Object2IntOpenHashMap<>();

    /** Push server-truth to the client: full container content + carried stack. */
    private void resyncNow(ServerPlayer sp) {
        int count = resyncsThisTick.getOrDefault(sp.getUUID(), 0);
        if (count >= Config.MAX_RESYNCS_PER_TICK.get()) return;

        AbstractContainerMenu menu = sp.containerMenu; // always non-null (player inv if none open)
        int stateId = menu.incrementStateId();
        sp.connection.send(new ClientboundContainerSetContentPacket(
                menu.containerId,
                stateId,
                menu.getItems(),
                menu.getCarried()
        ));

        resyncsThisTick.put(sp.getUUID(), count + 1);
    }

    private void markSuspect(ServerPlayer sp, int currentTick) {
        if (!Config.ENABLE_WATCHDOG_RESYNC.get()) return;
        int until = currentTick + Config.WATCHDOG_TICKS.get();
        suspectUntilTick.put(sp.getUUID(), until);
    }

    // --- TICK: rate-limit reset + watchdog follow-up ---------------------------------------------

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post evt) {
        if (!(evt.getEntity() instanceof ServerPlayer sp)) return;
        UUID id = sp.getUUID();
        resyncsThisTick.removeInt(id);

        int now = sp.server.getTickCount();
        int until = suspectUntilTick.getOrDefault(id, -1);
        if (until >= 0 && now >= until) {
            resyncNow(sp);
            suspectUntilTick.removeInt(id);
        }
    }

    // --- WORLD / PLACEMENT: only resync on FAILED (canceled) interactions ------------------------

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock evt) {
        if (!(evt.getEntity() instanceof ServerPlayer sp)) return;
        if (evt.isCanceled()) {
            resyncNow(sp);
            markSuspect(sp, sp.server.getTickCount());
        }
    }

    // WORLD / ITEM USE IN AIR (buckets, eggs, etc.): only on FAIL (canceled)
    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem evt) {
        if (!(evt.getEntity() instanceof ServerPlayer sp)) return;
        if (evt.isCanceled()) {
            resyncNow(sp);
            markSuspect(sp, sp.server.getTickCount());
        }
    }

    // --- MENUS: opening/closing typically “fixes” ghosts; mirror that behavior -------------------

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open evt) {
        if (evt.getEntity() instanceof ServerPlayer sp) {
            resyncNow(sp);
        }
    }

    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close evt) {
        if (evt.getEntity() instanceof ServerPlayer sp) {
            resyncNow(sp);
        }
    }

    // --- DIMENSION CHANGE: another moment to always be correct -----------------------------------

    @SubscribeEvent
    public void onPlayerChangedDim(PlayerEvent.PlayerChangedDimensionEvent evt) {
        if (evt.getEntity() instanceof ServerPlayer sp) {
            resyncNow(sp);
        }
    }
}
