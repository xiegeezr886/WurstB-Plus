package net.wurstclient.tools.protector;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Template copied and randomized into the output. Depends only on the JDK. */
public final class StringBootstrap {
    private StringBootstrap() {}

    public static String resolve(MethodHandles.Lookup lookup, String name,
            Class<?> type, String... chunks) {
        byte[] key = Base64.getDecoder().decode("WURST_KEY_SHARE_A");
        byte[] share = Base64.getDecoder().decode("WURST_KEY_SHARE_B");
        byte[] plain = null;
        try {
            if (type != String.class) throw new GeneralSecurityException("Invalid constant type");
            for (int i = 0; i < key.length; i++) key[i] ^= share[i];
            byte[] packed = Base64.getDecoder().decode(String.join("", chunks));
            if (packed.length < 28) throw new GeneralSecurityException("Truncated constant");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, packed, 0, 12));
            cipher.updateAAD(aad(lookup.lookupClass().getName(), name));
            plain = cipher.doFinal(packed, 12, packed.length - 12);
            if ((plain.length & 1) != 0) throw new GeneralSecurityException("Invalid UTF-16 length");
            // Charset decoders replace unpaired surrogates; preserve every Java char instead.
            char[] chars = new char[plain.length / 2];
            for (int i = 0; i < chars.length; i++)
                chars[i] = (char) ((plain[2 * i] & 255) << 8 | plain[2 * i + 1] & 255);
            String result = new String(chars).intern();
            Arrays.fill(chars, '\0');
            return result;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new BootstrapMethodError("Protected string authentication failed", e);
        } finally {
            Arrays.fill(key, (byte) 0);
            Arrays.fill(share, (byte) 0);
            if (plain != null) Arrays.fill(plain, (byte) 0);
        }
    }

    static byte[] aad(String owner, String constant) {
        return ("WurstB+ string v2\0" + owner + '\0' + constant).getBytes(StandardCharsets.UTF_8);
    }

    public static CallSite link(MethodHandles.Lookup lookup, String name, MethodType callSiteType,
            Class<?> owner, String target, MethodType methodType, int kind) throws ReflectiveOperationException {
        // Use the JVM-provided caller's access rights, never a privileged helper lookup.
        MethodHandle handle;
        if (kind == 0) handle = lookup.findStatic(owner, target, methodType);
        else if (kind == 1) handle = lookup.findVirtual(owner, target, methodType);
        else throw new IllegalArgumentException("Invalid protected call kind");
        return new ConstantCallSite(handle.asType(callSiteType));
    }
}
