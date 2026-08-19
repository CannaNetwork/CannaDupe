package net.cannanetwork.cannadupe.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.cannanetwork.cannadupe.CannaDupeClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** A visual copy of the player's actual inventory, used to choose the dupe item. */
public final class ItemPickerScreen extends Screen {
    private static final int COLS = 9, ROWS = 4, SLOT = 20, PANEL_W = 218;
    private final Screen parent;
    private final List<Entry> entries = new ArrayList<>();
    private EditBox search;
    private String filter = "";
    private int selectedSlot = -1;
    private int panelX, panelY;

    public ItemPickerScreen(Screen parent) { super(Component.literal("CannaDupe Item Picker")); this.parent = parent; snapshotInventory(); }

    @Override protected void init() {
        panelX = (width - PANEL_W) / 2; panelY = Math.max(12, (height - 208) / 2);
        addRenderableOnly(new GridRenderer());
        search = addRenderableWidget(new EditBox(font, panelX + 9, panelY + 42, PANEL_W - 18, 20, Component.literal("Search inventory")));
        search.setHint(Component.literal("Search inventory..."));
        search.setResponder(value -> filter = value.strip().toLowerCase(Locale.ROOT));
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose()).bounds(panelX + 9, panelY + 178, 94, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Use Selected Item"), button -> onClose()).bounds(panelX + 107, panelY + 178, 102, 20).build());
        setInitialFocus(search);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            Entry entry = entryAt((int) event.x(), (int) event.y());
            if (entry != null && visible(entry) && !entry.stack.isEmpty()) {
                selectedSlot = entry.index;
                CannaDupeClient.CONTROLLER.setTarget(entry.stack.getItem());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    private Entry entryAt(int mouseX, int mouseY) { int gridX=panelX+19,gridY=panelY+70,col=(mouseX-gridX)/SLOT,row=(mouseY-gridY)/SLOT; if(mouseX<gridX||mouseY<gridY||col<0||col>=COLS||row<0||row>=ROWS)return null; return entries.get(row*COLS+col); }
    private boolean visible(Entry entry) { return filter.isEmpty() || entry.search.contains(filter); }
    private String selectedText() { for (Entry entry : entries) if (entry.index == selectedSlot) return "Selected: " + entry.stack.getHoverName().getString() + " x" + entry.stack.getCount(); return "Selected: none"; }
    private void snapshotInventory() { Inventory inv=Minecraft.getInstance().player==null?null:Minecraft.getInstance().player.getInventory(); for(int i=0;i<COLS*ROWS;i++){ItemStack stack=inv==null?ItemStack.EMPTY:inv.getItem(i).copy();String query=stack.isEmpty()?"":(stack.getHoverName().getString()+" "+stack.getItem()).toLowerCase(Locale.ROOT);entries.add(new Entry(i,stack,query));} }

    private final class GridRenderer implements Renderable {
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_W + 2, panelY + 210, 0xff090909);
            graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + 208, 0xff282828);
            graphics.centeredText(font, "CannaDupe", panelX + PANEL_W / 2, panelY + 10, 0xffffffff);
            graphics.centeredText(font, "Choose an item to duplicate", panelX + PANEL_W / 2, panelY + 25, 0xffbdbdbd);
            int gridX=panelX+19,gridY=panelY+70;
            for (Entry entry : entries) drawSlot(graphics, entry, gridX+(entry.index%COLS)*SLOT, gridY+(entry.index/COLS)*SLOT);
            graphics.fill(panelX+9,panelY+158,panelX+PANEL_W-9,panelY+174,0xff151515);
            graphics.text(font, selectedText(), panelX+14, panelY+162, 0xffdedede, false);
            Entry hovered=entryAt(mouseX,mouseY);
            if(hovered!=null&&visible(hovered)&&!hovered.stack.isEmpty())graphics.setTooltipForNextFrame(font,hovered.stack,mouseX,mouseY);
        }
        private void drawSlot(GuiGraphicsExtractor graphics, Entry entry, int x, int y) {
            boolean selected=entry.index==selectedSlot;
            graphics.fill(x,y,x+18,y+18,selected?0xff35bc53:0xff5a5a5a);
            graphics.fill(x+1,y+1,x+17,y+17,selected?0xff164f27:0xff303030);
            if(visible(entry)&&!entry.stack.isEmpty()){graphics.item(entry.stack,x+1,y+1);graphics.itemDecorations(font,entry.stack,x+1,y+1);}
        }
    }
    private record Entry(int index, ItemStack stack, String search) { }
}
