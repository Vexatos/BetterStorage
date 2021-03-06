package net.mcft.copy.betterstorage.block.tileentity;

import net.mcft.copy.betterstorage.BetterStorage;
import net.mcft.copy.betterstorage.inventory.InventoryTileEntity;
import net.mcft.copy.betterstorage.utils.PlayerUtils;
import net.mcft.copy.betterstorage.utils.WorldUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet132TileEntityData;
import net.minecraftforge.common.ForgeDirection;

public abstract class TileEntityConnectable extends TileEntityContainer implements IInventory {
	
	private ForgeDirection orientation = ForgeDirection.UNKNOWN;
	private ForgeDirection connected = ForgeDirection.UNKNOWN;
	
	public ForgeDirection getOrientation() { return orientation; }
	public void setOrientation(ForgeDirection orientation) { this.orientation = orientation; }
	
	public ForgeDirection getConnected() { return connected; }
	public void setConnected(ForgeDirection connected) { this.connected = connected; }
	
	/** Returns the possible directions the container can connect to. */
	public abstract ForgeDirection[] getPossibleNeighbors();
	
	/** Returns if this container is connected to another one. */
	public boolean isConnected() { return (getConnected() != ForgeDirection.UNKNOWN); }
	
	/** Returns if this container is the main container, or not connected to another container. */
	public boolean isMain() {
		ForgeDirection connected = getConnected();
		return (!isConnected() || connected.offsetX + connected.offsetY + connected.offsetZ > 0);
	}
	
	/** Returns the main container. */
	public TileEntityConnectable getMainTileEntity() {
		if (isMain()) return this;
		TileEntityConnectable connectable = getConnectedTileEntity();
		if (connectable != null) return connectable;
		BetterStorage.log.warning("getConnectedTileEntity() returned null.");
		return this;
	}
	
	/** Returns the connected container. */
	public TileEntityConnectable getConnectedTileEntity() {
		if (!isConnected()) return null;
		ForgeDirection connected = getConnected();
		int x = xCoord + connected.offsetX;
		int y = yCoord + connected.offsetY;
		int z = zCoord + connected.offsetZ;
		return WorldUtils.get(worldObj, x, y, z, TileEntityConnectable.class);
	}
	
	/** Returns if the container can connect to the other container. */
	public boolean canConnect(TileEntityConnectable connectable) {
		return ((connectable != null) &&                                  // check for null
		        (getBlockType() == connectable.getBlockType()) &&         // check for same block tpye
		        (getBlockMetadata() == connectable.getBlockMetadata()) && // check for same material
		        (getOrientation() == connectable.orientation) &&               // check for same orientation
		        // Make sure the containers are not already connected.
		        !isConnected() && !connectable.isConnected());
	}
	
	/** Connects the container to any other containers nearby, if possible. */
	public void checkForConnections() {
		if (worldObj.isRemote) return;
		TileEntityConnectable connectableFound = null;
		ForgeDirection dirFound = ForgeDirection.UNKNOWN;
		for (ForgeDirection dir : getPossibleNeighbors()) {
			int x = xCoord + dir.offsetX;
			int y = yCoord + dir.offsetY;
			int z = zCoord + dir.offsetZ;
			TileEntityConnectable connectable = WorldUtils.get(worldObj, x, y, z, TileEntityConnectable.class);
			if (!canConnect(connectable)) continue;
			if (connectableFound != null) return;
			connectableFound = connectable;
			dirFound = dir;
		}
		if (connectableFound == null) return;
		setConnected(dirFound);
		connectableFound.setConnected(dirFound.getOpposite());
		// Mark the block for an update, sends description packet to players.
		worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		worldObj.markBlockForUpdate(connectableFound.xCoord, connectableFound.yCoord, connectableFound.zCoord);
	}
	
	/** Disconnects the container from its connected container, if it has one. */
	public void disconnect() {
		if (!isConnected()) return;
		TileEntityConnectable connectable = getConnectedTileEntity();
		setConnected(ForgeDirection.UNKNOWN);
		worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		if (connectable != null) {
			connectable.setConnected(ForgeDirection.UNKNOWN);
			worldObj.markBlockForUpdate(connectable.xCoord, connectable.yCoord, connectable.zCoord);
		} else BetterStorage.log.warning("getConnectedTileEntity() returned null.");
	}
	
	// TileEntityContainer stuff
	
	/** Returns the unlocalized name of the container. <br>
	 *  "Large" will be appended if the container is connected to another one. */
	protected abstract String getConnectableName();
	
	@Override
	public final String getName() { return (getConnectableName() + (isConnected() ? "Large" : "")); }
	
	@Override
	public void openGui(EntityPlayer player) {
		if (!canPlayerUseContainer(player)) return;
		PlayerUtils.openGui(player, getName(), getColumns(), (isConnected() ? 2 : 1) * getRows(),
		                    getCustomTitle(), createContainer(player));
	}
	
	@Override
	public InventoryTileEntity getPlayerInventory() {
		if (isConnected()) {
			TileEntityConnectable main = getMainTileEntity();
			TileEntityConnectable connected = ((main == this) ? getConnectedTileEntity() : this);
			return new InventoryTileEntity(this, main, connected);
		} else return super.getPlayerInventory();
	}
	
	// Update entity
	
	@Override
	public void updateEntity() {
		super.updateEntity();
		
		double x = xCoord + 0.5;
		double y = yCoord + 0.5;
		double z = zCoord + 0.5;
		
		if (isConnected()) {
			if (!isMain()) return;
			TileEntityConnectable connectable = getConnectedTileEntity();
			if (connectable != null) {
				x = (x + connectable.xCoord + 0.5) / 2;
				z = (z + connectable.zCoord + 0.5) / 2;
				lidAngle = Math.max(lidAngle, connectable.lidAngle);
			}
		}
		
		float pitch = worldObj.rand.nextFloat() * 0.1F + 0.9F;
		
		// Play sound when opening
		if ((lidAngle > 0.0F) && (prevLidAngle == 0.0F))
			worldObj.playSoundEffect(x, y, z, "random.chestopen", 0.5F, pitch);
		// Play sound when closing
		if ((lidAngle < 0.5F) && (prevLidAngle >= 0.5F))
			worldObj.playSoundEffect(x, y, z, "random.chestclosed", 0.5F, pitch);
	}
	
	// IInventory stuff
	
	/** Returns if the container is accessible by other machines etc. */
	protected boolean isAccessible() { return true; }
	
	@Override
	public String getInvName() { return getName(); }
	@Override
	public boolean isInvNameLocalized() { return !shouldLocalizeTitle(); }
	@Override
	public int getInventoryStackLimit() { return 64; }
	@Override
	public int getSizeInventory() {
		return (isAccessible() ? getPlayerInventory().getSizeInventory() : 0);
	}
	@Override
	public ItemStack getStackInSlot(int slot) {
		return (isAccessible() ? getPlayerInventory().getStackInSlot(slot) : null);
	}
	@Override
	public void setInventorySlotContents(int slot, ItemStack stack) {
		if (isAccessible()) getPlayerInventory().setInventorySlotContents(slot, stack);
	}
	@Override
	public ItemStack decrStackSize(int slot, int amount) {
		return (isAccessible() ? getPlayerInventory().decrStackSize(slot, amount) : null);
	}
	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		return (isAccessible() ? getPlayerInventory().isItemValidForSlot(slot, stack) : false);
	}
	@Override
	public boolean isUseableByPlayer(EntityPlayer player) {
		return (isAccessible() ? getPlayerInventory().isUseableByPlayer(player) : false);
	}
	@Override
	public ItemStack getStackInSlotOnClosing(int slot) { return null; }
	@Override
	public void openChest() { if (isAccessible()) getPlayerInventory().openChest(); }
	@Override
	public void closeChest() { if (isAccessible()) getPlayerInventory().closeChest(); }
	@Override
	public void onInventoryChanged() {  }
	
	// Tile entity synchronization
	
	@Override
	public Packet getDescriptionPacket() {
		NBTTagCompound compound = new NBTTagCompound();
		compound.setByte("orientation", (byte)getOrientation().ordinal());
		compound.setByte("connected", (byte)getConnected().ordinal());
        return new Packet132TileEntityData(xCoord, yCoord, zCoord, 0, compound);
	}
	@Override
	public void onDataPacket(INetworkManager net, Packet132TileEntityData packet) {
		NBTTagCompound compound = packet.customParam1;
		setOrientation(ForgeDirection.getOrientation(compound.getByte("orientation")));
		setConnected(ForgeDirection.getOrientation(compound.getByte("connected")));
	}
	
	// Reading from / writing to NBT
	
	@Override
	public void readFromNBT(NBTTagCompound compound) {
		super.readFromNBT(compound);
		setOrientation(ForgeDirection.getOrientation(compound.getByte("orientation")));
		setConnected(ForgeDirection.getOrientation(compound.getByte("connected")));
	}
	
	@Override
	public void writeToNBT(NBTTagCompound compound) {
		super.writeToNBT(compound);
		compound.setByte("orientation", (byte)getOrientation().ordinal());
		compound.setByte("connected", (byte)getConnected().ordinal());
	}
	
}
