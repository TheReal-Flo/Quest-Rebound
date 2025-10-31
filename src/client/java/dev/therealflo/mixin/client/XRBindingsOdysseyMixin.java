package dev.therealflo.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.client_vr.provider.openxr.XRBindings;

import java.util.HashSet;

/**
 * Mixin to map Samsung Odyssey controller bindings to Quest 2 bindings.
 * This allows Odyssey users to use Quest 2 controller layout since they're similar.
 */
@Mixin(value = XRBindings.class, remap = false)
public abstract class XRBindingsOdysseyMixin {

    @Unique
    private static final String ODYSSEY_PROFILE = "/interaction_profiles/samsung/odyssey_controller";

    /**
     * Invoke the private/static Quest 2 binding builder without modifying Vivecraft.
     */
    @Invoker("quest2Bindings")
    private static HashSet request$invokeQuest2Bindings() {
        throw new AssertionError("Mixin invoker not applied");
    }

    /**
     * Return Quest 2 bindings when Odyssey profile is requested.
     * This allows Odyssey controllers to use the Quest 2 binding layout.
     */
    @Inject(method = "getBinding", at = @At("HEAD"), cancellable = true)
    private static void request$injectOdysseyMappings(String headsetProfile,
                                                      CallbackInfoReturnable<HashSet> cir) {
        if (ODYSSEY_PROFILE.equals(headsetProfile)) {
            HashSet quest2Bindings = request$invokeQuest2Bindings();
            // Short-circuit: use Quest 2 bindings for Odyssey
            cir.setReturnValue(quest2Bindings);
        }
    }

    /**
     * Ensure Odyssey profile appears in supported headsets so Vivecraft suggests it.
     */
    @Inject(method = "supportedHeadsets", at = @At("RETURN"))
    private static void request$addOdysseyToSupported(CallbackInfoReturnable<HashSet> cir) {
        HashSet<String> set = cir.getReturnValue();
        if (!set.contains(ODYSSEY_PROFILE)) {
            set.add(ODYSSEY_PROFILE);
        }
    }
}
