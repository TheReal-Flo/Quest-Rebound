package dev.therealflo.client.screens;

import dev.therealflo.client.DefaultBindingManager;
import dev.therealflo.client.InputPathDescriptions;
import dev.therealflo.client.RequestModClient;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.CheckboxComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Screen for selecting which actions should be bound to a specific input.
 * Shows all available actions organized by category with checkboxes.
 */
public class SelectActionScreen extends BaseOwoScreen<FlowLayout> {
    private final Screen parentScreen;
    private final String interactionProfile;
    private final String inputPath;
    private final InputPathDescriptions.InputDescription inputDesc;
    private final Collection<Pair<String, String>> allBindings;
    private final Set<String> currentlyBoundActions;
    private final Map<String, CheckboxComponent> actionCheckboxes = new LinkedHashMap<>();

    public SelectActionScreen(Screen parentScreen, String interactionProfile, String inputPath,
                               InputPathDescriptions.InputDescription inputDesc,
                               Collection<Pair<String, String>> allBindings) {
        this.parentScreen = parentScreen;
        this.interactionProfile = interactionProfile;
        this.inputPath = inputPath;
        this.inputDesc = inputDesc;
        this.allBindings = allBindings;
        
        // Build set of currently bound actions for this input
        this.currentlyBoundActions = new HashSet<>();
        for (Pair<String, String> binding : allBindings) {
            if (binding.getRight().equals(inputPath)) {
                currentlyBoundActions.add(binding.getLeft());
            }
        }
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
                Components.label(Text.literal("Select Actions for " + inputDesc.displayName))
                        .color(Color.ofRgb(0xFFFFFF))
                        .shadow(true)
                        .margins(Insets.bottom(5))
        );
        
        // Subtitle with input path
        mainContainer.child(
                Components.label(Text.literal(inputPath))
                        .color(Color.ofRgb(0xAAAAAA))
                        .margins(Insets.bottom(10))
        );
        
        // Get all unique actions from the bindings
        Map<String, List<String>> actionsByCategory = categorizeActions();
        
        // Create scrollable container for the action list
        ScrollContainer<FlowLayout> scrollContainer = Containers.verticalScroll(
                Sizing.fill(100),
                Sizing.fill(70),
                Containers.verticalFlow(Sizing.fill(100), Sizing.content())
        );
        
        FlowLayout scrollContent = (FlowLayout) scrollContainer.child();
        scrollContent.padding(Insets.of(5));
        
        // Create sections for each category
        for (Map.Entry<String, List<String>> entry : actionsByCategory.entrySet()) {
            String category = entry.getKey();
            List<String> actions = entry.getValue();
            
            if (actions.isEmpty()) continue;
            
            // Category header
            scrollContent.child(
                    Components.label(Text.literal(category))
                            .color(Color.ofRgb(0x00FFFF))
                            .shadow(true)
                            .margins(Insets.of(10, 0, 5, 0))
            );
            
            // Create checkbox for each action
            for (String action : actions) {
                boolean isCurrentlyBound = currentlyBoundActions.contains(action);
                
                CheckboxComponent checkbox = Components.checkbox(Text.literal(getActionTranslation(action)))
                        .checked(isCurrentlyBound);
                
                checkbox.margins(Insets.of(2, 0, 2, 10));
                
                // Store checkbox reference for later
                actionCheckboxes.put(action, checkbox);
                
                scrollContent.child(checkbox);
            }
        }
        
        mainContainer.child(scrollContainer);
        
        // Button container
        FlowLayout buttonContainer = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        buttonContainer.gap(5);
        buttonContainer.margins(Insets.top(10));
        
        // Apply button
        buttonContainer.child(
                Components.button(
                        Text.literal("Apply"),
                        button -> onApply()
                )
        );
        
        // Cancel button
        buttonContainer.child(
                Components.button(
                        Text.literal("Cancel"),
                        button -> this.close()
                )
        );
        
        mainContainer.child(buttonContainer);
        
        rootComponent.child(mainContainer);
    }
    
    /**
     * Categorizes all actions from the bindings by their action set.
     * Now includes ALL registered actions, not just those currently bound.
     */
    private Map<String, List<String>> categorizeActions() {
        Map<String, List<String>> actionsByCategory = new LinkedHashMap<>();
        
        // Initialize categories in desired order
        actionsByCategory.put("Global", new ArrayList<>());
        actionsByCategory.put("Ingame", new ArrayList<>());
        actionsByCategory.put("Mod", new ArrayList<>());
        actionsByCategory.put("Contextual", new ArrayList<>());
        actionsByCategory.put("GUI", new ArrayList<>());
        actionsByCategory.put("Keyboard", new ArrayList<>());
        actionsByCategory.put("Other", new ArrayList<>());
        
        // Get ALL registered actions from Vivecraft
        List<String> allActions = RequestModClient.getAllRegisteredActions();
        
        if (allActions.isEmpty()) {
            // Fallback: If we can't get registered actions, use only those in bindings
            System.out.println("[SelectActionScreen] Warning: Could not get registered actions, falling back to bindings only");
            Set<String> bindingActions = new HashSet<>();
            for (Pair<String, String> binding : allBindings) {
                bindingActions.add(binding.getLeft());
            }
            allActions = new ArrayList<>(bindingActions);
        }
        
        System.out.println("[SelectActionScreen] Total actions available: " + allActions.size());
        
        // Sort actions alphabetically within their category
        allActions.sort(String::compareTo);
        
        // Categorize each action
        for (String action : allActions) {
            String category = getActionSetCategory(action);
            actionsByCategory.get(category).add(action);
        }
        
        // Debug: Print counts per category
        for (Map.Entry<String, List<String>> entry : actionsByCategory.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                System.out.println("[SelectActionScreen] Category '" + entry.getKey() + "' has " + entry.getValue().size() + " actions");
            }
        }
        
        return actionsByCategory;
    }
    
    /**
     * Gets the action set category for an action path.
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
     * Called when the user clicks Apply.
     * Updates the bindings based on checkbox selections.
     */
    private void onApply() {
        // Build new set of selected actions
        Set<String> selectedActions = new HashSet<>();
        for (Map.Entry<String, CheckboxComponent> entry : actionCheckboxes.entrySet()) {
            if (entry.getValue().isChecked()) {
                selectedActions.add(entry.getKey());
            }
        }
        
        // Create new bindings list
        List<Pair<String, String>> newBindings = new ArrayList<>();
        
        // Keep all bindings for other inputs
        for (Pair<String, String> binding : allBindings) {
            if (!binding.getRight().equals(inputPath)) {
                newBindings.add(binding);
            }
        }
        
        // Add new bindings for this input
        for (String action : selectedActions) {
            newBindings.add(Pair.of(action, inputPath));
        }
        
        // Save the new bindings
        DefaultBindingManager manager = DefaultBindingManager.getInstance();
        manager.saveBindingsForProfile(interactionProfile, newBindings);
        
        System.out.println("Saved " + newBindings.size() + " bindings for " + interactionProfile);
        System.out.println("Input " + inputPath + " now has " + selectedActions.size() + " actions bound");
        
        // Return to parent screen
        this.close();
    }
    
    @Override
    public void close() {
        if (this.client != null) {
            // Refresh the parent screen if it's a ChangeBindingScreen to show updated bindings
            if (parentScreen instanceof ChangeBindingScreen changeBindingScreen) {
                changeBindingScreen.refresh();
            }
            this.client.setScreen(parentScreen);
        }
    }
}
