package net.mcft.copy.betterstorage.block;

import java.util.Locale;

import net.mcft.copy.betterstorage.BetterStorage;
import net.mcft.copy.betterstorage.misc.Constants;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import cpw.mods.fml.common.registry.GameRegistry;

public abstract class BlockContainerBetterStorage extends BlockContainer {
	
	protected BlockContainerBetterStorage(int id, Material material) {
		
		super(id, material);
		
		setCreativeTab(BetterStorage.creativeTab);
		
		String name = getClass().getSimpleName();                                    // BlockMyBlock
		name = name.substring(5, 6).toLowerCase(Locale.ENGLISH) + name.substring(6); // 'm' + "yBlock"
		setUnlocalizedName(Constants.modId + "." + name);                            // modname.myBlock
		
		registerBlock();
		
	}
	
	/** Registers the block in the GameRegistry. */
	protected void registerBlock() {
		String name = getUnlocalizedName();
		name = name.substring(name.lastIndexOf('.') + 1);
		
		Class<? extends Item> itemClass = getItemClass();
		if (ItemBlock.class.isAssignableFrom(itemClass)) {
			GameRegistry.registerBlock(this, (Class<? extends ItemBlock>)itemClass, name, Constants.modId);
		} else {
			GameRegistry.registerBlock(this, ItemBlock.class, name, Constants.modId);
			Item.itemsList[blockID] = null;
			try { itemClass.getConstructor(int.class).newInstance(blockID); }
			catch (Exception e) { throw new RuntimeException(e); }
		}
	}
	
	/** Returns the item class used for this block. <br>
	 *  Doesn't have to be an ItemBlock. */
	protected Class<? extends Item> getItemClass() { return ItemBlock.class; }
	
}
