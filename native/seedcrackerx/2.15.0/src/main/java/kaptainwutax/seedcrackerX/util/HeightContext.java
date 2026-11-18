package kaptainwutax.seedcrackerX.util;

public class HeightContext {
   private final int bottomY;
   private final int topY;

   public HeightContext(int minY, int maxY) {
      this.bottomY = minY;
      this.topY = maxY;
   }

   public int getTopY() {
      return this.topY;
   }

   public int getBottomY() {
      return this.bottomY;
   }

   public int getHeight() {
      return this.topY - this.bottomY;
   }

   public int getDistanceToBottom(int yValue) {
      return yValue - this.bottomY;
   }

   public int getDistanceToTop(int yValue) {
      return this.topY - yValue - 1;
   }
}
