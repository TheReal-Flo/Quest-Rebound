package dev.therealflo.client.screens;

import dev.therealflo.client.BindingSetRegistry;
import dev.therealflo.client.PresetPackageManager;
import dev.therealflo.client.RemotePresetClient;
import dev.therealflo.client.RemotePresetConfig;
import dev.therealflo.client.RequestModClient;
import dev.therealflo.client.SessionLinkManager;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PresetBrowserScreen extends BaseOwoScreen<FlowLayout> {
    private static final int PAGE_SIZE = 6;

    private final Screen parentScreen;
    private final String interactionProfile;
    private final RemotePresetClient presetClient = new RemotePresetClient();

    private final List<RemotePresetClient.PresetSummary> presets = new ArrayList<>();
    private String searchQuery = "";
    private int offset;
    private boolean loadingPresets;
    private boolean importingPreset;

    private TextBoxComponent searchField;
    private LabelComponent statusLabel;
    private LabelComponent pageLabel;
    private ButtonComponent prevButton;
    private FlowLayout listContent;

    public PresetBrowserScreen(Screen parentScreen, String interactionProfile) {
        this.parentScreen = parentScreen;
        this.interactionProfile = interactionProfile;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        RequestUi.root(rootComponent);

        RemotePresetConfig.ConfigData config = RemotePresetConfig.getInstance().get();

        FlowLayout panel = RequestUi.panel(90, 90);
        panel.child(RequestUi.header(
                Text.translatable("screen.request.browse_presets"),
                Text.translatable("text.request.server_url", config.baseUrl)
        ));

        // Search + session row
        FlowLayout searchRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        searchRow.verticalAlignment(VerticalAlignment.CENTER);
        searchRow.gap(6);
        searchRow.margins(Insets.bottom(4));

        this.searchField = Components.textBox(Sizing.expand(), this.searchQuery);
        this.searchField.setMaxLength(80);
        searchRow.child(this.searchField);
        searchRow.child(Components.button(
                Text.translatable("button.request.search_presets"),
                button -> {
                    this.searchQuery = this.searchField.getText();
                    this.offset = 0;
                    loadPresets();
                }
        ));
        panel.child(searchRow);

        // Status row
        this.statusLabel = (LabelComponent) Components.label(Text.empty())
                .color(RequestUi.WARNING)
                .horizontalSizing(Sizing.fill(100))
                .margins(Insets.bottom(4));
        panel.child(this.statusLabel);

        this.listContent = RequestUi.listContent();
        rebuildPresetList();
        panel.child(RequestUi.scrollArea(this.listContent));

        // Pagination row
        FlowLayout pagination = RequestUi.footer();
        this.prevButton = RequestUi.footerButton(
                Text.translatable("button.request.prev_page"),
                button -> {
                    if (this.offset >= PAGE_SIZE) {
                        this.offset -= PAGE_SIZE;
                        loadPresets();
                    }
                }
        );
        this.prevButton.active = this.offset > 0;
        pagination.child(this.prevButton);
        this.pageLabel = (LabelComponent) Components.label(pageText()).color(RequestUi.MUTED);
        pagination.child(this.pageLabel);
        pagination.child(RequestUi.footerButton(
                Text.translatable("button.request.next_page"),
                button -> {
                    this.offset += PAGE_SIZE;
                    loadPresets();
                }
        ));
        panel.child(pagination);

        // Main action row
        FlowLayout footer = RequestUi.footer();
        footer.margins(Insets.top(2));
        ButtonComponent uploadButton = RequestUi.footerButton(
                Text.translatable("button.request.upload_preset"),
                button -> {
                    if (this.client != null) {
                        String activeSetId = BindingSetRegistry.getInstance().getActiveSetId(this.interactionProfile);
                        this.client.setScreen(new PresetUploadScreen(this, this.interactionProfile, activeSetId));
                    }
                }
        );
        uploadButton.active = SessionLinkManager.isLinked();
        footer.child(uploadButton);
        footer.child(RequestUi.footerButton(
                Text.translatable("button.request.back"),
                button -> close()
        ));
        panel.child(footer);

        rootComponent.child(panel);

        if (this.presets.isEmpty() && !this.loadingPresets) {
            loadPresets();
        }

        SessionLinkManager.ensureLinkedWithConsent(this, linked -> {
            if (linked && this.client != null) {
                this.client.execute(() -> uploadButton.active = true);
            }
        });
    }

    private Text pageText() {
        return Text.translatable("text.request.page", this.offset / PAGE_SIZE + 1);
    }

    private void rebuildPresetList() {
        this.listContent.clearChildren();

        for (RemotePresetClient.PresetSummary preset : this.presets) {
            FlowLayout card = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            card.surface(RequestUi.CARD);
            card.padding(Insets.of(5));
            card.verticalAlignment(VerticalAlignment.CENTER);

            FlowLayout info = Containers.verticalFlow(Sizing.expand(), Sizing.content());
            info.gap(2);
            info.child(Components.label(Text.literal(preset.title)).color(RequestUi.TEXT).shadow(true));
            info.child(Components.label(
                    Text.literal(preset.ownerName + "  |  " + preset.minecraftVersion + "  |  " + preset.modVersion)
            ).color(RequestUi.MUTED));
            String description = preset.description == null ? "" : preset.description;
            if (!description.isBlank()) {
                if (description.length() > 90) {
                    description = description.substring(0, 90) + "...";
                }
                info.child(Components.label(Text.literal(description)).color(RequestUi.MUTED));
            }
            card.child(info);

            ButtonComponent importButton = Components.button(
                    Text.translatable("button.request.import_preset"),
                    button -> importPreset(preset)
            );
            importButton.sizing(Sizing.fixed(70), Sizing.fixed(RequestUi.LIST_BUTTON_HEIGHT));
            card.child(importButton);

            this.listContent.child(card);
        }
    }

    private void setStatus(String status) {
        this.statusLabel.text(Text.literal(status == null ? "" : status));
    }

    private void loadPresets() {
        if (this.loadingPresets) {
            return;
        }

        this.loadingPresets = true;
        setStatus(Text.translatable("text.request.discovery_loading").getString());
        String query = this.searchField != null ? this.searchField.getText() : this.searchQuery;
        this.searchQuery = query;

        CompletableFuture.supplyAsync(() -> this.presetClient.listPresets(query, this.offset, PAGE_SIZE))
                .whenComplete((items, throwable) -> {
                    if (this.client == null) {
                        this.loadingPresets = false;
                        return;
                    }

                    this.client.execute(() -> {
                        this.loadingPresets = false;
                        this.presets.clear();
                        if (throwable != null) {
                            String message = RequestModClient.formatError(throwable);
                            RequestModClient.logError("Preset discovery failed: " + message, throwable);
                            setStatus(message);
                        } else {
                            this.presets.addAll(items);
                            setStatus(this.presets.isEmpty()
                                    ? Text.translatable("text.request.discovery_empty").getString()
                                    : Text.translatable("text.request.discovery_loaded", this.presets.size()).getString());
                        }
                        this.prevButton.active = this.offset > 0;
                        this.pageLabel.text(pageText());
                        rebuildPresetList();
                    });
                });
    }

    private void importPreset(RemotePresetClient.PresetSummary preset) {
        if (this.importingPreset) {
            return;
        }

        this.importingPreset = true;
        setStatus(Text.translatable("text.request.import_in_progress", preset.title).getString());

        CompletableFuture.supplyAsync(() -> {
            RemotePresetClient.DownloadedPreset downloadedPreset = this.presetClient.downloadPreset(preset.id);
            return PresetPackageManager.importPreset(downloadedPreset);
        }).whenComplete((importedName, throwable) -> {
            if (this.client == null) {
                this.importingPreset = false;
                return;
            }

            this.client.execute(() -> {
                this.importingPreset = false;
                if (throwable != null) {
                    String message = RequestModClient.formatError(throwable);
                    RequestModClient.logError("Preset import failed: " + message, throwable);
                    setStatus(message);
                } else {
                    setStatus(Text.translatable("message.request.import_success", importedName).getString());
                }
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
