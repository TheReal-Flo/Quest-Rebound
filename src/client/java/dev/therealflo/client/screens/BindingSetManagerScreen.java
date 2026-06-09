package dev.therealflo.client.screens;

import dev.therealflo.client.BindingSetRegistry;
import dev.therealflo.client.RequestModClient;
import dev.therealflo.client.screens.BindingSetNameScreen.Mode;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

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
        rootComponent
                .surface(Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(io.wispforest.owo.ui.core.VerticalAlignment.CENTER);

        BindingSetRegistry registry = BindingSetRegistry.getInstance();

        FlowLayout mainContainer = Containers.verticalFlow(Sizing.fill(92), Sizing.fill(92));
        mainContainer.padding(Insets.of(10));

        mainContainer.child(
                Components.label(Text.translatable("screen.request.binding_sets"))
                        .color(Color.ofRgb(0xFFFFFF))
                        .shadow(true)
                        .margins(Insets.bottom(4))
        );

        mainContainer.child(
                Components.label(Text.literal(interactionProfile))
                        .color(Color.ofRgb(0xAAAAAA))
                        .margins(Insets.bottom(4))
        );

        mainContainer.child(
                Components.label(Text.translatable("text.request.binding_set_hint"))
                        .color(Color.ofRgb(0xC8C8C8))
                        .margins(Insets.bottom(8))
        );

        ScrollContainer<FlowLayout> scrollContainer = Containers.verticalScroll(
                Sizing.fill(100),
                Sizing.fill(78),
                Containers.verticalFlow(Sizing.fill(100), Sizing.content())
        );
        FlowLayout scrollContent = (FlowLayout) scrollContainer.child();
        scrollContent.gap(4);

        for (BindingSetRegistry.BindingSetEntry entry : registry.getOrderedSets(interactionProfile)) {
            FlowLayout row = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
            row.padding(Insets.of(6));
            row.surface(Surface.PANEL);

            StringBuilder line = new StringBuilder(entry.displayName());
            if (entry.active()) {
                line.append(" [Active]");
            }
            if (!entry.enabled()) {
                line.append(" [Disabled]");
            }

            row.child(
                    Components.label(Text.literal(line.toString()))
                            .color(Color.ofRgb(entry.active() ? 0x00FFAA : 0xFFFFFF))
                            .shadow(entry.active())
                            .margins(Insets.bottom(2))
            );

            FlowLayout primaryActions = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            primaryActions.gap(4);

            primaryActions.child(Components.button(
                    Text.translatable("button.request.activate_set"),
                    button -> {
                        registry.setActiveSet(interactionProfile, entry.id());
                        refresh();
                    }
            ));

            primaryActions.child(Components.button(
                    Text.translatable("button.request.edit_set"),
                    button -> {
                        if (this.client != null) {
                            this.client.setScreen(new ChangeBindingScreen(this, interactionProfile, entry.id()));
                        }
                    }
            ));

            if (!"default".equals(entry.id())) {
                primaryActions.child(Components.button(
                        Text.translatable("button.request.rename_set"),
                        button -> {
                            if (this.client != null) {
                                this.client.setScreen(new BindingSetNameScreen(
                                        this,
                                        interactionProfile,
                                        Mode.RENAME,
                                        entry.id(),
                                        entry.displayName()
                                ));
                            }
                        }
                ));
            }

            primaryActions.child(Components.button(
                    Text.translatable(entry.enabled() ? "button.request.disable_set" : "button.request.enable_set"),
                    button -> {
                        registry.setEnabled(interactionProfile, entry.id(), !entry.enabled());
                        refresh();
                    }
            ));

            FlowLayout secondaryActions = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            secondaryActions.gap(4);

            secondaryActions.child(Components.button(
                    Text.translatable("button.request.move_up"),
                    button -> {
                        registry.moveSet(interactionProfile, entry.id(), -1);
                        refresh();
                    }
            ));

            secondaryActions.child(Components.button(
                    Text.translatable("button.request.move_down"),
                    button -> {
                        registry.moveSet(interactionProfile, entry.id(), 1);
                        refresh();
                    }
            ));

            secondaryActions.child(Components.button(
                    Text.translatable("button.request.duplicate_set"),
                    button -> {
                        if (this.client != null) {
                            this.client.setScreen(new BindingSetNameScreen(
                                    this,
                                    interactionProfile,
                                    Mode.DUPLICATE,
                                    entry.id(),
                                    entry.displayName() + " Copy"
                            ));
                        }
                    }
            ));

            if (!"default".equals(entry.id())) {
                secondaryActions.child(Components.button(
                        Text.translatable("button.request.delete_set"),
                        button -> {
                            registry.deleteSet(interactionProfile, entry.id());
                            refresh();
                        }
                ));
            }

            row.child(primaryActions);
            row.child(secondaryActions);
            scrollContent.child(row);
        }

        mainContainer.child(scrollContainer);

        FlowLayout buttonRow = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        buttonRow.horizontalAlignment(HorizontalAlignment.CENTER);
        buttonRow.gap(6);
        buttonRow.margins(Insets.top(10));

        buttonRow.child(Components.button(
                Text.translatable("button.request.create_set"),
                button -> {
                    if (this.client != null) {
                        this.client.setScreen(new BindingSetNameScreen(
                                this,
                                interactionProfile,
                                Mode.CREATE,
                                null,
                                registry.suggestNewSetName(interactionProfile)
                        ));
                    }
                }
        ));

        buttonRow.child(Components.button(
                Text.translatable("button.request.browse_presets"),
                button -> {
                    if (this.client != null) {
                        this.client.setScreen(new PresetBrowserScreen(this, interactionProfile));
                    }
                }
        ));

        buttonRow.child(Components.button(
                Text.translatable("button.request.upload_preset"),
                button -> {
                    if (this.client != null) {
                        this.client.setScreen(new PresetUploadScreen(
                            this,
                            interactionProfile,
                            registry.getActiveSetId(interactionProfile)
                        ));
                    }
                }
        ));

        buttonRow.child(Components.button(
                Text.literal("Back"),
                button -> close()
        ));

        mainContainer.child(buttonRow);
        rootComponent.child(mainContainer);
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
