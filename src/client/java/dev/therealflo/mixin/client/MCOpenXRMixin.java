package dev.therealflo.mixin.client;

import dev.therealflo.client.DefaultBindingManager;
import dev.therealflo.client.RuntimeBindingRouter;
import dev.therealflo.client.api.MCOpenXRReload;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler;
import org.vivecraft.client_vr.gameplay.screenhandlers.RadialHandler;
import org.vivecraft.client_vr.provider.control.VRInputAction;
import org.vivecraft.client_vr.provider.control.VRInputActionSet;
import org.vivecraft.client_vr.provider.openxr.MCOpenXR;
import org.vivecraft.client_vr.provider.openxr.XRBindings;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Consumer;

@Mixin(value = MCOpenXR.class, remap = false)
public abstract class MCOpenXRMixin implements MCOpenXRReload {

    @Shadow public abstract void readNewData(VRInputAction action);

    @Shadow public String systemName;

    @Unique
    private String request$lastKnownProfile = RuntimeBindingRouter.OCULUS_TOUCH_PROFILE;

    @Override
    public void reloadXRBindings() {
        RuntimeBindingRouter.getInstance().reloadMappings();
    }

    @Redirect(
        method = "loadDefaultBindings",
        at = @At(
            value = "INVOKE",
            target = "Lorg/vivecraft/client_vr/provider/openxr/XRBindings;getBinding(Ljava/lang/String;)Ljava/util/HashSet;"
        )
    )
    private HashSet<Pair<String, String>> request$bindRawSuperset(String headset) {
        Collection<Pair<String, String>> defaults = XRBindings.getBinding(headset);
        DefaultBindingManager.getInstance().saveDefaultBindingsIfNeeded(headset, defaults);
        return new HashSet<>(RuntimeBindingRouter.getInstance().getRawBindingsForProfile(headset));
    }

    @Redirect(
        method = "updatePose",
        at = @At(value = "INVOKE", target = "Ljava/util/Collection;forEach(Ljava/util/function/Consumer;)V")
    )
    private void request$routeInputReads(Collection<VRInputAction> actions, Consumer<VRInputAction> consumer) {
        RuntimeBindingRouter router = RuntimeBindingRouter.getInstance();
        EnumSet<VRInputActionSet> activeActionSets = request$getActiveActionSets();
        String interactionProfile = request$getCurrentInteractionProfile();
        Map<String, VRInputAction> inputActions = ((MCVRAccessor) this).request$getInputActions();

        for (VRInputAction action : actions) {
            if (router.isRawAction(action)) {
                this.readNewData(action);
            }
        }

        for (VRInputAction action : actions) {
            if (router.isRawAction(action)) {
                continue;
            }

            if ("boolean".equals(action.type) || "vector1".equals(action.type) || "vector2".equals(action.type)) {
                router.routeLogicalAction(action, inputActions, interactionProfile, activeActionSets);
            } else {
                this.readNewData(action);
            }
        }
    }

    @Unique
    private EnumSet<VRInputActionSet> request$getActiveActionSets() {
        var minecraft = ((MCVRAccessor) this).request$getMinecraftClient();
        EnumSet<VRInputActionSet> activeSets = EnumSet.of(
            VRInputActionSet.GLOBAL,
            VRInputActionSet.MOD,
            VRInputActionSet.MIXED_REALITY,
            VRInputActionSet.TECHNICAL
        );

        if (minecraft.currentScreen == null) {
            activeSets.add(VRInputActionSet.INGAME);
            activeSets.add(VRInputActionSet.CONTEXTUAL);
        } else {
            activeSets.add(VRInputActionSet.GUI);
            if (ClientDataHolderVR.getInstance().vrSettings.ingameBindingsInGui) {
                activeSets.add(VRInputActionSet.INGAME);
            }
        }

        if (KeyboardHandler.SHOWING || RadialHandler.isShowing()) {
            activeSets.add(VRInputActionSet.KEYBOARD);
        }

        return activeSets;
    }

    @Unique
    private String request$getCurrentInteractionProfile() {
        String detectedProfile = request$guessInteractionProfile();
        if (!detectedProfile.isBlank()) {
            this.request$lastKnownProfile = detectedProfile;
        }
        return this.request$lastKnownProfile;
    }

    @Unique
    private String request$guessInteractionProfile() {
        return RuntimeBindingRouter.guessProfileFromSystemName(this.systemName);
    }
}
