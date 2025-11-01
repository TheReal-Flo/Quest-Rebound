package dev.therealflo.mixin.client;

import dev.therealflo.client.screens.ChangeBindingScreen;
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
        int x = this.width / 2 - w / 2;
        int y = this.height - 128;

        this.addDrawableChild(
                ButtonWidget.builder(Text.translatable("key.request.open_binding"),
                                b -> MinecraftClient.getInstance().setScreen(new ChangeBindingScreen()))
                        .dimensions(x, y, w, h)
                        .build()
        );
    }

    // (optional) make title visible if you want to draw extra text, not required for the button itself
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
    }
}