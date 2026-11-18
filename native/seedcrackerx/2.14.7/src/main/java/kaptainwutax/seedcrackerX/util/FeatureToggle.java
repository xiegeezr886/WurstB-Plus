package kaptainwutax.seedcrackerX.util;

public class FeatureToggle {
   private boolean enabled;

   public FeatureToggle(boolean flag) {
      this.enabled = flag;
   }

   public void set(boolean flag) {
      this.enabled = flag;
   }

   public boolean get() {
      return this.enabled;
   }
}
