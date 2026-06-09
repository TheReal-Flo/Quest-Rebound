package dev.therealflo.client.screens;

import dev.therealflo.client.BindingSetRegistry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class BindingSetNameScreen extends Screen {
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
    private TextFieldWidget nameField;
    private ButtonWidget saveButton;

    public BindingSetNameScreen(
        BindingSetManagerScreen parentScreen,
        String interactionProfile,
        Mode mode,
        String sourceSetId,
        String initialValue
    ) {
        super(Text.translatable(switch (mode) {
            case CREATE -> "screen.request.create_set";
            case DUPLICATE -> "screen.request.duplicate_set";
            case RENAME -> "screen.request.rename_set";
        }));
        this.parentScreen = parentScreen;
        this.interactionProfile = interactionProfile;
        this.mode = mode;
        this.sourceSetId = sourceSetId;
        this.initialValue = initialValue;
    }

    @Override
    protected void init() {
        int fieldWidth = 220;
        int fieldHeight = 20;
        int centerX = this.width / 2;
        int top = this.height / 2 - 20;

        this.nameField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, top, fieldWidth, fieldHeight, Text.empty());
        this.nameField.setMaxLength(64);
        this.saveButton = this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("button.request.save"),
                button -> this.request$submit()
        ).dimensions(centerX - 110, top + 32, 105, 20).build());
        this.nameField.setChangedListener(value -> {
            if (this.saveButton != null) {
                this.saveButton.active = !value.trim().isEmpty();
            }
        });
        this.nameField.setText(this.initialValue);
        this.addDrawableChild(this.nameField);
        this.setInitialFocus(this.nameField);
        this.saveButton.active = !this.initialValue.trim().isEmpty();

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("button.request.cancel"),
                button -> this.close()
        ).dimensions(centerX + 5, top + 32, 105, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 52, 0xFFFFFF);
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.translatable(this.mode == Mode.RENAME ? "text.request.rename_set_hint" : "text.request.create_set_hint"),
            this.width / 2,
            this.height / 2 - 36,
            0xA0A0A0
        );
        super.render(context, mouseX, mouseY, delta);
        this.nameField.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            request$submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void request$submit() {
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

        this.parentScreen.refresh();
        if (this.client != null) {
            this.client.setScreen(this.parentScreen);
        }
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
