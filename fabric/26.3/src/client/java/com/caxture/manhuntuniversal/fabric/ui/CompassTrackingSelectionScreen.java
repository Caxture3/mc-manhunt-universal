package com.caxture.manhuntuniversal.fabric.ui;

import com.caxture.manhuntuniversal.protocol.TrackingSelectionOpen;
import com.caxture.manhuntuniversal.protocol.TrackingSelectionTarget;
import com.caxture.manhuntuniversal.protocol.TrackingSelectionUpdate;
import com.caxture.manhuntuniversal.protocol.TrackingAvailability;
import com.caxture.manhuntuniversal.common.CountdownText;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    private TrackingSelectionTarget currentSelection;
    private final Consumer<TrackingSelectionTarget> selectionHandler;
    private final Runnable cancelHandler;
    private final TrackingAvailabilityClock availabilityClock;
    private boolean completed;

    public CompassTrackingSelectionScreen(TrackingSelectionOpen request,
                                          Consumer<TrackingSelectionTarget> selectionHandler,
                                          Runnable cancelHandler) {
        super(Component.translatable(CompassSelectorLanguage.titleKey(
                Minecraft.getInstance().getLanguageManager().getSelected())));
        this.sessionId = request.sessionId();
        this.authorizationId = request.authorizationId();
        this.currentSelection = request.currentSelection();
        this.selectionHandler = Objects.requireNonNull(selectionHandler, "selectionHandler");
        this.cancelHandler = Objects.requireNonNull(cancelHandler, "cancelHandler");
        this.availabilityClock = new TrackingAvailabilityClock(request.availability(), System.nanoTime());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float partialTick) {
        extractTransparentBackground(graphics);
        TrackingAvailability availability = availabilityClock.at(System.nanoTime());
        CompassSelectorLayout layout = CompassSelectorLayout.fit(width, height, availability);
        int hovered = layout.panelAt(mouseX, mouseY).orElse(-1);
        int selected = -1;
        for (int index = 0; index < layout.panelCount(); index++) {
            if (layout.option(index).target() == currentSelection) {
                selected = index;
            }
        }

        for (int index = 0; index < layout.panelCount(); index++) {
            CompassSelectorOption option = layout.option(index);
            boolean unlocked = availability.unlocked(option.target());
            float opacity = CompassSelectorLayout.opacity(index, selected, hovered, unlocked);
            renderPanel(graphics, layout, layout.panel(index), option, opacity,
                    availability.remainingMillis(option.target()));
            if (hovered == index && !unlocked && minecraft.options.allowCursorChanges().get()) {
                graphics.requestCursor(CursorTypes.NOT_ALLOWED);
            }
        }
        renderTitle(graphics, layout);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        TrackingAvailability availability = availabilityClock.at(System.nanoTime());
        CompassSelectorLayout layout = CompassSelectorLayout.fit(width, height, availability);
        var selection = CompassSelectorInteraction.selectionAt(
                layout,
                (int) Math.floor(event.x()),
                (int) Math.floor(event.y()),
                event.button(),
                currentSelection, availability);
        if (selection.isPresent()) {
            completed = true;
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
    public void removed() {
        if (!completed) {
            completed = true;
            cancelHandler.run();
        }
        super.removed();
    }

    public void closeFromServer() {
        completed = true;
        minecraft.setScreenAndShow(null);
    }

    public void update(TrackingSelectionUpdate update) {
        if (completed || !matches(update.sessionId(), update.authorizationId())) {
            return;
        }
        currentSelection = update.currentSelection();
        availabilityClock.update(update.availability(), System.nanoTime());
        if (!update.availability().hasVisibleOptions()) {
            closeFromServer();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public boolean matches(UUID expectedSessionId, long expectedAuthorizationId) {
        return sessionId.equals(expectedSessionId) && authorizationId == expectedAuthorizationId;
    }

    private void renderPanel(GuiGraphicsExtractor graphics, CompassSelectorLayout layout, SelectorRect panel,
                             CompassSelectorOption option, float opacity, long remainingMillis) {
        SelectorRect crop = layout.sourceCrop();
        int textureColor = CompassSelectorColor.alphaWhite(opacity);
        int textColor = CompassSelectorColor.alphaWhite(opacity);
        Identifier texture = Identifier.fromNamespaceAndPath(
                CompassSelectorRenderSpec.NAMESPACE, option.texturePath());
        graphics.blit(CompassSelectorRenderPipeline.IMAGE, texture,
                panel.x(), panel.y(), crop.x(), crop.y(),
                panel.width(), panel.height(), crop.width(), crop.height(),
                2560, 1440, textureColor);

        SelectorTypography typography = layout.typography();
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
        if (remainingMillis > 0) {
            int countdownScale = englishScale * 2;
            renderCenteredText(graphics, Component.literal(CountdownText.format(remainingMillis)),
                    panel.x() + panel.width() / 2.0F,
                    panel.y() + (panel.height() - font.lineHeight * countdownScale) / 2.0F,
                    countdownScale, 0xFFFFFFFF);
        }
    }

    private void renderTitle(GuiGraphicsExtractor graphics, CompassSelectorLayout layout) {
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

    private void renderCenteredText(GuiGraphicsExtractor graphics, Component text,
                                    float centerX, float y, int scale, int color) {
        float renderedWidth = font.width(text) * scale;
        graphics.pose().pushMatrix();
        graphics.pose().scale(scale, scale);
        try {
            graphics.text(font, text,
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
