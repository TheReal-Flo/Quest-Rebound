package dev.therealflo.mixin.client;

import dev.therealflo.client.RuntimeBindingRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.provider.control.VRInputAction;

import java.util.Map;

@Mixin(value = MCVR.class, remap = false)
public abstract class MCVRMixin {

    @Shadow
    protected Map<String, VRInputAction> inputActions;

    @Shadow
    protected Map<String, VRInputAction> inputActionsByKeyBinding;

    @Inject(method = "populateInputActions", at = @At("TAIL"))
    private void request$registerRawActions(CallbackInfo ci) {
        RuntimeBindingRouter.getInstance().ensureRawActionsRegistered(this.inputActions, this.inputActionsByKeyBinding);
    }
}
