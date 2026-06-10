package dev.therealflo.client.screens;

import dev.therealflo.client.BindingSetRegistry;
import dev.therealflo.client.PresetPackageManager;
import dev.therealflo.client.RemotePresetClient;
import dev.therealflo.client.RemotePresetConfig;
import dev.therealflo.client.RequestModClient;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class PresetUploadScreen extends BaseOwoScreen<FlowLayout> {
    private final Screen parentScreen;
    private final String interactionProfile;
    private final String setId;
    private final RemotePresetClient presetClient = new RemotePresetClient();

    private TextBoxComponent titleField;
    private TextBoxComponent descriptionField;
    private LabelComponent statusLabel;
    private boolean uploading;

    public PresetUploadScreen(Screen parentScreen, String interactionProfile, String setId) {
        this.parentScreen = parentScreen;
        this.interactionProfile = interactionProfile;
        this.setId = setId;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        RequestUi.root(rootComponent);

        FlowLayout panel = Containers.verticalFlow(Sizing.fixed(320), Sizing.content());
        panel.surface(io.wispforest.owo.ui.core.Surface.DARK_PANEL);
        panel.padding(Insets.of(14));
        panel.horizontalAlignment(HorizontalAlignment.CENTER);

        panel.child(RequestUi.header(
                Text.translatable("screen.request.upload_preset"),
                Text.translatable("text.request.server_url", RemotePresetConfig.getInstance().get().baseUrl)
        ));

        FlowLayout form = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        form.gap(4);

        form.child(Components.label(Text.translatable("text.request.preset_title")).color(RequestUi.TEXT));
        this.titleField = Components.textBox(Sizing.fill(100),
                BindingSetRegistry.getInstance().getSetDisplayName(this.interactionProfile, this.setId));
        this.titleField.setMaxLength(80);
        form.child(this.titleField);

        form.child(Components.label(Text.translatable("text.request.preset_description"))
                .color(RequestUi.TEXT)
                .margins(Insets.top(4)));
        this.descriptionField = Components.textBox(Sizing.fill(100));
        this.descriptionField.setMaxLength(500);
        form.child(this.descriptionField);

        panel.child(form);

        this.statusLabel = (LabelComponent) Components.label(Text.empty())
                .color(RequestUi.WARNING)
                .horizontalSizing(Sizing.fill(100));
        panel.child(this.statusLabel.margins(Insets.top(8)));

        FlowLayout footer = RequestUi.footer();
        footer.child(RequestUi.footerButton(
                Text.translatable("button.request.upload_preset"),
                button -> upload()
        ));
        footer.child(RequestUi.footerButton(
                Text.translatable("button.request.browse_presets"),
                button -> {
                    if (this.client != null) {
                        this.client.setScreen(new PresetBrowserScreen(this.parentScreen, this.interactionProfile));
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

    private void setStatus(String status) {
        this.statusLabel.text(Text.literal(status == null ? "" : status));
    }

    private void upload() {
        if (this.uploading) {
            return;
        }

        String title = this.titleField.getText().trim();
        if (title.isEmpty()) {
            setStatus(Text.translatable("text.request.upload_requires_title").getString());
            return;
        }

        this.uploading = true;
        setStatus(Text.translatable("text.request.upload_in_progress").getString());

        CompletableFuture.supplyAsync(() -> this.presetClient.uploadPreset(
                PresetPackageManager.buildUploadRequest(
                        this.interactionProfile,
                        this.setId,
                        title,
                        this.descriptionField.getText()
                )
        )).whenComplete((preset, throwable) -> {
            if (this.client == null) {
                return;
            }

            this.client.execute(() -> {
                this.uploading = false;
                if (throwable != null) {
                    String message = RequestModClient.formatError(throwable);
                    RequestModClient.logError("Preset upload failed: " + message, throwable);
                    setStatus(message);
                    return;
                }

                setStatus(Text.translatable("message.request.upload_success", preset.preset.title).getString());
            });
        });
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parentScreen);
            return;
        }
        super.close();
    }
}
