package dev.therealflo.client.screens;

import dev.therealflo.client.BindingSetRegistry;
import dev.therealflo.client.RequestModClient;
import dev.therealflo.client.SessionLinkManager;
import dev.therealflo.client.screens.BindingSetNameScreen.Mode;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class BindingSetManagerScreen extends BaseOwoScreen<FlowLayout> {
    private final Screen parentScreen;
    private final String interactionProfile;

    public BindingSetManagerScreen(Screen parentScreen) {
        this(parentScreen, RequestModClient.getPreferredInteractionProfile());
    }

    public BindingSetManagerScreen(Screen parentScreen, String interactionProfile) {
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

        BindingSetRegistry registry = BindingSetRegistry.getInstance();

        FlowLayout panel = RequestUi.panel(90, 90);
        panel.child(RequestUi.header(
                Text.translatable("screen.request.binding_sets"),
                Text.translatable("text.request.binding_set_hint")
        ));

        FlowLayout listContent = RequestUi.listContent();
        List<BindingSetRegistry.BindingSetEntry> entries = registry.getOrderedSets(interactionProfile);
        for (int i = 0; i < entries.size(); i++) {
            listContent.child(buildSetCard(registry, entries.get(i), i == 0, i == entries.size() - 1));
        }
        panel.child(RequestUi.scrollArea(listContent));

        FlowLayout footer = RequestUi.footer();
        footer.child(RequestUi.footerButton(
                Text.translatable("button.request.create_set"),
                button -> {
                    if (this.client != null) {
                        this.client.setScreen(new BindingSetNameScreen(
                                this, interactionProfile, Mode.CREATE, null,
                                registry.suggestNewSetName(interactionProfile)
                        ));
                    }
                }
        ));
        footer.child(RequestUi.footerButton(
                Text.translatable("button.request.browse_presets"),
                button -> {
                    if (this.client != null) {
                        this.client.setScreen(new PresetBrowserScreen(this, interactionProfile));
                    }
                }
        ));
        var uploadButton = RequestUi.footerButton(
                Text.translatable("button.request.upload_preset"),
                button -> {
                    if (this.client != null) {
                        this.client.setScreen(new PresetUploadScreen(
                                this, interactionProfile,
                                registry.getActiveSetId(interactionProfile)
                        ));
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

        SessionLinkManager.ensureLinkedAsync().thenAccept(linked -> {
            if (linked && this.client != null) {
                this.client.execute(() -> uploadButton.active = true);
            }
        });
    }

    private FlowLayout buildSetCard(BindingSetRegistry registry, BindingSetRegistry.BindingSetEntry entry,
                                    boolean first, boolean last) {
        FlowLayout card = RequestUi.card(entry.active());

        // Name line with status badges
        FlowLayout nameRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        nameRow.verticalAlignment(VerticalAlignment.CENTER);
        nameRow.gap(6);
        nameRow.child(Components.label(Text.literal(entry.displayName()))
                .color(entry.active() ? RequestUi.ACCENT : RequestUi.TEXT)
                .shadow(entry.active()));
        if (entry.active()) {
            nameRow.child(Components.label(Text.translatable("text.request.badge_active")).color(RequestUi.SUCCESS));
        }
        if (!entry.enabled()) {
            nameRow.child(Components.label(Text.translatable("text.request.badge_disabled")).color(RequestUi.MUTED));
        }
        card.child(nameRow.margins(Insets.bottom(2)));

        FlowLayout actions = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        actions.gap(4);

        if (!entry.active()) {
            actions.child(Components.button(
                    Text.translatable("button.request.activate_set"),
                    button -> {
                        registry.setActiveSet(interactionProfile, entry.id());
                        refresh();
                    }
            ));
        }

        actions.child(Components.button(
                Text.translatable("button.request.edit_set"),
                button -> {
                    if (this.client != null) {
                        this.client.setScreen(new ChangeBindingScreen(this, interactionProfile, entry.id()));
                    }
                }
        ));

        if (!"default".equals(entry.id())) {
            actions.child(Components.button(
                    Text.translatable("button.request.rename_set"),
                    button -> {
                        if (this.client != null) {
                            this.client.setScreen(new BindingSetNameScreen(
                                    this, interactionProfile, Mode.RENAME, entry.id(), entry.displayName()
                            ));
                        }
                    }
            ));
        }

        actions.child(Components.button(
                Text.translatable("button.request.duplicate_set"),
                button -> {
                    if (this.client != null) {
                        this.client.setScreen(new BindingSetNameScreen(
                                this, interactionProfile, Mode.DUPLICATE, entry.id(), entry.displayName() + " Copy"
                        ));
                    }
                }
        ));

        actions.child(Components.button(
                Text.translatable(entry.enabled() ? "button.request.disable_set" : "button.request.enable_set"),
                button -> {
                    registry.setEnabled(interactionProfile, entry.id(), !entry.enabled());
                    refresh();
                }
        ));

        var moveUp = Components.button(
                Text.literal("▲"),
                button -> {
                    registry.moveSet(interactionProfile, entry.id(), -1);
                    refresh();
                }
        );
        moveUp.active = !first;
        actions.child(moveUp);

        var moveDown = Components.button(
                Text.literal("▼"),
                button -> {
                    registry.moveSet(interactionProfile, entry.id(), 1);
                    refresh();
                }
        );
        moveDown.active = !last;
        actions.child(moveDown);

        if (!"default".equals(entry.id())) {
            actions.child(Components.button(
                    Text.translatable("button.request.delete_set"),
                    button -> {
                        registry.deleteSet(interactionProfile, entry.id());
                        refresh();
                    }
            ));
        }

        card.child(actions);
        return card;
    }

    public void refresh() {
        if (this.uiAdapter != null && this.uiAdapter.rootComponent != null) {
            this.uiAdapter.rootComponent.clearChildren();
            this.build(this.uiAdapter.rootComponent);
        }
    }

    @Override
    public void close() {
        if (this.client != null && this.parentScreen != null) {
            this.client.setScreen(this.parentScreen);
            return;
        }
        super.close();
    }
}
