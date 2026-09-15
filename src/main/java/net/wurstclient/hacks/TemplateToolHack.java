/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.wurstclient.Category;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.templatetool.TemplateToolState;
import net.wurstclient.hacks.templatetool.states.SelectBoxStartState;
import net.wurstclient.util.BoxBackports;
import net.wurstclient.util.RenderUtils;

public final class TemplateToolHack extends Hack
	implements UpdateListener, RenderListener, GUIRenderListener
{
	private TemplateToolState state;
	private BlockPos startPos;
	private BlockPos endPos;
	private BlockPos originPos;
	/** 选 origin 那一刻玩家的朝向。模板坐标是"以 origin 为原点、按这个朝向旋转"存盘的。 */
	private net.minecraft.core.Direction templateFront;
	private final LinkedHashMap<BlockPos, BlockState> nonEmptyBlocks =
		new LinkedHashMap<>();
	private final LinkedHashSet<BlockPos> sortedBlocks = new LinkedHashSet<>();
	private boolean blockTypesEnabled;
	private File file;
	
	public TemplateToolHack()
	{
		super("TemplateTool");
		setCategory(Category.BLOCKS);
	}
	
	@Override
	public void onEnable()
	{
		WURST.getHax().autoBuildHack.setEnabled(false);
		WURST.getHax().instaBuildHack.setEnabled(false);
		WURST.getHax().bowAimbotHack.setEnabled(false);
		WURST.getHax().excavatorHack.setEnabled(false);
		
		setState(new SelectBoxStartState());
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		EVENTS.add(GUIRenderListener.class, this);
	}
	
	@Override
	public void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		EVENTS.remove(GUIRenderListener.class, this);
		
		setState(null);
		startPos = null;
		endPos = null;
		originPos = null;
		templateFront = null;
		nonEmptyBlocks.clear();
		sortedBlocks.clear();
		blockTypesEnabled = false;
		file = null;
	}
	
	@Override
	public void onUpdate()
	{
		if(state != null)
			state.onUpdate(this);
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		int black = 0x80000000;
		int green15 = 0x2600FF00;
		
		// Draw template bounds
		if(startPos != null && endPos != null)
		{
			AABB bounds =
				BoxBackports.enclosing(startPos, endPos).deflate(1 / 16.0);
			RenderUtils.drawOutlinedBox(matrixStack, bounds, black, true);
		}
		
		// Draw origin
		if(originPos != null)
		{
			AABB box = new AABB(originPos).deflate(1 / 16.0);
			RenderUtils.drawOutlinedBox(matrixStack, box, black, false);
			RenderUtils.drawSolidBox(matrixStack, box, green15, false);
		}
		
		// Draw state-specific things
		if(state != null)
			state.onRender(this, matrixStack, partialTicks);
	}
	
	@Override
	public void onRenderGUI(GuiGraphics context, float partialTicks)
	{
		if(state != null)
			state.onRenderGUI(this, context, partialTicks);
	}
	
	public void setState(TemplateToolState state)
	{
		if(this.state != null)
			this.state.onExit(this);
		
		this.state = state;
		
		if(state != null)
			state.onEnter(this);
	}
	
	public BlockPos getStartPos()
	{
		return startPos;
	}
	
	public void setStartPos(BlockPos pos)
	{
		startPos = pos;
	}
	
	public BlockPos getEndPos()
	{
		return endPos;
	}
	
	public void setEndPos(BlockPos pos)
	{
		endPos = pos;
	}
	
	public BlockPos getOriginPos()
	{
		return originPos;
	}
	
	public void setOriginPos(BlockPos pos)
	{
		originPos = pos;
		// 在这里（而不是存盘时）记下朝向：见 getTemplateFront() 的说明。
		templateFront = MC.player.getDirection();
	}
	
	/**
	 * 存盘时用来把绝对坐标转成模板坐标的朝向。
	 *
	 * <p>旧实现是在 {@code SavingFileState} 里现读 {@code MC.player.getDirection()}，
	 * 但那时候玩家已经过了「选 origin → 按回车 → 排序几 tick → 输入名字」这些步骤，
	 * 中途完全可以自由转身（排序阶段没有界面挡住视角）。反例：面向北选好 origin，
	 * 在"Creating template..."那几 tick 里把镜头转到东 —— 存出来的模板整体转了 90°，
	 * 与你在选 origin 时看到的布局不一致（AutoBuild 会照着这个转过的布局建造）。
	 */
	public net.minecraft.core.Direction getTemplateFront()
	{
		return templateFront != null ? templateFront : MC.player.getDirection();
	}
	
	public LinkedHashMap<BlockPos, BlockState> getNonEmptyBlocks()
	{
		return nonEmptyBlocks;
	}
	
	public LinkedHashSet<BlockPos> getSortedBlocks()
	{
		return sortedBlocks;
	}
	
	public boolean areBlockTypesEnabled()
	{
		return blockTypesEnabled;
	}
	
	public void setBlockTypesEnabled(boolean blockTypesEnabled)
	{
		this.blockTypesEnabled = blockTypesEnabled;
	}
	
	public File getFile()
	{
		return file;
	}
	
	public void setFile(File file)
	{
		this.file = file;
	}
}
