package com.caxture.manhuntuniversal.fabric.ui;

import com.caxture.manhuntuniversal.protocol.*;
import com.caxture.manhuntuniversal.common.CountdownText;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import java.util.*;
import java.util.function.BiConsumer;

/** Generic data-driven selector: server extensions do not install client code. */
public final class DynamicTrackingSelectionScreen extends Screen {
    private final UUID session;
    private final long authorization;
    private final SelectorPageState state;
    private final BiConsumer<String, Long> choice;
    private final Runnable cancel;
    private boolean completed;
    public DynamicTrackingSelectionScreen(DynamicTrackingOpen request, BiConsumer<String,Long> choice, Runnable cancel) {
        super(Component.translatable(CompassSelectorLanguage.titleKey(Minecraft.getInstance().getLanguageManager().getSelected())));
        session = request.sessionId(); authorization = request.authorizationId(); this.choice = Objects.requireNonNull(choice); this.cancel = Objects.requireNonNull(cancel);
        state = new SelectorPageState(request.directoryRevision(),request.currentSelection(),request.targets(),System.nanoTime());
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        var layout = CompassSelectorLayout.fit(Math.max(1,width),Math.max(1,height)); var canvas = layout.canvas();
        long now = System.nanoTime(); var options = state.options();
        int hovered = mouseY >= height-28 && state.pages()>1 ? -1 : state.hit(canvas,mouseX,mouseY).orElse(-1);
        int selected = -1; for (int index=0; index<options.size(); index++) if (state.selection().filter(options.get(index).id()::equals).isPresent()) selected=index;
        for (int index=0; index<options.size(); index++) {
            var option=options.get(index); var panel=state.panel(canvas,index); boolean enabled=state.enabled(option,now);
            float opacity=CompassSelectorLayout.opacity(index,selected,hovered,enabled);
            Optional<String> resource=SelectorResourceStyle.texture(option,id->minecraft.getResourceManager().getResource(Identifier.parse(id)).isPresent());
            int color=CompassSelectorColor.alphaWhite(opacity);
            if (resource.isPresent()) {
                int cropWidth=2560/Math.max(1,options.size());
                graphics.blit(CompassSelectorRenderPipeline.IMAGE, Identifier.parse(resource.get()), panel.x(),panel.y(),
                        (2560-cropWidth)/2,0,panel.width(),panel.height(),cropWidth,1440,2560,1440,color);
            } else graphics.fill(panel.x(),panel.y(),panel.x()+panel.width(),panel.y()+panel.height(),
                    ((Math.round(opacity*255)&255)<<24)|(option.color()&0xffffff));
            var labels=SelectorTargetLabels.resolve(option.id(),option.name(),option.secondaryName());
            int mainScale=fit(layout.typography().chineseScale(),labels.primary(),panel.width()-8);
            int englishScale=fit(layout.typography().englishScale(),labels.secondary(),panel.width()-8);
            int y=panel.y()+Math.round(panel.height()*0.67f);
            text(graphics,labels.primary(),panel.x()+panel.width()/2f,y,mainScale,color,panel.width()-8);
            text(graphics,labels.secondary(),panel.x()+panel.width()/2f,y+font.lineHeight*mainScale+4,englishScale,color,panel.width()-8);
            if (!option.description().isBlank()) text(graphics,option.description(),panel.x()+panel.width()/2f,
                    panel.y()+panel.height()*0.88f,1,color,panel.width()-8);
            option.itemIcon().flatMap(id->BuiltInRegistries.ITEM.getOptional(Identifier.parse(id))).ifPresent(item->
                    graphics.renderItem(item.getDefaultInstance(),panel.x()+panel.width()/2-8,panel.y()+Math.round(panel.height()*0.24f)));
            long remaining=state.remaining(option,now);
            if (remaining>0) {
                int scale=englishScale*2;
                text(graphics,CountdownText.format(remaining),panel.x()+panel.width()/2f,
                        panel.y()+(panel.height()-font.lineHeight*scale)/2f,scale,0xffffffff,panel.width()-8);
            } else if (!enabled && !option.lockedReason().isBlank()) text(graphics,option.lockedReason(),panel.x()+panel.width()/2f,
                    panel.y()+panel.height()/2f,1,0xffffffff,panel.width()-8);
            if (hovered==index && !enabled && minecraft.options.allowCursorChanges().get()) graphics.requestCursor(CursorTypes.NOT_ALLOWED);
        }
        int titleScale=fit(layout.typography().titleScale(),getTitle().getString(),canvas.width()-16);
        text(graphics,getTitle().getString(),canvas.x()+canvas.width()/2f,canvas.y()+Math.max(8,canvas.height()*0.05f),titleScale,0xffffffff,canvas.width()-16);
        if (state.pages()>1) text(graphics,"◀   "+(state.page()+1)+" / "+state.pages()+"   ▶",width/2f,height-22,1,0xffffffff,width-8);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (CompassSelectorInteraction.isPrimaryButton(event.button()) && state.pages()>1 && event.y()>=height-28 && event.y()<height) {
            if (event.x()<width/2d) state.previous(); else state.next(); return true;
        }
        var layout=CompassSelectorLayout.fit(Math.max(1,width),Math.max(1,height));
        var selection=CompassSelectorInteraction.selectionAt(state,layout.canvas(),
                (int)Math.floor(event.x()),(int)Math.floor(event.y()),event.button(),System.nanoTime());
        if (selection.isPresent()) {
            var option=selection.orElseThrow();
            completed=true; choice.accept(option.id(),state.revision());
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK,1f)); onClose(); return true;
        }
        return super.mouseClicked(event,doubleClick);
    }
    public boolean matches(UUID session,long authorization) { return this.session.equals(session)&&this.authorization==authorization; }
    public void update(DynamicTrackingUpdate update) {
        if (completed || !matches(update.sessionId(),update.authorizationId())) return;
        state.update(update.directoryRevision(),update.currentSelection(),update.targets(),System.nanoTime());
        if (state.options().isEmpty()) closeFromServer();
    }
    public void closeFromServer() { completed=true; minecraft.setScreenAndShow(null); }
    @Override public void onClose() { minecraft.setScreenAndShow(null); }
    @Override public void removed() { if (!completed) {completed=true;cancel.run();} super.removed(); }
    @Override public boolean isPauseScreen() { return false; }
    private int fit(int preferred,String text,int available) { int scale=preferred; while(scale>1&&font.width(text)*scale>available) scale--;return scale; }
    private void text(GuiGraphics graphics,String value,float center,float y,int scale,int color,int width) {
        if(width<=0)return; String clipped=font.plainSubstrByWidth(value,Math.max(1,width/Math.max(1,scale)));
        graphics.pose().pushMatrix();graphics.pose().scale(scale,scale);
        try {graphics.drawString(font,Component.literal(clipped),Math.round((center-font.width(clipped)*scale/2f)/scale),Math.round(y/scale),color,true);}
        finally {graphics.pose().popMatrix();}
    }
}
