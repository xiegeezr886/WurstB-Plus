package kaptainwutax.seedcrackerX.cracker.decorator;

import com.seedfinding.latticg.reversal.DynamicProgram;
import com.seedfinding.latticg.reversal.calltype.java.JavaCalls;
import com.seedfinding.latticg.util.LCG;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

public class FullFungusData {
   public final ArrayList<Integer> layerSizes = new ArrayList<>();
   public final int[][][] layers;
   public final ArrayList<Integer> vines = new ArrayList<>();
   public final ArrayList<Integer> bigtrunkData = new ArrayList<>();
   public final int estimatedData;
   public boolean big;
   public int height;
   public int vineLayerSize;

   public FullFungusData(
      List<Integer> layerSizes,
      int[][][] layers,
      ArrayList<Integer> vines,
      boolean big,
      int height,
      int vineLayerSize,
      ArrayList<Integer> bigTrunkData,
      int estimatedData
   ) {
      this.layerSizes.addAll(layerSizes);
      this.layers = (int[][][])layers.clone();
      this.vines.addAll(vines);
      this.big = big;
      this.height = height;
      this.vineLayerSize = vineLayerSize;
      this.bigtrunkData.addAll(bigTrunkData);
      this.estimatedData = estimatedData;
   }

   public static FullFungusData getBestFungus(List<FullFungusData> fungusList) {
      int data = 0;
      FullFungusData out = null;

      for (FullFungusData fungus : fungusList) {
         int fungusData = fungus.estimatedData;
         if (fungusData > data) {
            data = fungusData;
            out = fungus;
         }
      }

      return out;
   }

   public LongStream crackSeed() {
      int doppelt = 0;
      if (this.height > 7 && this.height % 2 == 0) {
         doppelt = 1;
         if (this.height > 13) {
            doppelt = 2;
         }
      }

      DynamicProgram device = DynamicProgram.create(LCG.JAVA);
      if (doppelt < 2) {
         device.skip(2L);
      } else {
         device.skip(1L);
         device.add(JavaCalls.nextInt(12).equalTo(0));
      }

      if (this.big) {
         device.add(JavaCalls.nextFloat().betweenII(0.0F, 0.06F));
      } else {
         device.skip(1L);
      }

      if (this.big) {
         for (int blockdata : this.bigtrunkData) {
            if (blockdata == 0) {
               device.skip(1L);
            } else if (blockdata == 1) {
               device.add(JavaCalls.nextFloat().betweenII(0.0F, 0.1F));
            }
         }
      }

      device.skip(2L);
      ArrayList<Integer> done = new ArrayList<>();

      for (int j = 3; j > 0; j--) {
         for (int i = 0; i < this.vineLayerSize * 8; i++) {
            if (this.vines.get(i) == 0 && !done.contains(i)) {
               device.skip(1L);
            } else if (this.vines.get(i) == j) {
               done.add(i);
               device.add(JavaCalls.nextFloat().betweenII(0.0F, 0.15F));
            } else if (!done.contains(i)) {
               device.skip(1L);
            }
         }

         device.skip(1L);
      }

      int layer = 0;

      for (int size : this.layerSizes) {
         size *= 2;

         for (int x = 0; x <= size; x++) {
            boolean siteX = x == 0 || x == size;

            for (int z = 0; z <= size; z++) {
               boolean siteZ = z == 0 || z == size;
               int relativePos = (siteX ? 1 : 0) + (siteZ ? 1 : 0);
               int blockType = this.layers[layer][x][z];
               this.generateBlock(relativePos, blockType, device);
            }
         }

         device.skip(1L);
         layer++;
      }

      return device.reverse();
   }

   private void generateBlock(int relativePos, int blockType, DynamicProgram device) {
      if (blockType != 3) {
         switch (relativePos) {
            case 0:
               switch (blockType) {
                  case 0:
                     device.skip(2L);
                     return;
                  case 1:
                     device.skip(3L);
                     return;
                  case 2:
                     device.add(JavaCalls.nextFloat().betweenII(0.0F, 0.1F));
                     return;
                  default:
                     return;
               }
            case 1:
               switch (blockType) {
                  case 0:
                     device.skip(1L);
                     device.add(JavaCalls.nextFloat().betweenII(0.98F, 1.0F));
                     return;
                  case 1:
                     device.skip(3L);
                     return;
                  case 2:
                     device.add(JavaCalls.nextFloat().betweenII(0.0F, 5.0E-4F));
                     return;
                  default:
                     return;
               }
            case 2:
               switch (blockType) {
                  case 0:
                     device.skip(2L);
                     break;
                  case 1:
                     device.skip(3L);
                     break;
                  case 2:
                     device.add(JavaCalls.nextFloat().betweenII(0.0F, 0.01F));
               }
         }
      }
   }
}
