package net.wurstclient.tools.protector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;

record ProtectionConfig(List<String> include, List<String> exclude,
        boolean encryptStrings, boolean renamePrivateMethods,
        boolean maskNumbers, boolean stripDebug, boolean indirectPrivateCalls, Set<String> keepMethods) {
    static ProtectionConfig defaults() {
        return new ProtectionConfig(List.of("net/wurstclient/"),
            List.of("net/wurstclient/addon/"), true, true, true, true, true, Set.of());
    }

    static ProtectionConfig load(Path path) throws IOException {
        Properties p = new Properties();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            p.load(reader);
        }
        Set<String> known = Set.of("include", "exclude", "encryptStrings",
            "renamePrivateMethods", "maskNumbers", "maskIntegers", "stripDebug", "indirectPrivateCalls", "keepMethods");
        for (String key : p.stringPropertyNames())
            if (!known.contains(key)) throw new IOException("Unknown protection option: " + key);
        List<String> include = prefixes(p.getProperty("include", "net.wurstclient."));
        if (include.isEmpty()) throw new IOException("include must not be empty");
        // Keep v1 configuration files usable, but never silently resolve conflicting switches.
        if (p.containsKey("maskNumbers") && p.containsKey("maskIntegers")
                && flag(p, "maskNumbers") != flag(p, "maskIntegers"))
            throw new IOException("maskNumbers and legacy maskIntegers disagree");
        return new ProtectionConfig(include,
            prefixes(p.getProperty("exclude", "net.wurstclient.addon.")),
            flag(p, "encryptStrings"), flag(p, "renamePrivateMethods"),
            flag(p, p.containsKey("maskNumbers") ? "maskNumbers" : "maskIntegers"),
            flag(p, "stripDebug"), flag(p, "indirectPrivateCalls"),
            Set.copyOf(values(p.getProperty("keepMethods", ""))));
    }

    private static List<String> values(String value) {
        return Arrays.stream(value.split(",")).map(String::trim)
            .filter(s -> !s.isEmpty()).toList();
    }

    private static List<String> prefixes(String value) throws IOException {
        List<String> result = values(value).stream().map(s -> s.replace('.', '/')).toList();
        for (String s : result)
            if (s.contains("*") || s.startsWith("/") || s.contains("//"))
                throw new IOException("Expected a class/package prefix, not a glob: " + s);
        return result;
    }

    private static boolean flag(Properties p, String key) throws IOException {
        String value = p.getProperty(key, "true").trim();
        if (!value.equals("true") && !value.equals("false"))
            throw new IOException(key + " must be true or false");
        return Boolean.parseBoolean(value);
    }

    boolean includes(String name) {
        return include.stream().anyMatch(name::startsWith)
            && exclude.stream().noneMatch(name::startsWith)
            && !name.contains("/shaded/") && !name.contains("/mixin/")
            && !name.contains("/mixins/") && !name.contains("/mixinterface/");
    }
}
