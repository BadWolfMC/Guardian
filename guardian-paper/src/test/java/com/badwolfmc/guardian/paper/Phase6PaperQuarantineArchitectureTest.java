package com.badwolfmc.guardian.paper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase6PaperQuarantineArchitectureTest {
    private static final Path ADAPTER = Path.of(
        "src/main/java/com/badwolfmc/guardian/paper/PaperAdmissionAdapter.java");
    private static final Path GUARD = Path.of(
        "src/main/java/com/badwolfmc/guardian/paper/PaperQuarantineGuard.java");

    @Test
    void quarantineIsASeparateRegisteredSecurityBoundary() throws Exception {
        String adapter = Files.readString(ADAPTER);
        assertTrue(adapter.contains("new PaperQuarantineGuard(sessions)"));
        assertTrue(adapter.contains("registerEvents(quarantineGuard, plugin)"));
        assertFalse(adapter.contains("public void onInventoryClick(InventoryClickEvent event)"),
            "transport adapter should not regain the quarantine event matrix");
    }

    @Test
    void quarantineResolvesTheExactPlayConnectionRatherThanUuidAlone() throws Exception {
        String guard = Files.readString(GUARD);
        assertTrue(guard.contains("sessions.forPlay(player.getUniqueId(), player)"));
        assertTrue(guard.contains("session != null && session.quarantined()"));
        assertFalse(guard.contains("sessions.forPlayer(player.getUniqueId()).get"));
    }

    @Test
    void movementContainerAndPersistentPlayerInputSurfacesAreBlocked() throws Exception {
        String guard = Files.readString(GUARD);
        assertContainsAll(guard,
            "onMove(PlayerMoveEvent event)",
            "event.hasChangedPosition()",
            "onJump(PlayerJumpEvent event)",
            "onTeleport(PlayerTeleportEvent event)",
            "onPortal(PlayerPortalEvent event)",
            "onEndGateway(PlayerTeleportEndGatewayEvent event)",
            "onInventoryOpen(InventoryOpenEvent event)",
            "onInventoryClick(InventoryClickEvent event)",
            "onInventoryDrag(InventoryDragEvent event)",
            "onInteract(PlayerInteractEvent event)",
            "onInteractEntity(PlayerInteractEntityEvent event)",
            "onInteractAtEntity(PlayerInteractAtEntityEvent event)",
            "onArmorStandManipulate(PlayerArmorStandManipulateEvent event)",
            "onItemFrameChange(PlayerItemFrameChangeEvent event)",
            "onNameEntity(PlayerNameEntityEvent event)",
            "onBreak(BlockBreakEvent event)",
            "onPlace(BlockPlaceEvent event)",
            "onSignChange(SignChangeEvent event)",
            "onHarvestBlock(PlayerHarvestBlockEvent event)",
            "onShearBlock(PlayerShearBlockEvent event)",
            "onFlowerPotManipulate(PlayerFlowerPotManipulateEvent event)",
            "onDrop(PlayerDropItemEvent event)",
            "onPickup(EntityPickupItemEvent event)",
            "onPickupExperience(PlayerPickupExperienceEvent event)",
            "onConsume(PlayerItemConsumeEvent event)",
            "onHeldSlot(PlayerItemHeldEvent event)",
            "onSwapHands(PlayerSwapHandItemsEvent event)",
            "onEditBook(PlayerEditBookEvent event)",
            "onPickItem(PlayerPickItemEvent event)",
            "onSwapEquipment(PlayerSwapWithEquipmentSlotEvent event)",
            "onInsertLecternBook(PlayerInsertLecternBookEvent event)",
            "onTakeLecternBook(PlayerTakeLecternBookEvent event)",
            "onBucketEmpty(PlayerBucketEmptyEvent event)",
            "onBucketFill(PlayerBucketFillEvent event)",
            "onBucketEntity(PlayerBucketEntityEvent event)",
            "onFish(PlayerFishEvent event)",
            "onShear(PlayerShearEntityEvent event)",
            "onLeash(PlayerLeashEntityEvent event)",
            "onUnleash(PlayerUnleashEntityEvent event)",
            "onCommand(PlayerCommandPreprocessEvent event)",
            "onSignCommand(PlayerSignCommandPreprocessEvent event)",
            "onChat(AsyncChatEvent event)",
            "onStartSpectating(PlayerStartSpectatingEntityEvent event)",
            "onStopSpectating(PlayerStopSpectatingEntityEvent event)",
            "onBedEnter(PlayerBedEnterEvent event)");
    }


    @Test
    void specializedInteractionFamiliesAreGuardedExplicitly() throws Exception {
        String guard = Files.readString(GUARD);
        assertContainsAll(guard,
            "onInteractAtEntity(PlayerInteractAtEntityEvent event)",
            "onArmorStandManipulate(PlayerArmorStandManipulateEvent event)",
            "onItemFrameChange(PlayerItemFrameChangeEvent event)",
            "onSignCommand(PlayerSignCommandPreprocessEvent event)");
    }

    @Test
    void outboundCombatProjectileAndVehicleEffectsAreBlocked() throws Exception {
        String guard = Files.readString(GUARD);
        assertContainsAll(guard,
            "onPreAttack(PrePlayerAttackEntityEvent event)",
            "event instanceof EntityDamageByEntityEvent byEntity",
            "playerActor(byEntity.getDamager())",
            "onProjectileLaunch(ProjectileLaunchEvent event)",
            "projectile.getShooter() instanceof Player player",
            "onMount(EntityMountEvent event)",
            "onDismount(EntityDismountEvent event)",
            "onVehicleEnter(VehicleEnterEvent event)",
            "onVehicleExit(VehicleExitEvent event)",
            "onVehicleDamage(VehicleDamageEvent event)",
            "onVehicleDestroy(VehicleDestroyEvent event)");
    }

    @Test
    void quarantineAlsoFreezesClientMovementStateToggles() throws Exception {
        String guard = Files.readString(GUARD);
        assertContainsAll(guard,
            "onToggleFlight(PlayerToggleFlightEvent event)",
            "onToggleSneak(PlayerToggleSneakEvent event)",
            "onToggleSprint(PlayerToggleSprintEvent event)");
    }

    private static void assertContainsAll(String source, String... needles) {
        for (String needle : needles) {
            assertTrue(source.contains(needle), () -> "missing quarantine guard: " + needle);
        }
    }
}
