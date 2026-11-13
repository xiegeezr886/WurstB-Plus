package net.wurstclient.tools.protector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import java.util.zip.CRC32;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import org.objectweb.asm.util.CheckClassAdapter;

/** Post-remap protection; never loads or executes classes from the input jar. */
public final class Protector implements Opcodes {
    static final String MARKER = "META-INF/wurstb-protection-v2.txt";
    private static final String TEMPLATE = StringBootstrap.class.getName().replace('.', '/');
    private static final String BOOTSTRAP_DESC =
        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;[Ljava/lang/String;)Ljava/lang/String;";
    private static final String CALL_BOOTSTRAP_DESC =
        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
        + "Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;I)Ljava/lang/invoke/CallSite;";
    private static final Pattern IDENTIFIER = Pattern.compile("\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*");
    private static final Set<String> SERIAL_HOOKS = Set.of("readObject", "writeObject",
        "readResolve", "writeReplace", "readObjectNoData", "$deserializeLambda$");
    private final SecureRandom random = new SecureRandom();
    private final byte[] key = new byte[32];
    private final byte[] noncePrefix = new byte[4];
    private long nonceCounter;
    private final ProtectionConfig config;
    private final Map<Member, String> names = new LinkedHashMap<>();
    private final Set<Member> renamedTargets = new HashSet<>();
    private final NumberMasker numbers = new NumberMasker(random);
    private final Map<String, String> skipped = new TreeMap<>();
    private final Set<String> reserved = new HashSet<>();
    private String helper;
    private int protectedClasses, encryptedStrings, retainedFieldStrings, chunkedStrings, indirectCalls;

    private record Member(String owner, String name, String descriptor) {}

    private Protector(ProtectionConfig config) {
        this.config = config;
        random.nextBytes(key);
        random.nextBytes(noncePrefix);
        reserved.addAll(config.keepMethods());
        reserved.addAll(SERIAL_HOOKS);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3)
            throw new IllegalArgumentException("Usage: protector <input.jar> <new-output.jar> [config.properties]");
        protect(Path.of(args[0]), Path.of(args[1]),
            args.length == 3 ? ProtectionConfig.load(Path.of(args[2])) : ProtectionConfig.defaults());
    }

    static void protect(Path input, Path output, ProtectionConfig config) throws Exception {
        Protector protector = new Protector(config);
        try {
            protector.run(input.toAbsolutePath().normalize(), output.toAbsolutePath().normalize());
        } finally {
            java.util.Arrays.fill(protector.key, (byte) 0);
        }
    }

    private void run(Path input, Path output) throws Exception {
        if (!Files.isRegularFile(input)) throw new IOException("Input jar not found: " + input);
        if (!output.toString().endsWith(".jar")) throw new IOException("Output must end in .jar");
        Path mapping = Path.of(output + ".mapping.tsv");
        Path report = Path.of(output + ".report.txt");
        for (Path path : List.of(output, mapping, report))
            if (Files.exists(path)) throw new IOException("Refusing to overwrite: " + path);

        Map<String, byte[]> entries = readJar(input);
        if (entries.containsKey(MARKER) || entries.containsKey("META-INF/wurstb-protection-v1.txt"))
            throw new IOException("Input is already protected");
        rejectSignatures(entries);
        if (entries.containsKey("module-info.class"))
            throw new IOException("Explicit outer JPMS modules require a dedicated module configuration");
        Map<String, ClassNode> classes = new LinkedHashMap<>();
        Set<String> uniqueClasses = new HashSet<>();
        for (var entry : entries.entrySet()) {
            if (!entry.getKey().endsWith(".class")) {
                if (isText(entry.getKey())) collectWords(new String(entry.getValue(), StandardCharsets.UTF_8));
                continue;
            }
            // Nested jars are opaque resources. Multi-release project classes need a separate mapping per version.
            ClassNode node = new ClassNode();
            new ClassReader(entry.getValue()).accept(node, 0);
            if (entry.getKey().startsWith("META-INF/versions/")) {
                if (config.includes(node.name)) throw new IOException("Multi-release project class is unsupported: " + node.name);
                continue;
            }
            if (!entry.getKey().equals(node.name + ".class"))
                throw new IOException("Class path/name mismatch: " + entry.getKey());
            if (!uniqueClasses.add(node.name)) throw new IOException("Duplicate class: " + node.name);
            classes.put(node.name, node);
            collectConstants(node);
        }
        helper = "net/wurstclient/protection/P" + token();
        while (entries.containsKey(helper + ".class")) helper += token();
        Set<String> selected = new HashSet<>();
        for (ClassNode node : classes.values()) {
            String reason = exclusion(node);
            if (reason != null) {
                if (node.name.startsWith("net/wurstclient/")) skipped.put(node.name, reason);
                continue;
            }
            selected.add(node.name);
            planNames(node);
        }
        if (selected.isEmpty()) throw new IOException("No eligible project classes found; check include/exclude");
        names.forEach((member, name) -> renamedTargets.add(new Member(member.owner, name, member.descriptor)));

        Remapper remapper = new Remapper(ASM9) {
            @Override public String mapMethodName(String owner, String name, String descriptor) {
                return names.getOrDefault(new Member(owner, name, descriptor), name);
            }
        };
        for (ClassNode source : classes.values()) {
            // Remap all callers, including nestmates and method handles outside the selected scope.
            ClassNode node = new ClassNode();
            source.accept(new ClassRemapper(node, remapper));
            if (selected.contains(node.name)) {
                transform(node);
                protectedClasses++;
            } else if (!referencesRenamedMethod(source)) {
                continue; // Keep third-party and Mixin bytecode byte-for-byte when no references need updating.
            }
            entries.put(node.name + ".class", checkedBytes(node));
        }
        if (encryptedStrings > 0 || indirectCalls > 0) entries.put(helper + ".class", bootstrapBytes());
        entries.put(MARKER, "WurstB+ Protector format 2\n".getBytes(StandardCharsets.UTF_8));

        Files.createDirectories(output.getParent());
        Path temp = Files.createTempFile(output.getParent(), ".protector-", ".tmp");
        boolean mappingCreated = false, reportCreated = false, committed = false;
        try {
            try (var zip = new ZipOutputStream(Files.newOutputStream(temp))) {
                for (var entry : entries.entrySet()) {
                    ZipEntry z = new ZipEntry(entry.getKey());
                    z.setTime(0);
                    zip.putNextEntry(z);
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }
            StringBuilder map = new StringBuilder("owner\toriginal\tdescriptor\tprotected\n");
            names.forEach((m, name) -> map.append(m.owner).append('\t').append(m.name)
                .append('\t').append(m.descriptor).append('\t').append(name).append('\n'));
            try (var stream = Files.newOutputStream(mapping, StandardOpenOption.CREATE_NEW)) {
                mappingCreated = true;
                stream.write(map.toString().getBytes(StandardCharsets.UTF_8));
            }
            String reportText = report(input, temp);
            try (var stream = Files.newOutputStream(report, StandardOpenOption.CREATE_NEW)) {
                reportCreated = true;
                stream.write(reportText.getBytes(StandardCharsets.UTF_8));
            }
            // Same filesystem rename, no replacement. A failed transform never publishes a partial jar.
            Files.move(temp, output);
            committed = true;
        } finally {
            Files.deleteIfExists(temp);
            if (!committed) {
                if (mappingCreated) Files.deleteIfExists(mapping);
                if (reportCreated) Files.deleteIfExists(report);
            }
        }
        System.out.printf("Protected %d classes, %d private methods, %d strings, %d indirect calls.%nOutput: %s%nReport: %s%n",
            protectedClasses, names.size(), encryptedStrings, indirectCalls, output, report);
    }

    private Map<String, byte[]> readJar(Path path) throws IOException {
        Map<String, byte[]> entries = new TreeMap<>();
        Set<String> seen = new HashSet<>();
        long total = 0;
        try (ZipFile zip = new ZipFile(path.toFile())) {
            var iterator = zip.entries();
            while (iterator.hasMoreElements()) {
                ZipEntry e = iterator.nextElement();
                String name = e.getName();
                String pathName = e.isDirectory() ? name.substring(0, name.length() - 1) : name;
                if (name.startsWith("/") || name.contains("\\") || name.contains(":")
                        || name.chars().anyMatch(c -> c < 32 || c == 127)
                        || java.util.Arrays.stream(pathName.split("/", -1))
                            .anyMatch(s -> s.isEmpty() || s.equals(".") || s.equals("..")))
                    throw new IOException("Unsafe jar entry: " + name);
                if (!seen.add(name)) throw new IOException("Duplicate jar entry: " + name);
                if (seen.size() > 100_000) throw new IOException("Too many jar entries");
                if (e.isDirectory()) continue;
                int limit = (int) Math.min(128L * 1024 * 1024, 512L * 1024 * 1024 - total);
                if (e.getSize() > limit) throw new IOException("Jar exceeds protection memory limits: " + name);
                byte[] data;
                try (var stream = zip.getInputStream(e)) {
                    data = stream.readNBytes(limit + 1);
                }
                total += data.length;
                if (data.length > limit)
                    throw new IOException("Jar exceeds protection memory limits: " + name);
                CRC32 crc = new CRC32();
                crc.update(data);
                if (e.getSize() != data.length || e.getCrc() != crc.getValue())
                    throw new IOException("Corrupt jar entry (size/CRC mismatch): " + name);
                entries.put(name, data);
            }
        }
        return entries;
    }

    private static void rejectSignatures(Map<String, byte[]> entries) throws IOException {
        for (String name : entries.keySet()) {
            String upper = name.toUpperCase(Locale.ROOT);
            if (upper.matches("META-INF/[^/]+\\.(SF|RSA|DSA|EC)") || upper.startsWith("META-INF/SIG-"))
                throw new IOException("Signed input must be protected before signing: " + name);
            if (upper.equals("META-INF/MANIFEST.MF")) {
                Manifest manifest = new Manifest(new java.io.ByteArrayInputStream(entries.get(name)));
                List<java.util.jar.Attributes> attributes = new ArrayList<>(manifest.getEntries().values());
                attributes.add(manifest.getMainAttributes());
                for (var attrs : attributes)
                    for (Object key : attrs.keySet())
                        if (key.toString().toLowerCase(Locale.ROOT).contains("digest"))
                            throw new IOException("Input contains signing digests; protect before signing");
            }
        }
    }

    private String exclusion(ClassNode node) {
        if (!config.includes(node.name)) return "outside scope / compatibility exclusion";
        if (hasAnnotation(node.visibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;")
                || hasAnnotation(node.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;"))
            return "Mixin";
        if (node.methods.stream().anyMatch(m -> (m.access & ACC_NATIVE) != 0)) return "JNI/native class";
        if ((node.version & 0xffff) < V11) return "class version older than Java 11";
        return null;
    }

    private static boolean hasAnnotation(List<AnnotationNode> list, String descriptor) {
        return list != null && list.stream().anyMatch(a -> a.desc.equals(descriptor));
    }

    private void collectWords(String text) {
        var matcher = IDENTIFIER.matcher(text);
        while (matcher.find()) reserved.add(matcher.group());
    }

    private static boolean isText(String name) {
        return name.endsWith(".json") || name.endsWith(".toml") || name.endsWith(".cfg")
            || name.endsWith(".properties") || name.endsWith(".xml") || name.endsWith(".mf")
            || name.endsWith(".MF") || name.startsWith("META-INF/services/");
    }

    private void collectConstant(Object value) {
        if (value instanceof String s) collectWords(s);
        if (value instanceof AnnotationNode a) collectConstant(a.values);
        if (value instanceof Iterable<?> values) values.forEach(this::collectConstant);
        if (value instanceof Object[] values)
            for (Object item : values) collectConstant(item);
        if (value instanceof ConstantDynamic c)
            for (int i = 0; i < c.getBootstrapMethodArgumentCount(); i++)
                collectConstant(c.getBootstrapMethodArgument(i));
    }

    private void collectConstants(ClassNode node) {
        collectConstant(node.visibleAnnotations);
        collectConstant(node.invisibleAnnotations);
        collectConstant(node.visibleTypeAnnotations);
        collectConstant(node.invisibleTypeAnnotations);
        node.fields.forEach(f -> {
            collectConstant(f.value);
            collectConstant(f.visibleAnnotations);
            collectConstant(f.invisibleAnnotations);
            collectConstant(f.visibleTypeAnnotations);
            collectConstant(f.invisibleTypeAnnotations);
        });
        if (node.recordComponents != null) node.recordComponents.forEach(r -> {
            collectConstant(r.visibleAnnotations);
            collectConstant(r.invisibleAnnotations);
        });
        node.methods.forEach(m -> {
            collectConstant(m.annotationDefault);
            collectConstant(m.visibleAnnotations);
            collectConstant(m.invisibleAnnotations);
            collectConstant(m.visibleTypeAnnotations);
            collectConstant(m.invisibleTypeAnnotations);
            collectConstant(m.visibleParameterAnnotations);
            collectConstant(m.invisibleParameterAnnotations);
            m.instructions.forEach(i -> {
                if (i instanceof LdcInsnNode ldc) collectConstant(ldc.cst);
                if (i instanceof InvokeDynamicInsnNode indy)
                    for (Object arg : indy.bsmArgs) collectConstant(arg);
            });
        });
    }

    private void planNames(ClassNode node) {
        if (!config.renamePrivateMethods() || (node.access & (ACC_ENUM | ACC_RECORD)) != 0) return;
        // SerializedLambda stores implementation names. Its compiler-generated deserializer compares them.
        if (node.methods.stream().anyMatch(m -> m.name.equals("$deserializeLambda$"))) return;
        Set<String> used = new HashSet<>();
        node.methods.forEach(m -> used.add(m.name));
        for (MethodNode method : node.methods) {
            if ((method.access & ACC_PRIVATE) == 0 || (method.access & (ACC_NATIVE | ACC_ABSTRACT)) != 0
                    || method.name.startsWith("<") || reserved.contains(method.name)
                    || method.visibleAnnotations != null || method.invisibleAnnotations != null) continue;
            String name;
            do { name = "p" + token(); } while (!used.add(name));
            names.put(new Member(node.name, method.name, method.desc), name);
        }
    }

    private boolean referencesRenamedMethod(ClassNode source) {
        // A dry remap sees direct calls, bootstrap handles, condy and EnclosingMethod metadata.
        boolean[] changed = {false};
        source.accept(new ClassRemapper(new ClassNode(), new Remapper(ASM9) {
            @Override public String mapMethodName(String owner, String name, String desc) {
                if (names.containsKey(new Member(owner, name, desc))) changed[0] = true;
                return name;
            }
        }));
        return changed[0];
    }

    private void transform(ClassNode node) throws GeneralSecurityException {
        Map<String, ConstantDynamic> constants = new HashMap<>();
        if (config.stripDebug()) { node.sourceFile = null; node.sourceDebug = null; }
        node.fields.forEach(f -> { if (f.value instanceof String) retainedFieldStrings++; });
        for (MethodNode method : node.methods) {
            if (config.stripDebug()) {
                method.localVariables = null;
                // Local-variable type annotations refer to the removed table/ranges and reveal local types.
                method.visibleLocalVariableAnnotations = null;
                method.invisibleLocalVariableAnnotations = null;
                // Retain MethodParameters: reflection-based APIs can depend on them.
            }
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (config.stripDebug() && insn instanceof LineNumberNode) {
                    method.instructions.remove(insn);
                } else if (config.encryptStrings() && insn instanceof LdcInsnNode ldc && ldc.cst instanceof String s) {
                    ldc.cst = encrypted(node.name, s, constants);
                } else if (config.encryptStrings() && insn instanceof InvokeDynamicInsnNode indy
                        && indy.bsm.getOwner().equals("java/lang/invoke/StringConcatFactory")) {
                    // javac stores concat recipes outside LDC instructions; protect those strings too.
                    for (int i = 0; i < indy.bsmArgs.length; i++)
                        if (indy.bsmArgs[i] instanceof String s) indy.bsmArgs[i] = encrypted(node.name, s, constants);
                } else if (config.indirectPrivateCalls() && insn instanceof MethodInsnNode call
                        && renamedTargets.contains(new Member(call.owner, call.name, call.desc))) {
                    indirect(node, method, call, constants);
                } else if (config.maskNumbers()) {
                    InsnList replacement = numbers.replacement(insn);
                    if (replacement == null) continue;
                    method.instructions.insertBefore(insn, replacement);
                    method.instructions.remove(insn);
                }
            }
        }
    }

    private void indirect(ClassNode caller, MethodNode method, MethodInsnNode call,
            Map<String, ConstantDynamic> constants) throws GeneralSecurityException {
        boolean isStatic = call.getOpcode() == INVOKESTATIC;
        String descriptor = call.desc;
        if (!isStatic) {
            Type[] original = Type.getArgumentTypes(call.desc);
            Type[] arguments = new Type[original.length + 1];
            arguments[0] = Type.getObjectType(call.owner);
            System.arraycopy(original, 0, arguments, 1, original.length);
            descriptor = Type.getMethodDescriptor(Type.getReturnType(call.desc), arguments);
        }
        Object target = config.encryptStrings() ? encrypted(caller.name, call.name, constants) : call.name;
        method.instructions.set(call, new InvokeDynamicInsnNode("c" + token(), descriptor,
            new Handle(H_INVOKESTATIC, helper, "link", CALL_BOOTSTRAP_DESC, false),
            Type.getObjectType(call.owner), target, Type.getMethodType(call.desc), isStatic ? 0 : 1));
        indirectCalls++;
    }

    private Object encrypted(String owner, String text, Map<String, ConstantDynamic> constants)
            throws GeneralSecurityException {
        if (text.isEmpty()) return text;
        if (constants.containsKey(text)) return constants.get(text);
        String constantName = "s" + token();
        byte[] plain = new byte[text.length() * 2];
        for (int i = 0; i < text.length(); i++) {
            plain[i * 2] = (byte) (text.charAt(i) >>> 8);
            plain[i * 2 + 1] = (byte) text.charAt(i);
        }
        // Unique nonces under this build's key (random prefix + monotonic 64-bit counter).
        byte[] nonce = java.nio.ByteBuffer.allocate(12).put(noncePrefix).putLong(nonceCounter++).array();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(StringBootstrap.aad(owner.replace('/', '.'), constantName));
        byte[] ciphertext = cipher.doFinal(plain);
        java.util.Arrays.fill(plain, (byte) 0);
        byte[] packed = java.nio.ByteBuffer.allocate(nonce.length + ciphertext.length)
            .put(nonce).put(ciphertext).array();
        String encoded = Base64.getEncoder().encodeToString(packed);
        // Split ciphertext, not plaintext. The varargs bootstrap authenticates the entire joined payload.
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < encoded.length(); start += 60000)
            chunks.add(encoded.substring(start, Math.min(start + 60000, encoded.length())));
        if (chunks.size() > 1) chunkedStrings++;
        ConstantDynamic constant = new ConstantDynamic(constantName, "Ljava/lang/String;",
            new Handle(H_INVOKESTATIC, helper, "resolve", BOOTSTRAP_DESC, false),
            chunks.toArray());
        constants.put(text, constant);
        encryptedStrings++;
        return constant;
    }

    private byte[] bootstrapBytes() throws Exception {
        byte[] share = new byte[32];
        random.nextBytes(share);
        byte[] second = key.clone();
        for (int i = 0; i < second.length; i++) second[i] ^= share[i];
        ClassNode template = new ClassNode();
        try (var input = StringBootstrap.class.getResourceAsStream("/" + TEMPLATE + ".class")) {
            if (input == null) throw new IOException("Missing bootstrap template");
            new ClassReader(input).accept(template, ClassReader.SKIP_DEBUG);
        }
        for (MethodNode method : template.methods)
            for (AbstractInsnNode insn : method.instructions)
                if (insn instanceof LdcInsnNode ldc) {
                    if ("WURST_KEY_SHARE_A".equals(ldc.cst)) ldc.cst = Base64.getEncoder().encodeToString(share);
                    if ("WURST_KEY_SHARE_B".equals(ldc.cst)) ldc.cst = Base64.getEncoder().encodeToString(second);
                }
        ClassNode renamed = new ClassNode();
        template.accept(new ClassRemapper(renamed, new Remapper(ASM9) {
            @Override public String map(String name) { return name.equals(TEMPLATE) ? helper : name; }
        }));
        return checkedBytes(renamed);
    }

    private static byte[] checkedBytes(ClassNode node) throws Exception {
        // Rebuild the constant pool from scratch: reusing ClassReader's pool would retain plaintext.
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(new CheckClassAdapter(writer, false));
        byte[] result = writer.toByteArray(); // Method/class size overflow is fatal, never silently downgraded.
        ClassNode check = new ClassNode();
        new ClassReader(result).accept(check, 0);
        for (MethodNode method : check.methods)
            if ((method.access & (ACC_ABSTRACT | ACC_NATIVE)) == 0)
                try { new Analyzer<>(new BasicVerifier()).analyze(check.name, method); }
                catch (Exception e) { throw new IOException("Invalid transformed method: " + check.name + "." + method.name, e); }
        return result;
    }

    private String token() {
        byte[] bytes = new byte[12];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String report(Path input, Path output) throws Exception {
        StringBuilder report = new StringBuilder("WurstB+ Protector v2\n")
            .append("input.sha256=").append(sha256(input)).append('\n')
            .append("output.sha256=").append(sha256(output)).append('\n')
            .append("classes.protected=").append(protectedClasses).append('\n')
            .append("methods.renamed=").append(names.size()).append('\n')
            .append("strings.encrypted=").append(encryptedStrings).append('\n')
            .append("calls.indirect=").append(indirectCalls).append('\n')
            .append("integers.masked=").append(numbers.integers).append('\n')
            .append("longs.masked=").append(numbers.longs).append('\n')
            .append("floats.masked=").append(numbers.floats).append('\n')
            .append("doubles.masked=").append(numbers.doubles).append('\n')
            .append("strings.encrypted.chunked=").append(chunkedStrings).append('\n')
            .append("strings.retained.fieldConstantValues=").append(retainedFieldStrings).append('\n')
            .append("config=").append(config).append('\n')
            .append("Class/field/public API names, annotations and resources are retained.\n")
            .append("Encrypted strings include indirect-call target names. Call owner/types remain visible.\n")
            .append("AES protects stored literals, not secrets from a debugger. Keys ship in the jar.\n")
            .append("SHA-256 is an audit checksum, not an authenticated signature.\n")
            .append("Mapping is private build output. Do not include it in the release jar.\n\nExcluded project classes:\n");
        skipped.forEach((name, reason) -> report.append(name).append(" : ").append(reason).append('\n'));
        return report.toString();
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var stream = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = stream.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
