package dev.therealflo.mixin.client;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.provider.control.VRInputAction;

import java.util.Map;

@Mixin(value = MCVR.class, remap = false)
public interface MCVRAccessor {

    @Accessor("mc")
    MinecraftClient request$getMinecraftClient();

    @Accessor("inputActions")
    Map<String, VRInputAction> request$getInputActions();
}
