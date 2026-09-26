import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class Baritone1214to1215Patcher {
    private static final String ENTRY = "baritone/launch/mixins/MixinScreen.class";
    private static final String NETWORK_ENTRY = "baritone/launch/mixins/MixinNetworkManager.class";
    private static final String MINECRAFT_ENTRY = "baritone/launch/mixins/MixinMinecraft.class";
    private static final String RENDERER_ENTRY = "baritone/launch/mixins/MixinWorldRenderer.class";
    private static final String OWNER = "baritone/launch/mixins/MixinScreen";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
    private static final String CI = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    private static final String CIR = CI + "Returnable";
    private static final String OLD_DESC = "(Lnet/minecraft/network/chat/ClickEvent;"
        + "Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/screens/Screen;L" + CI + ";)V";
    private static final String ADAPTER_DESC = "(Lnet/minecraft/network/chat/Style;L" + CIR + ";)V";
    private static final String OLD_NETWORK_DESC = "(Lnet/minecraft/network/protocol/Packet;"
        + "Lio/netty/channel/ChannelFutureListener;ZL" + CI + ";)V";
    private static final String NEW_NETWORK_DESC = "(Lnet/minecraft/network/protocol/Packet;"
        + "Lnet/minecraft/network/PacketSendListener;ZL" + CI + ";)V";
    private static final String OLD_LEVEL_DESC = "(Lnet/minecraft/client/multiplayer/ClientLevel;L" + CI + ";)V";
    private static final String NEW_LEVEL_DESC = "(Lnet/minecraft/client/multiplayer/ClientLevel;"
        + "Lnet/minecraft/client/gui/screens/ReceivingLevelScreen$Reason;L" + CI + ";)V";
    private static final String OLD_RENDER_DESC = "(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;"
        + "Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;"
        + "Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;"
        + "Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZL" + CI + ";)V";
    private static final String NEW_RENDER_DESC = "(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;"
        + "Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;"
        + "Lnet/minecraft/client/renderer/GameRenderer;Lorg/joml/Matrix4f;"
        + "Lorg/joml/Matrix4f;L" + CI + ";)V";

    private Baritone1214to1215Patcher() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Baritone1214to1215Patcher <per-MC Baritone jar>");
        }
        Path jar = Path.of(args[0]);
        Path temp = Files.createTempFile(jar.getParent(), "baritone-screen-", ".jar");
        int found = 0;
        boolean patched = false;
        try {
            try (ZipInputStream in = new ZipInputStream(Files.newInputStream(jar));
                 ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(temp))) {
                ZipEntry entry;
                while ((entry = in.getNextEntry()) != null) {
                    byte[] bytes = in.readAllBytes();
                    if (entry.getName().equals(ENTRY)) {
                        found++;
                        if (jar.toString().contains("mc1.21.4")) {
                            // The 1.21.4 ClickEvent API needs its own adapter.
                            ZipEntry copy = new ZipEntry(entry.getName());
                            copy.setTime(entry.getTime());
                            out.putNextEntry(copy);
                            out.write(bytes);
                            out.closeEntry();
                            continue;
                        }
                        ClassReader reader = new ClassReader(bytes);
                        ClassWriter writer = new ClassWriter(reader, 0);
                        int[] oldMethods = {0};
                        int[] adapters = {0};
                        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                            @Override
                            public MethodVisitor visitMethod(int access, String name, String desc,
                                                             String signature, String[] exceptions) {
                                MethodVisitor delegate = super.visitMethod(access, name, desc,
                                    signature, exceptions);
                                if (name.equals("baritone$handleComponentClicked")) {
                                    adapters[0]++;
                                    if (!desc.equals(ADAPTER_DESC)) {
                                        throw new IllegalStateException("Unexpected adapter descriptor: " + desc);
                                    }
                                }
                                if (!name.equals("handleCustomClickEvent")) return delegate;
                                oldMethods[0]++;
                                if (!desc.equals(OLD_DESC)) {
                                    throw new IllegalStateException("Unexpected Baritone handler: " + desc);
                                }
                                return new MethodVisitor(Opcodes.ASM9, delegate) {
                                    @Override
                                    public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                                        if (annotation.equals(INJECT)) return null;
                                        return super.visitAnnotation(annotation, visible);
                                    }
                                };
                            }

                            @Override
                            public void visitEnd() {
                                if (oldMethods[0] != 1 || adapters[0] > 1) {
                                    throw new IllegalStateException("Unexpected Screen mixin methods");
                                }
                                if (adapters[0] == 0) addAdapter();
                                super.visitEnd();
                            }

                            private void addAdapter() {
                                MethodVisitor mv = super.visitMethod(Opcodes.ACC_PRIVATE,
                                    "baritone$handleComponentClicked", ADAPTER_DESC, null, null);
                                AnnotationVisitor inject = mv.visitAnnotation(INJECT, true);
                                inject.visit("method", "handleComponentClicked");
                                AnnotationVisitor at = inject.visitAnnotation("at", AT);
                                at.visit("value", "HEAD");
                                at.visitEnd();
                                inject.visit("cancellable", true);
                                inject.visitEnd();

                                Label done = new Label();
                                mv.visitCode();
                                mv.visitVarInsn(Opcodes.ALOAD, 1);
                                mv.visitJumpInsn(Opcodes.IFNULL, done);
                                mv.visitVarInsn(Opcodes.ALOAD, 1);
                                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/network/chat/Style",
                                    "getClickEvent", "()Lnet/minecraft/network/chat/ClickEvent;", false);
                                mv.visitInsn(Opcodes.ACONST_NULL);
                                mv.visitInsn(Opcodes.ACONST_NULL);
                                mv.visitVarInsn(Opcodes.ALOAD, 2);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, OWNER,
                                    "handleCustomClickEvent", OLD_DESC, false);
                                mv.visitVarInsn(Opcodes.ALOAD, 2);
                                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CI, "isCancelled", "()Z", false);
                                mv.visitJumpInsn(Opcodes.IFEQ, done);
                                mv.visitVarInsn(Opcodes.ALOAD, 2);
                                mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Boolean", "TRUE", "Ljava/lang/Boolean;");
                                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CIR, "setReturnValue",
                                    "(Ljava/lang/Object;)V", false);
                                mv.visitLabel(done);
                                mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                                mv.visitInsn(Opcodes.RETURN);
                                mv.visitMaxs(4, 3);
                                mv.visitEnd();
                            }
                        }, 0);
                        patched |= adapters[0] == 0;
                        bytes = writer.toByteArray();
                    } else if (entry.getName().equals(NETWORK_ENTRY)) {
                        found++;
                        ClassReader reader = new ClassReader(bytes);
                        ClassWriter writer = new ClassWriter(reader, 0);
                        int[] seen = {0};
                        int[] changed = {0};
                        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                            @Override
                            public MethodVisitor visitMethod(int access, String name, String desc,
                                                             String signature, String[] exceptions) {
                                if (!name.equals("preDispatchPacket") && !name.equals("postDispatchPacket")) {
                                    return super.visitMethod(access, name, desc, signature, exceptions);
                                }
                                seen[0]++;
                                if (!desc.equals(OLD_NETWORK_DESC) && !desc.equals(NEW_NETWORK_DESC)) {
                                    throw new IllegalStateException(name + " has unexpected descriptor " + desc);
                                }
                                if (desc.equals(OLD_NETWORK_DESC)) changed[0]++;
                                return super.visitMethod(access, name, NEW_NETWORK_DESC,
                                    signature, exceptions);
                            }
                        }, 0);
                        if (seen[0] != 2) {
                            throw new IllegalStateException("Expected two packet handlers, found " + seen[0]);
                        }
                        patched |= changed[0] > 0;
                        bytes = writer.toByteArray();
                    } else if (entry.getName().equals(MINECRAFT_ENTRY)) {
                        found++;
                        ClassReader reader = new ClassReader(bytes);
                        ClassWriter writer = new ClassWriter(reader, 0);
                        int[] seen = {0};
                        int[] changed = {0};
                        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                            @Override
                            public MethodVisitor visitMethod(int access, String name, String desc,
                                                             String signature, String[] exceptions) {
                                if (!name.equals("preLoadWorld") && !name.equals("postLoadWorld")) {
                                    return super.visitMethod(access, name, desc, signature, exceptions);
                                }
                                seen[0]++;
                                if (!desc.equals(OLD_LEVEL_DESC) && !desc.equals(NEW_LEVEL_DESC)) {
                                    throw new IllegalStateException(name + " has unexpected descriptor " + desc);
                                }
                                if (desc.equals(OLD_LEVEL_DESC)) changed[0]++;
                                MethodVisitor delegate = super.visitMethod(access, name, NEW_LEVEL_DESC,
                                    signature, exceptions);
                                return new MethodVisitor(Opcodes.ASM9, delegate) {
                                    @Override
                                    public void visitMaxs(int maxStack, int maxLocals) {
                                        super.visitMaxs(maxStack, Math.max(maxLocals, 4));
                                    }
                                };
                            }
                        }, 0);
                        if (seen[0] != 2) throw new IllegalStateException("Expected two setLevel handlers");
                        patched |= changed[0] > 0;
                        bytes = writer.toByteArray();
                    } else if (entry.getName().equals(RENDERER_ENTRY)) {
                        found++;
                        ClassReader reader = new ClassReader(bytes);
                        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES) {
                            @Override
                            protected String getCommonSuperClass(String left, String right) {
                                return "java/lang/Object";
                            }
                        };
                        int[] seen = {0};
                        int[] changed = {0};
                        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                            @Override
                            public MethodVisitor visitMethod(int access, String name, String desc,
                                                             String signature, String[] exceptions) {
                                if (!name.equals("onStartHand")) {
                                    return super.visitMethod(access, name, desc, signature, exceptions);
                                }
                                seen[0]++;
                                if (!desc.equals(OLD_RENDER_DESC) && !desc.equals(NEW_RENDER_DESC)) {
                                    throw new IllegalStateException("Unexpected renderLevel handler: " + desc);
                                }
                                if (desc.equals(OLD_RENDER_DESC)) changed[0]++;
                                MethodVisitor delegate = super.visitMethod(access, name, NEW_RENDER_DESC,
                                    signature, exceptions);
                                if (desc.equals(NEW_RENDER_DESC)) return delegate;
                                return new MethodVisitor(Opcodes.ASM9, delegate) {
                                    @Override
                                    public void visitVarInsn(int opcode, int index) {
                                        // GameRenderer is inserted before both matrices in 1.21.5.
                                        if (opcode == Opcodes.ALOAD && (index == 5 || index == 6)) index++;
                                        super.visitVarInsn(opcode, index);
                                    }
                                };
                            }
                        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                        if (seen[0] != 1) throw new IllegalStateException("Expected one renderLevel handler");
                        patched |= changed[0] > 0;
                        bytes = writer.toByteArray();
                    }
                    ZipEntry copy = new ZipEntry(entry.getName());
                    copy.setTime(entry.getTime());
                    out.putNextEntry(copy);
                    out.write(bytes);
                    out.closeEntry();
                }
            }
            if (found != 4) throw new IllegalStateException("Missing Baritone mixin class");
            if (patched) Files.move(temp, jar, StandardCopyOption.REPLACE_EXISTING);
            System.out.println(jar + ": " + (patched ? "patched" : "already patched"));
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
