package dev.therealflo.client.screens;

import dev.therealflo.client.BindingSetRegistry;
import dev.therealflo.client.DefaultBindingManager;
import dev.therealflo.client.InputPathDescriptions;
import dev.therealflo.client.RequestModClient;
import org.vivecraft.client_vr.provider.openxr.XRBindings;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.GridLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Screen for changing VR controller bindings.
 * Shows all controller inputs with their currently bound actions (game/mod keys only).
 */
public class ChangeBindingScreen extends BaseOwoScreen<FlowLayout> {
    private final Screen parentScreen;
    private final String interactionProfile;
    private final String setId;
    private Collection<Pair<String, String>> allBindings;
    
    public ChangeBindingScreen() {
        this(null, RequestModClient.getPreferredInteractionProfile());
    }
    
    public ChangeBindingScreen(String interactionProfile) {
        this(null, interactionProfile);
    }

    public ChangeBindingScreen(Screen parentScreen, String interactionProfile) {
        this(parentScreen, interactionProfile, BindingSetRegistry.getInstance().getActiveSetId(interactionProfile));
    }

    public ChangeBindingScreen(Screen parentScreen, String interactionProfile, String setId) {
        this.parentScreen = parentScreen;
        this.interactionProfile = interactionProfile;
        this.setId = setId;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent
                .surface(Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.CENTER);

        // Main container
        FlowLayout mainContainer = Containers.verticalFlow(Sizing.fill(90), Sizing.fill(90));
        mainContainer.padding(Insets.of(10));
        
        // Title
        mainContainer.child(
                Components.label(Text.literal("Controller Bindings: " +
                        BindingSetRegistry.getInstance().getSetDisplayName(interactionProfile, setId)))
                        .color(Color.ofRgb(0xFFFFFF))
                        .shadow(true)
                        .margins(Insets.bottom(10))
        );
        
        // Load current bindings
        DefaultBindingManager manager = DefaultBindingManager.getInstance();
        allBindings = manager.loadBindingsForSet(interactionProfile, setId, XRBindings.getBinding(interactionProfile));
        
        if (allBindings == null || allBindings.isEmpty()) {
            mainContainer.child(
                    Components.label(Text.literal("No bindings found"))
                            .color(Color.ofRgb(0xFF0000))
            );
            rootComponent.child(mainContainer);
            return;
        }
        
        // Build a map of input paths to their bound actions (only game/mod keys)
        Map<String, List<String>> inputToActions = buildInputToActionsMap(allBindings);
        
        // Get all available inputs for this controller
        Map<String, InputPathDescriptions.InputDescription> allInputs = new LinkedHashMap<>(
                InputPathDescriptions.getAllInputs(interactionProfile)
        );
        for (Pair<String, String> binding : allBindings) {
            allInputs.putIfAbsent(binding.getRight(), InputPathDescriptions.getDescription(interactionProfile, binding.getRight()));
        }
        
        // Create scrollable container for the grid
        ScrollContainer<FlowLayout> scrollContainer = Containers.verticalScroll(
                Sizing.fill(100),
                Sizing.fill(75),
                Containers.verticalFlow(Sizing.fill(100), Sizing.content())
        );
        
        FlowLayout scrollContent = (FlowLayout) scrollContainer.child();
        
        // Create header
        GridLayout headerGrid = Containers.grid(Sizing.fill(100), Sizing.content(), 1, 2);
        headerGrid.child(
                Components.label(Text.literal("Input"))
                        .color(Color.ofRgb(0xFFFF00))
                        .shadow(true),
                0, 0
        );
        headerGrid.child(
                Components.label(Text.literal("Bound action"))
                        .color(Color.ofRgb(0xFFFF00))
                        .shadow(true),
                0, 1
        );
        scrollContent.child(headerGrid.margins(Insets.bottom(5)));
        
        // Group inputs by hand for better organization
        Map<String, Map<String, InputPathDescriptions.InputDescription>> byHand = new LinkedHashMap<>();
        byHand.put("Left", new LinkedHashMap<>());
        byHand.put("Right", new LinkedHashMap<>());
        byHand.put("Unknown", new LinkedHashMap<>());
        for (Map.Entry<String, InputPathDescriptions.InputDescription> entry : allInputs.entrySet()) {
            byHand.computeIfAbsent(entry.getValue().hand, ignored -> new LinkedHashMap<>()).put(entry.getKey(), entry.getValue());
        }
        
        // Create sections for each hand
        for (String hand : Arrays.asList("Left", "Right", "Unknown")) {
            Map<String, InputPathDescriptions.InputDescription> handInputs = byHand.get(hand);
            
            if (handInputs.isEmpty()) continue;
            
            // Hand header
            scrollContent.child(
                    Components.label(Text.literal(hand + " Hand"))
                            .color(Color.ofRgb(0x00FFFF))
                            .shadow(true)
                            .margins(Insets.of(10, 0, 5, 0))
            );
            
            // Create a row for each input
            for (Map.Entry<String, InputPathDescriptions.InputDescription> entry : handInputs.entrySet()) {
                String inputPath = entry.getKey();
                InputPathDescriptions.InputDescription inputDesc = entry.getValue();
                
                // Get actions bound to this input (only game/mod keys)
                List<String> boundActions = inputToActions.getOrDefault(inputPath, new ArrayList<>());
                
                // Validate bindings
                ValidationResult validation = validateBindings(inputPath, allBindings);
                
                // Create grid for this input (2 columns: input name, button)
                GridLayout bindingGrid = Containers.grid(Sizing.fill(100), Sizing.content(), 1, 2);
                bindingGrid.padding(Insets.of(5));
                bindingGrid.margins(Insets.bottom(2));
                
                // Input name
                bindingGrid.child(
                        Components.label(Text.literal(inputDesc.displayName))
                                .color(Color.ofRgb(0xFFFFFF))
                                .maxWidth(150),
                        0, 0
                );
                
                // Bound action(s)
                String actionDisplay;
                Color actionColor;
                
                if (boundActions.isEmpty()) {
                    actionDisplay = "Not bound";
                    actionColor = Color.ofRgb(0x888888);
                } else if (boundActions.size() == 1) {
                    String actionName = getActionTranslation(boundActions.getFirst());
                    String category = getActionSetCategory(boundActions.getFirst());
                    actionDisplay = actionName + " [" + category + "]";
                    actionColor = validation.isValid ? Color.ofRgb(0x00FF00) : Color.ofRgb(0xFF0000);
                } else {
                    String actionName = getActionTranslation(boundActions.getFirst());
                    String category = getActionSetCategory(boundActions.getFirst());
                    actionDisplay = actionName + " [" + category + "] (+" + (boundActions.size() - 1) + ")";
                    actionColor = validation.isValid ? Color.ofRgb(0x00FF00) : Color.ofRgb(0xFF0000);
                }
                
                // Show validation error if present (use text prefix instead of emoji)
                if (!validation.isValid) {
                    actionDisplay = "[!] " + actionDisplay;
                }
                
                // Change button - show for ALL bindings now (not just game/mod)
                if (!boundActions.isEmpty()) {
                    bindingGrid.child(
                            Components.button(
                                    Text.literal(actionDisplay),
                                    button -> onChangeBinding(inputPath, inputDesc, boundActions, validation)
                            ).sizing(Sizing.fill(50), Sizing.fixed(40)),
                            0, 1
                    );
                } else {
                    // Show "Bind" button for unbound inputs
                    bindingGrid.child(
                            Components.button(
                                    Text.literal("Bind..."),
                                    button -> onChangeBinding(inputPath, inputDesc, boundActions, validation)
                            ).sizing(Sizing.fill(50), Sizing.fixed(40)),
                            0, 1
                    );
                }
                
                scrollContent.child(bindingGrid);
            }
        }
        
        mainContainer.child(scrollContainer);
        
        // Back + Quit buttons
        FlowLayout buttonRow = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        buttonRow.horizontalAlignment(HorizontalAlignment.CENTER);
        buttonRow.gap(8);
        buttonRow.margins(Insets.top(10));

        buttonRow.child(
                Components.button(
                        Text.literal("Back"),
                        button -> close()
                )
        );

        buttonRow.child(
                Components.button(
                        Text.literal("Quit Game"),
                        button -> {
                            if (this.client != null) {
                                this.client.scheduleStop();
                            }
                        }
                )
        );

        mainContainer.child(buttonRow);
        
        rootComponent.child(mainContainer);
    }

    /**
     * Represents the result of binding validation.
     */
    private static class ValidationResult {
        boolean isValid;
        String errorMessage;
        boolean hasGlobal;
        int ingameModCount;
        
        ValidationResult(boolean isValid, String errorMessage, boolean hasGlobal, int ingameModCount) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
            this.hasGlobal = hasGlobal;
            this.ingameModCount = ingameModCount;
        }
    }
    
    /**
     * Validates the bindings for a specific input path according to the rules:
     * - Only one ingame or mod binding allowed
     * - One global binding allowed, but no other bindings if global is present
     */
    private ValidationResult validateBindings(String inputPath, Collection<Pair<String, String>> bindings) {
        int ingameModCount = 0;
        boolean hasGlobal = false;
        
        for (Pair<String, String> binding : bindings) {
            if (!binding.getRight().equals(inputPath)) {
                continue;
            }
            
            String action = binding.getLeft();
            
            if (action.startsWith("/actions/global/in/")) {
                hasGlobal = true;
            } else if (action.startsWith("/actions/ingame/in/") || action.startsWith("/actions/mod/in/")) {
                ingameModCount++;
            }
        }
        
        // Check validation rules
        if (hasGlobal && (ingameModCount > 0)) {
            return new ValidationResult(false, "Global binding cannot coexist with other bindings", hasGlobal, ingameModCount);
        }
        
        if (ingameModCount > 1) {
            return new ValidationResult(false, "Only one ingame/mod binding allowed per input", hasGlobal, ingameModCount);
        }
        
        return new ValidationResult(true, null, hasGlobal, ingameModCount);
    }

    /**
     * Builds a map of input paths to their bound actions.
     * Now includes ALL actions - ingame, mod, global, contextual, gui, and keyboard.
     */
    private Map<String, List<String>> buildInputToActionsMap(Collection<Pair<String, String>> bindings) {
        Map<String, List<String>> inputToActions = new LinkedHashMap<>();
        
        for (Pair<String, String> binding : bindings) {
            String action = binding.getLeft();
            String inputPath = binding.getRight();
            
            // Include ALL actions (no filtering)
            inputToActions.computeIfAbsent(inputPath, k -> new ArrayList<>()).add(action);
        }
        
        return inputToActions;
    }

    /**
     * Checks if an action is a game or mod keybinding (not global).
     * Returns true for actions in /actions/ingame or /actions/mod action sets.
     * This is still used for validation purposes.
     */
    private boolean isGameOrModAction(String action) {
        return action.startsWith("/actions/ingame/in/") ||
               action.startsWith("/actions/mod/in/");
    }

    /**
     * Gets the action set category for display purposes.
     */
    private String getActionSetCategory(String action) {
        if (action.startsWith("/actions/ingame/in/")) {
            return "Ingame";
        } else if (action.startsWith("/actions/mod/in/")) {
            return "Mod";
        } else if (action.startsWith("/actions/global/in/")) {
            return "Global";
        } else if (action.startsWith("/actions/contextual/in/")) {
            return "Contextual";
        } else if (action.startsWith("/actions/gui/in/")) {
            return "GUI";
        } else if (action.startsWith("/actions/keyboard/in/")) {
            return "Keyboard";
        } else {
            return "Other";
        }
    }

    /**
     * Converts an action path to a human-readable name.
     */
    private String getActionTranslation(String actionPath) {
        // Extract the last part of the action path
        String[] parts = actionPath.split("/");
        String actionName = parts[parts.length - 1];

        return Text.translatable(actionName).getString();
    }

    /**
     * Refreshes the screen by reloading bindings and rebuilding the UI.
     * Called when returning from SelectActionScreen to show updated bindings.
     */
    public void refresh() {
        // Clear the current UI
        if (this.uiAdapter != null && this.uiAdapter.rootComponent != null) {
            this.uiAdapter.rootComponent.clearChildren();
            // Rebuild the UI with fresh data
            this.build(this.uiAdapter.rootComponent);
        }
    }

    /**
     * Called when the user clicks "Change" for a binding.
     * Opens the SelectActionScreen to allow the user to choose which actions to bind.
     */
    private void onChangeBinding(String inputPath, InputPathDescriptions.InputDescription inputDesc, 
                                  List<String> boundActions, ValidationResult validation) {
        System.out.println("Opening action selection for input: " + inputPath + " (" + inputDesc.displayName + ")");
        System.out.println("Currently bound actions: " + boundActions);
        
        if (!validation.isValid) {
            System.out.println("Warning - current bindings are invalid: " + validation.errorMessage);
        }
        
        // Open the SelectActionScreen
        if (this.client != null) {
            this.client.setScreen(new SelectActionScreen(
                    this,
                    interactionProfile,
                    setId,
                    inputPath,
                    inputDesc,
                    allBindings
            ));
        }
    }

    @Override
    public void close() {
        if (this.client != null && this.parentScreen != null) {
            this.client.setScreen(this.parentScreen);
            return;
        }
        super.close();
    }
}
