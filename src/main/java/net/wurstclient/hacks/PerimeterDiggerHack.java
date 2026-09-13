/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.List;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.perimeter.PerimeterArea;
import net.wurstclient.perimeter.PerimeterAutomation;
import net.wurstclient.perimeter.PerimeterAutomationState;
import net.wurstclient.perimeter.PerimeterText;
import net.wurstclient.perimeter.config.PerimeterConfig;
import net.wurstclient.perimeter.config.PerimeterConfigStore;
import net.wurstclient.perimeter.config.PerimeterDetectedArea;
import net.wurstclient.perimeter.config.PerimeterScanline;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.ChatUtils;

/**
 * Native perimeter automation for the v1.6 client.
 *
 * <p>
 * The hack is the client side entry point: it owns the per-world configuration,
 * keeps it in sync with the ClickGUI toggles and drives
 * {@link PerimeterAutomation}, which does the actual work.
 */
@SearchTags({"perimeter", "perimeter digger", "quarry", "dig", "excavate"})
@DontSaveState
public final class PerimeterDiggerHack extends Hack implements UpdateListener
{
	private final CheckboxSetting collectDrops =
		new CheckboxSetting("Collect drops", "Walk over nearby drops.", true);
	
	private final CheckboxSetting autoUnload =
		new CheckboxSetting("Auto unload",
			"Travel to an unloading point and drop the products down a shaft.",
			true);
	
	private final CheckboxSetting autoEat =
		new CheckboxSetting("Auto eat", "Eat when hungry or hurt.", true);
	
	private final PerimeterAutomation automation = new PerimeterAutomation();
	
	private PerimeterConfig config = new PerimeterConfig();
	private PerimeterConfigStore store;
	private boolean configLoaded;
	private String worldIdentity;
	private PerimeterArea plannedArea;
	
	public PerimeterDiggerHack()
	{
		super("PerimeterDigger");
		setCategory(Category.BLOCKS);
		addSetting(collectDrops);
		addSetting(autoUnload);
		addSetting(autoEat);
	}
	
	@Override
	protected boolean canEnable()
	{
		return MC.player != null;
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		
		if(!ensureConfigLoaded())
		{
			setEnabled(false);
			return;
		}
		
		syncConfigFromSettings();
		List<String> problems = automation.start(config);
		
		if(!problems.isEmpty())
		{
			ChatUtils.error("[Perimeter] "
				+ PerimeterText.get("perimeterdigger.command.problems"));
			
			for(String problem : problems)
				ChatUtils.message("  - " + problem);
			
			setEnabled(false);
			return;
		}
		
		saveConfig();
		ChatUtils.message("[Perimeter] " + automation.describeStatus());
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		automation.stop();
	}
	
	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;
		
		String identity = PerimeterConfigStore.resolveIdentity();
		
		if(worldIdentity != null && !worldIdentity.equals(identity))
		{
			automation.onWorldChanged();
			configLoaded = false;
			ensureConfigLoaded();
		}
		
		worldIdentity = identity;
		automation.onTick();
		
		if(automation.getState() == PerimeterAutomationState.COMPLETE)
		{
			saveConfig();
			ChatUtils.message("[Perimeter] " + automation.describeStatus());
			setEnabled(false);
		}
	}
	
	@Override
	public String getRenderName()
	{
		PerimeterAutomationState state = automation.getState();
		
		if(state == PerimeterAutomationState.IDLE)
			return getName();
		
		return getName() + " [" + PerimeterText.stateName(state) + "]";
	}
	
	// ------------------------------------------------------------------
	// API used by .perimeter and /perimeterdig
	// ------------------------------------------------------------------
	
	public PerimeterAutomation getAutomation()
	{
		return automation;
	}
	
	public PerimeterConfig getConfig()
	{
		return config;
	}
	
	public PerimeterConfigStore getStore()
	{
		if(store == null)
			store = new PerimeterConfigStore();
		
		return store;
	}
	
	public boolean ensureConfigLoaded()
	{
		if(configLoaded)
			return true;
		
		try
		{
			store = new PerimeterConfigStore();
			config = store.load();
			worldIdentity = store.identity();
			configLoaded = true;
			syncSettingsFromConfig();
			return true;
		}catch(Exception e)
		{
			ChatUtils.error("[Perimeter] Could not load the configuration: "
				+ e.getMessage());
			return false;
		}
	}
	
	public boolean saveConfig()
	{
		if(store == null)
			store = new PerimeterConfigStore();
		
		return store.save(config);
	}
	
	public boolean loadConfig()
	{
		configLoaded = false;
		boolean loaded = ensureConfigLoaded();
		
		if(loaded)
			ChatUtils.message("[Perimeter] "
				+ PerimeterText.get("perimeterdigger.command.loaded",
					store.path().toString()));
		
		return loaded;
	}
	
	/**
	 * Stores a rectangular plan as the detected area, so that the automation
	 * digs exactly the planned box.
	 */
	public boolean plan(PerimeterArea newArea)
	{
		if(newArea == null)
			return false;
		
		if(automation.getState() != PerimeterAutomationState.IDLE
			&& automation.getState() != PerimeterAutomationState.COMPLETE)
		{
			ChatUtils.error(
				"[Perimeter] Already running. Use .perimeter stop first.");
			return false;
		}
		
		ensureConfigLoaded();
		
		PerimeterDetectedArea detected = new PerimeterDetectedArea();
		detected.boundaryBlock = null;
		detected.boundaryY = newArea.minY();
		detected.minX = newArea.minX();
		detected.maxX = newArea.maxX();
		detected.minZ = newArea.minZ();
		detected.maxZ = newArea.maxZ();
		detected.columnCount = newArea.columnCount();
		
		for(int z = newArea.minZ(); z <= newArea.maxZ(); z++)
			detected.scanlines
				.add(new PerimeterScanline(z, newArea.minX(), newArea.maxX()));
		
		detected.normalize();
		config.detectedArea = detected;
		config.diggingMinY = newArea.minY();
		config.diggingMaxY = newArea.maxY();
		plannedArea = newArea;
		saveConfig();
		
		ChatUtils.message("[Perimeter] Planned " + newArea.describe());
		return true;
	}
	
	public void start()
	{
		ensureConfigLoaded();
		syncConfigFromSettings();
		
		List<String> problems = automation.start(config);
		
		if(!problems.isEmpty())
		{
			ChatUtils.error("[Perimeter] "
				+ PerimeterText.get("perimeterdigger.command.problems"));
			
			for(String problem : problems)
				ChatUtils.message("  - " + problem);
			
			return;
		}
		
		if(!isEnabled())
			setEnabled(true);
		
		ChatUtils.message("[Perimeter] " + automation.describeStatus());
	}
	
	public void pause()
	{
		if(!automation.isRunning())
		{
			ChatUtils.error("[Perimeter] Nothing to pause.");
			return;
		}
		
		automation.pause();
		ChatUtils.message("[Perimeter] " + automation.describeStatus());
	}
	
	public void resume()
	{
		if(!automation.isPaused())
		{
			ChatUtils.error("[Perimeter] Nothing to resume.");
			return;
		}
		
		if(!isEnabled())
			setEnabled(true);
		
		automation.resume();
		ChatUtils.message("[Perimeter] " + automation.describeStatus());
	}
	
	public void stop()
	{
		automation.stop();
		saveConfig();
		
		if(isEnabled())
			setEnabled(false);
		
		ChatUtils.message("[Perimeter] Stopped and saved the configuration.");
	}
	
	public String describeStatus()
	{
		StringBuilder builder = new StringBuilder(automation.describeStatus());
		
		if(plannedArea != null)
			builder.append(System.lineSeparator()).append("plan: ")
				.append(plannedArea.describe());
		
		if(store != null)
			builder.append(System.lineSeparator()).append("config: ")
				.append(store.path()).append(" (").append(store.identity())
				.append(')');
		
		return builder.toString();
	}
	
	public boolean hasPlan()
	{
		return config != null && config.hasDetectedArea()
			&& config.hasDiggingRange();
	}
	
	public boolean isRunning()
	{
		return automation.isRunning()
			&& automation.getState() != PerimeterAutomationState.COMPLETE;
	}
	
	private void syncConfigFromSettings()
	{
		config.functions.collectDrops = collectDrops.isChecked();
		config.functions.unload = autoUnload.isChecked();
		config.functions.eat = autoEat.isChecked();
	}
	
	private void syncSettingsFromConfig()
	{
		collectDrops.setChecked(config.functions.collectDrops);
		autoUnload.setChecked(config.functions.unload);
		autoEat.setChecked(config.functions.eat);
	}
}
