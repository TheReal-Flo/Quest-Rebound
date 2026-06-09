package dev.therealflo.mixin.client;

import dev.therealflo.client.BindingSetRegistry;
import dev.therealflo.client.DefaultBindingManager;
import dev.therealflo.client.RequestModClient;
import dev.therealflo.client.screens.ChangeBindingScreen;
import dev.therealflo.client.screens.BindingSetManagerScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.ControlsOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ControlsOptionsScreen.class)
public abstract class ControlsOptionsScreenMixin extends Screen {
    protected ControlsOptionsScreenMixin(Text title) { super(title); }

    // ControlsOptionsScreen declares addOptions(); inject after it finishes
    @Inject(method = "addOptions", at = @At("RETURN"))
    private void request$addButton(CallbackInfo ci) {
        int w = 150, h = 20;
        int gap = 4;
        int x = this.width / 2 - w - gap / 2;
        int y = this.height - 128;
        String profile = RequestModClient.getPreferredInteractionProfile();

        this.addDrawableChild(
                ButtonWidget.builder(Text.translatable("button.request.manage_sets"),
                                b -> MinecraftClient.getInstance().setScreen(new BindingSetManagerScreen((Screen) (Object) this, profile)))
                        .dimensions(x, y, w, h)
                        .build()
        );

        this.addDrawableChild(
                ButtonWidget.builder(Text.translatable("key.request.open_binding"),
                                b -> MinecraftClient.getInstance().setScreen(new ChangeBindingScreen((Screen) (Object) this, profile)))
                        .dimensions(x + w + gap, y, w, h)
                        .build()
        );

        this.addDrawableChild(
                ButtonWidget.builder(Text.translatable("button.request.swap_joysticks"),
                                b -> request$swapJoysticks(profile))
                        .dimensions(x, y + h + gap, w * 2 + gap, h)
                        .build()
        );
    }

    // (optional) make title visible if you want to draw extra text, not required for the button itself
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
    }

    private void request$swapJoysticks(String profile) {
        String activeSetId = BindingSetRegistry.getInstance().getActiveSetId(profile);
        boolean swapped = DefaultBindingManager.getInstance()
                .swapJoystickBindings(profile, activeSetId);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(
                    Text.literal(swapped ? "Swapped left/right joystick bindings" : "No joystick bindings found to swap"),
                    false
            );
        }
    }
}
