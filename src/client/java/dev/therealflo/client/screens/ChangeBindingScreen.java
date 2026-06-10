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
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Screen for changing VR controller bindings.
 * Shows all controller inputs with their currently bound actions.
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
        RequestUi.root(rootComponent);

        FlowLayout panel = RequestUi.panel(90, 90);
        panel.child(RequestUi.header(
                Text.translatable("screen.request.change_bindings",
                        BindingSetRegistry.getInstance().getSetDisplayName(interactionProfile, setId)),
                Text.literal(interactionProfile)
        ));

        DefaultBindingManager manager = DefaultBindingManager.getInstance();
        allBindings = manager.loadBindingsForSet(interactionProfile, setId, XRBindings.getBinding(interactionProfile));

        if (allBindings == null || allBindings.isEmpty()) {
            panel.child(Components.label(Text.translatable("text.request.no_bindings"))
                    .color(RequestUi.ERROR));
            rootComponent.child(panel);
            return;
        }

        Map<String, List<String>> inputToActions = buildInputToActionsMap(allBindings);

        Map<String, InputPathDescriptions.InputDescription> allInputs = new LinkedHashMap<>(
                InputPathDescriptions.getAllInputs(interactionProfile)
        );
        for (Pair<String, String> binding : allBindings) {
            allInputs.putIfAbsent(binding.getRight(), InputPathDescriptions.getDescription(interactionProfile, binding.getRight()));
        }

        FlowLayout listContent = RequestUi.listContent();

        // Group inputs by hand for better organization
        Map<String, Map<String, InputPathDescriptions.InputDescription>> byHand = new LinkedHashMap<>();
        byHand.put("Left", new LinkedHashMap<>());
        byHand.put("Right", new LinkedHashMap<>());
        byHand.put("Unknown", new LinkedHashMap<>());
        for (Map.Entry<String, InputPathDescriptions.InputDescription> entry : allInputs.entrySet()) {
            byHand.computeIfAbsent(entry.getValue().hand, ignored -> new LinkedHashMap<>()).put(entry.getKey(), entry.getValue());
        }

        for (String hand : Arrays.asList("Left", "Right", "Unknown")) {
            Map<String, InputPathDescriptions.InputDescription> handInputs = byHand.get(hand);
            if (handInputs.isEmpty()) continue;

            listContent.child(RequestUi.sectionLabel(Text.translatable("text.request.hand_" + hand.toLowerCase(Locale.ROOT))));

            for (Map.Entry<String, InputPathDescriptions.InputDescription> entry : handInputs.entrySet()) {
                listContent.child(buildBindingRow(entry.getKey(), entry.getValue(), inputToActions));
            }
        }

        panel.child(RequestUi.scrollArea(listContent));

        FlowLayout footer = RequestUi.footer();
        footer.child(RequestUi.footerButton(Text.translatable("button.request.back"), button -> close()));
        panel.child(footer);

        rootComponent.child(panel);
    }

    private FlowLayout buildBindingRow(String inputPath, InputPathDescriptions.InputDescription inputDesc,
                                       Map<String, List<String>> inputToActions) {
        List<String> boundActions = inputToActions.getOrDefault(inputPath, new ArrayList<>());
        ValidationResult validation = validateBindings(inputPath, allBindings);

        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.surface(RequestUi.CARD);
        row.padding(Insets.of(5));
        row.verticalAlignment(VerticalAlignment.CENTER);

        // Input name on the left, taking the remaining space
        FlowLayout nameColumn = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        nameColumn.child(Components.label(Text.literal(inputDesc.displayName)).color(RequestUi.TEXT));
        if (!validation.isValid) {
            nameColumn.child(Components.label(Text.literal(validation.errorMessage))
                    .color(RequestUi.ERROR)
                    .margins(Insets.top(2)));
        }
        row.child(nameColumn);

        // Bound action button on the right
        Text buttonText;
        if (boundActions.isEmpty()) {
            buttonText = Text.translatable("button.request.bind");
        } else {
            String actionName = getActionTranslation(boundActions.getFirst());
            String category = getActionSetCategory(boundActions.getFirst());
            String display = actionName + " [" + category + "]";
            if (boundActions.size() > 1) {
                display += " (+" + (boundActions.size() - 1) + ")";
            }
            if (!validation.isValid) {
                display = "[!] " + display;
            }
            buttonText = Text.literal(display);
        }

        var bindButton = Components.button(buttonText,
                button -> onChangeBinding(inputPath, inputDesc, boundActions, validation));
        bindButton.sizing(Sizing.fill(50), Sizing.fixed(RequestUi.LIST_BUTTON_HEIGHT));
        row.child(bindButton);

        return row;
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
     */
    private Map<String, List<String>> buildInputToActionsMap(Collection<Pair<String, String>> bindings) {
        Map<String, List<String>> inputToActions = new LinkedHashMap<>();

        for (Pair<String, String> binding : bindings) {
            inputToActions.computeIfAbsent(binding.getRight(), k -> new ArrayList<>()).add(binding.getLeft());
        }

        return inputToActions;
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
        String[] parts = actionPath.split("/");
        String actionName = parts[parts.length - 1];
        return Text.translatable(actionName).getString();
    }

    /**
     * Refreshes the screen by reloading bindings and rebuilding the UI.
     * Called when returning from SelectActionScreen to show updated bindings.
     */
    public void refresh() {
        if (this.uiAdapter != null && this.uiAdapter.rootComponent != null) {
            this.uiAdapter.rootComponent.clearChildren();
            this.build(this.uiAdapter.rootComponent);
        }
    }

    /**
     * Called when the user clicks a binding. Opens the SelectActionScreen.
     */
    private void onChangeBinding(String inputPath, InputPathDescriptions.InputDescription inputDesc,
                                 List<String> boundActions, ValidationResult validation) {
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
