package net.wurstclient.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CameraOwnershipTest {
    @Test void onlyBetterThirdPersonTakesOwnershipOfTheOpalCamera() {
        var loader = FabricLoader.FIXTURE;
        var plugin = new WurstMixinConfigPlugin();
        String camera = "net.wurstclient.mixin.OpenOpalCameraMixin";
        try {
            loader.loadedMod = null;
            assertTrue(plugin.shouldApplyMixin("Camera", camera));
            loader.loadedMod = "othermod";
            assertTrue(plugin.shouldApplyMixin("Camera", camera));
            loader.loadedMod = "betterthirdperson";
            assertFalse(plugin.shouldApplyMixin("Camera", camera));
            assertTrue(plugin.shouldApplyMixin("GameRenderer", "net.wurstclient.mixin.GameRendererMixin"));
        } finally { loader.loadedMod = null; }
    }
}
