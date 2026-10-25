package com.caxture.manhuntuniversal.fabric.ui;

import com.caxture.manhuntuniversal.protocol.TrackingSelectionOpen;
import com.caxture.manhuntuniversal.protocol.TrackingSelectionTarget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Full-screen, image-backed runner tracking selector. */
public final class CompassTrackingSelectionScreen extends Screen {
    private final UUID sessionId;
    private final long authorizationId;
    private final TrackingSelectionTarget currentSelection;
    private final Consumer<TrackingSelectionTarget> selectionHandler;

    public CompassTrackingSelectionScreen(TrackingSelectionOpen request,
                                          Consumer<TrackingSelectionTarget> selectionHandler) {
        super(Component.translatable(CompassSelectorLanguage.titleKey(
                Minecraft.getInstance().getLanguageManager().getSelected())));
        this.sessionId = request.sessionId();
        this.authorizationId = request.authorizationId();
        this.currentSelection = request.currentSelection();
        this.selectionHandler = Objects.requireNonNull(selectionHandler, "selectionHandler");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY,
                                   float partialTick) {
        renderTransparentBackground(graphics);
        CompassSelectorLayout layout = CompassSelectorLayout.fit(width, height);
        int hovered = layout.panelAt(mouseX, mouseY).orElse(-1);
        int selected = currentSelection == TrackingSelectionTarget.NONE
                ? -1 : CompassSelectorOption.fromTarget(currentSelection).ordinal();

        for (CompassSelectorOption option : CompassSelectorOption.values()) {
            int index = option.ordinal();
            float opacity = CompassSelectorLayout.opacity(index, selected, hovered);
            renderPanel(graphics, layout.panel(index), option, opacity);
        }
        renderTitle(graphics, layout);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        CompassSelectorLayout layout = CompassSelectorLayout.fit(width, height);
        var selection = CompassSelectorInteraction.selectionAt(
                layout,
                (int) Math.floor(event.x()),
                (int) Math.floor(event.y()),
                event.button(),
                currentSelection);
        if (selection.isPresent()) {
            CompassSelectorCommit.commit(
                    selection.orElseThrow(),
                    selectionHandler,
                    this::playSelectionSound,
                    this::onClose);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public boolean matches(UUID expectedSessionId, long expectedAuthorizationId) {
        return sessionId.equals(expectedSessionId) && authorizationId == expectedAuthorizationId;
    }

    private void renderPanel(GuiGraphics graphics, SelectorRect panel,
                             CompassSelectorOption option, float opacity) {
        SelectorRect crop = CompassSelectorLayout.SOURCE_CROP;
        int textureColor = CompassSelectorColor.alphaWhite(opacity);
        int textColor = CompassSelectorColor.alphaWhite(opacity);
        Identifier texture = Identifier.fromNamespaceAndPath(
                CompassSelectorRenderSpec.NAMESPACE, option.texturePath());
        graphics.blit(CompassSelectorRenderPipeline.IMAGE, texture,
                panel.x(), panel.y(), crop.x(), crop.y(),
                panel.width(), panel.height(), crop.width(), crop.height(),
                2560, 1440, textureColor);

        SelectorTypography typography = CompassSelectorLayout.fit(width, height).typography();
        String baseKey = option.translationBaseKey();
        Component chinese = Component.translatable(baseKey + ".zh");
        int chineseScale = fittingScale(
                typography.chineseScale(), chinese, panel.width() - 8);
        int chineseY = panel.y() + Math.round(panel.height() * 0.67F);
        renderCenteredText(graphics, chinese, panel.x() + panel.width() / 2.0F,
                chineseY, chineseScale, textColor);

        int englishScale = typography.englishScale();
        Component[] english = new Component[option.englishLines()];
        for (int line = 0; line < english.length; line++) {
            english[line] = Component.translatable(baseKey + ".en." + (line + 1));
            englishScale = Math.min(englishScale,
                    fittingScale(englishScale, english[line], panel.width() - 8));
        }
        int gap = Math.max(3, panel.height() / 120);
        int englishY = chineseY + font.lineHeight * chineseScale + gap;
        for (int line = 0; line < english.length; line++) {
            renderCenteredText(graphics, english[line], panel.x() + panel.width() / 2.0F,
                    englishY + line * font.lineHeight * englishScale,
                    englishScale, textColor);
        }
    }

    private void renderTitle(GuiGraphics graphics, CompassSelectorLayout layout) {
        SelectorRect canvas = layout.canvas();
        int scale = fittingScale(layout.typography().titleScale(), title, canvas.width() - 16);
        int y = canvas.y() + Math.max(8, Math.round(canvas.height() * 0.05F));
        renderCenteredText(graphics, title, canvas.x() + canvas.width() / 2.0F,
                y, scale, 0xFFFFFFFF);
    }

    private int fittingScale(int preferred, Component text, int availableWidth) {
        int scale = preferred;
        while (scale > 1 && font.width(text) * scale > availableWidth) {
            scale--;
        }
        return scale;
    }

    private void renderCenteredText(GuiGraphics graphics, Component text,
                                    float centerX, float y, int scale, int color) {
        float renderedWidth = font.width(text) * scale;
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);
        try {
            graphics.drawString(font, text,
                    Math.round((centerX - renderedWidth / 2.0F) / scale),
                    Math.round(y / scale), color, true);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private void playSelectionSound() {
        minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
