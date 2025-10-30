package dev.therealflo.client.screens;

import dev.therealflo.client.InputPathDescriptions;
import dev.therealflo.client.api.MCOpenXRReload;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.vivecraft.client_vr.ClientDataHolderVR;

import java.util.Map;

public class ChangeBindingScreen extends BaseOwoScreen<FlowLayout> {

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        Map<String, InputPathDescriptions.InputDescription> questInputs = InputPathDescriptions.getAllInputs(
                "/interaction_profiles/oculus/touch_controller"
        );

        rootComponent
                .surface(Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.CENTER);

        rootComponent.child(
                Components.label(Text.literal("Reload Bindings"))
                        .color(Color.ofRgb(0xffffff))
                        .shadow(true)
                        .horizontalTextAlignment(HorizontalAlignment.CENTER)
                        .verticalTextAlignment(VerticalAlignment.CENTER)
        );

        for (InputPathDescriptions.InputDescription input : questInputs.values()) {
            if (InputPathDescriptions.isAxisInput(input.path)) continue;
            rootComponent.child(
                    Components.label(Text.literal(input.description))
                            .color(Color.ofRgb(0xffffff))
                            .shadow(true)
                            .horizontalTextAlignment(HorizontalAlignment.CENTER)
                            .verticalTextAlignment(VerticalAlignment.CENTER)
            );
        }
    }
}
