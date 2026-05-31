package dev.therealflo.client;

import net.minecraft.client.option.KeyBinding;
import org.apache.commons.lang3.tuple.Pair;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.client_vr.provider.control.VRInputAction;
import org.vivecraft.client_vr.provider.control.VRInputActionSet;
import org.vivecraft.client_vr.provider.openxr.XRBindings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builds the immutable raw OpenXR action superset and routes raw state into logical Vivecraft actions.
 */
public final class RuntimeBindingRouter {
    public static final String OCULUS_TOUCH_PROFILE = "/interaction_profiles/oculus/touch_controller";
    public static final String PICO4_PROFILE = "/interaction_profiles/bytedance/pico4_controller";
    public static final String PICO_NEO3_PROFILE = "/interaction_profiles/bytedance/pico_neo3_controller";
    public static final String VIVE_PROFILE = "/interaction_profiles/htc/vive_controller";
    public static final String VIVE_COSMOS_PROFILE = "/interaction_profiles/htc/vive_cosmos_controller";
    public static final String ODYSSEY_PROFILE = "/interaction_profiles/samsung/odyssey_controller";

    private static final String RAW_KEY_PREFIX = "request.raw.";
    private static final List<String> KNOWN_PROFILES = List.of(
        OCULUS_TOUCH_PROFILE,
        PICO4_PROFILE,
        PICO_NEO3_PROFILE,
        VIVE_PROFILE,
        VIVE_COSMOS_PROFILE,
        ODYSSEY_PROFILE
    );

    private static final RuntimeBindingRouter INSTANCE = new RuntimeBindingRouter();

    private final Object lock = new Object();
    private final Map<RawBindingKey, RawActionDefinition> rawDefinitionsByKey = new LinkedHashMap<>();
    private final Map<String, RawActionDefinition> rawDefinitionsByActionName = new LinkedHashMap<>();
    private final Map<String, Collection<Pair<String, String>>> defaultBindingsByProfile = new HashMap<>();
    private final Map<String, Collection<Pair<String, String>>> rawBindingsByProfile = new HashMap<>();
    private final Map<String, Map<String, List<RouteSource>>> routeCacheByProfile = new HashMap<>();
    private boolean rawDefinitionsBuilt;

    private RuntimeBindingRouter() {}

    public static RuntimeBindingRouter getInstance() {
        return INSTANCE;
    }

    public static String guessProfileFromSystemName(String systemName) {
        if (systemName == null || systemName.isBlank()) {
            return "";
        }

        String name = systemName.toLowerCase(java.util.Locale.ROOT);

        if (name.contains("cosmos")) {
            return VIVE_COSMOS_PROFILE;
        }
        if (name.contains("pico")) {
            return OCULUS_TOUCH_PROFILE;
        }
        if (name.contains("quest") || name.contains("oculus") || name.contains("meta")) {
            return OCULUS_TOUCH_PROFILE;
        }
        if (name.contains("odyssey") || name.contains("windows")) {
            return ODYSSEY_PROFILE;
        }
        if (name.contains("vive")) {
            return VIVE_PROFILE;
        }

        return "";
    }

    public void ensureRawActionsRegistered(
        Map<String, VRInputAction> inputActions,
        Map<String, VRInputAction> inputActionsByKeyBinding)
    {
        synchronized (this.lock) {
            if (!this.rawDefinitionsBuilt) {
                buildRawDefinitions(inputActions);
            }

            for (RawActionDefinition definition : this.rawDefinitionsByActionName.values()) {
                if (inputActions.containsKey(definition.actionName())) {
                    continue;
                }

                KeyBinding keyMapping = new KeyBinding(definition.keyName(), -1, "key.categories.misc");
                VRInputAction rawAction = new VRInputAction(
                    keyMapping,
                    "optional",
                    definition.type(),
                    VRInputActionSet.GLOBAL
                );

                inputActions.put(rawAction.name, rawAction);
                inputActionsByKeyBinding.put(rawAction.keyBinding.getTranslationKey(), rawAction);
            }
        }
    }

    public void reloadMappings() {
        synchronized (this.lock) {
            this.routeCacheByProfile.clear();
        }
    }

    public Collection<Pair<String, String>> getRawBindingsForProfile(String interactionProfile) {
        synchronized (this.lock) {
            return new LinkedHashSet<>(this.rawBindingsByProfile.getOrDefault(interactionProfile, List.of()));
        }
    }

    public boolean isRawAction(VRInputAction action) {
        return action != null && isRawActionName(action.name);
    }

    public boolean isRawActionName(String actionName) {
        return actionName != null && actionName.startsWith(VRInputActionSet.GLOBAL.name + "/in/" + RAW_KEY_PREFIX);
    }

    public boolean canBind(String interactionProfile, String inputPath, String actionType) {
        synchronized (this.lock) {
            if (!this.rawDefinitionsBuilt) {
                return false;
            }
            return this.rawDefinitionsByKey.containsKey(new RawBindingKey(inputPath, actionType));
        }
    }

    public void routeLogicalAction(
        VRInputAction logicalAction,
        Map<String, VRInputAction> inputActions,
        String interactionProfile,
        EnumSet<VRInputActionSet> activeActionSets)
    {
        if (isRawAction(logicalAction)) {
            return;
        }

        Map<String, List<RouteSource>> routesByAction = getRoutesForProfile(interactionProfile, inputActions);
        List<RouteSource> routes = routesByAction.getOrDefault(logicalAction.name, List.of());
        boolean actionSetActive = activeActionSets.contains(logicalAction.actionSet);

        if (logicalAction.isHanded()) {
            for (ControllerType hand : ControllerType.values()) {
                routeIntoHand(logicalAction, hand, routes, inputActions, actionSetActive);
            }
        } else {
            routeIntoIndex(logicalAction, 0, null, routes, inputActions, actionSetActive);
        }
    }

    private Map<String, List<RouteSource>> getRoutesForProfile(
        String interactionProfile,
        Map<String, VRInputAction> inputActions)
    {
        synchronized (this.lock) {
            String normalizedProfile = DefaultBindingManager.getInstance().getUnifiedProfile(
                interactionProfile == null || interactionProfile.isBlank() ? OCULUS_TOUCH_PROFILE : interactionProfile
            );

            return this.routeCacheByProfile.computeIfAbsent(normalizedProfile, profile -> {
                Collection<Pair<String, String>> fallbackBindings =
                    this.defaultBindingsByProfile.getOrDefault(profile, XRBindings.getBinding(profile));
                Collection<Pair<String, String>> configuredBindings = DefaultBindingManager.getInstance()
                    .loadBindingsForActiveProfile(profile, fallbackBindings);

                Map<String, List<RouteSource>> routes = new HashMap<>();
                if (configuredBindings == null) {
                    return routes;
                }

                for (Pair<String, String> binding : configuredBindings) {
                    VRInputAction logicalAction = inputActions.get(binding.getLeft());
                    if (logicalAction == null || isRawAction(logicalAction)) {
                        continue;
                    }

                    RawActionDefinition rawDefinition = this.rawDefinitionsByKey.get(
                        new RawBindingKey(binding.getRight(), logicalAction.type)
                    );
                    if (rawDefinition == null) {
                        continue;
                    }

                    routes.computeIfAbsent(logicalAction.name, ignored -> new ArrayList<>())
                        .add(new RouteSource(rawDefinition.actionName(), handForPath(binding.getRight())));
                }

                return routes;
            });
        }
    }

    private void routeIntoHand(
        VRInputAction logicalAction,
        ControllerType hand,
        List<RouteSource> routes,
        Map<String, VRInputAction> inputActions,
        boolean actionSetActive)
    {
        routeIntoIndex(logicalAction, hand.ordinal(), hand, routes, inputActions, actionSetActive);
    }

    private void routeIntoIndex(
        VRInputAction logicalAction,
        int index,
        ControllerType hand,
        List<RouteSource> routes,
        Map<String, VRInputAction> inputActions,
        boolean actionSetActive)
    {
        List<VRInputAction> rawActions = new ArrayList<>();
        for (RouteSource route : routes) {
            if (hand != null && route.hand() != null && route.hand() != hand) {
                continue;
            }

            VRInputAction rawAction = inputActions.get(route.rawActionName());
            if (rawAction != null) {
                rawActions.add(rawAction);
            }
        }

        switch (logicalAction.type) {
            case "boolean" -> routeBoolean(logicalAction, index, rawActions, actionSetActive);
            case "vector1" -> routeVector1(logicalAction, index, rawActions, actionSetActive);
            case "vector2" -> routeVector2(logicalAction, index, rawActions, actionSetActive);
            default -> clearLogicalState(logicalAction, index, logicalAction.type, actionSetActive);
        }
    }

    private void routeBoolean(VRInputAction logicalAction, int index, List<VRInputAction> rawActions, boolean active) {
        boolean previousState = logicalAction.digitalData[index].state;
        boolean newState = false;
        boolean anyActive = false;
        long activeOrigin = 0L;
        long pressedOrigin = 0L;

        if (active) {
            for (VRInputAction rawAction : rawActions) {
                boolean rawActive;
                boolean rawState;
                long rawOrigin;

                if ("boolean".equals(rawAction.type)) {
                    rawActive = rawAction.digitalData[0].isActive;
                    rawState = rawAction.digitalData[0].state;
                    rawOrigin = rawAction.digitalData[0].activeOrigin;
                } else {
                    rawActive = rawAction.analogData[0].isActive;
                    rawState = analogPressed(rawAction);
                    rawOrigin = rawAction.analogData[0].activeOrigin;
                }

                anyActive |= rawActive;
                newState |= rawState;

                if (rawActive && activeOrigin == 0L) {
                    activeOrigin = rawOrigin;
                }
                if (rawState && pressedOrigin == 0L) {
                    pressedOrigin = rawOrigin;
                }
            }
        }

        logicalAction.digitalData[index].state = active && newState;
        logicalAction.digitalData[index].isActive = active && anyActive;
        logicalAction.digitalData[index].isChanged = previousState != logicalAction.digitalData[index].state;
        logicalAction.digitalData[index].activeOrigin =
            logicalAction.digitalData[index].state ? pressedOrigin : (logicalAction.digitalData[index].isActive ? activeOrigin : 0L);
    }

    private void routeVector1(VRInputAction logicalAction, int index, List<VRInputAction> rawActions, boolean active) {
        float previousX = logicalAction.analogData[index].x;
        float newX = 0.0F;
        boolean anyActive = false;
        long origin = 0L;

        if (active) {
            float bestMagnitude = -1.0F;
            for (VRInputAction rawAction : rawActions) {
                float candidateX;
                boolean rawActive;
                long candidateOrigin;

                if ("boolean".equals(rawAction.type)) {
                    rawActive = rawAction.digitalData[0].isActive;
                    candidateX = rawAction.digitalData[0].state ? 1.0F : 0.0F;
                    candidateOrigin = rawAction.digitalData[0].activeOrigin;
                } else {
                    rawActive = rawAction.analogData[0].isActive;
                    candidateX = rawAction.analogData[0].x;
                    candidateOrigin = rawAction.analogData[0].activeOrigin;
                }

                anyActive |= rawActive;
                float magnitude = Math.abs(candidateX);
                if (rawActive && magnitude >= bestMagnitude) {
                    bestMagnitude = magnitude;
                    newX = candidateX;
                    origin = candidateOrigin;
                }
            }
        }

        logicalAction.analogData[index].deltaX = (active && anyActive ? newX : 0.0F) - previousX;
        logicalAction.analogData[index].deltaY = 0.0F;
        logicalAction.analogData[index].deltaZ = 0.0F;
        logicalAction.analogData[index].x = active && anyActive ? newX : 0.0F;
        logicalAction.analogData[index].y = 0.0F;
        logicalAction.analogData[index].z = 0.0F;
        logicalAction.analogData[index].isActive = active && anyActive;
        logicalAction.analogData[index].isChanged = logicalAction.analogData[index].deltaX != 0.0F;
        logicalAction.analogData[index].activeOrigin = logicalAction.analogData[index].isActive ? origin : 0L;
    }

    private void routeVector2(VRInputAction logicalAction, int index, List<VRInputAction> rawActions, boolean active) {
        float previousX = logicalAction.analogData[index].x;
        float previousY = logicalAction.analogData[index].y;
        float newX = 0.0F;
        float newY = 0.0F;
        boolean anyActive = false;
        long origin = 0L;

        if (active) {
            float bestMagnitude = -1.0F;
            for (VRInputAction rawAction : rawActions) {
                if (!Objects.equals(rawAction.type, "vector2")) {
                    continue;
                }

                boolean rawActive = rawAction.analogData[0].isActive;
                float candidateX = rawAction.analogData[0].x;
                float candidateY = rawAction.analogData[0].y;
                float magnitude = candidateX * candidateX + candidateY * candidateY;

                anyActive |= rawActive;
                if (rawActive && magnitude >= bestMagnitude) {
                    bestMagnitude = magnitude;
                    newX = candidateX;
                    newY = candidateY;
                    origin = rawAction.analogData[0].activeOrigin;
                }
            }
        }

        logicalAction.analogData[index].deltaX = (active && anyActive ? newX : 0.0F) - previousX;
        logicalAction.analogData[index].deltaY = (active && anyActive ? newY : 0.0F) - previousY;
        logicalAction.analogData[index].deltaZ = 0.0F;
        logicalAction.analogData[index].x = active && anyActive ? newX : 0.0F;
        logicalAction.analogData[index].y = active && anyActive ? newY : 0.0F;
        logicalAction.analogData[index].z = 0.0F;
        logicalAction.analogData[index].isActive = active && anyActive;
        logicalAction.analogData[index].isChanged =
            logicalAction.analogData[index].deltaX != 0.0F || logicalAction.analogData[index].deltaY != 0.0F;
        logicalAction.analogData[index].activeOrigin = logicalAction.analogData[index].isActive ? origin : 0L;
    }

    private void clearLogicalState(VRInputAction logicalAction, int index, String actionType, boolean active) {
        if ("boolean".equals(actionType)) {
            boolean previousState = logicalAction.digitalData[index].state;
            logicalAction.digitalData[index].state = false;
            logicalAction.digitalData[index].isActive = false;
            logicalAction.digitalData[index].isChanged = previousState;
            logicalAction.digitalData[index].activeOrigin = 0L;
            return;
        }

        float previousX = logicalAction.analogData[index].x;
        float previousY = logicalAction.analogData[index].y;
        float previousZ = logicalAction.analogData[index].z;
        logicalAction.analogData[index].deltaX = -previousX;
        logicalAction.analogData[index].deltaY = -previousY;
        logicalAction.analogData[index].deltaZ = -previousZ;
        logicalAction.analogData[index].x = 0.0F;
        logicalAction.analogData[index].y = 0.0F;
        logicalAction.analogData[index].z = 0.0F;
        logicalAction.analogData[index].isActive = false;
        logicalAction.analogData[index].isChanged = active &&
            (previousX != 0.0F || previousY != 0.0F || previousZ != 0.0F);
        logicalAction.analogData[index].activeOrigin = 0L;
    }

    private void buildRawDefinitions(Map<String, VRInputAction> inputActions) {
        DefaultBindingManager bindingManager = DefaultBindingManager.getInstance();

        for (String profile : KNOWN_PROFILES) {
            Collection<Pair<String, String>> defaultBindings = XRBindings.getBinding(profile);
            this.defaultBindingsByProfile.put(bindingManager.getUnifiedProfile(profile), List.copyOf(defaultBindings));
            bindingManager.saveDefaultBindingsIfNeeded(profile, defaultBindings);

            Set<Pair<String, String>> rawBindings = new LinkedHashSet<>();
            for (Pair<String, String> binding : defaultBindings) {
                VRInputAction logicalAction = inputActions.get(binding.getLeft());
                if (logicalAction == null) {
                    continue;
                }

                RawBindingKey rawKey = new RawBindingKey(binding.getRight(), logicalAction.type);
                RawActionDefinition definition = this.rawDefinitionsByKey.computeIfAbsent(rawKey, this::createDefinition);
                this.rawDefinitionsByActionName.put(definition.actionName(), definition);
                rawBindings.add(Pair.of(definition.actionName(), binding.getRight()));
            }

            this.rawBindingsByProfile.put(profile, rawBindings);
        }

        this.rawDefinitionsBuilt = true;
    }

    private RawActionDefinition createDefinition(RawBindingKey rawKey) {
        String sanitizedPath = rawKey.path()
            .replaceFirst("^/", "")
            .replace('/', '.')
            .replaceAll("[^a-zA-Z0-9._-]", "_");
        String keyName = RAW_KEY_PREFIX + rawKey.type() + "." + sanitizedPath;
        String actionName = VRInputActionSet.GLOBAL.name + "/in/" + keyName;
        return new RawActionDefinition(rawKey, keyName, actionName);
    }

    private static boolean analogPressed(VRInputAction action) {
        float x = action.analogData[0].x;
        float y = action.analogData[0].y;
        float z = action.analogData[0].z;
        return Math.abs(x) > 0.5F || Math.abs(y) > 0.5F || Math.abs(z) > 0.5F;
    }

    private static ControllerType handForPath(String inputPath) {
        if (inputPath.contains("/user/hand/right/")) {
            return ControllerType.RIGHT;
        }
        if (inputPath.contains("/user/hand/left/")) {
            return ControllerType.LEFT;
        }
        return null;
    }

    private record RawBindingKey(String path, String type) {}

    private record RawActionDefinition(RawBindingKey key, String keyName, String actionName) {
        public String type() {
            return this.key.type();
        }
    }

    private record RouteSource(String rawActionName, ControllerType hand) {}
}
