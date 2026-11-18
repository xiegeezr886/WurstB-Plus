package net.wurstclient.epsilon;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.wurstclient.hacks.*;
import org.junit.jupiter.api.Test;

final class CombatSettingsTest {
  @Test
  void allReplacementModulesRegisterTheirSettings() {
    
    net.wurstclient.test.NativeTestBootstrap.bootstrap();
    for (Class<?> type :
        new Class<?>[] {
          AntiBotHack.class,
          ReachHack.class,
          DelayRemoverHack.class,
          KillauraHack.class,
          MultiAuraHack.class,
          AimAssistHack.class,
          BowAimbotHack.class,
          CriticalsHack.class,
          TriggerBotHack.class,
          AutoClickerHack.class,
          AutoArmorHack.class,
          AutoWeaponHack.class,
          AutoTotemHack.class,
          KeepSprintHack.class,
          AntiKnockbackHack.class,
          BacktrackHack.class,
          CrystalAuraHack.class,
          TpAuraHack.class,
          HitboxesHack.class,
          AutoGapHack.class,
          ScaffoldWalkHack.class
        }) assertDoesNotThrow(() -> type.getConstructor().newInstance(), type.getSimpleName());
  }
}
