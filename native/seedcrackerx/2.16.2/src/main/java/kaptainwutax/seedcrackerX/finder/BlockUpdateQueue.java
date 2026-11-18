package kaptainwutax.seedcrackerX.finder;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;

public class BlockUpdateQueue {
   private final Queue<Pair<Thread, ArrayList<BlockPos>>> blocksAndAction = new LinkedList<>();
   private final HashSet<BlockPos> alreadyChecked = new HashSet<>();

   public boolean add(ArrayList<BlockPos> blockPoses, BlockPos originPos, Thread operationAtEnd) {
      if (this.alreadyChecked.add(originPos)) {
         this.blocksAndAction.add(new Pair(operationAtEnd, blockPoses));
         return true;
      } else {
         return false;
      }
   }

   public void tick() {
      if (!this.blocksAndAction.isEmpty()) {
         Pair<Thread, ArrayList<BlockPos>> current = this.blocksAndAction.peek();
         ArrayList<BlockPos> currentBlocks = (ArrayList<BlockPos>)current.getSecond();

         for (int i = 0; i < 5; i++) {
            if (currentBlocks.isEmpty()) {
               ((Thread)current.getFirst()).start();
               this.blocksAndAction.remove();
               if (this.blocksAndAction.isEmpty()) {
                  return;
               }

               current = this.blocksAndAction.peek();
               currentBlocks = (ArrayList<BlockPos>)current.getSecond();
            }

            if (Minecraft.getInstance().getConnection() == null) {
               this.blocksAndAction.clear();
               return;
            }

            ServerboundPlayerActionPacket p = new ServerboundPlayerActionPacket(Action.ABORT_DESTROY_BLOCK, currentBlocks.remove(0), Direction.DOWN);
            Minecraft.getInstance().getConnection().send(p);
         }
      }
   }
}
