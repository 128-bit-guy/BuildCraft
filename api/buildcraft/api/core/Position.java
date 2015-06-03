/** Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution. */
package buildcraft.api.core;

import io.netty.buffer.ByteBuf;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class Position implements ISerializable {

	public double x, y, z;
	public EnumFacing orientation;

	public Position() {
		x = 0;
		y = 0;
		z = 0;
		orientation = null;
	}

	public Position(double ci, double cj, double ck) {
		x = ci;
		y = cj;
		z = ck;
		orientation = null;
	}

	public Position(double ci, double cj, double ck, EnumFacing corientation) {
		x = ci;
		y = cj;
		z = ck;
		orientation = corientation;
	}

	public Position(Position p) {
		x = p.x;
		y = p.y;
		z = p.z;
		orientation = p.orientation;
	}

	public Position(NBTTagCompound nbttagcompound) {
		readFromNBT(nbttagcompound);
	}

	public Position(TileEntity tile) {
		x = tile.getPos().getX();
		y = tile.getPos().getY();
		z = tile.getPos().getZ();
		orientation = null;
	}

	public Position(BlockPos index) {
		x = index.getX();
		y = index.getY();
		z = index.getZ();
		orientation = null;
	}

	public void moveRight(double step) {
		switch (orientation) {
			case SOUTH:
				x = x - step;
				break;
			case NORTH:
				x = x + step;
				break;
			case EAST:
				z = z + step;
				break;
			case WEST:
				z = z - step;
				break;
			default:
		}
	}

	public void moveLeft(double step) {
		moveRight(-step);
	}

	public void moveForwards(double step) {
		switch (orientation) {
			case UP:
				y = y + step;
				break;
			case DOWN:
				y = y - step;
				break;
			case SOUTH:
				z = z + step;
				break;
			case NORTH:
				z = z - step;
				break;
			case EAST:
				x = x + step;
				break;
			case WEST:
				x = x - step;
				break;
			default:
		}
	}

	public void moveBackwards(double step) {
		moveForwards(-step);
	}

	public void moveUp(double step) {
		switch (orientation) {
			case SOUTH:
			case NORTH:
			case EAST:
			case WEST:
				y = y + step;
				break;
			default:
		}

	}

	public void moveDown(double step) {
		moveUp(-step);
	}

	public void writeToNBT(NBTTagCompound nbttagcompound) {
		if (orientation == null) {
			orientation = EnumFacing.NORTH;
		}

		nbttagcompound.setDouble("i", x);
		nbttagcompound.setDouble("j", y);
		nbttagcompound.setDouble("k", z);
		nbttagcompound.setByte("orientation", (byte) orientation.ordinal());
	}

	public void readFromNBT(NBTTagCompound nbttagcompound) {
		x = nbttagcompound.getDouble("i");
		y = nbttagcompound.getDouble("j");
		z = nbttagcompound.getDouble("k");
		orientation = EnumFacing.values()[nbttagcompound.getByte("orientation")];
	}

	@Override
	public String toString() {
		return "{" + x + ", " + y + ", " + z + "}";
	}

	public Position min(Position p) {
		return new Position(p.x > x ? x : p.x, p.y > y ? y : p.y, p.z > z ? z : p.z);
	}

	public Position max(Position p) {
		return new Position(p.x < x ? x : p.x, p.y < y ? y : p.y, p.z < z ? z : p.z);
	}

	public boolean isClose(Position newPosition, float f) {
		double dx = x - newPosition.x;
		double dy = y - newPosition.y;
		double dz = z - newPosition.z;

		double sqrDis = dx * dx + dy * dy + dz * dz;

		return !(sqrDis > f * f);
	}

	@Override
	public void readData(ByteBuf stream) {
		x = stream.readDouble();
		y = stream.readDouble();
		z = stream.readDouble();
		byte b = stream.readByte();
		if (b != 6) {
			orientation = EnumFacing.values()[b];
		} else {
			orientation = null;
		}
	}

	@Override
	public void writeData(ByteBuf stream) {
		stream.writeDouble(x);
		stream.writeDouble(y);
		stream.writeDouble(z);
		stream.writeByte(orientation == null ? 6 : orientation.ordinal());
	}

	@Override
	public int hashCode() {
		return (51 * (int) x) + (13 * (int) y) + (int) z;
	}
}
