/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun (GPL-3.0).
package net.wurstclient.hacks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;

/**
 * Plays lightning, particle, sound, firework and explosion effects when a
 * living entity dies. The sound is configurable through a raw sound event ID,
 * which replaces the upstream's four large sound-group enums.
 */
@SearchTags({"kill fx", "kill effects", "death effects", "lightning kill",
	"kill particles"})
public final class KillFxHack extends Hack implements UpdateListener,
	PlayerAttacksEntityListener, WorldChangeListener
{
	private final CheckboxSetting onlyTargeted = new CheckboxSetting(
		"Only targeted",
		"Only trigger the effects for entities you have attacked.", true);

	private final SliderSetting targetTimeout = new SliderSetting(
		"Target memory", "Seconds an attacked target is remembered.", 3.5,
		0.5, 10, 0.1, ValueDisplay.DECIMAL.withSuffix("s"));

	private final CheckboxSetting useLightning = new CheckboxSetting(
		"Lightning", "Summons lightning when an entity dies.", true);

	private final SliderSetting lightningAmount = new SliderSetting(
		"Lightning amount", "Amount of lightning bolts to summon.", 1, 1, 10,
		1, ValueDisplay.INTEGER);

	private final CheckboxSetting useParticles = new CheckboxSetting(
		"Particles", "Spawns particles when an entity dies.", true);

	private final EnumSetting<ParticleCategory> particleCategory =
		new EnumSetting<>("Particle category", "Category of particles to use.",
			ParticleCategory.values(), ParticleCategory.MAGIC);

	private final EnumSetting<CombatParticle> combatParticle =
		new EnumSetting<>("Combat particle", "Particle used in combat mode.",
			CombatParticle.values(), CombatParticle.CRIT);

	private final EnumSetting<MagicParticle> magicParticle =
		new EnumSetting<>("Magic particle", "Particle used in magic mode.",
			MagicParticle.values(), MagicParticle.END_ROD);

	private final EnumSetting<FireParticle> fireParticle =
		new EnumSetting<>("Fire particle", "Particle used in fire mode.",
			FireParticle.values(), FireParticle.FLAME);

	private final EnumSetting<NatureParticle> natureParticle =
		new EnumSetting<>("Nature particle", "Particle used in nature mode.",
			NatureParticle.values(), NatureParticle.HEART);

	private final EnumSetting<UpdateParticle> updateParticle =
		new EnumSetting<>("1.21 particle", "Particle used in 1.21 mode.",
			UpdateParticle.values(), UpdateParticle.OMINOUS_SPAWNING);

	private final EnumSetting<MiscParticle> miscParticle =
		new EnumSetting<>("Misc particle", "Particle used in misc mode.",
			MiscParticle.values(), MiscParticle.SCULK_SOUL);

	private final EnumSetting<ParticleShape> particleShape =
		new EnumSetting<>("Particle shape", "Shape of the particle effect.",
			ParticleShape.values(), ParticleShape.BURST);

	private final SliderSetting particleCount = new SliderSetting(
		"Particle count", "Amount of particles to spawn.", 40, 5, 200, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting particleSpeed = new SliderSetting(
		"Particle speed", "Speed multiplier of the particles.", 0.2, 0, 2,
		0.01, ValueDisplay.DECIMAL);

	private final CheckboxSetting useSound = new CheckboxSetting("Sound",
		"Plays a sound when an entity dies.", true);

	private final TextFieldSetting soundId = new TextFieldSetting("Sound ID",
		"Sound event ID, e.g. minecraft:entity.lightning_bolt.thunder.",
		"minecraft:entity.lightning_bolt.thunder");

	private final SliderSetting volume = new SliderSetting("Volume",
		"Volume of the kill sound.", 1, 0, 10, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting pitch = new SliderSetting("Pitch",
		"Pitch of the kill sound.", 1, 0.5, 2, 0.01, ValueDisplay.DECIMAL);

	private final CheckboxSetting useFirework = new CheckboxSetting("Firework",
		"Spawns a firework rocket at the death position.", false);

	private final CheckboxSetting useExplosion = new CheckboxSetting(
		"Explosion smoke", "Spawns a large explosion particle.", false);

	private final Set<Integer> processedEntities = new HashSet<>();
	private final Map<Integer, Long> attackedTargets = new HashMap<>();

	public KillFxHack()
	{
		super("KillFX");
		setCategory(Category.RENDER);
		addSetting(onlyTargeted);
		addSetting(targetTimeout);
		addSetting(useLightning);
		addSetting(lightningAmount);
		addSetting(useParticles);
		addSetting(particleCategory);
		addSetting(combatParticle);
		addSetting(magicParticle);
		addSetting(fireParticle);
		addSetting(natureParticle);
		addSetting(updateParticle);
		addSetting(miscParticle);
		addSetting(particleShape);
		addSetting(particleCount);
		addSetting(particleSpeed);
		addSetting(useSound);
		addSetting(soundId);
		addSetting(volume);
		addSetting(pitch);
		addSetting(useFirework);
		addSetting(useExplosion);

		targetTimeout.visibleWhen(onlyTargeted::isChecked);
		lightningAmount.visibleWhen(useLightning::isChecked);
		combatParticle.visibleWhen(() -> useParticles.isChecked()
			&& particleCategory.getSelected() == ParticleCategory.COMBAT);
		magicParticle.visibleWhen(() -> useParticles.isChecked()
			&& particleCategory.getSelected() == ParticleCategory.MAGIC);
		fireParticle.visibleWhen(() -> useParticles.isChecked()
			&& particleCategory.getSelected() == ParticleCategory.FIRE);
		natureParticle.visibleWhen(() -> useParticles.isChecked()
			&& particleCategory.getSelected() == ParticleCategory.NATURE);
		updateParticle.visibleWhen(() -> useParticles.isChecked()
			&& particleCategory.getSelected() == ParticleCategory.UPDATE_121);
		miscParticle.visibleWhen(() -> useParticles.isChecked()
			&& particleCategory.getSelected() == ParticleCategory.MISC);
		particleShape.visibleWhen(useParticles::isChecked);
		particleCount.visibleWhen(useParticles::isChecked);
		particleSpeed.visibleWhen(useParticles::isChecked);
		soundId.visibleWhen(useSound::isChecked);
		volume.visibleWhen(useSound::isChecked);
		pitch.visibleWhen(useSound::isChecked);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PlayerAttacksEntityListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
		processedEntities.clear();
		attackedTargets.clear();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PlayerAttacksEntityListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		processedEntities.clear();
		attackedTargets.clear();
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		processedEntities.clear();
		attackedTargets.clear();
	}

	@Override
	public void onPlayerAttacksEntity(Entity target)
	{
		if(target instanceof LivingEntity)
			attackedTargets.put(target.getId(), System.currentTimeMillis());
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		if(onlyTargeted.isChecked())
		{
			long now = System.currentTimeMillis();
			long timeout = (long)(targetTimeout.getValue() * 1000);
			attackedTargets.entrySet()
				.removeIf(entry -> now - entry.getValue() > timeout);
		}

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(!(entity instanceof LivingEntity living) || entity == MC.player)
				continue;

			if(living.getHealth() > 0 && !living.isDeadOrDying())
				continue;

			if(!processedEntities.add(entity.getId()))
				continue;

			if(onlyTargeted.isChecked()
				&& !attackedTargets.containsKey(entity.getId()))
				continue;

			renderEffects(living);
		}
	}

	private void renderEffects(LivingEntity entity)
	{
		Vec3 pos = new Vec3(entity.getX(), entity.getY(), entity.getZ());

		if(useLightning.isChecked())
		{
			int amount = lightningAmount.getValueI();
			for(int i = 0; i < amount; i++)
			{
				double offsetX =
					i == 0 ? 0 : (Math.random() - 0.5) * 0.5;
				double offsetZ =
					i == 0 ? 0 : (Math.random() - 0.5) * 0.5;

				LightningBolt bolt =
					new LightningBolt(EntityType.LIGHTNING_BOLT, MC.level);
				bolt.setPos(pos.x + offsetX, pos.y, pos.z + offsetZ);
				MC.level.addFreshEntity(bolt);
			}
		}

		if(useParticles.isChecked())
		{
			ParticleOptions effect = switch(particleCategory.getSelected())
			{
				case COMBAT -> combatParticle.getSelected().particle;
				case MAGIC -> magicParticle.getSelected().particle;
				case FIRE -> fireParticle.getSelected().particle;
				case NATURE -> natureParticle.getSelected().particle;
				case UPDATE_121 -> updateParticle.getSelected().particle;
				case MISC -> miscParticle.getSelected().particle;
			};

			spawnParticles(entity, effect);
		}

		if(useSound.isChecked())
		{
			ResourceLocation id = ResourceLocation.tryParse(soundId.getValue());
			if(id != null)
			{
				SoundEvent sound = SoundEvent.createVariableRangeEvent(id);
				MC.level.playLocalSound(pos.x, pos.y, pos.z, sound,
					SoundSource.PLAYERS, volume.getValueF(), pitch.getValueF(),
					false);
			}
		}

		if(useFirework.isChecked())
		{
			ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET);
			FireworkRocketEntity rocket = new FireworkRocketEntity(MC.level,
				pos.x, pos.y + 0.5, pos.z, stack);
			MC.level.addFreshEntity(rocket);
			MC.level.addParticle(ParticleTypes.EXPLOSION, pos.x, pos.y + 1,
				pos.z, 0, 0, 0);
		}

		if(useExplosion.isChecked())
			MC.level.addParticle(ParticleTypes.EXPLOSION_EMITTER, pos.x,
				pos.y + 1, pos.z, 0, 0, 0);
	}

	private void spawnParticles(LivingEntity entity, ParticleOptions effect)
	{
		Vec3 pos = new Vec3(entity.getX(), entity.getY(), entity.getZ());
		int count = particleCount.getValueI();
		double speed = particleSpeed.getValue();
		double height = entity.getBbHeight();
		double width = entity.getBbWidth();

		switch(particleShape.getSelected())
		{
			case BURST ->
			{
				for(int i = 0; i < count; i++)
					MC.level.addParticle(effect,
						pos.x + (Math.random() - 0.5) * width,
						pos.y + Math.random() * height,
						pos.z + (Math.random() - 0.5) * width,
						(Math.random() - 0.5) * speed,
						(Math.random() - 0.5) * speed,
						(Math.random() - 0.5) * speed);
			}

			case SPHERE ->
			{
				for(int i = 0; i < count; i++)
				{
					double theta = 2 * Math.PI * Math.random();
					double phi = Math.acos(2 * Math.random() - 1);
					double dx = Math.sin(phi) * Math.cos(theta);
					double dy = Math.sin(phi) * Math.sin(theta);
					double dz = Math.cos(phi);

					MC.level.addParticle(effect, pos.x + dx,
						pos.y + dy + height / 2, pos.z + dz, 0, 0, 0);
				}
			}

			case SPIRAL ->
			{
				for(int i = 0; i < count; i++)
				{
					double yOffset = (double)i / count * 2.5;
					double angle = yOffset * 5;
					double radius = 0.8;

					MC.level.addParticle(effect, pos.x + Math.cos(angle)
						* radius, pos.y + yOffset,
						pos.z + Math.sin(angle) * radius, 0, 0.05, 0);
				}
			}

			case COLUMN ->
			{
				for(int i = 0; i < count; i++)
					MC.level.addParticle(effect,
						pos.x + (Math.random() - 0.5) * width, pos.y + 0.1,
						pos.z + (Math.random() - 0.5) * width, 0, speed, 0);
			}

			case HALO ->
			{
				for(int i = 0; i < count; i++)
				{
					double angle = (double)i / count * Math.PI * 2;
					double radius = 0.7;

					MC.level.addParticle(effect, pos.x + Math.cos(angle)
						* radius, pos.y + height + 0.5,
						pos.z + Math.sin(angle) * radius, 0, 0, 0);
				}
			}

			case HEART ->
			{
				for(int i = 0; i < count; i++)
				{
					double t = (double)i / count * Math.PI * 2;
					double hx = 16 * Math.pow(Math.sin(t), 3);
					double hz = 13 * Math.cos(t) - 5 * Math.cos(2 * t)
						- 2 * Math.cos(3 * t) - Math.cos(4 * t);
					double scale = 0.06;

					MC.level.addParticle(effect, pos.x + hx * scale,
						pos.y + 1.2 + hz * scale * 0.5,
						pos.z + hz * scale, 0, 0, 0);
				}
			}

			case HELIX ->
			{
				for(int i = 0; i < count; i++)
				{
					double h = (double)i / count * 3.0;
					double angle = h * 4.0;
					double radius = 0.6;

					MC.level.addParticle(effect,
						pos.x + Math.cos(angle) * radius, pos.y + h,
						pos.z + Math.sin(angle) * radius, 0, 0, 0);
					MC.level.addParticle(effect,
						pos.x + Math.cos(angle + Math.PI) * radius,
						pos.y + h,
						pos.z + Math.sin(angle + Math.PI) * radius, 0, 0, 0);
				}
			}

			case STAR ->
			{
				for(int i = 0; i < count; i++)
				{
					double angle = (double)i / count * Math.PI * 2;
					double dx = Math.cos(angle);
					double dz = Math.sin(angle);

					MC.level.addParticle(effect, pos.x, pos.y + 0.2, pos.z,
						dx * speed, 0, dz * speed);
				}
			}

			case RING ->
			{
				for(int i = 0; i < count; i++)
				{
					double angle = (double)i / count * Math.PI * 2;
					double dx = Math.cos(angle);
					double dz = Math.sin(angle);

					MC.level.addParticle(effect, pos.x + dx * 0.2,
						pos.y + 0.1, pos.z + dz * 0.2, dx * speed * 2, 0,
						dz * speed * 2);
				}
			}
		}
	}

	public enum ParticleCategory
	{
		COMBAT("Combat"),
		MAGIC("Magic"),
		FIRE("Fire"),
		NATURE("Nature"),
		UPDATE_121("1.21"),
		MISC("Misc");

		private final String name;

		ParticleCategory(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	public enum ParticleShape
	{
		BURST("Burst"),
		SPHERE("Sphere"),
		SPIRAL("Spiral"),
		COLUMN("Column"),
		HALO("Halo"),
		HEART("Heart"),
		HELIX("Helix"),
		STAR("Star"),
		RING("Ring");

		private final String name;

		ParticleShape(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	public enum CombatParticle
	{
		DAMAGE(ParticleTypes.DAMAGE_INDICATOR, "Damage"),
		CRIT(ParticleTypes.CRIT, "Crit"),
		ENCHANTED_HIT(ParticleTypes.ENCHANTED_HIT, "Enchanted Hit"),
		SWEEP(ParticleTypes.SWEEP_ATTACK, "Sweep"),
		EXPLOSION(ParticleTypes.EXPLOSION, "Explosion"),
		EXPLOSION_HUGE(ParticleTypes.EXPLOSION_EMITTER, "Huge Explosion"),
		SONIC(ParticleTypes.SONIC_BOOM, "Sonic Boom"),
		TOTEM(ParticleTypes.TOTEM_OF_UNDYING, "Totem"),
		FIREWORK(ParticleTypes.FIREWORK, "Firework"),
		EGG_CRACK(ParticleTypes.EGG_CRACK, "Egg Crack");

		private final ParticleOptions particle;
		private final String name;

		CombatParticle(ParticleOptions particle, String name)
		{
			this.particle = particle;
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	public enum MagicParticle
	{
		WITCH(ParticleTypes.WITCH, "Witch"),
		END_ROD(ParticleTypes.END_ROD, "End Rod"),
		PORTAL(ParticleTypes.PORTAL, "Portal"),
		ENCHANT(ParticleTypes.ENCHANT, "Enchant"),
		NAUTILUS(ParticleTypes.NAUTILUS, "Nautilus"),
		ELDER_GUARDIAN(ParticleTypes.ELDER_GUARDIAN, "Elder Guardian"),
		SCULK_CHARGE(ParticleTypes.SCULK_CHARGE_POP, "Sculk Charge"),
		SOUL(ParticleTypes.SOUL, "Soul"),
		GLOW_SQUID_INK(ParticleTypes.GLOW_SQUID_INK, "Glow Squid Ink");

		private final ParticleOptions particle;
		private final String name;

		MagicParticle(ParticleOptions particle, String name)
		{
			this.particle = particle;
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	public enum FireParticle
	{
		FLAME(ParticleTypes.FLAME, "Flame"),
		SOUL_FLAME(ParticleTypes.SOUL_FIRE_FLAME, "Soul Flame"),
		SMALL_FLAME(ParticleTypes.SMALL_FLAME, "Small Flame"),
		COPPER_FLAME(ParticleTypes.COPPER_FIRE_FLAME, "Copper Flame"),
		LAVA(ParticleTypes.LAVA, "Lava"),
		LARGE_SMOKE(ParticleTypes.LARGE_SMOKE, "Large Smoke"),
		SMOKE(ParticleTypes.SMOKE, "Smoke"),
		WHITE_SMOKE(ParticleTypes.WHITE_SMOKE, "White Smoke"),
		CAMPFIRE(ParticleTypes.CAMPFIRE_COSY_SMOKE, "Campfire"),
		CAMPFIRE_SIGNAL(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
			"Campfire Signal"),
		GLOW(ParticleTypes.GLOW, "Glow"),
		WAX_ON(ParticleTypes.WAX_ON, "Wax On"),
		WAX_OFF(ParticleTypes.WAX_OFF, "Wax Off"),
		SCRAPE(ParticleTypes.SCRAPE, "Scrape"),
		SPARK(ParticleTypes.ELECTRIC_SPARK, "Electric Spark");

		private final ParticleOptions particle;
		private final String name;

		FireParticle(ParticleOptions particle, String name)
		{
			this.particle = particle;
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	public enum NatureParticle
	{
		HEART(ParticleTypes.HEART, "Heart"),
		CLOUD(ParticleTypes.CLOUD, "Cloud"),
		RAIN(ParticleTypes.RAIN, "Rain"),
		SNOW(ParticleTypes.SNOWFLAKE, "Snowflake"),
		SLIME(ParticleTypes.ITEM_SLIME, "Slime"),
		BUBBLE(ParticleTypes.BUBBLE, "Bubble"),
		BUBBLE_COLUMN_UP(ParticleTypes.BUBBLE_COLUMN_UP, "Bubble Column Up"),
		CURRENT_DOWN(ParticleTypes.CURRENT_DOWN, "Current Down"),
		BUBBLE_POP(ParticleTypes.BUBBLE_POP, "Bubble Pop"),
		SPLASH(ParticleTypes.SPLASH, "Splash"),
		FISHING(ParticleTypes.FISHING, "Fishing"),
		DOLPHIN(ParticleTypes.DOLPHIN, "Dolphin"),
		UNDERWATER(ParticleTypes.UNDERWATER, "Underwater"),
		NOTE(ParticleTypes.NOTE, "Note"),
		CHERRY(ParticleTypes.CHERRY_LEAVES, "Cherry Leaves"),
		PALE_OAK(ParticleTypes.PALE_OAK_LEAVES, "Pale Oak Leaves"),
		FIREFLY(ParticleTypes.FIREFLY, "Firefly"),
		SPORE(ParticleTypes.SPORE_BLOSSOM_AIR, "Spore Blossom"),
		WHITE_ASH(ParticleTypes.WHITE_ASH, "White Ash"),
		WARPED_SPORE(ParticleTypes.WARPED_SPORE, "Warped Spore"),
		CRIMSON_SPORE(ParticleTypes.CRIMSON_SPORE, "Crimson Spore");

		private final ParticleOptions particle;
		private final String name;

		NatureParticle(ParticleOptions particle, String name)
		{
			this.particle = particle;
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	public enum UpdateParticle
	{
		GUST(ParticleTypes.GUST, "Gust"),
		SMALL_GUST(ParticleTypes.SMALL_GUST, "Small Gust"),
		GUST_EMITTER_LARGE(ParticleTypes.GUST_EMITTER_LARGE,
			"Large Gust Emitter"),
		GUST_EMITTER_SMALL(ParticleTypes.GUST_EMITTER_SMALL,
			"Small Gust Emitter"),
		TRIAL_DETECTION(ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER,
			"Trial Detection"),
		TRIAL_DETECTION_OMINOUS(
			ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER_OMINOUS,
			"Trial Detection Ominous"),
		OMINOUS_SPAWNING(ParticleTypes.OMINOUS_SPAWNING, "Ominous Spawning"),
		VAULT(ParticleTypes.VAULT_CONNECTION, "Vault Connection"),
		RAID_OMEN(ParticleTypes.RAID_OMEN, "Raid Omen"),
		TRIAL_OMEN(ParticleTypes.TRIAL_OMEN, "Trial Omen"),
		INFESTED(ParticleTypes.INFESTED, "Infested"),
		COBWEB(ParticleTypes.ITEM_COBWEB, "Cobweb"),
		DUST_PLUME(ParticleTypes.DUST_PLUME, "Dust Plume");

		private final ParticleOptions particle;
		private final String name;

		UpdateParticle(ParticleOptions particle, String name)
		{
			this.particle = particle;
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	public enum MiscParticle
	{
		ASH(ParticleTypes.ASH, "Ash"),
		MYCELIUM(ParticleTypes.MYCELIUM, "Mycelium"),
		SCULK_SOUL(ParticleTypes.SCULK_SOUL, "Sculk Soul"),
		HAPPY(ParticleTypes.HAPPY_VILLAGER, "Happy Villager"),
		ANGRY(ParticleTypes.ANGRY_VILLAGER, "Angry Villager"),
		SNEEZE(ParticleTypes.SNEEZE, "Sneeze"),
		INK(ParticleTypes.SQUID_INK, "Squid Ink");

		private final ParticleOptions particle;
		private final String name;

		MiscParticle(ParticleOptions particle, String name)
		{
			this.particle = particle;
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
