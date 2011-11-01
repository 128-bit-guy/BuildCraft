/** 
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 * 
 * BuildCraft is distributed under the terms of the Minecraft Mod Public 
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */

package net.minecraft.src.buildcraft.transport;

import net.minecraft.src.EntityItem;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.ModLoader;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;
import net.minecraft.src.buildcraft.api.APIProxy;

public class TransportProxy {

	public static void displayGUIFilter(EntityPlayer entityplayer, TileGenericPipe tilePipe) {
		if (!APIProxy.isClient(APIProxy.getWorld())) {
			ModLoader.getMinecraftInstance().displayGuiScreen(
					new GuiDiamondPipe(entityplayer.inventory, tilePipe));
		}
	}
	
	static public void obsidianPipePickup (World world, EntityItem item, TileEntity tile) {
		ModLoader.getMinecraftInstance().effectRenderer
		.addEffect(new TileEntityPickupFX(world, item, tile));
	}
	
}
