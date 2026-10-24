/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import java.util.HashMap;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.resources.ResourceLocation;
import net.wurstclient.util.json.JsonUtils;
import net.wurstclient.util.json.WsonObject;

/**
 * Adds Wurst capes after the vanilla registration instead of replacing
 * {@code registerSkins}. Replacing it used to shadow every other skin
 * provider (CustomSkinLoader, offline-skin launchers) and the title screen
 * ended up showing the default skin.
 */
@Mixin(SkinManager.class)
public abstract class PlayerSkinProviderMixin
{
	@Unique
	private static HashMap<String, String> capes;

	@Inject(at = @At("TAIL"),
		method = "registerSkins(Lcom/mojang/authlib/GameProfile;Lnet/minecraft/client/resources/SkinManager$SkinTextureCallback;Z)V")
	private void onLoadSkin(GameProfile profile,
		SkinManager.SkinTextureCallback callback, boolean requireSecure,
		CallbackInfo ci)
	{
		Util.backgroundExecutor().execute(() -> {
			String capeUrl = findWurstCape(profile);

			if(capeUrl == null)
				return;

			MinecraftProfileTexture cape =
				new MinecraftProfileTexture(capeUrl, null);

			Minecraft.getInstance().execute(() ->
				RenderSystem.recordRenderCall(() -> registerTexture(cape,
					Type.CAPE, callback)));
		});
	}

	@Unique
	private static String findWurstCape(GameProfile profile)
	{
		try
		{
			if(capes == null)
				setupWurstCapes();

			String name = profile.getName();
			String uuid = profile.getId().toString();

			if(capes.containsKey(name))
				return capes.get(name);
			if(capes.containsKey(uuid))
				return capes.get(uuid);

		}catch(Exception e)
		{
			System.err.println("[Wurst] Failed to load cape for '"
				+ profile.getName() + "' (" + profile.getId() + ")");
			e.printStackTrace();
		}

		return null;
	}

	@Unique
	private static void setupWurstCapes()
	{
		try
		{
			// assign map first to prevent endless retries if download fails
			capes = new HashMap<>();

			// download cape list from wurstclient.net
			WsonObject rawCapes = JsonUtils.parseURLToObject(
				"https://www.wurstclient.net/api/v1/capes.json");

			// assign capes
			capes = rawCapes.getAllStrings();

		}catch(Exception e)
		{
			System.err
				.println("[Wurst] Failed to load capes from wurstclient.net!");

			e.printStackTrace();
		}
	}

	@Shadow
	@Nullable
	private ResourceLocation registerTexture(
		MinecraftProfileTexture profileTexture, Type type,
		@Nullable SkinManager.SkinTextureCallback callback)
	{
		throw new AssertionError();
	}
}
