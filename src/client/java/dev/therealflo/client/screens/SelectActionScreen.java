package dev.therealflo.client.screens;

import dev.therealflo.client.DefaultBindingManager;
import dev.therealflo.client.InputPathDescriptions;
import dev.therealflo.client.RequestModClient;
import dev.therealflo.client.RuntimeBindingRouter;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.CheckboxComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.CollapsibleContainer;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.provider.control.VRInputAction;

/**
 * Screen for selecting which actions should be bound to a specific input.
 * Shows all available actions organized by collapsible category with checkboxes.
 */
public class SelectActionScreen extends BaseOwoScreen<FlowLayout> {
    private final Screen parentScreen;
    private final String interactionProfile;
    private final String setId;
    private final String inputPath;
    private final InputPathDescriptions.InputDescription inputDesc;
    private final Collection<Pair<String, String>> allBindings;
    private final Set<String> currentlyBoundActions;
    private final Map<String, CheckboxComponent> actionCheckboxes = new LinkedHashMap<>();

    public SelectActionScreen(Screen parentScreen, String interactionProfile, String setId, String inputPath,
                              InputPathDescriptions.InputDescription inputDesc,
                              Collection<Pair<String, String>> allBindings) {
        this.parentScreen = parentScreen;
        this.interactionProfile = interactionProfile;
        this.setId = setId;
        this.inputPath = inputPath;
        this.inputDesc = inputDesc;
        this.allBindings = allBindings;

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
        RequestUi.root(rootComponent);

        FlowLayout panel = RequestUi.panel(90, 90);
        panel.child(RequestUi.header(
                Text.translatable("screen.request.select_actions", inputDesc.displayName),
                Text.literal(inputPath)
        ));

        Map<String, List<String>> actionsByCategory = categorizeActions();

        FlowLayout listContent = RequestUi.listContent();
        boolean hasAnyActions = false;

        for (Map.Entry<String, List<String>> entry : actionsByCategory.entrySet()) {
            String category = entry.getKey();
            List<String> actions = entry.getValue();
            if (actions.isEmpty()) continue;
            hasAnyActions = true;

            // Expand a category by default if it contains a bound action
            boolean expanded = actions.stream().anyMatch(currentlyBoundActions::contains);

            CollapsibleContainer section = Containers.collapsible(
                    Sizing.fill(100), Sizing.content(),
                    Text.literal(category + " (" + actions.size() + ")"),
                    expanded
            );
            section.surface(RequestUi.CARD);
            section.padding(Insets.of(5));

            for (String action : actions) {
                CheckboxComponent checkbox = Components.checkbox(Text.literal(getActionTranslation(action)))
                        .checked(currentlyBoundActions.contains(action));
                checkbox.margins(Insets.of(2, 2, 8, 0));
                actionCheckboxes.put(action, checkbox);
                section.child(checkbox);
            }

            listContent.child(section);
        }

        if (!hasAnyActions) {
            listContent.child(
                    Components.label(Text.translatable("text.request.no_bindable_actions"))
                            .color(RequestUi.ERROR)
                            .margins(Insets.top(8))
            );
        }

        panel.child(RequestUi.scrollArea(listContent));

        FlowLayout footer = RequestUi.footer();
        footer.child(RequestUi.footerButton(Text.translatable("button.request.apply"), button -> onApply()));
        footer.child(RequestUi.footerButton(Text.translatable("button.request.cancel"), button -> this.close()));
        panel.child(footer);

        rootComponent.child(panel);
    }

    /**
     * Categorizes all bindable actions by their action set.
     */
    private Map<String, List<String>> categorizeActions() {
        Map<String, List<String>> actionsByCategory = new LinkedHashMap<>();

        actionsByCategory.put("Global", new ArrayList<>());
        actionsByCategory.put("Ingame", new ArrayList<>());
        actionsByCategory.put("Mod", new ArrayList<>());
        actionsByCategory.put("Contextual", new ArrayList<>());
        actionsByCategory.put("GUI", new ArrayList<>());
        actionsByCategory.put("Keyboard", new ArrayList<>());
        actionsByCategory.put("Other", new ArrayList<>());

        List<String> allActions = RequestModClient.getAllRegisteredActions();

        if (allActions.isEmpty()) {
            // Fallback: if we can't get registered actions, use only those in bindings
            Set<String> bindingActions = new HashSet<>();
            for (Pair<String, String> binding : allBindings) {
                bindingActions.add(binding.getLeft());
            }
            allActions = new ArrayList<>(bindingActions);
        }

        allActions.sort(String::compareTo);

        for (String action : allActions) {
            if (!isBindableAction(action)) {
                continue;
            }
            actionsByCategory.get(getActionSetCategory(action)).add(action);
        }

        return actionsByCategory;
    }

    private boolean isBindableAction(String actionName) {
        MCVR vr = ClientDataHolderVR.getInstance().vr;
        if (vr == null) {
            return false;
        }

        VRInputAction action = vr.getInputActionByName(actionName);
        if (action == null || RuntimeBindingRouter.getInstance().isRawAction(action)) {
            return false;
        }

        return RuntimeBindingRouter.getInstance().canBind(interactionProfile, inputPath, action.type);
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
        String[] parts = actionPath.split("/");
        String actionName = parts[parts.length - 1];
        return Text.translatable(actionName).getString();
    }

    /**
     * Called when the user clicks Apply.
     * Updates the bindings based on checkbox selections.
     */
    private void onApply() {
        Set<String> selectedActions = new HashSet<>();
        for (Map.Entry<String, CheckboxComponent> entry : actionCheckboxes.entrySet()) {
            if (entry.getValue().isChecked()) {
                selectedActions.add(entry.getKey());
            }
        }

        List<Pair<String, String>> newBindings = new ArrayList<>();

        for (Pair<String, String> binding : allBindings) {
            if (!binding.getRight().equals(inputPath)) {
                newBindings.add(binding);
            }
        }

        for (String action : selectedActions) {
            newBindings.add(Pair.of(action, inputPath));
        }

        DefaultBindingManager manager = DefaultBindingManager.getInstance();
        manager.saveBindingsForSet(interactionProfile, setId, newBindings);

        this.close();
    }

    @Override
    public void close() {
        if (this.client != null) {
            if (parentScreen instanceof ChangeBindingScreen changeBindingScreen) {
                changeBindingScreen.refresh();
            }
            this.client.setScreen(parentScreen);
        }
    }
}
