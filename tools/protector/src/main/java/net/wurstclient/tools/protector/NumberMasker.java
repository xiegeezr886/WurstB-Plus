package net.wurstclient.tools.protector;

import java.security.SecureRandom;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** No floating-point arithmetic: reconstruct raw bits, including signed zero and NaN payloads. */
final class NumberMasker implements Opcodes {
    private final SecureRandom random;
    int integers, longs, floats, doubles;

    NumberMasker(SecureRandom random) { this.random = random; }

    InsnList replacement(AbstractInsnNode insn) {
        Number value = value(insn);
        if (value == null) return null;
        if (value instanceof Integer i) { integers++; return maskedInt(i); }
        if (value instanceof Long l) { longs++; return maskedLong(l); }
        if (value instanceof Float f) {
            floats++;
            InsnList result = maskedInt(Float.floatToRawIntBits(f));
            result.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Float", "intBitsToFloat", "(I)F", false));
            return result;
        }
        doubles++;
        InsnList result = maskedLong(Double.doubleToRawLongBits(value.doubleValue()));
        result.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Double", "longBitsToDouble", "(J)D", false));
        return result;
    }

    private InsnList maskedInt(int value) {
        int mask;
        do { mask = random.nextInt(); } while (mask == 0 || mask == value);
        InsnList result = new InsnList();
        result.add(new LdcInsnNode(value ^ mask));
        result.add(new LdcInsnNode(mask));
        result.add(new InsnNode(IXOR));
        return result;
    }

    private InsnList maskedLong(long value) {
        long mask;
        do { mask = random.nextLong(); } while (mask == 0 || mask == value);
        InsnList result = new InsnList();
        result.add(new LdcInsnNode(value ^ mask));
        result.add(new LdcInsnNode(mask));
        result.add(new InsnNode(LXOR));
        return result;
    }

    private static Number value(AbstractInsnNode insn) {
        int op = insn.getOpcode();
        if (op >= ICONST_M1 && op <= ICONST_5) return op - ICONST_0;
        if (op >= LCONST_0 && op <= LCONST_1) return (long) (op - LCONST_0);
        if (op >= FCONST_0 && op <= FCONST_2) return (float) (op - FCONST_0);
        if (op >= DCONST_0 && op <= DCONST_1) return (double) (op - DCONST_0);
        if (insn instanceof IntInsnNode i && (op == BIPUSH || op == SIPUSH)) return i.operand;
        if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Number n
                && (n instanceof Integer || n instanceof Long || n instanceof Float || n instanceof Double)) return n;
        return null;
    }
}
