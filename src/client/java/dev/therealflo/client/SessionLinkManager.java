package dev.therealflo.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates automatic linking of the Minecraft session with the preset
 * server. Screens use {@link #isLinked()} to enable/disable auth-gated
 * buttons and {@link #ensureLinkedAsync()} to trigger a background link.
 */
public final class SessionLinkManager {
    private static final AtomicBoolean LINKING = new AtomicBoolean(false);

    private SessionLinkManager() {
    }

    /** Whether a usable auth token for the preset server exists right now. */
    public static boolean isLinked() {
        return RemotePresetConfig.getInstance().hasUsableToken();
    }

    /**
     * Links the session in the background if needed. Completes with true once
     * a usable session exists, false if linking failed. Never throws; failures
     * are logged and surfaced again when an upload actually needs the link.
     */
    public static CompletableFuture<Boolean> ensureLinkedAsync() {
        if (isLinked()) {
            return CompletableFuture.completedFuture(true);
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
