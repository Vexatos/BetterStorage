package net.mcft.copy.betterstorage.proxy;

import net.mcft.copy.betterstorage.Config;
import net.mcft.copy.betterstorage.attachment.EnumAttachmentInteraction;
import net.mcft.copy.betterstorage.attachment.IHasAttachments;
import net.mcft.copy.betterstorage.block.BlockEnderBackpack;
import net.mcft.copy.betterstorage.block.crate.CratePileCollection;
import net.mcft.copy.betterstorage.content.Blocks;
import net.mcft.copy.betterstorage.entity.EntityFrienderman;
import net.mcft.copy.betterstorage.inventory.InventoryStacks;
import net.mcft.copy.betterstorage.item.ItemBackpack;
import net.mcft.copy.betterstorage.item.ItemEnderBackpack;
import net.mcft.copy.betterstorage.misc.CurrentItem;
import net.mcft.copy.betterstorage.misc.PropertiesBackpack;
import net.mcft.copy.betterstorage.misc.handlers.PacketHandler;
import net.mcft.copy.betterstorage.utils.EntityUtils;
import net.mcft.copy.betterstorage.utils.NbtUtils;
import net.mcft.copy.betterstorage.utils.RandomUtils;
import net.mcft.copy.betterstorage.utils.WorldUtils;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.ChestGenHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.Event.Result;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.EntityEvent.EntityConstructing;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent.SpecialSpawn;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action;
import net.minecraftforge.event.world.WorldEvent.Save;
import net.minecraftforge.event.world.WorldEvent.Unload;
import cpw.mods.fml.common.IPlayerTracker;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import cpw.mods.fml.common.registry.GameRegistry;

public class CommonProxy implements IPlayerTracker {
	
	public void initialize() {
		MinecraftForge.EVENT_BUS.register(this);
		GameRegistry.registerPlayerTracker(this);
	}
	
	@ForgeSubscribe
	public void onWorldSave(Save event) {
		CratePileCollection.saveAll(event.world);
	}
	
	@ForgeSubscribe
	public void onWorldUnload(Unload event) {
		CratePileCollection.unload(event.world);
	}
	
	@ForgeSubscribe
	public void onEntityConstructing(EntityConstructing event) {
		if (event.entity instanceof EntityLivingBase)
			ItemBackpack.initBackpackData((EntityLivingBase)event.entity);
	}
	
	@ForgeSubscribe
	public void onSpecialSpawn(SpecialSpawn event) {
		
		EntityLivingBase entity = event.entityLiving;
		double probability = 0.0;
		if (entity.getClass() == EntityZombie.class) probability = 1.0 / 800;
		else if (entity.getClass() == EntityPigZombie.class) probability = 1.0 / 1000;
		else if (entity.getClass() == EntitySkeleton.class) probability = 1.0 / 1200;
		else if ((entity.getClass() == EntityEnderman.class) && RandomUtils.getBoolean(1.0 / 80) &&
		         (entity.worldObj.getBiomeGenForCoords((int)entity.posX, (int)entity.posZ) != BiomeGenBase.sky) &&
		         (Blocks.enderBackpack != null)) {
			EntityFrienderman frienderman = new EntityFrienderman(entity.worldObj);
			frienderman.setPositionAndRotation(entity.posX, entity.posY, entity.posZ, entity.rotationYaw, 0);
			entity.worldObj.spawnEntityInWorld(frienderman);
			ItemBackpack.getBackpackData(frienderman).spawnsWithBackpack = true;
			entity.setDead();
		}
		if (RandomUtils.getDouble() >= probability || (Blocks.backpack == null)) return;
		ItemBackpack.getBackpackData(event.entityLiving).spawnsWithBackpack = true;
		
	}
	
	@ForgeSubscribe
	public void onPlayerInteract(PlayerInteractEvent event) {
		
		EntityPlayer player = event.entityPlayer;
		World world = event.entity.worldObj;
		int x = event.x;
		int y = event.y;
		int z = event.z;
		
		// Attempt to place equipped backpack.
		if (event.action == Action.RIGHT_CLICK_BLOCK)
			if (ItemBackpack.placeBackpack(player, x, y, z, event.face))
				event.useBlock = Result.DENY;
		
		// Interact with attachments.
		if ((event.action == Action.LEFT_CLICK_BLOCK) ||
		    (event.action == Action.RIGHT_CLICK_BLOCK)) {
			IHasAttachments hasAttachments = WorldUtils.get(world, x, y, z, IHasAttachments.class);
			if (hasAttachments != null) {
				EnumAttachmentInteraction interactionType =
						((event.action == Action.LEFT_CLICK_BLOCK)
								? EnumAttachmentInteraction.attack
								: EnumAttachmentInteraction.use);
				if (hasAttachments.getAttachments().interact(player, interactionType)) {
					event.useBlock = Result.DENY;
					event.useItem = Result.DENY;
				}
			}
		}
		
	}
	
	@ForgeSubscribe
	public void onEntityInteract(EntityInteractEvent event) {
		
		// Right clicking the back equipped by another
		// entity will open a GUI for that backpack.
		
		if (event.entity.worldObj.isRemote) return;
		EntityPlayerMP player = (EntityPlayerMP)event.entity;
		if (!(event.target instanceof EntityLivingBase)) return;
		EntityLivingBase target = (EntityLivingBase)event.target;
		
		ItemBackpack.openBackpack(player, target);
		player.swingItem();
		
	}
	
	@ForgeSubscribe
	public void onEntityJoinWorldEvent(EntityJoinWorldEvent event) {
		
		// If an entity is already dead / destroyed when
		// it's added to the world, cancel it being added.
		if (event.entity.isDead) event.setCanceled(true);
		
		// If an ender backpack ever drops as an item,
		// instead teleport it somewhere as a block.
		
		if (!(event.entity instanceof EntityItem)) return;
		EntityItem entity = (EntityItem)event.entity;
		ItemStack stack = entity.getDataWatcher().getWatchableObjectItemStack(10);
		if ((stack == null) || !(stack.getItem() instanceof ItemEnderBackpack)) return;
		event.setCanceled(true);
		for (int i = 0; i < 64; i++)
			if (BlockEnderBackpack.teleportRandomly(entity.worldObj, entity.posX, entity.posY, entity.posZ, (i > 48), stack))
				break;
		
	}
	
	// Random items to be found in a backpack
	private static final WeightedRandomChestContent[] randomBackpackItems = new WeightedRandomChestContent[]{
		
		new WeightedRandomChestContent(Item.stick.itemID, 0, 8, 20, 100),
		new WeightedRandomChestContent(Block.planks.blockID, 0, 2, 10, 100),
		new WeightedRandomChestContent(Block.wood.blockID, 0, 1, 8, 40),
		new WeightedRandomChestContent(Block.cobblestone.blockID, 0, 6, 16, 80),
		
		new WeightedRandomChestContent(Item.pickaxeWood.itemID, 50, 1, 1, 35),
		new WeightedRandomChestContent(Item.pickaxeWood.itemID, 20, 1, 1, 10),
		new WeightedRandomChestContent(Item.pickaxeStone.itemID, 120, 1, 1, 10),
		new WeightedRandomChestContent(Item.pickaxeStone.itemID, 80, 1, 1, 5),
		new WeightedRandomChestContent(Item.pickaxeIron.itemID, 220, 1, 1, 2),
		
		new WeightedRandomChestContent(Item.swordWood.itemID, 40, 1, 1, 30),
		new WeightedRandomChestContent(Item.swordStone.itemID, 60, 1, 1, 5),
		
		new WeightedRandomChestContent(Item.bow.itemID, 200, 1, 1, 10),
		new WeightedRandomChestContent(Item.bow.itemID, 50, 1, 1, 3),
		new WeightedRandomChestContent(Item.fishingRod.itemID, 20, 1, 1, 4),
		new WeightedRandomChestContent(Item.compass.itemID, 0, 1, 1, 6),
		new WeightedRandomChestContent(Item.pocketSundial.itemID, 0, 1, 1, 5),
		
		new WeightedRandomChestContent(Block.torchWood.blockID, 0, 6, 24, 30),
		new WeightedRandomChestContent(Item.arrow.itemID, 0, 2, 12, 10),
		new WeightedRandomChestContent(Item.rottenFlesh.itemID, 0, 3, 6, 15),
		new WeightedRandomChestContent(Item.bone.itemID, 0, 2, 5, 20),
		new WeightedRandomChestContent(Item.silk.itemID, 0, 3, 10, 15),
		
		new WeightedRandomChestContent(Item.appleRed.itemID, 0, 2, 5, 15),
		new WeightedRandomChestContent(Item.bread.itemID, 0, 2, 4, 10),
		new WeightedRandomChestContent(Item.wheat.itemID, 0, 3, 6, 10),
		new WeightedRandomChestContent(Item.carrot.itemID, 0, 1, 2, 8),
		new WeightedRandomChestContent(Item.potato.itemID, 0, 1, 2, 5),
		new WeightedRandomChestContent(Item.fishRaw.itemID, 0, 1, 4, 5),
		new WeightedRandomChestContent(Item.fishCooked.itemID, 0, 1, 2, 4),
		
		new WeightedRandomChestContent(Item.coal.itemID, 0, 3, 9, 20),
		new WeightedRandomChestContent(Item.coal.itemID, 0, 20, 32, 5),
		new WeightedRandomChestContent(Block.oreIron.blockID, 0, 2, 5, 15),
		new WeightedRandomChestContent(Block.oreIron.blockID, 0, 10, 20, 2),
		new WeightedRandomChestContent(Block.oreGold.blockID, 0, 2, 7, 8),
		new WeightedRandomChestContent(Item.diamond.itemID, 0, 1, 2, 1),
		new WeightedRandomChestContent(Item.emerald.itemID, 0, 1, 1, 1),
		
	};
	
	@ForgeSubscribe
	public void onLivingUpdate(LivingUpdateEvent event) {
		
		// Update backpack animation and play sound when it opens / closes
		
		EntityLivingBase entity = event.entityLiving;
		EntityPlayer player = ((entity instanceof EntityPlayer) ? (EntityPlayer)entity : null);
		ItemStack backpack = ItemBackpack.getBackpack(entity);
		
		PropertiesBackpack backpackData;
		if (backpack == null) {
			
			backpackData = EntityUtils.getProperties(entity, PropertiesBackpack.class);
			if (backpackData == null) return;
			
			// If the entity is supposed to spawn with
			// a backpack, equip it with one.
			if (backpackData.spawnsWithBackpack) {
				
				ItemStack[] contents = null;
				if (entity instanceof EntityFrienderman) {
					backpack = new ItemStack(Blocks.enderBackpack);
					((EntityLiving)entity).setEquipmentDropChance(3, 0.0F); // Remove drop chance for the backpack.
				} else {
					backpack = new ItemStack(Blocks.backpack, 1, RandomUtils.getInt(120, 240));
					ItemBackpack backpackType = (ItemBackpack)Item.itemsList[backpack.itemID];
					if (RandomUtils.getBoolean(0.15)) {
						// Give the backpack a random color.
						int r = RandomUtils.getInt(32, 224);
						int g = RandomUtils.getInt(32, 224);
						int b = RandomUtils.getInt(32, 224);
						int color = (r << 16) | (g << 8) | b;
						backpackType.func_82813_b(backpack, color);
					}
					contents = new ItemStack[backpackType.getColumns() * backpackType.getRows()];
					((EntityLiving)entity).setEquipmentDropChance(3, 1.0F); // Set drop chance for the backpack to 100%.
				}
				
				// If the entity spawned with enchanted armor,
				// move the enchantments over to the backpack.
				ItemStack armor = entity.getCurrentItemOrArmor(CurrentItem.CHEST);
				if (armor != null && armor.isItemEnchanted()) {
					NBTTagCompound compound = new NBTTagCompound();
					compound.setTag("ench", armor.getTagCompound().getTag("ench"));
					backpack.setTagCompound(compound);
				}
				
				if (contents != null) {
					// Add random items to the backpack.
					InventoryStacks inventory = new InventoryStacks(contents);
					// Add normal random backpack loot
					WeightedRandomChestContent.generateChestContents(
							RandomUtils.random, randomBackpackItems, inventory, 20);
					// With a chance of 10%, add some random dungeon loot
					if (RandomUtils.getDouble() < 0.1) {
						ChestGenHooks info = ChestGenHooks.getInfo(ChestGenHooks.DUNGEON_CHEST);
						WeightedRandomChestContent.generateChestContents(
								RandomUtils.random, info.getItems(RandomUtils.random), inventory, 5);
					}
					
				}
				
				ItemBackpack.setBackpack(entity, backpack, contents);
				backpackData.spawnsWithBackpack = false;
				
			} else {
				
				// If the entity doesn't have a backpack equipped,
				// but still has some backpack data, drop the items.
				if (backpackData.contents != null) {
					for (ItemStack stack : backpackData.contents)
						WorldUtils.dropStackFromEntity(entity, stack, 1.5F);
					backpackData.contents = null;
				}
				
			}
			
			return;
			
		} else backpackData = ItemBackpack.getBackpackData(entity);
		
		backpackData.prevLidAngle = backpackData.lidAngle;
		float lidSpeed = 0.2F;
		if (ItemBackpack.isBackpackOpen(entity))
			backpackData.lidAngle = Math.min(1.0F, backpackData.lidAngle + lidSpeed);
		else backpackData.lidAngle = Math.max(0.0F, backpackData.lidAngle - lidSpeed);
		
		String sound = Block.soundSnowFootstep.getStepSound();
		// Play sound when opening
		if ((backpackData.lidAngle > 0.0F) && (backpackData.prevLidAngle <= 0.0F))
			entity.worldObj.playSoundEffect(entity.posX, entity.posY, entity.posZ, sound, 1.0F, 0.5F);
		// Play sound when closing
		if ((backpackData.lidAngle < 0.2F) && (backpackData.prevLidAngle >= 0.2F))
			entity.worldObj.playSoundEffect(entity.posX, entity.posY, entity.posZ, sound, 0.8F, 0.3F);
		
	}
	
	@ForgeSubscribe
	public void onLivingDeath(LivingDeathEvent event) {
		
		// Drops the contents from an equipped backpack when the entity dies.
		
		EntityLivingBase entity = event.entityLiving;
		if (entity.worldObj.isRemote) return;
		ItemStack backpack = ItemBackpack.getBackpack(entity);
		if (backpack == null) return;
		PropertiesBackpack backpackData = ItemBackpack.getBackpackData(entity);
		if (backpackData.contents == null) return;
		
		boolean keepInventory = entity.worldObj.getGameRules().getGameRuleBooleanValue("keepInventory");
		if ((entity instanceof EntityPlayer) && keepInventory) {
			
			// If keep inventory is on, instead temporarily save the contents
			// to the persistent NBT tag and get them back when the player respawns.
			
			EntityPlayer player = (EntityPlayer)entity;
			NBTTagCompound compound = player.getEntityData();
			NBTTagCompound persistent;
			if (!compound.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
				persistent = new NBTTagCompound();
				compound.setTag(EntityPlayer.PERSISTED_NBT_TAG, persistent);
			} else persistent = compound.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);;
			
			NBTTagCompound backpackCompound = new NBTTagCompound();
			backpackCompound.setInteger("count", backpackData.contents.length);
			backpackCompound.setTag("Items", NbtUtils.writeItems(backpackData.contents));
			persistent.setTag("Backpack", backpackCompound);
			
		} else {
			
			for (ItemStack stack : backpackData.contents)
				WorldUtils.dropStackFromEntity(entity, stack, 4.0F);
			backpackData.contents = null;
			
		}
		
	}
	
	// IPlayerTracker implementation
	
	@Override
	public void onPlayerLogin(EntityPlayer player) {
		// Send player the information if the backpack open key is enabled on this server.
		Packet packet = PacketHandler.makePacket(PacketHandler.backpackKeyEnabled, Config.enableBackpackOpen);
		PacketDispatcher.sendPacketToPlayer(packet, (Player)player);
	}
	
	@Override
	public void onPlayerLogout(EntityPlayer player) {  }
	
	@Override
	public void onPlayerChangedDimension(EntityPlayer player) {  }
	
	@Override
	public void onPlayerRespawn(EntityPlayer player) {
		
		// If the player dies when when keepInventory is on and respawns,
		// retrieve the backpack items from eir persistent NBT tag.
		
		NBTTagCompound compound = player.getEntityData();
		if (!compound.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) return;
		NBTTagCompound persistent = compound.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
		if (!persistent.hasKey("Backpack")) return;
		NBTTagCompound backpack = persistent.getCompoundTag("Backpack");
		
		int size = backpack.getInteger("count");
		ItemStack[] contents = new ItemStack[size];
		NbtUtils.readItems(contents, backpack.getTagList("Items"));
		
		ItemBackpack.getBackpackData(player).contents = contents;
		
		persistent.removeTag("Backpack");
		if (persistent.hasNoTags())
			compound.removeTag(EntityPlayer.PERSISTED_NBT_TAG);
		
	}
	
}
