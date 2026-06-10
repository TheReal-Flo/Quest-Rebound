package dev.therealflo.client;

import dev.therealflo.client.RemotePresetConfig.LoginConsent;
import dev.therealflo.client.screens.LoginConsentScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Coordinates automatic linking of the Minecraft session with the preset
 * server. Screens use {@link #isLinked()} to enable/disable auth-gated
 * buttons and {@link #ensureLinkedWithConsent(Screen, Consumer)} to trigger
 * a background link. Linking only ever happens after the user has explicitly
 * agreed to it in the {@link LoginConsentScreen}; non-authed features keep
 * working when consent is denied.
 */
public final class SessionLinkManager {
    private static final AtomicBoolean LINKING = new AtomicBoolean(false);
    /** Avoids re-opening the consent popup when it was dismissed without a decision. */
    private static final AtomicBoolean CONSENT_ASKED_THIS_SESSION = new AtomicBoolean(false);

    private SessionLinkManager() {
    }

    /** Whether a usable auth token for the preset server exists right now. */
    public static boolean isLinked() {
        return RemotePresetConfig.getInstance().hasUsableToken();
    }

    /**
     * Ensures the session is linked, asking the user for consent first if
     * they have not decided yet. If consent is needed, the current screen is
     * temporarily replaced by a {@link LoginConsentScreen} that returns to
     * {@code returnTo} afterwards. {@code onResult} is called on an arbitrary
     * thread with whether a usable session exists.
     */
    public static void ensureLinkedWithConsent(Screen returnTo, Consumer<Boolean> onResult) {
        switch (RemotePresetConfig.getInstance().getLoginConsent()) {
            case GRANTED -> ensureLinkedAsync().thenAccept(onResult);
            case DENIED -> onResult.accept(false);
            case UNDECIDED -> {
                if (!CONSENT_ASKED_THIS_SESSION.compareAndSet(false, true)) {
                    onResult.accept(false);
                    return;
                }
                MinecraftClient client = MinecraftClient.getInstance();
                client.execute(() -> client.setScreen(new LoginConsentScreen(returnTo, agreed -> {
                    RemotePresetConfig.getInstance().setLoginConsent(
                            agreed ? LoginConsent.GRANTED : LoginConsent.DENIED);
                    if (agreed) {
                        ensureLinkedAsync().thenAccept(onResult);
                    } else {
                        onResult.accept(false);
                    }
                })));
            }
        }
    }

    /**
     * Links the session in the background if needed and consent was granted.
     * Completes with true once a usable session exists, false if linking
     * failed or is not allowed. Never throws; failures are logged and
     * surfaced again when an upload actually needs the link.
     */
    public static CompletableFuture<Boolean> ensureLinkedAsync() {
        if (isLinked()) {
            return CompletableFuture.completedFuture(true);
        }
        if (RemotePresetConfig.getInstance().getLoginConsent() != LoginConsent.GRANTED) {
            return CompletableFuture.completedFuture(false);
        }
        if (!LINKING.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                new RemotePresetClient().ensureLinkedSession();
                return true;
            } catch (Exception e) {
                RequestModClient.logError(
                        "Automatic session link failed: " + RequestModClient.formatError(e), e);
                return false;
            } finally {
                LINKING.set(false);
            }
        });
    }
}
