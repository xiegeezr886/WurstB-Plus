import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * 修 Baritone 的 InputOverrideHandler.onTick 空指针（ASM 版）。
 *
 * 崩溃（实测 2026-10-14 23:14）：
 *   java.lang.NullPointerException: Cannot invoke "Object.getClass()" because
 *     "baritone.api.utils.IPlayerContext.player().f_108618_" is null
 *       at baritone.eq.onTick(Unknown Source)
 *
 * f_108618_ = LocalPlayer.input（1.20.1 SRG）。该方法末尾两个分支都在
 * player().input 上调 getClass()，而 input 在玩家/世界未就绪时可能为 null。
 *
 * 修法：null 时按「不是我们的类」处理 —— 两个分支本就用 if_acmpeq /
 * if_acmpne 判断相等性，null 落到哪边是确定的：
 *   if_acmpeq t : 不等顺序执行 -> 目标是 cmp 之后那条指令
 *   if_acmpne t : 不等跳 t     -> 目标就是 t
 *
 * 关键（前几轮失败的教训）：必须用 COMPUTE_FRAMES 让 ASM 自己算帧，而它需要
 * 解析类型的共同父类 —— 那些是 Minecraft 的类型，不在被改的 jar 里。
 * 所以**必须把一个能解析 MC 类型的类路径传进来**（--cp），ASM 通过
 * getClassLoader() 用它来算共同父类。缺了这个类路径，就得手写帧，
 * 而手写帧会连环撞 verifier（帧缺失 / 位置错 / 栈不匹配），实测走不通。
 *
 * 用法：
 *   java -cp <patcher>;<asm> BariPatch <jar>                     只检测
 *   java -cp <patcher>;<asm> BariPatch <jar> --apply --cp <路径>  检测并改写
 *   <路径> 用 ; 分隔，可含 jar 与目录（取项目的 compileClasspath）
 */
public final class BariPatch
{
	private static final String INPUT_INTERNAL =
		"net/minecraft/client/player/Input";
	private static final String INPUT_DESC = "L" + INPUT_INTERNAL + ";";

	public static void main(String[] args) throws Exception
	{
		if(args.length < 1)
		{
			System.err.println("用法: BariPatch <jar> [--apply] [--cp p1;p2;...]");
			System.exit(2);
		}

		Path jar = Paths.get(args[0]);
		boolean apply = false;
		String cpArg = "";
		for(int i = 1; i < args.length; i++)
		{
			if("--apply".equals(args[i]))
				apply = true;
			else if("--cp".equals(args[i]) && i + 1 < args.length)
				cpArg = args[++i];
		}

		Map<String, byte[]> entries = new LinkedHashMap<>();
		List<String> order = new ArrayList<>();
		try(ZipInputStream in = new ZipInputStream(Files.newInputStream(jar)))
		{
			ZipEntry e;
			while((e = in.getNextEntry()) != null)
			{
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				byte[] buf = new byte[8192];
				int n;
				while((n = in.read(buf)) > 0)
					bos.write(buf, 0, n);
				entries.put(e.getName(), bos.toByteArray());
				order.add(e.getName());
			}
		}

		// 类加载器：先在 jar 自身里找，再交给传入的 MC 类路径
		List<URL> urls = new ArrayList<>();
		urls.add(jar.toUri().toURL());
		for(String s : cpArg.split(File.pathSeparator))
		{
			if(s.isEmpty())
				continue;
			try
			{
				urls.add(Paths.get(s).toUri().toURL());
			}catch(Exception ignore)
			{
				// 不存在的路径跳过
			}
		}
		final URLClassLoader loader = new URLClassLoader(
			urls.toArray(new URL[0]), BariPatch.class.getClassLoader());

		int scanned = 0, hitClasses = 0, patchedSites = 0;
		List<String> report = new ArrayList<>();
		Map<String, byte[]> updated = new LinkedHashMap<>();

		for(String name : order)
		{
			if(!name.endsWith(".class"))
				continue;
			scanned++;

			ClassNode cn = new ClassNode();
			try
			{
				new ClassReader(entries.get(name)).accept(cn, 0);
			}catch(Exception ex)
			{
				continue;
			}

			int sites = 0;
			for(MethodNode mn : cn.methods)
				sites += patchMethod(mn);

			if(sites == 0)
				continue;

			hitClasses++;
			patchedSites += sites;
			report.add(name + " x" + sites);

			if(apply)
			{
				ClassWriter cw = new ClassWriter(
					ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS)
				{
					@Override
					protected ClassLoader getClassLoader()
					{
						return loader;
					}
				};
				cn.accept(cw);
				updated.put(name, cw.toByteArray());
			}
		}

		System.out.println("  scanned=" + scanned + "  hitClasses="
			+ hitClasses + "  sites=" + patchedSites);
		for(String r : report)
			System.out.println("    " + r);

		if(hitClasses == 0)
		{
			System.out.println("  CLEAN");
			return;
		}
		if(!apply)
		{
			System.out.println("  DETECT-ONLY (use --apply to rewrite)");
			return;
		}

		entries.putAll(updated);
		Path bak = jar.resolveSibling(jar.getFileName() + ".bak");
		if(!Files.exists(bak))
			Files.copy(jar, bak);

		try(ZipOutputStream out = new ZipOutputStream(
			Files.newOutputStream(jar, StandardOpenOption.TRUNCATE_EXISTING,
				StandardOpenOption.WRITE)))
		{
			for(String name : order)
			{
				out.putNextEntry(new ZipEntry(name));
				out.write(entries.get(name));
				out.closeEntry();
			}
		}

		System.out.println("  PATCHED " + jar);
	}

	/** 找 [GETFIELD ...Input;] + [INVOKEVIRTUAL getClass()] 并插入 null 防护。 */
	private static int patchMethod(MethodNode mn)
	{
		if(mn.instructions == null || mn.instructions.size() == 0)
			return 0;

		int count = 0;
		InsnList insns = mn.instructions;

		for(AbstractInsnNode cur = insns.getFirst(); cur != null;)
		{
			AbstractInsnNode next = cur.getNext();

			if(!(cur instanceof FieldInsnNode) || cur.getOpcode()
				!= Opcodes.GETFIELD || !INPUT_DESC
					.equals(((FieldInsnNode)cur).desc))
			{
				cur = next;
				continue;
			}

			AbstractInsnNode gc = next;
			if(!(gc instanceof MethodInsnNode) || gc.getOpcode()
				!= Opcodes.INVOKEVIRTUAL
				|| !"getClass".equals(((MethodInsnNode)gc).name))
			{
				cur = next;
				continue;
			}

			AbstractInsnNode ldc = gc.getNext();
			AbstractInsnNode cmp = ldc == null ? null : ldc.getNext();
			if(!(ldc instanceof LdcInsnNode)
				|| !(cmp instanceof JumpInsnNode))
			{
				cur = next;
				continue;
			}

			int op = cmp.getOpcode();
			if(op != Opcodes.IF_ACMPEQ && op != Opcodes.IF_ACMPNE)
			{
				cur = next;
				continue;
			}

			// null 的等价结果 = 「不等于我方 Input 子类」
			LabelNode notEqual;
			if(op == Opcodes.IF_ACMPEQ)
			{
				notEqual = new LabelNode();
				insns.insert(cmp, notEqual);
			}else
				notEqual = ((JumpInsnNode)cmp).label;

			//   GETFIELD input
			//   DUP
			//   IFNULL Lnull        <- 新入口，离线块，栈为 [Input]
			//   INVOKEVIRTUAL getClass()   <- 原有
			//   ...
			//   Lnull: POP; GOTO notEqual
			LabelNode lnull = new LabelNode();
			InsnList guard = new InsnList();
			guard.add(new InsnNode(Opcodes.DUP));
			guard.add(new JumpInsnNode(Opcodes.IFNULL, lnull));
			insns.insert(cur, guard);

			InsnList tail = new InsnList();
			tail.add(lnull);
			tail.add(new InsnNode(Opcodes.POP));
			tail.add(new JumpInsnNode(Opcodes.GOTO, notEqual));
			insns.add(tail);

			count++;
			cur = cmp;
		}

		return count;
	}
}
