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

public final class Baritone1214ClickPatcher {
    private static final String ENTRY = "baritone/launch/mixins/MixinScreen.class";
    private static final String PLAYER_ENTRY = "baritone/launch/mixins/MixinClientPlayerEntity.class";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
    private static final String CIR = "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
    private static final String ADAPTER = "baritone$handleComponentClicked1214";
    private static final String ADAPTER_DESC = "(Lnet/minecraft/network/chat/Style;L" + CIR + ";)V";
    private static final String OLD_SPRINT_DESC = "(Lnet/minecraft/world/entity/player/Input;)Z";
    private static final String NEW_SPRINT_DESC = "(Lnet/minecraft/client/KeyMapping;)Z";
    private static final String OLD_SPRINT_TARGET = "Lnet/minecraft/world/entity/player/Input;sprint()Z";
    private static final String NEW_SPRINT_TARGET = "Lnet/minecraft/client/KeyMapping;isDown()Z";

    private Baritone1214ClickPatcher() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 1 || !args[0].contains("mc1.21.4")) {
            throw new IllegalArgumentException("Usage: Baritone1214ClickPatcher <1.21.4 Baritone jar>");
        }
        Path jar = Path.of(args[0]);
        Path temp = Files.createTempFile(jar.getParent(), "baritone-click-", ".jar");
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
                        ClassReader reader = new ClassReader(bytes);
                        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES) {
                            @Override
                            protected String getCommonSuperClass(String left, String right) {
                                return "java/lang/Object";
                            }
                        };
                        int[] obsolete = {0};
                        int[] adapters = {0};
                        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                            @Override
                            public MethodVisitor visitMethod(int access, String name, String desc,
                                                             String signature, String[] exceptions) {
                                if (name.equals("handleCustomClickEvent")
                                    || name.equals("baritone$handleComponentClicked")) {
                                    obsolete[0]++;
                                    return null;
                                }
                                if (name.equals(ADAPTER)) {
                                    adapters[0]++;
                                    if (!desc.equals(ADAPTER_DESC)) {
                                        throw new IllegalStateException("Unexpected click adapter: " + desc);
                                    }
                                }
                                return super.visitMethod(access, name, desc, signature, exceptions);
                            }

                            @Override
                            public void visitEnd() {
                                if (adapters[0] > 1) throw new IllegalStateException("Duplicate click adapter");
                                if (adapters[0] == 0) addAdapter();
                                super.visitEnd();
                            }

                            private void addAdapter() {
                                MethodVisitor mv = super.visitMethod(Opcodes.ACC_PRIVATE,
                                    ADAPTER, ADAPTER_DESC, null, null);
                                AnnotationVisitor inject = mv.visitAnnotation(INJECT, true);
                                inject.visit("method", "handleComponentClicked");
                                AnnotationVisitor at = inject.visitAnnotation("at", AT);
                                at.visit("value", "HEAD");
                                at.visitEnd();
                                inject.visit("cancellable", true);
                                inject.visitEnd();

                                Label done = new Label();
                                Label cancel = new Label();
                                mv.visitCode();
                                mv.visitVarInsn(Opcodes.ALOAD, 1);
                                mv.visitJumpInsn(Opcodes.IFNULL, done);
                                mv.visitVarInsn(Opcodes.ALOAD, 1);
                                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                                    "net/minecraft/network/chat/Style", "getClickEvent",
                                    "()Lnet/minecraft/network/chat/ClickEvent;", false);
                                mv.visitVarInsn(Opcodes.ASTORE, 3);
                                mv.visitVarInsn(Opcodes.ALOAD, 3);
                                mv.visitJumpInsn(Opcodes.IFNULL, done);
                                mv.visitVarInsn(Opcodes.ALOAD, 3);
                                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                                    "net/minecraft/network/chat/ClickEvent", "getAction",
                                    "()Lnet/minecraft/network/chat/ClickEvent$Action;", false);
                                mv.visitFieldInsn(Opcodes.GETSTATIC,
                                    "net/minecraft/network/chat/ClickEvent$Action", "RUN_COMMAND",
                                    "Lnet/minecraft/network/chat/ClickEvent$Action;");
                                mv.visitJumpInsn(Opcodes.IF_ACMPNE, done);
                                mv.visitVarInsn(Opcodes.ALOAD, 3);
                                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                                    "net/minecraft/network/chat/ClickEvent", "getValue",
                                    "()Ljava/lang/String;", false);
                                mv.visitVarInsn(Opcodes.ASTORE, 4);
                                mv.visitVarInsn(Opcodes.ALOAD, 4);
                                mv.visitFieldInsn(Opcodes.GETSTATIC,
                                    "baritone/api/command/IBaritoneChatControl", "FORCE_COMMAND_PREFIX",
                                    "Ljava/lang/String;");
                                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "startsWith",
                                    "(Ljava/lang/String;)Z", false);
                                mv.visitJumpInsn(Opcodes.IFEQ, done);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "baritone/api/BaritoneAPI",
                                    "getProvider", "()Lbaritone/api/IBaritoneProvider;", false);
                                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "baritone/api/IBaritoneProvider",
                                    "getPrimaryBaritone", "()Lbaritone/api/IBaritone;", true);
                                mv.visitVarInsn(Opcodes.ASTORE, 5);
                                mv.visitVarInsn(Opcodes.ALOAD, 5);
                                mv.visitJumpInsn(Opcodes.IFNULL, cancel);
                                mv.visitVarInsn(Opcodes.ALOAD, 5);
                                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "baritone/api/IBaritone",
                                    "getGameEventHandler", "()Lbaritone/api/event/listener/IEventBus;", true);
                                mv.visitTypeInsn(Opcodes.NEW, "baritone/api/event/events/ChatEvent");
                                mv.visitInsn(Opcodes.DUP);
                                mv.visitVarInsn(Opcodes.ALOAD, 4);
                                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "baritone/api/event/events/ChatEvent",
                                    "<init>", "(Ljava/lang/String;)V", false);
                                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "baritone/api/event/listener/IEventBus",
                                    "onSendChatMessage", "(Lbaritone/api/event/events/ChatEvent;)V", true);
                                mv.visitLabel(cancel);
                                mv.visitVarInsn(Opcodes.ALOAD, 2);
                                mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Boolean", "TRUE", "Ljava/lang/Boolean;");
                                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CIR, "setReturnValue",
                                    "(Ljava/lang/Object;)V", false);
                                mv.visitLabel(done);
                                mv.visitInsn(Opcodes.RETURN);
                                mv.visitMaxs(0, 0);
                                mv.visitEnd();
                            }
                        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                        patched |= obsolete[0] > 0 || adapters[0] == 0;
                        bytes = writer.toByteArray();
                    } else if (entry.getName().equals(PLAYER_ENTRY)) {
                        found++;
                        ClassReader reader = new ClassReader(bytes);
                        ClassWriter writer = new ClassWriter(reader, 0);
                        int[] seen = {0};
                        int[] changed = {0};
                        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                            @Override
                            public MethodVisitor visitMethod(int access, String name, String desc,
                                                             String signature, String[] exceptions) {
                                if (!name.equals("redirectSprintInput")) {
                                    return super.visitMethod(access, name, desc, signature, exceptions);
                                }
                                seen[0]++;
                                if (!desc.equals(OLD_SPRINT_DESC) && !desc.equals(NEW_SPRINT_DESC)) {
                                    throw new IllegalStateException("Unexpected sprint handler: " + desc);
                                }
                                if (desc.equals(OLD_SPRINT_DESC)) changed[0]++;
                                MethodVisitor delegate = super.visitMethod(access, name, NEW_SPRINT_DESC,
                                    signature, exceptions);
                                return new MethodVisitor(Opcodes.ASM9, delegate) {
                                    @Override
                                    public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                                        AnnotationVisitor outer = super.visitAnnotation(annotation, visible);
                                        if (!annotation.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;")) {
                                            return outer;
                                        }
                                        return new AnnotationVisitor(Opcodes.ASM9, outer) {
                                            @Override
                                            public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                                                AnnotationVisitor inner = super.visitAnnotation(name, descriptor);
                                                if (!name.equals("at")) return inner;
                                                return new AnnotationVisitor(Opcodes.ASM9, inner) {
                                                    @Override
                                                    public void visit(String key, Object value) {
                                                        if (key.equals("target") && value.equals(OLD_SPRINT_TARGET)) {
                                                            value = NEW_SPRINT_TARGET;
                                                        }
                                                        super.visit(key, value);
                                                    }
                                                };
                                            }
                                        };
                                    }

                                    @Override
                                    public void visitMethodInsn(int opcode, String owner, String method,
                                                                String descriptor, boolean isInterface) {
                                        if (owner.equals("net/minecraft/world/entity/player/Input")
                                            && method.equals("sprint") && descriptor.equals("()Z")) {
                                            owner = "net/minecraft/client/KeyMapping";
                                            method = "isDown";
                                        }
                                        super.visitMethodInsn(opcode, owner, method, descriptor, isInterface);
                                    }

                                    @Override
                                    public void visitFrame(int type, int count, Object[] locals,
                                                           int stackCount, Object[] stack) {
                                        if (locals != null) {
                                            for (int i = 0; i < count; i++) {
                                                if ("net/minecraft/world/entity/player/Input".equals(locals[i])) {
                                                    locals[i] = "net/minecraft/client/KeyMapping";
                                                }
                                            }
                                        }
                                        super.visitFrame(type, count, locals, stackCount, stack);
                                    }

                                    @Override
                                    public void visitLocalVariable(String name, String descriptor, String signature,
                                                                   Label start, Label end, int index) {
                                        if (descriptor.equals("Lnet/minecraft/world/entity/player/Input;")) {
                                            descriptor = "Lnet/minecraft/client/KeyMapping;";
                                        }
                                        super.visitLocalVariable(name, descriptor, signature, start, end, index);
                                    }
                                };
                            }
                        }, 0);
                        if (seen[0] != 1) throw new IllegalStateException("Expected one sprint handler");
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
            if (found != 2) throw new IllegalStateException("Missing Baritone Screen or player mixin");
            if (patched) Files.move(temp, jar, StandardCopyOption.REPLACE_EXISTING);
            System.out.println(jar + ": " + (patched ? "patched" : "already patched"));
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
