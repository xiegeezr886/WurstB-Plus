/* SPDX-License-Identifier: GPL-3.0 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.world.entity.Entity;

@Mixin(Entity.class)
public interface AutoLeavePortalAccessor
{
	@Accessor("isInsidePortal")
	boolean wurstIsInsidePortal();
}
