package dev.therealflo.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivecraft.api_beta.client.VivecraftClientAPI;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.provider.control.VRInputAction;

public class RequestModClient implements ClientModInitializer {
    private boolean registered = false;
    public static final String MOD_ID = "request";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void logInfo(String s) {
        LOGGER.info("[ReQuest] {}", s);
    }

    public static void logWarn(String s) {
        LOGGER.warn("[ReQuest] {}", s);
    }

    public static void logError(String s) {
        LOGGER.error("[ReQuest] {}", s);
    }

    @Override
    public void onInitializeClient() {
        logInfo("ReQuest has been loaded");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (registered) return;

            if (!VivecraftClientAPI.getInstance().isVrInitialized()) return;
            if (!VivecraftClientAPI.getInstance().isVrActive()) return;

            MCVR vr = ClientDataHolderVR.getInstance().vr;
            if (vr == null || vr.getInputActions().isEmpty()) return;

            registerRemap(vr);
            registered = true;
            logInfo("Controller remap installed");
        });
    }

    private void registerRemap(MCVR vr) {
        // Log all available actions once
        for (VRInputAction action : vr.getInputActions()) {
            logInfo("VR action available: " + action.name);
        }
    }
}