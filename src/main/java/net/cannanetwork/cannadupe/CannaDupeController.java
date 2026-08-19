package net.cannanetwork.cannadupe;

import net.cannanetwork.cannadupe.mixin.WindowAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * CannaDupe's GUI Move cycle. The player keeps the same yaw: walk backward
 * from the open grindstone until the server closes it, then walk forward to
 * the exact original interaction position and reopen the grinder.
 */
public final class CannaDupeController {
    private static final double TRIGGER_DISTANCE = 6.1;
    private static final double RETURN_DISTANCE = 0.55;
    private final Minecraft mc = Minecraft.getInstance();
    private Item target = Items.DIAMOND;
    private BlockPos grindstone;
    private Vec3 interactionPoint;
    private Phase phase = Phase.IDLE;
    private int phaseTicks, cycles;
    private boolean escWasDown;

    public Item target() { return target; }
    public void setTarget(Item item) { target = item; }
    public boolean running() { return phase != Phase.IDLE; }
    public int cycles() { return cycles; }
    public boolean hasGrindstone() { return grindstone != null; }
    public String status() {
        return switch (phase) {
            case IDLE -> grindstone == null ? "Ready — set a grindstone" : "Ready";
            case INSERT -> "Inserting item";
            case BACK_AWAY -> "Walking back";
            case WAIT_FOR_CLOSE -> "Waiting for dupe";
            case RETURN_FORWARD -> "Returning to grinder";
            case OPEN, WAIT_FOR_MENU -> "Opening grinder";
        };
    }
    public int targetCount() {
        if (mc.player == null) return 0;
        int count = 0;
        Inventory inventory = mc.player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(target)) count += stack.getCount();
        }
        ItemStack offhand = inventory.getItem(Inventory.SLOT_OFFHAND);
        return offhand.is(target) ? count + offhand.getCount() : count;
    }

    public void setGrindstone() {
        if (mc.player == null || mc.level == null) { message("Join a world first."); return; }
        BlockPos nearest = null; double nearestDistance = Double.MAX_VALUE;
        BlockPos origin = mc.player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-5, -3, -5), origin.offset(5, 3, 5))) {
            if (!mc.level.getBlockState(pos).is(Blocks.GRINDSTONE)) continue;
            double distance = mc.player.distanceToSqr(Vec3.atCenterOf(pos));
            if (distance < nearestDistance) { nearest = pos.immutable(); nearestDistance = distance; }
        }
        grindstone = nearest;
        if (grindstone == null) { message("No nearby grindstone found."); return; }
        message("Grindstone set at " + grindstone.toShortString() + ".");
    }

    /** Called while the physical grindstone GUI is open. */
    public void startOrStop() {
        if (running()) { stop("Stopped."); return; }
        if (mc.player == null || mc.level == null || !menuOpen()) { message("Open the grindstone first."); return; }
        if (grindstone == null) setGrindstone();
        if (grindstone == null) return;
        interactionPoint = mc.player.position();
        phase = Phase.INSERT;
        phaseTicks = 0;
        cycles = 0;
        message("Started. Uses Gui Move: back 6 blocks, then forward to repeat. Esc stops.");
    }

    public void tick() {
        UpdateChecker.notifyWhenPlayerReady();
        boolean escDown = GLFW.glfwGetKey(((WindowAccessor)(Object) mc.getWindow()).cannadupe$getHandle(), GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;
        if (running() && escDown && !escWasDown) stop("Stopped with Esc.");
        escWasDown = escDown;
        if (!running() || mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (grindstone == null || interactionPoint == null || !mc.level.getBlockState(grindstone).is(Blocks.GRINDSTONE)) { stop("Grindstone is missing."); return; }
        lockCameraOnGrindstone();
        lockSidewaysMovement();
        phaseTicks++;
        switch (phase) {
            case INSERT -> {
                if (!menuOpen()) { stop("Grindstone closed before item insertion."); return; }
                int slot = targetSlot();
                if (slot < 0) { stop("No selected item remains."); return; }
                mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player);
                next(Phase.BACK_AWAY);
            }
            case BACK_AWAY -> {
                mc.options.keyUp.setDown(false);
                mc.options.keyDown.setDown(true);
                if (distanceFromGrindstone() >= TRIGGER_DISTANCE) next(Phase.WAIT_FOR_CLOSE);
            }
            case WAIT_FOR_CLOSE -> {
                if (!menuOpen()) { next(Phase.RETURN_FORWARD); }
                else if (phaseTicks > 100) stop("Server did not close the grinder after 6 blocks.");
            }
            case RETURN_FORWARD -> {
                mc.options.keyDown.setDown(false);
                mc.options.keyUp.setDown(true);
                if (horizontalDistance(mc.player.position(), interactionPoint) <= RETURN_DISTANCE) { releaseKeys(); cycles++; next(Phase.OPEN); }
            }
            case OPEN -> {
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(grindstone), Direction.UP, grindstone, false));
                mc.player.swing(InteractionHand.MAIN_HAND);
                next(Phase.WAIT_FOR_MENU);
            }
            case WAIT_FOR_MENU -> {
                if (menuOpen()) next(Phase.INSERT);
                else if (phaseTicks > 40) stop("Grindstone did not reopen.");
            }
            default -> { }
        }
    }

    private boolean menuOpen() { return mc.screen instanceof GrindstoneScreen; }
    private void lockCameraOnGrindstone() {
        Vec3 target = Vec3.atCenterOf(grindstone);
        double dx = target.x - mc.player.getX(), dz = target.z - mc.player.getZ();
        mc.player.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
    }
    private int targetSlot() { for (int slot = 3; slot < mc.player.containerMenu.slots.size(); slot++) if (mc.player.containerMenu.getSlot(slot).getItem().is(target)) return slot; return -1; }
    private double distanceFromGrindstone() { return horizontalDistance(mc.player.position(), Vec3.atCenterOf(grindstone)); }
    private static double horizontalDistance(Vec3 first, Vec3 second) { double x = first.x - second.x, z = first.z - second.z; return Math.sqrt(x * x + z * z); }
    private void next(Phase next) { releaseKeys(); phase = next; phaseTicks = 0; }
    private void stop(String text) { releaseKeys(); phase = Phase.IDLE; interactionPoint = null; message(text); }
    private void lockSidewaysMovement() { mc.options.keyLeft.setDown(false); mc.options.keyRight.setDown(false); }
    private void releaseKeys() { mc.options.keyUp.setDown(false); mc.options.keyDown.setDown(false); mc.options.keyLeft.setDown(false); mc.options.keyRight.setDown(false); }
    private void message(String text) { System.out.println("[CannaDupe] " + text); }
    private enum Phase { IDLE, INSERT, BACK_AWAY, WAIT_FOR_CLOSE, RETURN_FORWARD, OPEN, WAIT_FOR_MENU }
}
