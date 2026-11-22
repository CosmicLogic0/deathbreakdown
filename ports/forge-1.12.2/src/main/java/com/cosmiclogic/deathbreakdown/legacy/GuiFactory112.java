package com.cosmiclogic.deathbreakdown.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;

import java.util.Set;

/** The mod list's "Config" button opens Death Breakdown's settings. */
public class GuiFactory112 implements IModGuiFactory {
    @Override public void initialize(Minecraft mc) {}
    @Override public boolean hasConfigGui() { return true; }
    @Override public GuiScreen createConfigGui(GuiScreen parent) { return new ConfigScreen112(parent); }
    @Override public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() { return null; }
}
