package dev.therealflo.client.screens;

import dev.therealflo.client.BindingSetRegistry;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

public class BindingSetNameScreen extends BaseOwoScreen<FlowLayout> {
    public enum Mode {
        CREATE,
        DUPLICATE,
        RENAME
    }

    private final BindingSetManagerScreen parentScreen;
    private final String interactionProfile;
    private final Mode mode;
    private final String sourceSetId;
    private final String initialValue;
    private TextBoxComponent nameField;
    private ButtonComponent saveButton;

    public BindingSetNameScreen(
            BindingSetManagerScreen parentScreen,
            String interactionProfile,
            Mode mode,
            String sourceSetId,
            String initialValue
    ) {
        this.parentScreen = parentScreen;
        this.interactionProfile = interactionProfile;
        this.mode = mode;
        this.sourceSetId = sourceSetId;
        this.initialValue = initialValue;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        RequestUi.root(rootComponent);

        FlowLayout panel = RequestUi.dialogPanel();
        panel.child(RequestUi.header(
                Text.translatable(switch (mode) {
                    case CREATE -> "screen.request.create_set";
                    case DUPLICATE -> "screen.request.duplicate_set";
                    case RENAME -> "screen.request.rename_set";
                }),
                Text.translatable(mode == Mode.RENAME ? "text.request.rename_set_hint" : "text.request.create_set_hint")
        ));

        this.nameField = Components.textBox(Sizing.fill(100), initialValue);
        this.nameField.setMaxLength(64);
        this.nameField.onChanged().subscribe(value -> {
            if (this.saveButton != null) {
                this.saveButton.active = !value.trim().isEmpty();
            }
        });
        panel.child(this.nameField.margins(Insets.vertical(4)));

        FlowLayout footer = RequestUi.footer();
        this.saveButton = RequestUi.footerButton(
                Text.translatable("button.request.save"),
                button -> submit()
        );
        this.saveButton.active = !initialValue.trim().isEmpty();
        footer.child(this.saveButton);
        footer.child(RequestUi.footerButton(
                Text.translatable("button.request.cancel"),
                button -> this.close()
        ));
        panel.child(footer);

        rootComponent.child(panel);
    }

    @Override
    protected void init() {
        super.init();
        // Focus the name field once the adapter is fully mounted; the focus
        // handler does not exist yet while build() is running.
        if (this.uiAdapter != null && this.nameField != null) {
            this.uiAdapter.rootComponent.focusHandler().focus(
                    this.nameField, io.wispforest.owo.ui.core.Component.FocusSource.KEYBOARD_CYCLE);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void submit() {
        String value = this.nameField.getText().trim();
        if (value.isEmpty()) {
            return;
        }

        BindingSetRegistry registry = BindingSetRegistry.getInstance();
        switch (this.mode) {
            case CREATE -> registry.createSet(this.interactionProfile, value, null);
            case DUPLICATE -> registry.duplicateSet(this.interactionProfile, this.sourceSetId, value);
            case RENAME -> registry.renameSet(this.interactionProfile, this.sourceSetId, value);
        }

        this.close();
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.parentScreen.refresh();
            this.client.setScreen(this.parentScreen);
        } else {
            super.close();
        }
    }
}
