/** 
 * BuildCraft is open-source. It is distributed under the terms of the 
 * BuildCraft Open Source License. It grants rights to read, modify, compile
 * or run the code. It does *NOT* grant the right to redistribute this software
 * or its modifications in any form, binary or source, except if expressively
 * granted by the copyright holder.
 */

package net.minecraft.src.buildcraft.transport;

import java.util.LinkedList;
import java.util.TreeMap;

import net.minecraft.src.EntityItem;
import net.minecraft.src.IInventory;
import net.minecraft.src.ItemStack;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.NBTTagList;
import net.minecraft.src.Packet230ModLoader;
import net.minecraft.src.TileEntity;
import net.minecraft.src.mod_BuildCraftTransport;
import net.minecraft.src.buildcraft.api.APIProxy;
import net.minecraft.src.buildcraft.api.EntityPassiveItem;
import net.minecraft.src.buildcraft.api.IPipeEntry;
import net.minecraft.src.buildcraft.api.Orientations;
import net.minecraft.src.buildcraft.api.Position;
import net.minecraft.src.buildcraft.core.CoreProxy;
import net.minecraft.src.buildcraft.core.IMachine;
import net.minecraft.src.buildcraft.core.PacketIds;
import net.minecraft.src.buildcraft.core.StackUtil;
import net.minecraft.src.buildcraft.core.Utils;

public class PipeTransportItems extends PipeTransport {	
	
	public TreeMap<Integer, EntityData> travelingEntities = new TreeMap<Integer, EntityData> ();
	LinkedList <EntityData> entitiesToLoad = new LinkedList <EntityData> ();
	
	public class EntityData {	
		// TODO: Move passive data here too, like position, speed and all?
		boolean toCenter = true;
		public EntityPassiveItem item;
		
		public Orientations orientation;
		
		public EntityData (EntityPassiveItem citem, Orientations orientation) {
			item = citem;
			
			this.orientation = orientation;
		}
	}
	
	public void readjustSpeed (EntityPassiveItem item) {
		if (container.pipe instanceof IPipeTransportItemsHook) {
			((IPipeTransportItemsHook) container.pipe).readjustSpeed(item);
		} else {
			defaultReajustSpeed(item);
		}
	}
	
	public void defaultReajustSpeed (EntityPassiveItem item) {
		
		if (item.speed > Utils.pipeNormalSpeed) {
			item.speed = item.speed - Utils.pipeNormalSpeed;
		}
		
		if (item.speed < Utils.pipeNormalSpeed) {
			item.speed = Utils.pipeNormalSpeed;
		}
	}
	
	@Override
	public void entityEntering (EntityPassiveItem item, Orientations orientation) {
		readjustSpeed(item);			
				
		if (!travelingEntities.containsKey(new Integer(item.entityId))) {
			travelingEntities.put(new Integer(item.entityId), new EntityData(
					item, orientation));
			
			item.container = container;
		}
		
		// Reajusting Ypos to make sure the object looks like sitting on the
		// pipe.
		if (orientation != Orientations.YPos && orientation != Orientations.YNeg) {
			item.setPosition(item.posX, yCoord + Utils.getPipeFloorOf(item.item), item.posZ);
		}
		
		if (container.pipe instanceof IPipeTransportItemsHook) {
			((IPipeTransportItemsHook) container.pipe).entityEntered(item,
					orientation);
		}
		
		if (APIProxy.isServerSide()) {
			if (item.synchroTracker.markTimeIfDelay(worldObj, 20)) {
				// FIXME: what about the other items???
				CoreProxy.sendToPlayers(createItemPacket(item, orientation),
						xCoord, yCoord, zCoord, 50,
						mod_BuildCraftTransport.instance);
			}
		}
	}

	/**
	 * Returns a list of all possible movements, that is to say adjacent 
	 * implementers of IPipeEntry or TileEntityChest.
	 */
	public LinkedList<Orientations> getPossibleMovements(Position pos,
			EntityPassiveItem item) {
		LinkedList<Orientations> result = new LinkedList<Orientations>();
		
		for (int o = 0; o < 6; ++o) {
			if (Orientations.values()[o] != pos.orientation.reverse()
					&& container.pipe.outputOpen(Orientations.values()[o])) {
				Position newPos = new Position(pos);
				newPos.orientation = Orientations.values()[o];
				newPos.moveForwards(1.0);

				if (canReceivePipeObjects(newPos, item)) {
					result.add(newPos.orientation);
				}
			}
		}
		
		if (this.container.pipe instanceof IPipeTransportItemsHook) {
			result = ((IPipeTransportItemsHook) this.container.pipe)
					.filterPossibleMovements(result, pos, item);
		}

		return result;
	}
	
	public boolean canReceivePipeObjects(Position p,
			EntityPassiveItem item) {
		TileEntity entity = worldObj.getBlockTileEntity((int) p.x, (int) p.y,
				(int) p.z);
		
		if (!Utils.checkPipesConnections(worldObj, (int) p.x, (int) p.y,
				(int) p.z, xCoord, yCoord, zCoord)) {
			return false;
		}
		
		if (entity instanceof IPipeEntry) {
			return true;
		} else if (entity instanceof TileGenericPipe) {
			TileGenericPipe pipe = (TileGenericPipe) entity;
			
			return pipe.pipe.transport instanceof PipeTransportItems;
		} else if (entity instanceof IInventory) {					
			if (new StackUtil(item.item).checkAvailableSlot((IInventory) entity,
					 false, p.orientation.reverse())) {
				return true;
			}
		}
		
		return false;
	}
		
	@Override
	public void updateEntity() {		
		moveSolids();
	}
	
	private void moveSolids () {
		for (EntityData data : entitiesToLoad) {
			travelingEntities.put(new Integer(data.item.entityId), data);
		}
		
		entitiesToLoad.clear();
		
		LinkedList <EntityData> toRemove = new LinkedList <EntityData> ();				
		
		for (EntityData data : travelingEntities.values()) {
			Position motion = new Position (0, 0, 0, data.orientation);
			motion.moveForwards(data.item.speed);												
			
			data.item.setPosition(data.item.posX + motion.x, data.item.posY
					+ motion.y, data.item.posZ + motion.z);
									
			if ((data.toCenter && middleReached(data)) || outOfBounds(data)) {
				data.toCenter = false;
				
				// Reajusting to the middle 

				data.item.setPosition(xCoord + 0.5,
						yCoord + Utils.getPipeFloorOf(data.item.item),
						zCoord + + 0.5);

				Orientations nextOrientation = resolveDestination (data);
				
				if (nextOrientation == Orientations.Unknown) {
					toRemove.add(data);
					EntityItem dropped = data.item.toEntityItem(worldObj,
							data.orientation);
					
					if (dropped != null) {
						// On SMP, the client side doesn't actually drops 
						// items
						onDropped(dropped);
					}
				} else {
					data.orientation = nextOrientation;
				}
				
				
		    } else if (!data.toCenter && endReached (data)) {
		    	toRemove.add(data);
		    	
				Position destPos = new Position(xCoord, yCoord, zCoord,
						data.orientation);
		    	
				destPos.moveForwards(1.0);
				
				TileEntity tile = worldObj.getBlockTileEntity((int) destPos.x,
						(int) destPos.y, (int) destPos.z);
				
				if (tile instanceof IPipeEntry) {
					((IPipeEntry) tile).entityEntering(data.item,
							data.orientation);
				} else if (tile instanceof TileGenericPipe
						&& ((TileGenericPipe) tile).pipe.transport instanceof PipeTransportItems) {
					TileGenericPipe pipe = (TileGenericPipe) tile;
						
					((PipeTransportItems) pipe.pipe.transport).entityEntering(
							data.item, data.orientation);
				} else if (tile instanceof IInventory) {
					StackUtil utils = new StackUtil(data.item.item);
					
					if (!APIProxy.isClient(worldObj)) {
						if (utils.checkAvailableSlot((IInventory) tile, true,
								destPos.orientation.reverse())
								&& utils.items.stackSize == 0) {
							
							// Do nothing, we're adding the object to the world							
						} else {
							data.item.item = utils.items;
							EntityItem dropped = data.item.toEntityItem(
									worldObj, data.orientation);
							
							if (dropped != null) {
								// On SMP, the client side doesn't actually drops 
								// items
								onDropped(dropped);
							}
						}
					}
				} else {
					EntityItem dropped = data.item.toEntityItem(worldObj,
							data.orientation);
					
					if (dropped != null) {
						// On SMP, the client side doesn't actually drops 
						// items
						onDropped(dropped);
					}
										
				}

		    }
		}	
		
		travelingEntities.values().removeAll(toRemove);
	}
	
	public boolean middleReached(EntityData entity) {
		float middleLimit = entity.item.speed * 1.01F;
		return (Math.abs(xCoord + 0.5 - entity.item.posX) < middleLimit
				&& Math.abs(yCoord + Utils.getPipeFloorOf(entity.item.item)
						- entity.item.posY) < middleLimit && Math.abs(zCoord + 0.5
				- entity.item.posZ) < middleLimit);
	}
	
	public boolean endReached (EntityData entity) {
		return entity.item.posX > xCoord + 1.0 
		|| entity.item.posX < xCoord
		|| entity.item.posY > yCoord + 1.0
		|| entity.item.posY < yCoord
		|| entity.item.posZ > zCoord + 1.0
		|| entity.item.posZ < zCoord;
	}
	
	public boolean outOfBounds (EntityData entity) {
		return entity.item.posX > xCoord + 2.0 
		|| entity.item.posX < xCoord - 1.0
		|| entity.item.posY > yCoord + 2.0
		|| entity.item.posY < yCoord - 1.0
		|| entity.item.posZ > zCoord + 2.0
		|| entity.item.posZ < zCoord - 1.0;
	}
	
	public Position getPosition() {
		return new Position (xCoord, yCoord, zCoord);
	}
	
	public void readFromNBT(NBTTagCompound nbttagcompound) {
		super.readFromNBT(nbttagcompound);
		
		NBTTagList nbttaglist = nbttagcompound.getTagList("travelingEntities");
		
		for (int j = 0; j < nbttaglist.tagCount(); ++j) {
			try {
				NBTTagCompound nbttagcompound2 = (NBTTagCompound) nbttaglist
				.tagAt(j);			

				EntityPassiveItem entity = new EntityPassiveItem (APIProxy.getWorld());
				entity.readFromNBT(nbttagcompound2);
				entity.container = container;

				EntityData data = new EntityData(entity,
						Orientations.values()[nbttagcompound2.getInteger("orientation")]);
				data.toCenter = nbttagcompound2.getBoolean("toCenter"); 

				entitiesToLoad.add(data);
			} catch (Throwable t) {
				t.printStackTrace();
				//  It may be the case that entities cannot be reloaded between
				//  two versions - ignore these errors.
			}
		}
    }

    public void writeToNBT(NBTTagCompound nbttagcompound) {
    	super.writeToNBT(nbttagcompound);    	
    	
    	NBTTagList nbttaglist = new NBTTagList();
    	    	
    	for (EntityData data : travelingEntities.values()) {    		
    		NBTTagCompound nbttagcompound2 = new NBTTagCompound ();
    		nbttaglist.setTag(nbttagcompound2);
    		data.item.writeToNBT(nbttagcompound2);
    		nbttagcompound2.setBoolean("toCenter", data.toCenter);
    		nbttagcompound2.setInteger("orientation", data.orientation.ordinal());    		
    	}
    	
    	nbttagcompound.setTag("travelingEntities", nbttaglist);
    }
    
    public Orientations resolveDestination (EntityData data) {
    	LinkedList<Orientations> listOfPossibleMovements = getPossibleMovements(new Position(
				xCoord, yCoord, zCoord, data.orientation), data.item);
		
		if (listOfPossibleMovements.size() == 0) {					
			return Orientations.Unknown;													
		} else {
			int i;
			
			if (APIProxy.isClient(worldObj) || APIProxy.isServerSide()) {
				i = Math.abs(data.item.entityId + xCoord + yCoord + zCoord
						+ data.item.deterministicRandomization)
						% listOfPossibleMovements.size();
			} else {
				i = worldObj.rand.nextInt(listOfPossibleMovements.size());
			}
					
			
			return listOfPossibleMovements.get(i);															
		}				
    }
    
    public void destroy () {
    	for (EntityData data : travelingEntities.values()) {
    		data.item.toEntityItem(worldObj, data.orientation);
    	}
    	
    	travelingEntities.clear();
    }
    
    protected void doWork () {}

	public void handleItemPacket(Packet230ModLoader packet) {
		if (packet.packetType != PacketIds.PipeItem.ordinal()) {
			return;
		}
		
		EntityPassiveItem item = new EntityPassiveItem(worldObj);
		item.entityId = packet.dataInt [3];

		int itemId = packet.dataInt [5];
		int stackSize = packet.dataInt [6];
		int dmg = packet.dataInt [7];

		item.item = new ItemStack(itemId, stackSize, dmg);		
		
		Orientations orientation;						
		orientation = Orientations.values()[packet.dataInt [4]];
		
		item.setPosition(packet.dataFloat[0], packet.dataFloat[1],
				packet.dataFloat[2]);
		item.speed = packet.dataFloat [3];
		item.deterministicRandomization = packet.dataInt [8];
		
		if (item.container == null) {
			travelingEntities.put(new Integer(item.entityId), new EntityData(
					item, orientation));
			item.container = container;
		} else {
			travelingEntities.get(new Integer(item.entityId)).orientation = orientation;
		}
	}
	
	public Packet230ModLoader createItemPacket (EntityPassiveItem item, Orientations orientation) {
		Packet230ModLoader packet = new Packet230ModLoader();
		
		item.deterministicRandomization += worldObj.rand.nextInt(6);
		
		packet.modId = mod_BuildCraftTransport.instance.getId();
		packet.packetType = PacketIds.PipeItem.ordinal();
		packet.isChunkDataPacket = true;
		
		packet.dataInt = new int [9];
		packet.dataInt [0] = xCoord;
		packet.dataInt [1] = yCoord;
		packet.dataInt [2] = zCoord;
		packet.dataInt [3] = item.entityId;
		packet.dataInt [4] = orientation.ordinal();
		packet.dataInt [5] = item.item.itemID;
		packet.dataInt [6] = item.item.stackSize;
		packet.dataInt [7] = item.item.getItemDamage();
		packet.dataInt [8] = item.deterministicRandomization;
		
		packet.dataFloat = new float [4];
		packet.dataFloat [0] = (float) item.posX;
		packet.dataFloat [1] = (float) item.posY;
		packet.dataFloat [2] = (float) item.posZ;
		packet.dataFloat [3] = (float) item.speed;
		
		return packet;		
	}

	public int getNumberOfItems () {
		return travelingEntities.size();
	}

	public void onDropped (EntityItem item) {
		
	}
	
	protected void neighborChange() {
			
	}
	
	public boolean isPipeConnected(TileEntity tile) {
		return tile instanceof TileGenericPipe 
    	    || tile instanceof IPipeEntry
			|| tile instanceof IInventory
			|| (tile instanceof IMachine && ((IMachine) tile).manageSolids());
	}
	
	public boolean acceptItems() {
		return true;
	}

}
