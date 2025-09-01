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
 * Server-side only: resends authoritative container + carried stacks
 * when interactions FAIL (canceled), plus a tiny watchdog follow-up.
 * Clean & surgical: no resync on successful actions.
 * HOTFIX: All ConfigValue.get() calls are wrapped to avoid IllegalStateException
 * when NeoForge hasn't finished loading the config yet.
 */
final class ServerResyncs {

    private final Object2IntOpenHashMap<UUID> suspectUntilTick = new Object2IntOpenHashMap<>();
    private final Object2IntOpenHashMap<UUID> resyncsThisTick = new Object2IntOpenHashMap<>();

    // --- safe config accessors (return conservative defaults if config not ready) ----------------

    private static int maxResyncsPerTick() {
        try {
            return Config.MAX_RESYNCS_PER_TICK.get();
        } catch (IllegalStateException notReady) {
            // Conservative: allow at least one resync to fix ghosts; avoids crash pre-config.
            return 1;
        }
    }

    private static boolean watchdogEnabled() {
        try {
            return Config.ENABLE_WATCHDOG_RESYNC.get();
        } catch (IllegalStateException notReady) {
            // Safe default: off until config loads.
            return false;
        }
    }

    private static int watchdogTicks() {
        try {
            return Config.WATCHDOG_TICKS.get();
        } catch (IllegalStateException notReady) {
            // No delayed follow-up until config loads.
            return 0;
        }
    }

    /** Push server-truth to the client: full container content + carried stack. */
    private void resyncNow(ServerPlayer sp) {
        int count = resyncsThisTick.getOrDefault(sp.getUUID(), 0);
        if (count >= maxResyncsPerTick()) return;

        AbstractContainerMenu menu = sp.containerMenu; // never null: defaults to player inv
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
        if (!watchdogEnabled()) return;
        int until = currentTick + watchdogTicks();
        suspectUntilTick.put(sp.getUUID(), until);
    }

    // --- TICK: rate-limit reset + watchdog follow-up ---------------------------------------------

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post evt) {
        if (!(evt.getEntity() instanceof ServerPlayer sp)) return;

        // per-tick rate limit reset
        UUID id = sp.getUUID();
        resyncsThisTick.removeInt(id);

        // watchdog follow-up after a canceled interaction
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
