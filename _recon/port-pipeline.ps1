param(
    [Parameter(Mandatory=$true)][string]$Base,
    [ValidateSet('accessor','direct')][string]$AccessMode = 'accessor'
)

$enc = New-Object System.Text.UTF8Encoding($false)
if(!(Test-Path $Base)) { throw "missing base: $Base" }

function Rep([string]$path,[string]$old,[string]$new) {
    if(!(Test-Path $path)) { return }
    $t = [IO.File]::ReadAllText($path)
    if($t.Contains($old)) { [IO.File]::WriteAllText($path, $t.Replace($old,$new), $enc) }
}

# ---- GUI hacks: GuiGraphicsExtractor -> GuiGraphics ----
$gui = @('SprintStatusHack','ImgWorldStatsHack','ImgTipsHack','MobHudHack','AmbienceHack','BetterBeaconsHack')
foreach($f in $gui) {
    $p = "$Base\hacks\$f.java"
    Rep $p 'import net.wurstclient.util.render.GuiGraphicsExtractor;' 'import net.minecraft.client.gui.GuiGraphics;'
    Rep $p 'GuiGraphicsExtractor' 'GuiGraphics'
    Rep $p 'context.text(' 'context.drawString('
    Rep $p 'graphics.text(' 'graphics.drawString('
    Rep $p 'pushMatrix()' 'pushPose()'
    Rep $p 'popMatrix()' 'popPose()'
    Rep $p 'pose.translate((float)x, (float)y);' 'pose.translate((float)x, (float)y, 0);'
    Rep $p 'pose.translate((float)textX.getValue(), (float)y);' 'pose.translate((float)textX.getValue(), (float)y, 0);'
    Rep $p 'pose.translate((float)x, (float)settingY);' 'pose.translate((float)x, (float)settingY, 0);'
    Rep $p 'pose.scale((float)textScale, (float)textScale);' 'pose.scale((float)textScale, (float)textScale, 1);'
    Rep $p 'pose.scale((float)scale, (float)scale);' 'pose.scale((float)scale, (float)scale, 1);'
}

# ---- global safe replacements ----
Get-ChildItem $Base -Recurse -Filter *.java | ForEach-Object {
    $t = [IO.File]::ReadAllText($_.FullName); $o = $t
    $t = $t.Replace('.getSelectedSlot()','.selected')
    $t = $t.Replace('.setSelectedSlot(','.selected = (')
    $t = [regex]::Replace($t,'SoundEvents\.(\w+)\.value\(\)','SoundEvents.$1')
    $t = $t.Replace('.getMainCamera().position()','.getMainCamera().getPosition()')
    $t = $t.Replace('EntityEvent.PROTECTED_FROM_DEATH','35')
    $t = $t.Replace('.getMinY()','.getMinBuildHeight()')
    $t = $t.Replace('.getMaxY()','.getMaxBuildHeight()')
    $t = $t.Replace('.getMinSectionY()','.getMinSection()')
    $t = $t.Replace('.getUnitVec3i()','.getNormal()')
    if($t -ne $o) { [IO.File]::WriteAllText($_.FullName,$t,$enc) }
}

# ---- identifiers / entity packages ----
foreach($rel in @('hacks\AdaAutoHotbarHack.java','hacks\SchematicProHack.java','hacks\AutoNametagHack.java','hacks\AutoRefreshTradeHack.java','hacks\AutoShearerHack.java','hacks\CrossbowAuraHack.java','hacks\MaceAuraHack.java','hacks\VillagerTraderHack.java')) {
    $p = "$Base\$rel"
    Rep $p 'net.minecraft.resources.Identifier' 'net.minecraft.resources.ResourceLocation'
    Rep $p 'Identifier.parse(' 'ResourceLocation.parse('
    Rep $p 'net.minecraft.world.entity.npc.villager.VillagerProfession' 'net.minecraft.world.entity.npc.VillagerProfession'
    Rep $p 'net.minecraft.world.entity.npc.villager.Villager' 'net.minecraft.world.entity.npc.Villager'
    Rep $p 'net.minecraft.world.entity.animal.sheep.Sheep' 'net.minecraft.world.entity.animal.Sheep'
    Rep $p 'net.minecraft.world.entity.animal.equine.AbstractHorse' 'net.minecraft.world.entity.animal.horse.AbstractHorse'
}
# registry API (after Identifier -> ResourceLocation has been applied)
Get-ChildItem $Base -Recurse -Filter *.java | ForEach-Object {
    $t = [IO.File]::ReadAllText($_.FullName); $o = $t
    $t = $t.Replace('.getValue(ResourceLocation','.get(ResourceLocation')
    if($t -ne $o) { [IO.File]::WriteAllText($_.FullName,$t,$enc) }
}

# ---- mouse button event ----
foreach($rel in @('hacks\AttractAuraHack.java','hacks\CrossbowAuraHack.java')) {
    $p = "$Base\$rel"
    Rep $p 'import net.wurstclient.events.MouseButtonPressListener;' 'import net.wurstclient.events.MouseButtonListener;'
    Rep $p 'MouseButtonPressListener' 'MouseButtonListener'
    Rep $p 'public void onMouseButtonPress(MouseButtonPressEvent event)' 'public void onMouseButton(int button, int action)'
    Rep $p 'event.getButton()' 'button'
    Rep $p 'event.getAction()' 'action'
}

# ---- EntityOwner: EntityReference -> UUID + getOwner ----
$p = "$Base\hacks\EntityOwnerHack.java"
Rep $p 'import net.minecraft.world.entity.EntityReference;' ''
Rep $p 'import net.minecraft.world.entity.Entity;' "import java.util.UUID;`n`nimport net.minecraft.world.entity.Entity;"
Rep $p "			EntityReference<LivingEntity> ownerRef =`n				ownable.getOwnerReference();`n			if(ownerRef == null)`n				continue;`n`n			String ownerName = getOwnerName(ownerRef);" "			UUID ownerUuid = ownable.getOwnerUUID();`n			if(ownerUuid == null)`n				continue;`n`n			String ownerName = getOwnerName(ownable, ownerUuid);"
Rep $p "	private String getOwnerName(EntityReference<LivingEntity> ownerRef)`n	{`n		LivingEntity owner =`n			EntityReference.getLivingEntity(ownerRef, MC.level);`n		if(owner != null)`n			return owner.getName().getString();`n`n		// Offline owners are not looked up over the network.`n		String uuid = ownerRef.getUUID().toString();`n		return uuid.substring(0, 8);`n	}" "	private String getOwnerName(OwnableEntity ownable, UUID ownerUuid)`n	{`n		LivingEntity owner = ownable.getOwner();`n		if(owner != null)`n			return owner.getName().getString();`n`n		// Offline owners are not looked up over the network.`n		return ownerUuid.toString().substring(0, 8);`n	}"

# ---- version-specific APIs (1.21 / 1.21.1) ----
Rep "$Base\hacks\AntiLagHack.java" 'import net.minecraft.world.entity.player.Input;' 'import net.minecraft.client.player.Input;'
Rep "$Base\hacks\AntiLagHack.java" "		Input keys = MC.player.input.keyPresses;`n		boolean moving = keys.forward() || keys.backward() || keys.left()`n			|| keys.right();" "		Input keys = MC.player.input;`n		boolean moving = keys.up || keys.down || keys.left || keys.right;"
Rep "$Base\hacks\AntiLagHack.java" 'Vec3 serverPos = packet.change().position();' 'Vec3 serverPos = new Vec3(packet.getX(), packet.getY(), packet.getZ());'
Rep "$Base\hacks\AntiLagHack.java" 'packet.id()' 'packet.getId()'
Rep "$Base\hacks\StuckHack.java" 'import net.minecraft.world.entity.player.Input;' ''
Rep "$Base\hacks\StuckHack.java" "		MC.player.input.keyPresses = Input.EMPTY;`n		MC.player.input.moveVector = Vec2.ZERO;" "		MC.player.input.leftImpulse = 0;`n		MC.player.input.forwardImpulse = 0;`n		MC.player.input.jumping = false;`n		MC.player.input.shiftKeyDown = false;"
Rep "$Base\hacks\FreezeHack.java" 'import net.minecraft.world.item.ItemUseAnimation;' 'import net.minecraft.world.item.UseAnim;'
Rep "$Base\hacks\FreezeHack.java" 'ItemUseAnimation.NONE' 'UseAnim.NONE'
Rep "$Base\hacks\BacktrackHack.java" 'import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;' 'import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;'
Rep "$Base\hacks\BacktrackHack.java" "		else if(packet instanceof ClientboundEntityPositionSyncPacket sync)`n			entity = MC.level.getEntity(sync.id());" "		else if(packet instanceof ClientboundTeleportEntityPacket sync)`n			entity = MC.level.getEntity(sync.getId());"
Rep "$Base\hacks\NoRotateHack.java" "import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;`n" ''
Rep "$Base\hacks\NoRotateHack.java" "import net.minecraft.world.entity.PositionMoveRotation;`n" ''
Rep "$Base\hacks\NoRotateHack.java" "		if(event.getPacket() instanceof ClientboundPlayerLookAtPacket`n			|| event.getPacket() instanceof ClientboundPlayerRotationPacket)`n		{`n			event.cancel();`n			return;`n		}" "		if(event.getPacket() instanceof ClientboundPlayerLookAtPacket)`n		{`n			event.cancel();`n			return;`n		}"
Rep "$Base\hacks\NoRotateHack.java" "		// Position packets also carry teleports, so they cannot be cancelled.`n		// Remember the current view instead and restore it after the packet`n		// has been applied.`n		PositionMoveRotation current = PositionMoveRotation.of(MC.player);`n		PositionMoveRotation result = PositionMoveRotation.calculateAbsolute(`n			current, packet.change(), packet.relatives());`n`n		boolean yawChanged =`n			Math.abs(wrapDegrees(result.yRot() - current.yRot())) > 1.0E-4F;`n		boolean pitchChanged =`n			Math.abs(wrapDegrees(result.xRot() - current.xRot())) > 1.0E-4F;`n		if(!yawChanged && !pitchChanged)`n			return;`n`n		if(restorePending)" "		// Position packets also carry teleports, so they cannot be cancelled.`n		// Remember the current view instead and restore it after the packet`n		// has been applied.`n		if(restorePending)"
Rep "$Base\hacks\NoRotateHack.java" 'MC.player.setOldRot();' 'MC.player.setOldPosAndRot();'
Rep "$Base\hacks\FurnaceUncloggerHack.java" 'import net.minecraft.world.item.crafting.RecipePropertySet;' 'import net.minecraft.world.item.crafting.RecipeType;'
Rep "$Base\hacks\FurnaceUncloggerHack.java" "		BlockState state = MC.level.getBlockState(target);`n`n		if(state.getBlock() == Blocks.FURNACE)`n			return MC.level.recipeAccess()`n				.propertySet(RecipePropertySet.FURNACE_INPUT).test(stack);`n		if(state.getBlock() == Blocks.BLAST_FURNACE)`n			return MC.level.recipeAccess()`n				.propertySet(RecipePropertySet.BLAST_FURNACE_INPUT).test(stack);`n		if(state.getBlock() == Blocks.SMOKER)`n			return MC.level.recipeAccess()`n				.propertySet(RecipePropertySet.SMOKER_INPUT).test(stack);`n`n		return false;`n	}" "		BlockState state = MC.level.getBlockState(target);`n`n		if(state.getBlock() == Blocks.FURNACE)`n			return matchesInput(RecipeType.SMELTING, stack);`n		if(state.getBlock() == Blocks.BLAST_FURNACE)`n			return matchesInput(RecipeType.BLASTING, stack);`n		if(state.getBlock() == Blocks.SMOKER)`n			return matchesInput(RecipeType.SMOKING, stack);`n`n		return false;`n	}`n`n	private static <I extends RecipeInput, T extends Recipe<I>> boolean matchesInput(RecipeType<T> type,`n		ItemStack stack)`n	{`n		return MC.level.getRecipeManager().getAllRecipesFor(type).stream()`n			.anyMatch(holder -> holder.value().getIngredients().stream()`n				.anyMatch(ingredient -> ingredient.test(stack)));`n	}"
Rep "$Base\hacks\FurnaceUncloggerHack.java" 'import net.minecraft.world.item.crafting.RecipeType;' "import net.minecraft.world.item.crafting.Recipe;`nimport net.minecraft.world.item.crafting.RecipeInput;`nimport net.minecraft.world.item.crafting.RecipeType;"
Rep "$Base\hacks\AutoArmorPlusHack.java" 'int type = stack.get(DataComponents.EQUIPPABLE).slot().getIndex();' 'int type = getEquipmentSlot(stack).getIndex();'
Rep "$Base\hacks\AutoArmorPlusHack.java" "		var equippable = stack.get(DataComponents.EQUIPPABLE);`n		return equippable != null && equippable.slot().getType()`n			.equals(EquipmentSlot.Type.HUMANOID_ARMOR);" "		return stack.getItem() instanceof ArmorItem armor`n			&& armor.getEquipmentSlot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR;"
Rep "$Base\hacks\AutoArmorPlusHack.java" 'EquipmentSlot slot = stack.get(DataComponents.EQUIPPABLE).slot();' 'EquipmentSlot slot = getEquipmentSlot(stack);'
Rep "$Base\hacks\AutoArmorPlusHack.java" '	private boolean isArmor(ItemStack stack)' "	private static EquipmentSlot getEquipmentSlot(ItemStack stack)`n	{`n		return stack.getItem() instanceof ArmorItem armor`n			? armor.getEquipmentSlot() : EquipmentSlot.MAINHAND;`n	}`n`n	private boolean isArmor(ItemStack stack)"
$t = [IO.File]::ReadAllText("$Base\hacks\AutoArmorPlusHack.java")
if(!$t.Contains('import net.minecraft.world.item.ArmorItem;')) {
    $t = $t.Replace('import net.minecraft.world.item.ItemStack;', "import net.minecraft.world.item.ArmorItem;`nimport net.minecraft.world.item.ItemStack;")
    [IO.File]::WriteAllText("$Base\hacks\AutoArmorPlusHack.java", $t, $enc)
}
Rep "$Base\hacks\AutoArmorPlusHack.java" 'int armorPoints = (int)mods.compute(Attributes.ARMOR, 0, slot);' "int armorPoints = 0;`n		for(ItemAttributeModifiers.Entry entry : mods.modifiers())`n			if(entry.slot().test(slot)`n				&& entry.attribute().is(Attributes.ARMOR))`n				armorPoints += (int)entry.modifier().amount();"

# ---- common specific fixes ----
Rep "$Base\hacks\ImgTipsHack.java" 'MobEffects.RESISTANCE' 'MobEffects.DAMAGE_RESISTANCE'
Rep "$Base\hacks\ImgTipsHack.java" 'MobEffects.STRENGTH' 'MobEffects.DAMAGE_BOOST'
Rep "$Base\hacks\ImgTipsHack.java" 'MobEffects.SPEED' 'MobEffects.MOVEMENT_SPEED'
Rep "$Base\hacks\ScreenActionsHack.java" 'InputConstants.isKeyDown(WurstClient.MC.getWindow(),' 'InputConstants.isKeyDown(WurstClient.MC.getWindow().getWindow(),'
Rep "$Base\hacks\AutoTPAcceptHack.java" "		if(click instanceof ClickEvent.RunCommand run)`n			out.add(run.command());`n		else if(click instanceof ClickEvent.SuggestCommand suggest)`n			out.add(suggest.command());" "		if(click != null)`n		{`n			if(click.getAction() == ClickEvent.Action.RUN_COMMAND)`n				out.add(click.getValue());`n			else if(click.getAction() == ClickEvent.Action.SUGGEST_COMMAND)`n				out.add(click.getValue());`n		}"
Rep "$Base\hacks\KillFxHack.java" "`n		COPPER_FLAME(ParticleTypes.COPPER_FIRE_FLAME, `"Copper Flame`")," ''
Rep "$Base\hacks\KillFxHack.java" "`n		FIREFLY(ParticleTypes.FIREFLY, `"Firefly`")," ''
Rep "$Base\hacks\KillFxHack.java" "`n		PALE_OAK(ParticleTypes.PALE_OAK_LEAVES, `"Pale Oak Leaves`")," ''
Rep "$Base\hacks\VillagerTraderHack.java" '.isDisabled()' '.isOutOfStock()'
Rep "$Base\hacks\VillagerTraderHack.java" '.profession()' '.getProfession()'
Rep "$Base\hacks\VillagerTraderHack.java" 'getProfession().is(profession)' 'getProfession() == profession'
Rep "$Base\hacks\AutoRefreshTradeHack.java" '.isDisabled()' '.isOutOfStock()'
Rep "$Base\hacks\AutoRefreshTradeHack.java" '.profession().is(VillagerProfession.LIBRARIAN)' '.getProfession().equals(VillagerProfession.LIBRARIAN)'
Rep "$Base\hacks\AutoKouZiHack.java" '.getProfile().name()' '.getProfile().getName()'
if($AccessMode -eq 'accessor') {
    Rep "$Base\hacks\AdaManualCrystalHack.java" 'import net.wurstclient.hack.Hack;' "import net.wurstclient.hack.Hack;`nimport net.wurstclient.mixin.MinecraftAccessor;"
    Rep "$Base\hacks\AdaManualCrystalHack.java" "		if(IMC.getRightClickDelay() > target)`n			IMC.setRightClickDelay(target);" "		MinecraftAccessor accessor = (MinecraftAccessor)(Object)MC;`n		if(accessor.getRightClickDelay() > target)`n			accessor.setRightClickDelay(target);"
    Rep "$Base\hacks\AutoChorusHack.java" 'MC.startUseItem();' '((net.wurstclient.mixin.MinecraftAccessor)(Object)MC).invokeStartUseItem();'
    Rep "$Base\hacks\LiquidFillerHack.java" 'IMC.setRightClickDelay(4);' '((net.wurstclient.mixin.MinecraftAccessor)(Object)MC).setRightClickDelay(4);'
} else {
    Rep "$Base\hacks\AdaManualCrystalHack.java" "		if(IMC.getRightClickDelay() > target)`n			IMC.setRightClickDelay(target);" "		if(MC.rightClickDelay > target)`n			MC.rightClickDelay = target;"
    Rep "$Base\hacks\LiquidFillerHack.java" 'IMC.setRightClickDelay(4);' 'MC.rightClickDelay = 4;'
}
Rep "$Base\hacks\SpearKillHack.java" 'entity.canBeCollidedWith(MC.player)' 'entity.canBeCollidedWith()'
Rep "$Base\hacks\AutoChestAuraHack.java" 'packet.containerId()' 'packet.getContainerId()'
Rep "$Base\hacks\ElytraGrimAccelerateHack.java" 'Vec3 incoming = packet.getMovement();' "Vec3 incoming =`n			new Vec3(packet.getXa(), packet.getYa(), packet.getZa());"
Rep "$Base\hacks\ReplenishHack.java" 'if(!shouldReplenish(current, wanted))' 'if(!shouldReplenish(current, wanted, slot))'
Rep "$Base\hacks\ReplenishHack.java" 'private boolean shouldReplenish(ItemStack current, ItemStack wanted)' "private boolean shouldReplenish(ItemStack current, ItemStack wanted,`n		int slot)"
Rep "$Base\hacks\InfinityMinerHack.java" 'MC.getConnection().disconnect(Component' 'MC.getConnection().getConnection().disconnect(Component'
Rep "$Base\hacks\KaboomHack.java" 'SoundEvents.GENERIC_EXPLODE,' 'SoundEvents.GENERIC_EXPLODE.value(),'

# ---- RenderUtils.submitText ----
$ru = "$Base\util\RenderUtils.java"
Rep $ru 'import net.minecraft.util.Mth;' "import net.minecraft.util.FormattedCharSequence;`nimport net.minecraft.util.Mth;"
Rep $ru '	public static float[] getRainbowColor()' "	public static void submitText(PoseStack matrices, float x, float y,`n		FormattedCharSequence text, boolean dropShadow,`n		Font.DisplayMode displayMode, int lightCoords, int color,`n		int backgroundColor, int outlineColor)`n	{`n		MultiBufferSource.BufferSource buffers = getVCP();`n		WurstClient.MC.font.drawInBatch(text, x, y, color, dropShadow,`n			matrices.last().pose(), buffers, displayMode, backgroundColor,`n			lightCoords);`n		buffers.endBatch();`n	}`n	`n	public static float[] getRainbowColor()"

# ---- ServerboundMovePlayerPacket constructors: drop extra horizontalCollision flag ----
$prefix = 'new ServerboundMovePlayerPacket.'
$expected = @{ 'Pos' = 5; 'Rot' = 4; 'PosRot' = 7; 'StatusOnly' = 2 }
Get-ChildItem $Base -Recurse -Filter *.java | ForEach-Object {
    $t = [IO.File]::ReadAllText($_.FullName)
    if(!$t.Contains($prefix)) { return }
    $out = New-Object System.Text.StringBuilder; $i = 0; $did = $false
    while($i -lt $t.Length) {
        $idx = $t.IndexOf($prefix,$i)
        if($idx -lt 0) { [void]$out.Append($t.Substring($i)); break }
        [void]$out.Append($t.Substring($i,$idx-$i))
        $p = $t.IndexOf('(',$idx)
        if($p -lt 0) { [void]$out.Append($t.Substring($idx)); break }
        $type = $t.Substring($idx+$prefix.Length,$p-($idx+$prefix.Length))
        $depth = 1; $j = $p+1
        while($j -lt $t.Length -and $depth -gt 0) { $c = $t[$j]; if($c -eq '('){$depth++} elseif($c -eq ')'){$depth--}; $j++ }
        if($depth -ne 0) { [void]$out.Append($t.Substring($idx)); break }
        $inner = $t.Substring($p+1,($j-1)-($p+1))
        $parts = New-Object System.Collections.ArrayList; $start = 0; $d = 0
        for($k=0;$k -lt $inner.Length;$k++) {
            $c = $inner[$k]
            if($c -eq '(' -or $c -eq '[' -or $c -eq '{'){$d++}
            elseif($c -eq ')' -or $c -eq ']' -or $c -eq '}'){$d--}
            elseif($c -eq ',' -and $d -eq 0){ [void]$parts.Add($inner.Substring($start,$k-$start)); $start = $k+1 }
        }
        [void]$parts.Add($inner.Substring($start))
        if($expected.ContainsKey($type) -and $parts.Count -eq $expected[$type]) {
            $parts.RemoveAt($parts.Count-1)
            [void]$out.Append($prefix+$type+'('+($parts -join ',')+')')
            $did = $true
        } else { [void]$out.Append($t.Substring($idx,$j-$idx)) }
        $i = $j
    }
    if($did) { [IO.File]::WriteAllText($_.FullName,$out.ToString(),$enc) }
}
