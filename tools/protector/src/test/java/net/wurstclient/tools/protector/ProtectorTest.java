package net.wurstclient.tools.protector;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;

class ProtectorTest implements Opcodes {
    @TempDir Path temp;
    private static final String SAMPLE = "net.wurstclient.fixture.Sample";

    @Test void protectedCodeExecutesWithFullJvmVerification() throws Exception {
        Path input = fixture(), output = temp.resolve("protected.jar");
        byte[] before = Files.readAllBytes(input);
        Protector.protect(input, output, ProtectionConfig.defaults());
        assertArrayEquals(before, Files.readAllBytes(input));
        runSample(output);
        Map<String, byte[]> original = read(input), result = read(output);
        for (String resource : List.of("fabric.mod.json", "wurst.mixins.json", "META-INF/mods.toml",
                "META-INF/jarjar/dependency.jar", "LICENSE.txt", "assets/native.dll"))
            assertArrayEquals(original.get(resource), result.get(resource), resource);
        assertArrayEquals(original.get("net/wurstclient/fixture/NativeApi.class"),
            result.get("net/wurstclient/fixture/NativeApi.class"));
        assertArrayEquals(original.get("net/wurstclient/fixture/MixinTarget.class"),
            result.get("net/wurstclient/fixture/MixinTarget.class"));
        byte[] sample = result.get(SAMPLE.replace('.', '/') + ".class");
        String pool = new String(sample, StandardCharsets.ISO_8859_1);
        assertFalse(pool.contains("literal-only-secret"));
        assertFalse(pool.contains("concat-recipe-secret:"));
        assertFalse(pool.contains("hiddenCalculation"));
        ClassNode node = new ClassNode();
        new ClassReader(sample).accept(node, 0);
        assertNull(node.sourceFile);
        assertTrue(node.methods.stream().allMatch(m -> m.localVariables == null || m.localVariables.isEmpty()));
        assertTrue(result.containsKey(Protector.MARKER));
        assertTrue(result.keySet().stream().noneMatch(n -> n.endsWith(".tsv") || n.endsWith(".report.txt")));
        assertTrue(Files.readString(Path.of(output + ".mapping.tsv")).contains("hiddenCalculation"));
        assertTrue(Files.readString(Path.of(output + ".report.txt")).contains("output.sha256="));
    }

    @Test void buildsUseFreshKeysNamesAndNonces() throws Exception {
        Path input = fixture(), a = temp.resolve("a.jar"), b = temp.resolve("b.jar");
        Protector.protect(input, a, ProtectionConfig.defaults());
        Protector.protect(input, b, ProtectionConfig.defaults());
        assertFalse(Arrays.equals(Files.readAllBytes(a), Files.readAllBytes(b)));
        assertNotEquals(Files.readString(Path.of(a + ".mapping.tsv")), Files.readString(Path.of(b + ".mapping.tsv")));
        runSample(b);
    }

    @Test void excludedNestmateStillGetsUpdatedPrivateCalls() throws Exception {
        Path input = fixture(), output = temp.resolve("nest.jar");
        ProtectionConfig config = new ProtectionConfig(List.of("net/wurstclient/"),
            List.of("net/wurstclient/fixture/Sample$Peer"), true, true, true, true, true, Set.of());
        Protector.protect(input, output, config);
        runSample(output);
    }

    @Test void tamperedCiphertextFailsAuthentication() throws Exception {
        Path input = fixture(), output = temp.resolve("protected.jar");
        Protector.protect(input, output, ProtectionConfig.defaults());
        Map<String, byte[]> entries = read(output);
        String path = SAMPLE.replace('.', '/') + ".class";
        ClassNode node = new ClassNode();
        new ClassReader(entries.get(path)).accept(node, 0);
        boolean changed = false;
        for (var method : node.methods) if (method.name.equals("secret"))
            for (var insn : method.instructions) if (insn instanceof LdcInsnNode ldc
                    && ldc.cst instanceof ConstantDynamic c) {
                byte[] packed = java.util.Base64.getDecoder().decode((String) c.getBootstrapMethodArgument(0));
                packed[packed.length - 1] ^= 1;
                ldc.cst = new ConstantDynamic(c.getName(), c.getDescriptor(), c.getBootstrapMethod(),
                    java.util.Base64.getEncoder().encodeToString(packed));
                changed = true;
            }
        assertTrue(changed);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        entries.put(path, writer.toByteArray());
        Path tampered = temp.resolve("tampered.jar");
        write(tampered, entries);
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{tampered.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            var method = loader.loadClass(SAMPLE).getMethod("secret");
            var error = assertThrows(InvocationTargetException.class, () -> method.invoke(null));
            assertInstanceOf(BootstrapMethodError.class, error.getCause());
        }
    }

    @Test void refusesOverwriteReprotectionSignedAndEmptyInputs() throws Exception {
        Path input = fixture(), output = temp.resolve("protected.jar");
        Protector.protect(input, output, ProtectionConfig.defaults());
        byte[] old = Files.readAllBytes(output);
        assertThrows(IOException.class, () -> Protector.protect(input, output, ProtectionConfig.defaults()));
        assertArrayEquals(old, Files.readAllBytes(output));
        assertThrows(IOException.class, () -> Protector.protect(input, input, ProtectionConfig.defaults()));
        Path second = temp.resolve("second.jar");
        assertThrows(IOException.class, () -> Protector.protect(output, second, ProtectionConfig.defaults()));
        assertFalse(Files.exists(second));
        Map<String, byte[]> entries = read(input);
        entries.put("META-INF/wurstb-protection-v1.txt", new byte[0]);
        Path legacy = temp.resolve("legacy.jar");
        write(legacy, entries);
        assertThrows(IOException.class, () -> Protector.protect(legacy, second, ProtectionConfig.defaults()));
        entries.remove("META-INF/wurstb-protection-v1.txt");
        entries.put("META-INF/TEST.RSA", new byte[]{1});
        Path signed = temp.resolve("signed.jar");
        write(signed, entries);
        assertThrows(IOException.class, () -> Protector.protect(signed, second, ProtectionConfig.defaults()));
        entries.remove("META-INF/TEST.RSA");
        entries.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nSHA-256-Digest-Manifest: abc\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        write(signed, entries);
        assertThrows(IOException.class, () -> Protector.protect(signed, second, ProtectionConfig.defaults()));
        Path empty = temp.resolve("empty.jar");
        write(empty, Map.of("LICENSE.txt", new byte[0]));
        assertThrows(IOException.class, () -> Protector.protect(empty, second, ProtectionConfig.defaults()));
        assertFalse(Files.exists(Path.of(second + ".mapping.tsv")));
    }

    @Test void rejectsUnsafePathsAndOwnMultiReleaseClasses() throws Exception {
        Path input = temp.resolve("unsafe.jar"), output = temp.resolve("out.jar");
        for (String name : List.of("../escape", "a/./b", "a//b", "/root", "a\\b", "a\u0000b", "./")) {
            write(input, Map.of(name, new byte[]{1}));
            assertThrows(IOException.class, () -> Protector.protect(input, output, ProtectionConfig.defaults()), name);
        }
        Map<String, byte[]> entries = read(fixture());
        entries.put("META-INF/versions/17/net/wurstclient/fixture/Sample.class",
            entries.get("net/wurstclient/fixture/Sample.class"));
        Path multi = temp.resolve("multi.jar");
        write(multi, entries);
        assertThrows(IOException.class, () -> Protector.protect(multi, output, ProtectionConfig.defaults()));
        assertFalse(Files.exists(output));
    }

    @Test void codeSizeOverflowDoesNotPublishUnprotectedFallback() throws Exception {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(V17, ACC_PUBLIC, "net/wurstclient/Huge", null, "java/lang/Object", null);
        var method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "large", "()V", null, null);
        method.visitCode();
        for (int i = 0; i < 16000; i++) { method.visitInsn(ICONST_1); method.visitInsn(POP); }
        method.visitInsn(RETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        Path input = temp.resolve("large.jar"), output = temp.resolve("large-out.jar");
        write(input, Map.of("net/wurstclient/Huge.class", writer.toByteArray()));
        assertThrows(org.objectweb.asm.MethodTooLargeException.class,
            () -> Protector.protect(input, output, ProtectionConfig.defaults()));
        assertFalse(Files.exists(output));
        assertFalse(Files.exists(Path.of(output + ".mapping.tsv")));
    }

    @Test void strictConfigurationAndDisabledPasses() throws Exception {
        Path configFile = temp.resolve("config.properties");
        Files.writeString(configFile, "encryptString=true\n");
        assertThrows(IOException.class, () -> ProtectionConfig.load(configFile));
        Files.writeString(configFile, "encryptStrings=tru\n");
        assertThrows(IOException.class, () -> ProtectionConfig.load(configFile));
        Files.writeString(configFile, "include=\n");
        assertThrows(IOException.class, () -> ProtectionConfig.load(configFile));
        Files.writeString(configFile, "maskNumbers=true\nmaskIntegers=false\n");
        assertThrows(IOException.class, () -> ProtectionConfig.load(configFile));
        Files.writeString(configFile, "indirectPrivateCalls=maybe\n");
        assertThrows(IOException.class, () -> ProtectionConfig.load(configFile));
        Files.writeString(configFile, "encryptStrings=false\nrenamePrivateMethods=false\nmaskIntegers=false\nstripDebug=false\n");
        Path output = temp.resolve("disabled.jar");
        Protector.protect(fixture(), output, ProtectionConfig.load(configFile));
        runSample(output);
        assertTrue(read(output).keySet().stream().noneMatch(n -> n.startsWith("net/wurstclient/protection/")));
    }

    @Test void maximumLengthStringIsEncryptedInChunksAndExecutes() throws Exception {
        String text = "x".repeat(65535);
        ClassWriter writer = new ClassWriter(0);
        writer.visit(V17, ACC_PUBLIC, "net/wurstclient/LongLiteral", null, "java/lang/Object", null);
        var method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "value", "()Ljava/lang/String;", null, null);
        method.visitCode();
        method.visitLdcInsn(text);
        method.visitInsn(ARETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        Path input = temp.resolve("long.jar"), output = temp.resolve("long-out.jar");
        write(input, Map.of("net/wurstclient/LongLiteral.class", writer.toByteArray()));
        Protector.protect(input, output, ProtectionConfig.defaults());
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            assertEquals(text, loader.loadClass("net.wurstclient.LongLiteral").getMethod("value").invoke(null));
        }
        assertFalse(new String(read(output).get("net/wurstclient/LongLiteral.class"),
            StandardCharsets.ISO_8859_1).contains("x".repeat(100)));
        assertTrue(Files.readString(Path.of(output + ".report.txt")).contains("strings.encrypted.chunked=1"));
    }

    @Test void swappingCiphertextsWithinSameClassFailsAuthentication() throws Exception {
        Path output = temp.resolve("protected.jar");
        Protector.protect(fixture(), output, ProtectionConfig.defaults());
        Map<String, byte[]> entries = read(output);
        String path = SAMPLE.replace('.', '/') + ".class";
        ClassNode node = new ClassNode();
        new ClassReader(entries.get(path)).accept(node, 0);
        LdcInsnNode first = null, second = null;
        for (var method : node.methods)
            for (var insn : method.instructions)
                if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof ConstantDynamic) {
                    if (method.name.equals("secret")) first = ldc;
                    if (method.name.equals("secondSecret")) second = ldc;
                }
        assertNotNull(first);
        assertNotNull(second);
        ConstantDynamic a = (ConstantDynamic) first.cst, b = (ConstantDynamic) second.cst;
        Object[] payload = new Object[b.getBootstrapMethodArgumentCount()];
        for (int i = 0; i < payload.length; i++) payload[i] = b.getBootstrapMethodArgument(i);
        // Keep the receiving constant's identity. Valid GCM from another constant is not valid here.
        first.cst = new ConstantDynamic(a.getName(), a.getDescriptor(), a.getBootstrapMethod(), payload);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        entries.put(path, writer.toByteArray());
        Path swapped = temp.resolve("swapped.jar");
        write(swapped, entries);
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{swapped.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            var method = loader.loadClass(SAMPLE).getMethod("secret");
            var error = assertThrows(InvocationTargetException.class, () -> method.invoke(null));
            assertInstanceOf(BootstrapMethodError.class, error.getCause());
        }
    }

    @Test void indirectCallsWorkWithAndWithoutStringEncryptionAndCanBeDisabled() throws Exception {
        Path input = fixture();
        for (boolean indirect : List.of(true, false)) {
            ProtectionConfig config = new ProtectionConfig(List.of("net/wurstclient/"),
                List.of(), false, true, true, true, indirect, Set.of());
            Path output = temp.resolve("calls-" + indirect + ".jar");
            Protector.protect(input, output, config);
            runSample(output);
            ClassNode node = new ClassNode();
            new ClassReader(read(output).get(SAMPLE.replace('.', '/') + ".class")).accept(node, 0);
            long routed = node.methods.stream().flatMap(m -> Arrays.stream(m.instructions.toArray()))
                .filter(i -> i instanceof org.objectweb.asm.tree.InvokeDynamicInsnNode indy
                    && indy.bsm.getOwner().startsWith("net/wurstclient/protection/")).count();
            assertEquals(indirect, routed > 0);
            assertTrue(Files.readString(Path.of(output + ".report.txt")).contains("strings.encrypted=0"));
        }
    }

    @Test void masksLongAndFloatingPointConstantsWithoutChangingRawBits() throws Exception {
        Number[] values = {Long.MIN_VALUE, Long.MAX_VALUE, -1L, 0L, 1L,
            0.0f, -0.0f, 1.0f, 2.0f, Float.MIN_VALUE, Float.MAX_VALUE,
            Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.intBitsToFloat(0x7fc00123),
            0.0d, -0.0d, 1.0d, Double.MIN_VALUE, Double.MAX_VALUE,
            Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.longBitsToDouble(0x7ff8000012345678L)};
        ClassWriter writer = new ClassWriter(0);
        writer.visit(V17, ACC_PUBLIC, "net/wurstclient/Numeric", null, "java/lang/Object", null);
        for (int i = 0; i < values.length; i++) {
            Number n = values[i];
            String descriptor = n instanceof Float ? "()F" : n instanceof Double ? "()D" : "()J";
            var method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "value" + i, descriptor, null, null);
            method.visitCode();
            if (i == 3) method.visitInsn(LCONST_0);
            else if (i == 4) method.visitInsn(LCONST_1);
            else if (i == 5) method.visitInsn(FCONST_0);
            else if (i == 7) method.visitInsn(FCONST_1);
            else if (i == 8) method.visitInsn(FCONST_2);
            else if (i == 14) method.visitInsn(DCONST_0);
            else if (i == 16) method.visitInsn(DCONST_1);
            else method.visitLdcInsn(n);
            method.visitInsn(n instanceof Float ? FRETURN : n instanceof Double ? DRETURN : LRETURN);
            method.visitMaxs(2, 0);
            method.visitEnd();
        }
        writer.visitEnd();
        Path input = temp.resolve("numbers.jar"), output = temp.resolve("numbers-out.jar");
        write(input, Map.of("net/wurstclient/Numeric.class", writer.toByteArray()));
        Protector.protect(input, output, ProtectionConfig.defaults());
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            Class<?> type = loader.loadClass("net.wurstclient.Numeric");
            for (int i = 0; i < values.length; i++) {
                Object actual = type.getMethod("value" + i).invoke(null);
                if (values[i] instanceof Float f)
                    assertEquals(Float.floatToRawIntBits(f), Float.floatToRawIntBits((Float) actual));
                else if (values[i] instanceof Double d)
                    assertEquals(Double.doubleToRawLongBits(d), Double.doubleToRawLongBits((Double) actual));
                else assertEquals(values[i], actual);
            }
        }
        String report = Files.readString(Path.of(output + ".report.txt"));
        assertTrue(report.contains("longs.masked=5"));
        assertTrue(report.contains("floats.masked=9"));
        assertTrue(report.contains("doubles.masked=8"));
    }

    @Test void rejectsCorruptCrcAndOversizedDeclaredEntriesBeforePublishing() throws Exception {
        Path input = temp.resolve("corrupt.jar"), output = temp.resolve("rejected.jar");
        byte[] payload = {1, 2, 3, 4};
        var crc = new java.util.zip.CRC32();
        crc.update(payload);
        try (var out = new ZipOutputStream(Files.newOutputStream(input))) {
            ZipEntry entry = new ZipEntry("resource.bin");
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(payload.length);
            entry.setCrc(crc.getValue());
            out.putNextEntry(entry);
            out.write(payload);
            out.closeEntry();
        }
        byte[] original = Files.readAllBytes(input), damaged = original.clone();
        var buffer = java.nio.ByteBuffer.wrap(damaged).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int dataStart = 30 + Short.toUnsignedInt(buffer.getShort(26)) + Short.toUnsignedInt(buffer.getShort(28));
        damaged[dataStart] ^= 1;
        Files.write(input, damaged);
        var error = assertThrows(IOException.class, () -> Protector.protect(input, output, ProtectionConfig.defaults()));
        assertTrue(error.getMessage().contains("CRC"), error.getMessage());
        assertFalse(Files.exists(output));
        buffer = java.nio.ByteBuffer.wrap(original).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int central = dataStart + payload.length;
        assertEquals(0x02014b50, buffer.getInt(central));
        buffer.putInt(central + 24, 128 * 1024 * 1024 + 1);
        Files.write(input, original);
        error = assertThrows(IOException.class, () -> Protector.protect(input, output, ProtectionConfig.defaults()));
        assertTrue(error.getMessage().contains("memory limits"), error.getMessage());
        assertFalse(Files.exists(output));
    }

    private Path fixture() throws Exception {
        Path source = temp.resolve("src"), classes = temp.resolve("classes");
        Files.createDirectories(source);
        Files.createDirectories(classes);
        Map<String, String> sources = Map.of(
            "Sample.java", """
                package net.wurstclient.fixture;
                import java.io.*;
                import java.util.function.IntSupplier;
                import java.util.function.Supplier;
                @Lookup("metadataHook")
                public class Sample {
                    public static final String API_CONSTANT = "public-constant";
                    public static final int INITIALIZED = initializationValue();
                    private static int initializationValue() { return 91; }
                    private int state;
                    private static int hiddenCalculation(int x) { return x ^ Integer.MIN_VALUE; }
                    private static int hiddenCalculation() { return 17; }
                    private synchronized long instanceCalculation(long base, double step, Object[] array) {
                        if (!Thread.holdsLock(this)) throw new AssertionError("monitor");
                        return base + (long) step + array.length;
                    }
                    private void incrementState() { state++; }
                    private static int recurseSum(int n) { return n == 0 ? 0 : n + recurseSum(n - 1); }
                    private static void throwChecked() throws IOException { throw new IOException("checked-value"); }
                    private static String concurrentValue() { return "concurrent-result"; }
                    private static String reflectionHook() { return "reflection-ok"; }
                    private static String 中文方法() { return "unicode-reflection-ok"; }
                    private static String metadataHook() { return "metadata-ok"; }
                    @Deprecated private static String annotatedHook() { return "annotation-ok"; }
                    public static String secret() { return "literal-only-secret"; }
                    public static String secondSecret() { return "second-secret-value"; }
                    public static String concat(int n) { return "concat-recipe-secret:" + n; }
                    public static class Peer {
                        public static int call() { return hiddenCalculation(123); }
                        public static long instanceCall(Sample value) { return value.instanceCalculation(40L, 1.0d, new Object[1]); }
                    }
                    public interface Arithmetic {
                        private int privateAdd(int n) { return n + 9; }
                        private static long privateScale(long n) { return n * 2; }
                        default int run(int n) { return privateAdd(n); }
                        static long runStatic(long n) { return privateScale(n); }
                    }
                    public static class Parent {
                        final int x;
                        Parent(int x) { this.x = x; }
                    }
                    public static class Child extends Parent {
                        private static int constructorValue() { return 33; }
                        Child() { super(constructorValue()); }
                    }
                    public static class Serialized {
                        public static Supplier<String> supplier() {
                            return (Supplier<String> & Serializable) () -> "serialized-lambda-value";
                        }
                    }
                    public record Value(int number, String label) {}
                    public enum Choice { A, B }
                    public static void main(String[] args) throws Exception {
                        if (INITIALIZED != 91 || new Child().x != 33) throw new AssertionError("initialization");
                        if (Peer.call() != (123 ^ Integer.MIN_VALUE)) throw new AssertionError("nestmate");
                        Sample instance = new Sample();
                        if (Peer.instanceCall(instance) != 42) throw new AssertionError("private instance");
                        instance.incrementState();
                        if (instance.state != 1 || recurseSum(10) != 55) throw new AssertionError("void/recursion");
                        try { ((Sample) null).instanceCalculation(1L, 1.0d, new Object[0]); throw new AssertionError("null receiver"); }
                        catch (NullPointerException expected) {}
                        try { throwChecked(); throw new AssertionError("missing exception"); }
                        catch (IOException expected) { if (!expected.getMessage().equals("checked-value")) throw expected; }
                        Arithmetic arithmetic = new Arithmetic() {};
                        if (arithmetic.run(3) != 12 || Arithmetic.runStatic(3L) != 6L) throw new AssertionError("private interface");
                        var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
                        try {
                            var jobs = new java.util.ArrayList<java.util.concurrent.Future<String>>();
                            for (int i = 0; i < 16; i++) jobs.add(pool.submit(() -> concurrentValue()));
                            for (var job : jobs) if (!job.get().equals("concurrent-result")) throw new AssertionError("concurrent bootstrap");
                        } finally { pool.shutdownNow(); }
                        IntSupplier lambda = Sample::hiddenCalculation;
                        if (lambda.getAsInt() != 17) throw new AssertionError("lambda");
                        if (secret() != "literal-only-secret") throw new AssertionError("interning");
                        if (!concat(42).equals("concat-recipe-secret:42")) throw new AssertionError("concat");
                        if (!Sample.class.getDeclaredMethod("reflectionHook").invoke(null).equals("reflection-ok"))
                            throw new AssertionError("reflection");
                        if (!Sample.class.getDeclaredMethod("中文方法").invoke(null).equals("unicode-reflection-ok"))
                            throw new AssertionError("unicode reflection");
                        if (!Sample.class.getDeclaredMethod(Sample.class.getAnnotation(Lookup.class).value())
                                .invoke(null).equals("metadata-ok")) throw new AssertionError("annotation reflection");
                        if (!Sample.class.getDeclaredMethod("annotatedHook").isAnnotationPresent(Deprecated.class))
                            throw new AssertionError("annotation");
                        String text = "中文\\u0000😀\\ud800";
                        if (text.length() != 6 || text.charAt(5) != 0xd800) throw new AssertionError("UTF-16");
                        int[] numbers = {-1, 0, 1, 5, 6, 127, -128, 32767, -32768, Integer.MIN_VALUE, Integer.MAX_VALUE};
                        if (numbers[9] != -2147483648 || numbers[10] != 2147483647) throw new AssertionError("integers");
                        Value value = new Value(7, "record-value");
                        if (!value.equals(new Value(7, "record-value")) || !value.toString().contains("record-value"))
                            throw new AssertionError("record");
                        if (Choice.valueOf("B") != Choice.B) throw new AssertionError("enum");
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) { out.writeObject(Serialized.supplier()); }
                        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                            if (!((Supplier<?>) in.readObject()).get().equals("serialized-lambda-value"))
                                throw new AssertionError("serialized lambda");
                        }
                        System.out.println("PROTECTED_OK");
                    }
                }
                """,
            "Lookup.java", "package net.wurstclient.fixture; @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Lookup { String value(); }",
            "NativeApi.java", "package net.wurstclient.fixture; public class NativeApi { private static native long callNative(); }",
            "Mixin.java", "package org.spongepowered.asm.mixin; @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) public @interface Mixin {}",
            "MixinTarget.java", "package net.wurstclient.fixture; @org.spongepowered.asm.mixin.Mixin public class MixinTarget { private static String hook() { return \"mixin-retained\"; } }");
        var args = new java.util.ArrayList<>(List.of("--release", System.getProperty("java.specification.version"),
            "-g", "-parameters", "-encoding", "UTF-8", "-d", classes.toString()));
        for (var entry : sources.entrySet()) {
            Path file = source.resolve(entry.getKey());
            Files.writeString(file, entry.getValue());
            args.add(file.toString());
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (var paths = Files.walk(classes)) {
            for (Path path : paths.filter(Files::isRegularFile).toList())
                entries.put(classes.relativize(path).toString().replace('\\', '/'), Files.readAllBytes(path));
        }
        entries.put("fabric.mod.json", "{\"schemaVersion\":1}".getBytes(StandardCharsets.UTF_8));
        entries.put("wurst.mixins.json", "{\"package\":\"net.wurstclient.fixture\",\"client\":[\"MixinTarget\"]}".getBytes(StandardCharsets.UTF_8));
        entries.put("META-INF/mods.toml", "modLoader=\"javafml\"".getBytes(StandardCharsets.UTF_8));
        entries.put("LICENSE.txt", "license-retained".getBytes(StandardCharsets.UTF_8));
        entries.put("assets/native.dll", new byte[]{0, 1, 2, 3});
        entries.put("META-INF/jarjar/dependency.jar", new byte[]{80, 75, 3, 4, 0});
        Path jar = temp.resolve("fixture.jar");
        write(jar, entries);
        return jar;
    }

    private void runSample(Path jar) throws Exception {
        String executable = Path.of(System.getProperty("java.home"), "bin",
            System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        Path log = temp.resolve("jvm-" + java.util.UUID.randomUUID() + ".txt");
        Process process = new ProcessBuilder(executable, "-Xverify:all", "-cp", jar.toString(), SAMPLE)
            .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Fixture JVM timed out");
        }
        String output = Files.readString(log);
        assertEquals(0, process.exitValue(), output);
        assertTrue(output.contains("PROTECTED_OK"), output);
    }

    private static Map<String, byte[]> read(Path jar) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var iterator = zip.entries();
            while (iterator.hasMoreElements()) {
                var entry = iterator.nextElement();
                try (var input = zip.getInputStream(entry)) { entries.put(entry.getName(), input.readAllBytes()); }
            }
        }
        return entries;
    }

    private static void write(Path jar, Map<String, byte[]> entries) throws IOException {
        try (var out = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (var entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
    }
}
