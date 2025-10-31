package dev.therealflo.client;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provides human-readable descriptions for OpenXR input paths.
 * Used for building UIs to display controller button names.
 */
public class InputPathDescriptions {
    
    /**
     * Represents a controller input with its descriptive information
     */
    public static class InputDescription {
        public final String path;
        public final String hand;
        public final String displayName;
        public final String description;
        
        public InputDescription(String path, String hand, String displayName, String description) {
            this.path = path;
            this.hand = hand;
            this.displayName = displayName;
            this.description = description;
        }
    }
    
    // Quest 2 / Pico 4 / Pico Neo 3 (Oculus Touch Controller) input paths
    private static final Map<String, InputDescription> QUEST_INPUTS = new LinkedHashMap<>();
    
    // Vive Controller input paths
    private static final Map<String, InputDescription> VIVE_INPUTS = new LinkedHashMap<>();
    
    // Cosmos Controller input paths
    private static final Map<String, InputDescription> COSMOS_INPUTS = new LinkedHashMap<>();
    
    static {
        // Initialize Quest 2 / Pico 4 / Pico Neo 3 inputs
        // Right Hand
        QUEST_INPUTS.put("/user/hand/right/input/trigger", 
            new InputDescription("/user/hand/right/input/trigger", "Right", "Trigger", "Right trigger"));
        QUEST_INPUTS.put("/user/hand/right/input/squeeze", 
            new InputDescription("/user/hand/right/input/squeeze", "Right", "Grip", "Right grip"));
        QUEST_INPUTS.put("/user/hand/right/input/thumbstick", 
            new InputDescription("/user/hand/right/input/thumbstick", "Right", "Thumbstick", "Right thumbstick (2D axis)"));
        QUEST_INPUTS.put("/user/hand/right/input/thumbstick/click", 
            new InputDescription("/user/hand/right/input/thumbstick/click", "Right", "Thumbstick Click", "Right thumbstick"));
        QUEST_INPUTS.put("/user/hand/right/input/a/click", 
            new InputDescription("/user/hand/right/input/a/click", "Right", "A Button", "A button"));
        QUEST_INPUTS.put("/user/hand/right/input/b/click", 
            new InputDescription("/user/hand/right/input/b/click", "Right", "B Button", "B button"));
        
        // Left Hand
        QUEST_INPUTS.put("/user/hand/left/input/trigger", 
            new InputDescription("/user/hand/left/input/trigger", "Left", "Trigger", "Left trigger"));
        QUEST_INPUTS.put("/user/hand/left/input/squeeze", 
            new InputDescription("/user/hand/left/input/squeeze", "Left", "Grip", "Left grip"));
        QUEST_INPUTS.put("/user/hand/left/input/thumbstick", 
            new InputDescription("/user/hand/left/input/thumbstick", "Left", "Thumbstick", "Left thumbstick (2D axis)"));
        QUEST_INPUTS.put("/user/hand/left/input/thumbstick/click", 
            new InputDescription("/user/hand/left/input/thumbstick/click", "Left", "Thumbstick Click", "Left thumbstick"));
        QUEST_INPUTS.put("/user/hand/left/input/x/click", 
            new InputDescription("/user/hand/left/input/x/click", "Left", "X Button", "X button"));
        QUEST_INPUTS.put("/user/hand/left/input/y/click", 
            new InputDescription("/user/hand/left/input/y/click", "Left", "Y Button", "Y button"));
        QUEST_INPUTS.put("/user/hand/left/input/menu/click", 
            new InputDescription("/user/hand/left/input/menu/click", "Left", "Menu Button", "Left hand menu button"));

        // NOT NEEDED FURTHER

        // Initialize Vive Controller inputs
        // Right Hand
        VIVE_INPUTS.put("/user/hand/right/input/trigger", 
            new InputDescription("/user/hand/right/input/trigger", "Right", "Trigger", "Right hand trigger button"));
        VIVE_INPUTS.put("/user/hand/right/input/squeeze", 
            new InputDescription("/user/hand/right/input/squeeze", "Right", "Grip", "Right hand grip button"));
        VIVE_INPUTS.put("/user/hand/right/input/trackpad", 
            new InputDescription("/user/hand/right/input/trackpad", "Right", "Trackpad", "Right hand trackpad (2D axis)"));
        VIVE_INPUTS.put("/user/hand/right/input/trackpad/click", 
            new InputDescription("/user/hand/right/input/trackpad/click", "Right", "Trackpad Click", "Right hand trackpad press"));
        VIVE_INPUTS.put("/user/hand/right/input/menu/click", 
            new InputDescription("/user/hand/right/input/menu/click", "Right", "Menu Button", "Right hand menu button"));
        
        // Left Hand
        VIVE_INPUTS.put("/user/hand/left/input/trigger", 
            new InputDescription("/user/hand/left/input/trigger", "Left", "Trigger", "Left hand trigger button"));
        VIVE_INPUTS.put("/user/hand/left/input/squeeze", 
            new InputDescription("/user/hand/left/input/squeeze", "Left", "Grip", "Right hand grip button"));
        VIVE_INPUTS.put("/user/hand/left/input/trackpad", 
            new InputDescription("/user/hand/left/input/trackpad", "Left", "Trackpad", "Left hand trackpad (2D axis)"));
        VIVE_INPUTS.put("/user/hand/left/input/trackpad/click", 
            new InputDescription("/user/hand/left/input/trackpad/click", "Left", "Trackpad Click", "Left hand trackpad press"));
        VIVE_INPUTS.put("/user/hand/left/input/menu/click", 
            new InputDescription("/user/hand/left/input/menu/click", "Left", "Menu Button", "Left hand menu button"));
        
        // Initialize Cosmos Controller inputs
        // Right Hand
        COSMOS_INPUTS.put("/user/hand/right/input/trigger", 
            new InputDescription("/user/hand/right/input/trigger", "Right", "Trigger", "Right trigger"));
        COSMOS_INPUTS.put("/user/hand/right/input/squeeze", 
            new InputDescription("/user/hand/right/input/squeeze", "Right", "Grip", "Right hand grip button"));
        COSMOS_INPUTS.put("/user/hand/right/input/thumbstick", 
            new InputDescription("/user/hand/right/input/thumbstick", "Right", "Thumbstick", "Right hand thumbstick (2D axis)"));
        COSMOS_INPUTS.put("/user/hand/right/input/thumbstick/click", 
            new InputDescription("/user/hand/right/input/thumbstick/click", "Right", "Thumbstick Click", "Right hand thumbstick press"));
        COSMOS_INPUTS.put("/user/hand/right/input/a/click", 
            new InputDescription("/user/hand/right/input/a/click", "Right", "A Button", "Right hand A button"));
        COSMOS_INPUTS.put("/user/hand/right/input/b/click", 
            new InputDescription("/user/hand/right/input/b/click", "Right", "B Button", "Right hand B button"));
        
        // Left Hand
        COSMOS_INPUTS.put("/user/hand/left/input/trigger", 
            new InputDescription("/user/hand/left/input/trigger", "Left", "Trigger", "Left hand trigger button"));
        COSMOS_INPUTS.put("/user/hand/left/input/squeeze", 
            new InputDescription("/user/hand/left/input/squeeze", "Left", "Grip", "Right hand grip button"));
        COSMOS_INPUTS.put("/user/hand/left/input/thumbstick", 
            new InputDescription("/user/hand/left/input/thumbstick", "Left", "Thumbstick", "Left hand thumbstick (2D axis)"));
        COSMOS_INPUTS.put("/user/hand/left/input/thumbstick/click", 
            new InputDescription("/user/hand/left/input/thumbstick/click", "Left", "Thumbstick Click", "Left hand thumbstick press"));
        COSMOS_INPUTS.put("/user/hand/left/input/x/click", 
            new InputDescription("/user/hand/left/input/x/click", "Left", "X Button", "Left hand X button"));
        COSMOS_INPUTS.put("/user/hand/left/input/y/click", 
            new InputDescription("/user/hand/left/input/y/click", "Left", "Y Button", "Left hand Y button"));
    }
    
    /**
     * Gets the input description map for a specific interaction profile
     */
    public static Map<String, InputDescription> getInputsForProfile(String interactionProfilePath) {
        // Normalize profile first
        String normalizedProfile = DefaultBindingManager.getInstance().getUnifiedProfile(interactionProfilePath);
        
        return switch (normalizedProfile) {
            case "/interaction_profiles/oculus/touch_controller" -> QUEST_INPUTS;
            case "/interaction_profiles/htc/vive_cosmos_controller" -> COSMOS_INPUTS;
            default -> VIVE_INPUTS; // Default to Vive for compatibility
        };
    }
    
    /**
     * Gets a description for a specific input path
     */
    public static InputDescription getDescription(String interactionProfilePath, String inputPath) {
        Map<String, InputDescription> inputs = getInputsForProfile(interactionProfilePath);
        return inputs.getOrDefault(inputPath, 
            new InputDescription(inputPath, "Unknown", inputPath, "Unknown input"));
    }
    
    /**
     * Gets a simple display name for an input path
     */
    public static String getDisplayName(String interactionProfilePath, String inputPath) {
        return getDescription(interactionProfilePath, inputPath).displayName;
    }
    
    /**
     * Gets all available input paths for a specific interaction profile
     */
    public static Map<String, InputDescription> getAllInputs(String interactionProfilePath) {
        return getInputsForProfile(interactionProfilePath);
    }
    
    /**
     * Checks if an input path is an axis (thumbstick/trackpad) vs a button
     */
    public static boolean isAxisInput(String inputPath) {
        return inputPath.contains("/thumbstick") && !inputPath.contains("/click") ||
               inputPath.contains("/trackpad") && !inputPath.contains("/click");
    }
    
    /**
     * Gets all inputs grouped by hand
     */
    public static Map<String, Map<String, InputDescription>> getInputsByHand(String interactionProfilePath) {
        Map<String, InputDescription> allInputs = getInputsForProfile(interactionProfilePath);
        Map<String, Map<String, InputDescription>> byHand = new LinkedHashMap<>();
        byHand.put("Left", new LinkedHashMap<>());
        byHand.put("Right", new LinkedHashMap<>());
        
        for (Map.Entry<String, InputDescription> entry : allInputs.entrySet()) {
            InputDescription desc = entry.getValue();
            byHand.get(desc.hand).put(entry.getKey(), desc);
        }
        
        return byHand;
    }
}
