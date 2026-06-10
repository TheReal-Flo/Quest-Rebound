package dev.therealflo.client.screens;

import dev.therealflo.client.api.MCOpenXRReload;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.OwoUIAdapter;
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
        RequestUi.root(rootComponent);

        FlowLayout panel = RequestUi.dialogPanel();
        panel.child(RequestUi.header(
                Text.translatable("screen.request.reload_bindings"),
                Text.translatable("text.request.reload_hint")
        ));

        FlowLayout footer = RequestUi.footer();
        footer.child(RequestUi.footerButton(
                Text.translatable("button.request.reload"),
                button -> {
                    if (ClientDataHolderVR.getInstance().vr instanceof MCOpenXRReload reloadable) {
                        reloadable.reloadXRBindings();
                    }
                }
        ));
        footer.child(RequestUi.footerButton(
                Text.translatable("button.request.back"),
                button -> close()
        ));
        panel.child(footer);

        rootComponent.child(panel);
    }
}
