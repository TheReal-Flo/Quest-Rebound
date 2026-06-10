package dev.therealflo.client.screens;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/**
 * Shared design system for all Quest: Rebound screens.
 * Every screen is built from the same building blocks so colors,
 * spacing and layout stay consistent across the whole mod.
 */
public final class RequestUi {
    // Palette
    public static final Color TEXT = Color.ofRgb(0xFFFFFF);
    public static final Color MUTED = Color.ofRgb(0x9AA0A6);
    public static final Color ACCENT = Color.ofRgb(0x53C4FF);
    public static final Color SUCCESS = Color.ofRgb(0x6FE3A0);
    public static final Color WARNING = Color.ofRgb(0xFFD27F);
    public static final Color ERROR = Color.ofRgb(0xFF7A7A);

    /** Background of cards/rows inside a panel. */
    public static final Surface CARD = Surface.flat(0x66000000);
    /** Card highlighted as the active/selected element. */
    public static final Surface CARD_ACTIVE = Surface.flat(0x66000000).and(Surface.outline(0xFF53C4FF));

    /** Standard height for buttons inside lists (large enough for VR pointers). */
    public static final int LIST_BUTTON_HEIGHT = 24;

    private RequestUi() {
    }

    /** Configures the root component every screen shares. */
    public static void root(FlowLayout rootComponent) {
        rootComponent
                .surface(Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.CENTER);
    }

    /** The main panel that holds a screen's content. */
    public static FlowLayout panel(int widthPercent, int heightPercent) {
        FlowLayout panel = Containers.verticalFlow(Sizing.fill(widthPercent), Sizing.fill(heightPercent));
        panel.surface(Surface.DARK_PANEL);
        panel.padding(Insets.of(7));
        panel.horizontalAlignment(HorizontalAlignment.CENTER);
        return panel;
    }

    /** A compact, content-sized panel for small dialogs. */
    public static FlowLayout dialogPanel() {
        FlowLayout panel = Containers.verticalFlow(Sizing.fixed(280), Sizing.content());
        panel.surface(Surface.DARK_PANEL);
        panel.padding(Insets.of(9));
        panel.horizontalAlignment(HorizontalAlignment.CENTER);
        return panel;
    }

    /** Screen title, optionally followed by a muted subtitle line. */
    public static FlowLayout header(Text title, Text subtitle) {
        FlowLayout header = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        header.horizontalAlignment(HorizontalAlignment.CENTER);
        header.margins(Insets.bottom(5));
        header.child(Components.label(title).color(TEXT).shadow(true));
        if (subtitle != null) {
            header.child(Components.label(subtitle).color(MUTED).margins(Insets.top(2)));
        }
        return header;
    }

    /** Section header inside a scrolling list (e.g. "Left Hand", category names). */
    public static LabelComponent sectionLabel(Text text) {
        return (LabelComponent) Components.label(text)
                .color(ACCENT)
                .shadow(true)
                .margins(Insets.of(5, 2, 0, 0));
    }

    /** Scrollable content area that takes all space left between header and footer. */
    public static ScrollContainer<FlowLayout> scrollArea(FlowLayout content) {
        content.gap(2);
        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(
                Sizing.fill(100), Sizing.expand(), content);
        scroll.surface(Surface.flat(0x33000000));
        scroll.padding(Insets.of(3));
        return scroll;
    }

    /** Empty content flow used inside {@link #scrollArea}. */
    public static FlowLayout listContent() {
        return Containers.verticalFlow(Sizing.fill(100), Sizing.content());
    }

    /** Footer row holding the screen's main actions, centered with even gaps. */
    public static FlowLayout footer() {
        FlowLayout footer = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        footer.horizontalAlignment(HorizontalAlignment.CENTER);
        footer.verticalAlignment(VerticalAlignment.CENTER);
        footer.gap(4);
        footer.margins(Insets.top(6));
        return footer;
    }

    /** A card row inside a list. */
    public static FlowLayout card(boolean active) {
        FlowLayout card = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        card.surface(active ? CARD_ACTIVE : CARD);
        card.padding(Insets.of(5));
        return card;
    }

    public static ButtonComponent button(Text text, Consumer<ButtonComponent> onPress) {
        return Components.button(text, onPress);
    }

    /** Fixed-width button for footers so main actions line up across screens. */
    public static ButtonComponent footerButton(Text text, Consumer<ButtonComponent> onPress) {
        ButtonComponent button = Components.button(text, onPress);
        button.horizontalSizing(Sizing.fixed(90));
        return button;
    }
}
