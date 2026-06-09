package dev.therealflo.client.screens;

import dev.therealflo.client.BindingSetRegistry;
import dev.therealflo.client.PresetPackageManager;
import dev.therealflo.client.RemotePresetClient;
import dev.therealflo.client.RemotePresetConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.concurrent.CompletableFuture;

public class PresetUploadScreen extends Screen {
    private final Screen parentScreen;
    private final String interactionProfile;
    private final String setId;
    private final RemotePresetClient presetClient = new RemotePresetClient();

    private TextFieldWidget titleField;
    private TextFieldWidget descriptionField;
    private String status = "";
    private boolean uploading;

    public PresetUploadScreen(Screen parentScreen, String interactionProfile, String setId) {
        super(Text.translatable("screen.request.upload_preset"));
        this.parentScreen = parentScreen;
        this.interactionProfile = interactionProfile;
        this.setId = setId;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int fieldWidth = 260;
        int y = 60;

        this.titleField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, y, fieldWidth, 20, Text.empty());
        this.titleField.setMaxLength(80);
        this.titleField.setText(BindingSetRegistry.getInstance().getSetDisplayName(this.interactionProfile, this.setId));
        this.addDrawableChild(this.titleField);

        this.descriptionField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, y + 32, fieldWidth, 20, Text.empty());
        this.descriptionField.setMaxLength(500);
        this.addDrawableChild(this.descriptionField);

        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("button.request.upload_preset"),
            button -> request$upload()
        ).dimensions(centerX - 130, y + 68, 125, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("button.request.browse_presets"),
            button -> {
                if (this.client != null) {
                    this.client.setScreen(new PresetBrowserScreen(this.parentScreen, this.interactionProfile));
                }
            }
        ).dimensions(centerX + 5, y + 68, 125, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("button.request.back"),
            button -> close()
        ).dimensions(centerX - 60, this.height - 32, 120, 20).build());
    }

    private void request$upload() {
        if (this.uploading) {
            return;
        }

        String title = this.titleField.getText().trim();
        if (title.isEmpty()) {
            this.status = Text.translatable("text.request.upload_requires_title").getString();
            return;
        }

        this.uploading = true;
        this.status = Text.translatable("text.request.upload_in_progress").getString();

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
                    this.status = throwable.getCause() != null ? throwable.getCause().getMessage() : throwable.getMessage();
                    return;
                }

                this.status = Text.translatable("message.request.upload_success", preset.preset.title).getString();
            });
        });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, 20, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("text.request.server_url",
            RemotePresetConfig.getInstance().get().baseUrl), centerX - 130, 36, 0xA0A0A0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("text.request.preset_title"), centerX - 130, 52, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("text.request.preset_description"), centerX - 130, 84, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal(this.status), centerX - 130, 126, 0xFFD27F);
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
