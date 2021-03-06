package net.mcft.copy.betterstorage.addon.thaumcraft;

import java.util.List;

import net.mcft.copy.betterstorage.block.BlockReinforcedChest;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockThaumiumChest extends BlockReinforcedChest {
	
	public BlockThaumiumChest(int id) {
		super(id, Material.iron);
		
		setHardness(12.0f);
		setResistance(35.0f);
		setStepSound(soundMetalFootstep);
		
		MinecraftForge.setBlockHarvestLevel(this, "pickaxe", 2);
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public int getRenderType() { return ThaumcraftAddon.thaumiumChestRenderId; }
	
	@Override
	public void getSubBlocks(int id, CreativeTabs tab, List list) {
		list.add(new ItemStack(id, 1, 0));
	}
	
	@Override
	public TileEntity createNewTileEntity(World world) {
		return new TileEntityThaumiumChest();
	}
	
}
