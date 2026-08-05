/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import java.io.File;
import java.util.UUID;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.User;
import net.minecraft.client.resources.DownloadedPackSource;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.packs.repository.RepositorySource;
import net.wurstclient.WurstClient;

@Mixin(DownloadedPackSource.class)
public abstract class ServerResourcePackProviderMixin
	implements RepositorySource
{
	@Shadow
	@Final
	private File serverPackDir;
	
	/**
	 * Patches a fingerprinting vulnerability by creating a separate cache
	 * folder for each Minecraft account.
	 *
	 * <p>
	 * This mixin targets the
	 * <code>new File(this.serverPacksRoot, string)</code> call in
	 * <code>download(URL, String, boolean)</code>.
	 *
	 * @see https://github.com/Wurst-Imperium/Wurst7/issues/1226
	 */
	@WrapOperation(
		at = @At(value = "NEW",
			target = "(Ljava/io/File;Ljava/lang/String;)Ljava/io/File;",
			ordinal = 0,
			remap = false),
		method = "downloadAndSelectResourcePack(Ljava/net/URL;Ljava/lang/String;Z)Ljava/util/concurrent/CompletableFuture;")
	private File wrapConstructor(File parent, String child,
		Operation<File> original)
	{
		File result = original.call(parent, child);
		
		// If the path has already been modified by another mod (likely trying
		// to patch the same exploit), don't modify it further.
		if(parent == null || !parent.equals(serverPackDir))
			return result;
			
		// "getUuidOrNull" was actually nullable in Minecraft 1.20.1,
		// so we fallback to the offline UUID when it returns null.
		User session = WurstClient.MC.getUser();
		UUID uuid = session.getProfileId();
		if(uuid == null)
			uuid = UUIDUtil.createOfflinePlayerUUID(session.getName());
		
		File newParent = new File(result.getParentFile(), uuid.toString());
		return new File(newParent, result.getName());
	}
}
