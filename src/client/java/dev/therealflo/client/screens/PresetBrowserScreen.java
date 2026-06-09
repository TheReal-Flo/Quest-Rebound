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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PresetBrowserScreen extends Screen {
    private static final int PAGE_SIZE = 6;

    private final Screen parentScreen;
    private final String interactionProfile;
    private final RemotePresetClient presetClient = new RemotePresetClient();

    private TextFieldWidget searchField;
    private final List<RemotePresetClient.PresetSummary> presets = new ArrayList<>();
    private String status = "";
    private String searchQuery = "";
    private int offset;
    private boolean loading;

    public PresetBrowserScreen(Screen parentScreen, String interactionProfile) {
        super(Text.translatable("screen.request.browse_presets"));
        this.parentScreen = parentScreen;
        this.interactionProfile = interactionProfile;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        this.searchField = new TextFieldWidget(this.textRenderer, centerX - 110, 44, 220, 20, Text.empty());
        this.searchField.setMaxLength(80);
        this.searchField.setText(this.searchQuery);
        this.addDrawableChild(this.searchField);

        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("button.request.search_presets"),
                button -> {
                    this.searchQuery = this.searchField.getText();
                    this.offset = 0;
                    request$loadPresets();
                }
        ).dimensions(centerX + 116, 44, 70, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("button.request.refresh_session"),
            button -> request$linkSession()
        ).dimensions(centerX - 186, 44, 70, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("button.request.upload_preset"),
            button -> {
                if (this.client != null) {
                    String activeSetId = BindingSetRegistry.getInstance().getActiveSetId(this.interactionProfile);
                    this.client.setScreen(new PresetUploadScreen(this, this.interactionProfile, activeSetId));
                }
            }
        ).dimensions(centerX - 186, this.height - 32, 90, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("button.request.prev_page"),
            button -> {
                if (this.offset >= PAGE_SIZE) {
                    this.offset -= PAGE_SIZE;
                    request$loadPresets();
                }
            }
        ).dimensions(centerX - 90, this.height - 32, 80, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("button.request.next_page"),
            button -> {
                this.offset += PAGE_SIZE;
                request$loadPresets();
            }
        ).dimensions(centerX + 10, this.height - 32, 80, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("button.request.back"),
            button -> close()
        ).dimensions(centerX + 110, this.height - 32, 80, 20).build());

        int startY = 88;
        for (int i = 0; i < PAGE_SIZE; i++) {
            final int index = i;
            this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("button.request.import_preset"),
                button -> request$importPreset(index)
            ).dimensions(centerX + 110, startY + i * 34, 80, 20).build());
        }

        if (this.presets.isEmpty() && !this.loading) {
            request$loadPresets();
        }
    }

    private void request$linkSession() {
        if (this.loading) {
            return;
        }

        this.loading = true;
        this.status = Text.translatable("text.request.link_in_progress").getString();
        CompletableFuture.supplyAsync(this.presetClient::linkCurrentSession)
            .whenComplete((identity, throwable) -> {
                if (this.client == null) {
                    return;
                }

                this.client.execute(() -> {
                    this.loading = false;
                    if (throwable != null) {
                        this.status = throwable.getCause() != null ? throwable.getCause().getMessage() : throwable.getMessage();
                    } else {
                        this.status = Text.translatable("message.request.link_success", identity.currentName()).getString();
                    }
                });
            });
    }

    private void request$loadPresets() {
        if (this.loading) {
            return;
        }

        this.loading = true;
        this.status = Text.translatable("text.request.discovery_loading").getString();
        String query = this.searchField != null ? this.searchField.getText() : this.searchQuery;
        this.searchQuery = query;

        CompletableFuture.supplyAsync(() -> this.presetClient.listPresets(query, this.offset, PAGE_SIZE))
            .whenComplete((items, throwable) -> {
                if (this.client == null) {
                    return;
                }

                this.client.execute(() -> {
                    this.loading = false;
                    this.presets.clear();
                    if (throwable != null) {
                        this.status = throwable.getCause() != null ? throwable.getCause().getMessage() : throwable.getMessage();
                    } else {
                        this.presets.addAll(items);
                        this.status = this.presets.isEmpty()
                            ? Text.translatable("text.request.discovery_empty").getString()
                            : Text.translatable("text.request.discovery_loaded", this.presets.size()).getString();
                    }
                    this.clearAndInit();
                });
            });
    }

    private void request$importPreset(int index) {
        if (index < 0 || index >= this.presets.size() || this.loading) {
            return;
        }

        RemotePresetClient.PresetSummary preset = this.presets.get(index);
        this.loading = true;
        this.status = Text.translatable("text.request.import_in_progress", preset.title).getString();

        CompletableFuture.supplyAsync(() -> {
            RemotePresetClient.DownloadedPreset downloadedPreset = this.presetClient.downloadPreset(preset.id);
            return PresetPackageManager.importPreset(downloadedPreset);
        }).whenComplete((importedName, throwable) -> {
            if (this.client == null) {
                return;
            }

            this.client.execute(() -> {
                this.loading = false;
                if (throwable != null) {
                    this.status = throwable.getCause() != null ? throwable.getCause().getMessage() : throwable.getMessage();
                } else {
                    this.status = Text.translatable("message.request.import_success", importedName).getString();
                }
            });
        });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, 20, 0xFFFFFF);
        RemotePresetConfig.ConfigData config = RemotePresetConfig.getInstance().get();
        String linkedName = config.linkedName == null || config.linkedName.isBlank()
            ? "Not linked"
            : config.linkedName;
        context.drawTextWithShadow(this.textRenderer, Text.translatable("text.request.server_url", config.baseUrl), centerX - 186, 30, 0xA0A0A0);
        context.drawTextWithShadow(this.textRenderer, Text.translatable("text.request.linked_as", linkedName), centerX + 10, 30, 0xA0A0A0);
        context.drawTextWithShadow(this.textRenderer, Text.literal(this.status), centerX - 186, 68, 0xFFD27F);

        int startY = 92;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int y = startY + i * 34;
            if (i >= this.presets.size()) {
                continue;
            }

            RemotePresetClient.PresetSummary preset = this.presets.get(i);
            context.drawTextWithShadow(this.textRenderer, preset.title, centerX - 186, y, 0xFFFFFF);
            context.drawTextWithShadow(
                this.textRenderer,
                preset.ownerName + "  |  " + preset.minecraftVersion + "  |  " + preset.modVersion,
                centerX - 186,
                y + 10,
                0xA0A0A0
            );
            String description = preset.description == null ? "" : preset.description;
            if (description.length() > 44) {
                description = description.substring(0, 44) + "...";
            }
            context.drawTextWithShadow(this.textRenderer, description, centerX - 186, y + 20, 0xC8C8C8);
        }
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
