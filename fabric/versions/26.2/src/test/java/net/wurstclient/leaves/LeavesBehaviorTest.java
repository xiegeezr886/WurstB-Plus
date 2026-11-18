package net.wurstclient.leaves;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.*;
import org.junit.jupiter.api.Test;

final class LeavesBehaviorTest {
  @Test
  void allPortModulesConstructWithoutDuplicateSettingNames() throws Exception {
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    Field folder = WurstClient.class.getDeclaredField("wurstFolder");
    folder.setAccessible(true);
    Object old = folder.get(WurstClient.INSTANCE);
    try {
      folder.set(WurstClient.INSTANCE, java.nio.file.Files.createTempDirectory("leaves-settings-"));
      for (Class<?> type :
          new Class<?>[] {
            AutoAnchorHack.class,
            AutoArmorPlusHack.class,
            AutoLoginHack.class,
            AutoMineHack.class,
            AutoOffHandHack.class,
            AutoPlaceBlockHack.class,
            AutoRefreshTradeHack.class,
            AutoSprintHack.class,
            AutoTorchHack.class,
            AutoWebHack.class,
            BreakEspHack.class,
            CrystalAuraPlusHack.class,
            DeathRenderHack.class,
            ElytraGrimAccelerateHack.class,
            FireworkElytraFlyHack.class,
            FollowHack.class,
            FriendsManagerHack.class,
            GlassFillerHack.class,
            GlobalSettingHack.class,
            ModuleListHack.class,
            NukerHack.class,
            PacketLoggerHack.class,
            PacketMineHack.class,
            PlaceRenderHack.class,
            PrinterHack.class,
            PushCrystalHack.class,
            ReplenishHack.class,
            SelfTrapHack.class,
            TreeBotHack.class
          }) assertDoesNotThrow(() -> type.getConstructor().newInstance(), type.getSimpleName());
    } finally {
      folder.set(WurstClient.INSTANCE, old);
    }
  }

  @Test
  void printerRejectsWrongFacingAndSlabHalf() {
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    var north =
        Blocks.FURNACE
            .defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    assertTrue(PrinterHack.matchesOrientation(north, north));
    assertFalse(
        PrinterHack.matchesOrientation(
            north, north.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)));
    var slab = Blocks.STONE_SLAB.defaultBlockState();
    assertFalse(
        PrinterHack.matchesOrientation(
            slab,
            slab.setValue(
                BlockStateProperties.SLAB_TYPE,
                net.minecraft.world.level.block.state.properties.SlabType.TOP)));
    assertFalse(PrinterHack.matchesOrientation(north, null));
  }

  @Test
  void maxLevelBooksAndPasswordChangeArguments() {
    assertTrue(AutoRefreshTradeHack.qualifies(1, 3, 1));
    assertFalse(AutoRefreshTradeHack.qualifies(2, 3, 5));
    assertEquals(
        "new", AutoLoginHack.capturedPassword(new String[] {"cp", "old", "new"}, "l", "reg", "cp"));
    assertNull(AutoLoginHack.capturedPassword(new String[] {"cp", "old"}, "l", "reg", "cp"));
    assertNull(AutoLoginHack.capturedPassword(new String[] {"other", "secret"}, "l", "reg", "cp"));
  }

  @Test
  void sameSizeFriendChangesAreDetected() {
    assertEquals(
        Set.of("Bob"),
        FriendsManagerHack.difference(Set.of("Alice", "Bob"), Set.of("Alice", "Carol")));
  }

  @Test
  void flightYawIncludesStrafingAndBackwardsDiagonal() {
    assertEquals(-90, FireworkElytraFlyHack.movementYaw(0, 0, 1), 0.001);
    assertEquals(-135, FireworkElytraFlyHack.movementYaw(0, -1, 1), 0.001);
    assertEquals(40, FireworkElytraFlyHack.movementYaw(40, 0, 0), 0.001);
  }

  @Test
  void rgbaDefaultsRetainTransparency() {
    LeavesColorSetting color = new LeavesColorSetting("Fill", new Color(140, 142, 255, 50));
    assertEquals(0x328C8EFF, color.getColorI());
    assertEquals(50, color.toJson().getAsJsonObject().get("alpha").getAsInt());
  }
}
