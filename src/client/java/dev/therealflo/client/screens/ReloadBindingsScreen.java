package dev.therealflo.client.screens;

import dev.therealflo.client.api.MCOpenXRReload;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.vivecraft.client_vr.ClientDataHolderVR;

public class ReloadBindingsScreen extends BaseOwoScreen<FlowLayout> {

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
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

        rootComponent.child(
                Components.button(
                        Text.literal("Reload"),
                        button -> {
                            if (ClientDataHolderVR.getInstance().vr instanceof MCOpenXRReload reloadable) {
                                reloadable.reloadXRBindings();
                            }
                        }
                )
        );
    }
}
