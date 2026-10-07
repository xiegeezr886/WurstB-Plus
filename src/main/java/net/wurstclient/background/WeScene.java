/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.wurstclient.util.json.JsonUtils;

/**
 * Wallpaper Engine 场景的图层树（纯 Java，可单测）。
 *
 * <p>
 * 只读渲染静态画面需要的那部分 {@code scene.json}：画布尺寸、缩放系数，以及
 * 每个会画出东西的对象的贴图、位置、大小、透明度与视差深度。</p>
 *
 * <p>
 * 场景包里的对象分三类，这里只留第一类：</p>
 * <ul>
 * <li>{@code image} 指向 {@code models/*.json} → {@code materials/*.json} →
 * {@code materials/<名字>.tex}，这是真正要画的图层；</li>
 * <li>粒子层（{@code particle}）、文字层（{@code text}，即时钟与日期）、音频层
 * 需要各自的运行时，直接跳过；</li>
 * <li>分隔用的全屏层引用 {@code models/util/fullscreenlayer.json}，这个模型不在
 * 包里，查不到就自然被跳过。</li>
 * </ul>
 *
 * <p>
 * 工坊 2359043440「Persica」实测：20 个对象里 17 个是分隔层，剩下 3 个图层
 * （Blossom backdrop / backdrop flowers / Blossom 2.0）就是要画的东西，画布
 * {@code orthogonalprojection} 3840x2160、{@code zoom} 1.0。</p>
 *
 * <p>
 * 没有实现的部分：godrays / blurprecise / filmgrain / waterwaves 这几个效果
 * 都是 GLSL 后期，静态渲染只画基础图层，亮度与光晕会比 Wallpaper Engine 里
 * 淡一些；时钟与日期文字层也不画。</p>
 */
public record WeScene(int width, int height, float zoom, boolean parallax,
	float parallaxAmount, float parallaxDelay, List<Layer> layers,
	List<ParticleLayer> particles)
{
	/** {@code scene.json} 在包里的名字。 */
	public static final String SCENE_JSON = "scene.json";

	/**
	 * 一个粒子层（雪这类 sprite 效果）。
	 *
	 * @param preset
	 *            {@code scene.json} 里写的预设路径，例如
	 *            {@code particles/presets/snowflat.json}
	 * @param layerIndex
	 *            它插在第几个图像图层<b>之前</b>。Persica 的两层雪分处树枝的前后，
	 *            这个位置不能丢——按场景顺序画，近处那层才会盖住树枝
	 */
	public record ParticleLayer(String name, String preset, float originX,
		float originY, float parallaxX, float parallaxY, int layerIndex)
	{}

	/**
	 * 一个要画的图层。
	 *
	 * @param name
	 *            对象名，只用于日志
	 * @param texture
	 *            材质里写的贴图名，例如 {@code "Blossom 2.0"}；要先用
	 *            {@link #textureEntryName(String, Collection)} 换成包里的条目名
	 * @param originX
	 *            画布坐标（左上角为原点，y 向下），{@code alignment} 为 center
	 *            时是图层中心
	 * @param sizeX
	 *            画布单位下的宽高；{@code autosize} 且没写 size 时为 0，由贴图
	 *            自身尺寸补上
	 * @param parallaxX
	 *            视差深度，0 表示不随鼠标移动
	 */
	public record Layer(String name, String texture, float originX, float originY,
		float sizeX, float sizeY, float scaleX, float scaleY, float alpha,
		float colorR, float colorG, float colorB, float parallaxX,
		float parallaxY, WeColorKey colorKey)
	{}

	private static final float DEFAULT_WIDTH = 1920;
	private static final float DEFAULT_HEIGHT = 1080;

	/**
	 * @param sceneJson
	 *            {@code scene.json} 的内容
	 * @param files
	 *            按包内条目名取文本，取不到返回 null；只用来读
	 *            {@code models/*.json} 与 {@code materials/*.json}
	 */
	public static WeScene parse(String sceneJson,
		Function<String, String> files) throws IOException
	{
		if(sceneJson == null || sceneJson.isBlank())
			throw new IOException("场景包里的 " + SCENE_JSON + " 是空的");

		JsonObject scene;

		try
		{
			scene = JsonUtils.GSON.fromJson(sceneJson, JsonObject.class);
		}catch(RuntimeException e)
		{
			throw new IOException("scene.json 不是合法 JSON", e);
		}

		if(scene == null)
			throw new IOException("scene.json 解析结果为空");

		JsonObject general = object(scene, "general");
		JsonObject projection = object(general, "orthogonalprojection");

		int width = (int)number(projection, "width", DEFAULT_WIDTH);
		int height = (int)number(projection, "height", DEFAULT_HEIGHT);

		if(width <= 0 || height <= 0)
			throw new IOException("画布尺寸不合理：" + width + "x" + height);

		float zoom = (float)number(general, "zoom", 1);
		boolean parallax = bool(general, "cameraparallax", false);
		float parallaxAmount =
			(float)number(general, "cameraparallaxamount", 1);
		float parallaxDelay = (float)number(general, "cameraparallaxdelay", 0);

		List<Layer> layers = new ArrayList<>();
		List<ParticleLayer> particles = new ArrayList<>();
		JsonArray objects = array(scene, "objects");

		for(JsonElement element : objects)
		{
			if(!element.isJsonObject())
				continue;

			JsonObject object = element.getAsJsonObject();

			Layer layer = readLayer(object, files, width, height);

			if(layer != null)
			{
				layers.add(layer);
				continue;
			}

			ParticleLayer particle =
				readParticleLayer(object, width, height, layers.size());

			if(particle != null)
				particles.add(particle);
		}

		return new WeScene(width, height, zoom, parallax, parallaxAmount,
			parallaxDelay, List.copyOf(layers), List.copyOf(particles));
	}

	/**
	 * 粒子层：对象里写的是 {@code particle} 预设路径，没有 {@code image}。
	 */
	private static ParticleLayer readParticleLayer(JsonObject object, int width,
		int height, int layerIndex)
	{
		if(!isVisible(object.get("visible")))
			return null;

		String preset = string(object, "particle", "");

		if(preset.isEmpty())
			return null;

		float[] origin = numbers(object.get("origin"), width / 2F, height / 2F,
			0);
		float[] depth = numbers(object.get("parallaxDepth"), 0, 0, 0);

		return new ParticleLayer(string(object, "name", preset), preset,
			origin[0], origin[1], depth[0], depth[1], layerIndex);
	}

	private static Layer readLayer(JsonObject object,
		Function<String, String> files, int width, int height)
	{
		if(!isVisible(object.get("visible")))
			return null;

		String modelName = string(object, "image", "");

		if(modelName.isEmpty())
			return null;

		float[] origin = numbers(object.get("origin"), width / 2F, height / 2F,
			0);
		float[] size = numbers(object.get("size"), 0, 0, 0);
		float[] scale = numbers(object.get("scale"), 1, 1, 1);
		float[] color = numbers(object.get("color"), 1, 1, 1);
		float[] depth = numbers(object.get("parallaxDepth"), 0, 0, 0);

		float alpha = (float)number(object, "alpha", 1);
		WeColorKey colorKey = WeColorKey.parse(object.get("effects"));

		// Wallpaper Engine 的**内置工具模型**不随包发布，所以 pkg.read 一定读不到：
		//   models/util/solidlayer.json  = 纯色矩形（用图层自己的 color/alpha 画）
		//   models/util/composelayer.json = 分组/合成层（自身不画东西）
		// 实测「流萤」里 Background / Progress Bar / Settings Container / Audio Bars /
		// 纯色 等 10 个对象都指向 solidlayer —— 它们以前因为"模型文件读不到"被整个丢掉，
		// 画面上就少了一块。用空贴图名标记成纯色层，由渲染那边铺成矩形。
		if(modelName.contains("util/solidlayer"))
			return new Layer(string(object, "name", modelName), "", origin[0],
				origin[1], size[0], size[1], scale[0], scale[1], alpha,
				color[0], color[1], color[2], depth[0], depth[1], colorKey);

		// 分组层自己不产生像素；它下面的子对象才是内容（子对象的渲染尚未实现）
		if(modelName.contains("util/composelayer"))
			return null;

		String texture = findTexture(modelName, files);

		if(texture == null)
			return null;

		return new Layer(string(object, "name", modelName), texture, origin[0],
			origin[1], size[0], size[1], scale[0], scale[1], alpha, color[0],
			color[1], color[2], depth[0], depth[1], colorKey);
	}

	/** 模型 → 材质 → 第一个贴图名；任何一步缺失都返回 null。 */
	private static String findTexture(String modelName,
		Function<String, String> files)
	{
		JsonObject model = read(files, modelName);

		if(model == null)
			return null;

		String materialName = string(model, "material", "");

		if(materialName.isEmpty())
			return null;

		JsonObject material = read(files, materialName);

		if(material == null)
			return null;

		for(JsonElement passElement : array(material, "passes"))
		{
			if(!passElement.isJsonObject())
				continue;

			JsonArray textures =
				array(passElement.getAsJsonObject(), "textures");

			for(JsonElement textureElement : textures)
				if(textureElement.isJsonPrimitive()
					&& textureElement.getAsJsonPrimitive().isString())
					return textureElement.getAsString();
		}

		return null;
	}

	private static JsonObject read(Function<String, String> files, String name)
	{
		if(files == null)
			return null;

		String text = files.apply(name);

		if(text == null || text.isBlank())
			return null;

		try
		{
			return JsonUtils.GSON.fromJson(text, JsonObject.class);
		}catch(RuntimeException e)
		{
			return null;
		}
	}

	/**
	 * 把材质里的贴图名换成包里的条目名。
	 *
	 * <p>
	 * Wallpaper Engine 把贴图都放在 {@code materials/} 下，材质里只写裸名字，
	 * 所以先试 {@code materials/<名字>.tex}；找不到再按后缀在整个包里找一次
	 * （大小写不敏感），最后返回 null 让调用方跳过这一层。
	 * </p>
	 */
	public static String textureEntryName(String texture,
		Collection<String> entries)
	{
		if(texture == null || texture.isBlank() || entries == null)
			return null;

		String direct = "materials/" + texture + ".tex";

		if(entries.contains(direct))
			return direct;

		String suffix = ("/" + texture + ".tex").toLowerCase(Locale.ROOT);
		String loose = (texture + ".tex").toLowerCase(Locale.ROOT);

		for(String name : entries)
		{
			String lower = name.toLowerCase(Locale.ROOT);

			if(lower.endsWith(suffix) || lower.equals(loose))
				return name;
		}

		return null;
	}

	/** {@code visible} 既可能是布尔，也可能是 {@code {user, value}}。 */
	private static boolean isVisible(JsonElement element)
	{
		if(element == null || element.isJsonNull())
			return true;

		if(element.isJsonPrimitive())
			return element.getAsJsonPrimitive().isBoolean()
				? element.getAsBoolean() : true;

		if(element.isJsonObject())
		{
			JsonObject object = element.getAsJsonObject();

			if(object.has("value"))
				return isVisible(object.get("value"));
		}

		return true;
	}

	private static JsonObject object(JsonObject parent, String key)
	{
		if(parent == null || !parent.has(key))
			return null;

		JsonElement element = parent.get(key);
		return element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	private static JsonArray array(JsonObject parent, String key)
	{
		if(parent == null || !parent.has(key))
			return new JsonArray();

		JsonElement element = parent.get(key);
		return element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
	}

	private static String string(JsonObject parent, String key, String fallback)
	{
		if(parent == null || !parent.has(key))
			return fallback;

		JsonElement element = parent.get(key);

		if(!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
			return fallback;

		String value = element.getAsString();
		return value.isBlank() ? fallback : value;
	}

	private static double number(JsonObject parent, String key, double fallback)
	{
		if(parent == null || !parent.has(key))
			return fallback;

		JsonElement element = parent.get(key);

		if(!element.isJsonPrimitive())
			return fallback;

		try
		{
			return element.getAsDouble();
		}catch(RuntimeException e)
		{
			return fallback;
		}
	}

	private static boolean bool(JsonObject parent, String key, boolean fallback)
	{
		if(parent == null || !parent.has(key))
			return fallback;

		JsonElement element = parent.get(key);

		if(!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean())
			return fallback;

		return element.getAsBoolean();
	}

	/**
	 * 解析 {@code "x y z"} 这类以空格分隔的向量，缺的部分用兜底值补。
	 *
	 * <p>
	 * 值**也可能是对象**：{@code {"script":…,"scriptproperties":…,"value":"x y z"}}
	 * —— 那是绑定到脚本/用户属性的形式，真实数值在 {@code value} 里。实测「流萤」里
	 * 大量 {@code origin}/{@code scale} 就是这种。以前遇到对象就退回兜底值，而兜底
	 * 是画布中心，图层直接跳到画面正中 —— 这是位移的第二条通路。处理方式与
	 * {@link #isVisible(JsonElement)} 对 {@code visible} 的处理保持一致。
	 * </p>
	 */
	static float[] numbers(JsonElement element, float... fallback)
	{
		if(element != null && element.isJsonObject())
		{
			JsonObject object = element.getAsJsonObject();

			if(object.has("value"))
				return numbers(object.get("value"), fallback);
		}

		float[] out = fallback.clone();

		if(element == null || !element.isJsonPrimitive())
			return out;

		String text = element.getAsString().trim();

		if(text.isEmpty())
			return out;

		String[] parts = text.split("\\s+");

		for(int i = 0; i < out.length && i < parts.length; i++)
			try
			{
				out[i] = Float.parseFloat(parts[i]);
			}catch(NumberFormatException e)
			{
				// 保留兜底值
			}

		return out;
	}
}
