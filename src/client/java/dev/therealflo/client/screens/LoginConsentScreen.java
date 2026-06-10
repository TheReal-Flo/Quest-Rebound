package dev.therealflo.client.screens;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.container.Containers;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.function.Consumer;

/**
 * One-time popup asking the user whether the mod may log in to the preset
 * server with their Minecraft session. Shown before the first automatic
 * session link; the decision is persisted by the caller.
 */
public class LoginConsentScreen extends BaseOwoScreen<FlowLayout> {
    private static final String PRIVACY_POLICY_URL = "https://rebound.quest/privacy";

    private final Screen parentScreen;
    private final Consumer<Boolean> onDecision;
    private boolean decided;

    public LoginConsentScreen(Screen parentScreen, Consumer<Boolean> onDecision) {
        this.parentScreen = parentScreen;
        this.onDecision = onDecision;
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
                Text.translatable("screen.request.login_consent"),
                null
        ));

        panel.child(Components.label(Text.translatable("text.request.login_consent_body"))
                .color(RequestUi.TEXT)
                .horizontalSizing(Sizing.fill(100)));

        panel.child(Components.label(Text.translatable("text.request.login_consent_hint"))
                .color(RequestUi.MUTED)
                .horizontalSizing(Sizing.fill(100))
                .margins(Insets.top(6)));

        panel.child(RequestUi.button(
                Text.translatable("button.request.privacy_policy"),
                button -> Util.getOperatingSystem().open(URI.create(PRIVACY_POLICY_URL))
        ).horizontalSizing(Sizing.fill(100)).margins(Insets.top(8)));

        FlowLayout footer = RequestUi.footer();
        footer.child(RequestUi.footerButton(
                Text.translatable("button.request.consent_agree"),
                button -> decide(true)
        ));
        footer.child(RequestUi.footerButton(
                Text.translatable("button.request.consent_decline"),
                button -> decide(false)
        ));
        panel.child(footer);

        rootComponent.child(panel);
    }

    private void decide(boolean agreed) {
        if (this.decided) {
            return;
        }
        this.decided = true;
        if (this.client != null) {
            this.client.setScreen(this.parentScreen);
        }
        this.onDecision.accept(agreed);
    }

    @Override
    public void close() {
        // Closing without choosing counts as declining for this session,
        // but leaves the decision unsaved so the popup appears again.
        if (this.decided) {
            return;
        }
        this.decided = true;
        if (this.client != null) {
            this.client.setScreen(this.parentScreen);
        }
    }
}
