package dev.therealflo.mixin.client;

import dev.therealflo.client.RequestModClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.provider.control.VRInputAction;

import java.util.Map;

@Mixin(value = MCVR.class, remap = false)
public class MCVRMixin {

    @Shadow
    private Map<String, VRInputAction> inputActions;

    @Shadow
    private Map<String, VRInputAction> inputActionsByKeyBinding;

    /**
     * Intercepts the populateInputActions method after all VRInputActions are created
     * to log information about registered actions, including mod keybinds.
     */
    @Inject(method = "populateInputActions", at = @At("TAIL"))
    private void logRegisteredActions(CallbackInfo ci) {
        RequestModClient.LOGGER.info("[ReQuest] VRInputActions registered successfully");

        // Log all available VRInputActions for debugging
        System.out.println("[ReQuest] Total VRInputActions created: " + inputActionsByKeyBinding.size());
        for (Map.Entry<String, VRInputAction> entry : inputActionsByKeyBinding.entrySet()) {
            VRInputAction action = entry.getValue();
            RequestModClient.LOGGER.info("[ReQuest]   - {} -> {} (ActionSet: {})", entry.getKey(), action.name, action.actionSet.name);
        }
    }
}