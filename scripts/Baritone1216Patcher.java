import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class Baritone1216Patcher {
    private static final String MINECRAFT_ENTRY = "baritone/launch/mixins/MixinMinecraft.class";
    private static final String RENDERER_ENTRY = "baritone/launch/mixins/MixinWorldRenderer.class";
    private static final String OLD_DESC = "(Lnet/minecraft/client/multiplayer/ClientLevel;"
        + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
    private static final String NEW_DESC = "(Lnet/minecraft/client/multiplayer/ClientLevel;"
        + "Lnet/minecraft/client/gui/screens/ReceivingLevelScreen$Reason;"
        + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
    private static final String OLD_RENDER_DESC = "(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;"
        + "Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;"
        + "Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;"
        + "Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z"
        + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
    private static final String NEW_RENDER_DESC = OLD_RENDER_DESC.replace(
        // The third matrix is unused by Baritone and absent from 1.21.6-1.21.8 renderLevel.
        "Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;",
        "Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;");

    private Baritone1216Patcher() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Baritone1216Patcher <per-MC Baritone jar>");
        }
        Path jar = Path.of(args[0]);
        Path temp = Files.createTempFile(jar.getParent(), "baritone-setlevel-", ".jar");
        int changed = 0;
        int matchedEntries = 0;
        try {
            try (ZipInputStream in = new ZipInputStream(Files.newInputStream(jar));
                 ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(temp))) {
                ZipEntry entry;
                while ((entry = in.getNextEntry()) != null) {
                    byte[] bytes = in.readAllBytes();
                    if (entry.getName().equals(MINECRAFT_ENTRY)) {
                        matchedEntries++;
                        ClassReader reader = new ClassReader(bytes);
                        ClassWriter writer = new ClassWriter(reader, 0);
                        int[] seen = {0};
                        int[] patched = {0};
                        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                            @Override
                            public MethodVisitor visitMethod(int access, String name, String desc,
                                                             String signature, String[] exceptions) {
                                if (!name.equals("preLoadWorld") && !name.equals("postLoadWorld")) {
                                    return super.visitMethod(access, name, desc, signature, exceptions);
                                }
                                seen[0]++;
                                if (!desc.equals(OLD_DESC) && !desc.equals(NEW_DESC)) {
                                    throw new IllegalStateException(name + " has unexpected descriptor " + desc);
                                }
                                if (desc.equals(OLD_DESC)) patched[0]++;
                                MethodVisitor delegate = super.visitMethod(access, name, NEW_DESC,
                                    signature, exceptions);
                                return new MethodVisitor(Opcodes.ASM9, delegate) {
                                    @Override
                                    public void visitMaxs(int maxStack, int maxLocals) {
                                        super.visitMaxs(maxStack, Math.max(maxLocals, 4));
                                    }
                                };
                            }
                        }, 0);
                        if (seen[0] != 2) {
                            throw new IllegalStateException("Expected two setLevel handlers, found " + seen[0]);
                        }
                        changed += patched[0];
                        bytes = writer.toByteArray();
                    } else if (entry.getName().equals(RENDERER_ENTRY)) {
                        matchedEntries++;
                        ClassReader reader = new ClassReader(bytes);
                        ClassWriter writer = new ClassWriter(reader, 0);
                        int[] seen = {0};
                        int[] patched = {0};
                        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                            @Override
                            public MethodVisitor visitMethod(int access, String name, String desc,
                                                             String signature, String[] exceptions) {
                                if (!name.equals("onStartHand")) {
                                    return super.visitMethod(access, name, desc, signature, exceptions);
                                }
                                seen[0]++;
                                if (!desc.equals(OLD_RENDER_DESC) && !desc.equals(NEW_RENDER_DESC)) {
                                    throw new IllegalStateException(name + " has unexpected descriptor " + desc);
                                }
                                if (desc.equals(OLD_RENDER_DESC)) patched[0]++;
                                return super.visitMethod(access, name, NEW_RENDER_DESC,
                                    signature, exceptions);
                            }
                        }, 0);
                        if (seen[0] != 1) {
                            throw new IllegalStateException("Expected one renderLevel handler, found " + seen[0]);
                        }
                        changed += patched[0];
                        bytes = writer.toByteArray();
                    }
                    ZipEntry copy = new ZipEntry(entry.getName());
                    copy.setTime(entry.getTime());
                    out.putNextEntry(copy);
                    out.write(bytes);
                    out.closeEntry();
                }
            }
            if (matchedEntries != 2) {
                throw new IllegalStateException("Expected both Baritone mixin classes, found " + matchedEntries);
            }
            if (changed > 0) {
                Files.move(temp, jar, StandardCopyOption.REPLACE_EXISTING);
            }
            System.out.println(jar + ": " + changed + " handler(s) patched");
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
