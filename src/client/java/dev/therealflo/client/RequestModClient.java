package dev.therealflo.client;

import dev.therealflo.client.screens.ChangeBindingScreen;
import dev.therealflo.client.screens.ReloadBindingsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.option.ControlsOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivecraft.api_beta.client.VivecraftClientAPI;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.provider.control.VRInputAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RequestModClient implements ClientModInitializer {
    private boolean registered = false;
    public static final String MOD_ID = "request";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final String CATEGORY = "key.categories.request";
    public static KeyBinding openReloadScreenKey;
    public static KeyBinding openBindingScreenKey;

    public static void logInfo(String s) {
        LOGGER.info("[ReQuest] {}", s);
    }

    public static void logWarn(String s) {
        LOGGER.warn("[ReQuest] {}", s);
    }

    public static void logError(String s) {
        LOGGER.error("[ReQuest] {}", s);
    }

    /**
     * Gets all registered VR input actions from Vivecraft.
     * Returns a list of action paths like "/actions/ingame/in/key.attack"
     */
    public static List<String> getAllRegisteredActions() {
        List<String> actions = new ArrayList<>();
        
        try {
            if (!VivecraftClientAPI.getInstance().isVrInitialized()) {
                logWarn("VR not initialized, cannot get registered actions");
                return actions;
            }
            
            MCVR vr = ClientDataHolderVR.getInstance().vr;
            if (vr == null) {
                logWarn("VR instance is null, cannot get registered actions");
                return actions;
            }
            
            // Get all VRInputActions
            for (VRInputAction action : vr.getInputActions()) {
                // action.name already contains the full path like "/actions/ingame/in/key.attack"
                // So just use it directly without building the path
                actions.add(action.name);
            }
            
            logInfo("Retrieved " + actions.size() + " registered VR actions");
        } catch (Exception e) {
            logError("Failed to get registered actions: " + e.getMessage());
            e.printStackTrace();
        }
        
        return actions;
    }

    @Override
    public void onInitializeClient() {
        logInfo("ReQuest has been loaded");

        openReloadScreenKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.request.open_reload", // Translation key for the keybinding name
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,  // Default key
                CATEGORY                // Category translation key
        ));

        openBindingScreenKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.request.open_binding", // Translation key for the keybinding name
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,  // Default key
                CATEGORY                // Category translation key
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openReloadScreenKey.wasPressed()) {
                client.execute(() -> client.setScreen(new ReloadBindingsScreen()));
            }

            if (openBindingScreenKey.wasPressed()) {
                client.execute(() -> client.setScreen(new ChangeBindingScreen()));
            }

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