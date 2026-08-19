package net.cannanetwork.cannadupe.mixin;

import net.cannanetwork.cannadupe.CannaDupeClient;
import net.cannanetwork.cannadupe.gui.ItemPickerScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class InventoryScreenMixin extends Screen {
    protected InventoryScreenMixin(Component title) { super(title); }
    @Inject(method = "init", at = @At("TAIL")) private void cannadupe$controls(CallbackInfo ci) {
        if (!((Object)this instanceof GrindstoneScreen)) return;
        int width = Math.min(340, this.width - 12), x = (this.width - width) / 2, y = 6, half = (width - 4) / 2;
        Button title = this.addRenderableWidget(Button.builder(Component.literal("CannaDupe"), b -> {}).bounds(x, y, width, 20).build());
        title.active = false;
        Button status = this.addRenderableWidget(Button.builder(statusLabel(), b -> {}).bounds(x, y + 22, width, 20).build());
        status.active = false;
        this.addRenderableWidget(Button.builder(targetLabel(), b -> this.minecraft.setScreen(new ItemPickerScreen((Screen)(Object)this))).bounds(x, y + 44, width, 20).build());
        Button grindstone = this.addRenderableWidget(Button.builder(grindstoneLabel(), b -> { CannaDupeClient.CONTROLLER.setGrindstone(); b.setMessage(grindstoneLabel()); status.setMessage(statusLabel()); }).bounds(x, y + 66, half, 20).build());
        Button start = this.addRenderableWidget(Button.builder(startLabel(), b -> { CannaDupeClient.CONTROLLER.startOrStop(); b.setMessage(startLabel()); status.setMessage(statusLabel()); }).bounds(x + half + 4, y + 66, half, 20).build());
        Button footer = this.addRenderableWidget(Button.builder(Component.literal("Esc to stop"), b -> {}).bounds(x, y + 88, half, 20).build());
        footer.active = false;
        Button cycles = this.addRenderableWidget(Button.builder(cyclesLabel(), b -> {}).bounds(x + half + 4, y + 88, half, 20).build());
        cycles.active = false;
    }

    private static Component statusLabel() { return Component.literal("Status: " + CannaDupeClient.CONTROLLER.status()); }
    private static Component targetLabel() { var dupe = CannaDupeClient.CONTROLLER; return Component.literal("Target: " + dupe.target().getDefaultInstance().getHoverName().getString() + " x" + dupe.targetCount() + " — click to choose"); }
    private static Component grindstoneLabel() { return Component.literal(CannaDupeClient.CONTROLLER.hasGrindstone() ? "✓ Grindstone set" : "Set Grindstone"); }
    private static Component startLabel() { return Component.literal(CannaDupeClient.CONTROLLER.running() ? "STOP" : "START"); }
    private static Component cyclesLabel() { return Component.literal("Cycles: " + CannaDupeClient.CONTROLLER.cycles()); }
}
