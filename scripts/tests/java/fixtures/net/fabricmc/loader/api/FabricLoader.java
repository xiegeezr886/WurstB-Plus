package net.fabricmc.loader.api;

// Loader boundary for the standalone camera-ownership test.
public interface FabricLoader {
    final class Fixture implements FabricLoader {
        public String loadedMod;
        public boolean isModLoaded(String id) { return id.equals(loadedMod); }
    }
    Fixture FIXTURE = new Fixture();
    static FabricLoader getInstance() { return FIXTURE; }
    boolean isModLoaded(String id);
}
