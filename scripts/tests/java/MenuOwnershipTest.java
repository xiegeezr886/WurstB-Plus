package net.wurstclient.mixin;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URL;
import org.junit.jupiter.api.Test;

class MenuOwnershipTest {
    private boolean applies(String mixin, boolean fancyMenu) throws Exception {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        ClassLoader resources = new ClassLoader(null) {
            @Override public URL getResource(String name) {
                try {
                    return fancyMenu && name.equals("de/keksuccino/fancymenu/FancyMenu.class")
                        ? new URL("file:/fixture/FancyMenu.class") : null;
                } catch (Exception e) { throw new AssertionError(e); }
            }
        };
        try {
            Thread.currentThread().setContextClassLoader(resources);
            return new WurstMixinConfigPlugin().shouldApplyMixin(
                "net.minecraft.client.gui.screens.TitleScreen", "net.wurstclient.mixin." + mixin);
        } finally { Thread.currentThread().setContextClassLoader(previous); }
    }

    @Test void fancyMenuKeepsItsTitleWidgets() throws Exception {
        assertFalse(applies("TitleScreenMixin", true));
    }
    @Test void plainClientRetainsWurstTitle() throws Exception {
        assertTrue(applies("TitleScreenMixin", false));
    }
    @Test void fancyMenuDoesNotDisableGameplayHooks() throws Exception {
        for (String name : new String[]{"GameRendererMixin", "MinecraftAccessor", "ReachLegacyRendererMixin"})
            assertTrue(applies(name, true), name);
    }
    @Test void absentContextLoaderKeepsNormalTitle() {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            assertTrue(new WurstMixinConfigPlugin().shouldApplyMixin("TitleScreen", "net.wurstclient.mixin.TitleScreenMixin"));
        } finally { Thread.currentThread().setContextClassLoader(previous); }
    }

    @Test void optionalSodiumProbeDoesNotDefineTheTargetClass() throws Exception {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new ClassLoader(null) {
                @Override protected Class<?> loadClass(String name, boolean resolve) {
                    throw new AssertionError("Eligibility probe must not define " + name);
                }
                @Override public URL getResource(String name) {
                    try { return name.equals("compat/OptionalRenderer.class")
                        ? new URL("file:/fixture/OptionalRenderer.class") : null; }
                    catch (Exception e) { throw new AssertionError(e); }
                }
            });
            var plugin = new WurstMixinConfigPlugin();
            assertTrue(plugin.shouldApplyMixin("compat.OptionalRenderer", "net.wurstclient.mixin.SodiumFluidRendererMixin"));
            assertFalse(plugin.shouldApplyMixin("compat.MissingRenderer", "net.wurstclient.mixin.SodiumFluidRendererMixin"));
        } finally { Thread.currentThread().setContextClassLoader(previous); }
    }
}
