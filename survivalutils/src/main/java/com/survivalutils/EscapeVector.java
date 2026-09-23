package com.survivalutils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class EscapeVector {
    public record Result(String direction, int clearBlocks, double score, String reason) {}

    private static final int[][] DIRS = {
            {0,-1},{1,-1},{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1}
    };
    private static final String[] NAMES = {"N","NE","E","SE","S","SW","W","NW"};

    private EscapeVector() {}

    public static Result calculate(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            return new Result("NONE",0,-999,"No world data");
        }

        Result best = new Result("NONE",0,-999,"No clear path");
        BlockPos base = client.player.getBlockPos();

        for (int i=0;i<DIRS.length;i++) {
            int dx=DIRS[i][0], dz=DIRS[i][1];
            int clear=0;
            double score=0;
            boolean lava=false;
            boolean drop=false;

            for (int step=1;step<=14;step++) {
                BlockPos feet=base.add(dx*step,0,dz*step);
                BlockPos head=feet.up();
                BlockPos floor=feet.down();

                boolean bodyClear=client.world.getBlockState(feet).isAir()
                        && client.world.getBlockState(head).isAir();
                if (!bodyClear) break;

                if (client.world.getFluidState(feet).isIn(FluidTags.LAVA)
                        || client.world.getFluidState(floor).isIn(FluidTags.LAVA)) {
                    lava=true;
                    break;
                }

                if (client.world.getBlockState(floor).isAir()) {
                    int safeDepth=0;
                    BlockPos scan=floor;
                    while(safeDepth<5 && client.world.getBlockState(scan).isAir()) {
                        safeDepth++;
                        scan=scan.down();
                    }
                    if (safeDepth>=4) {
                        drop=true;
                        break;
                    }
                }

                clear++;
                score += 2.4;
            }

            Vec3d probe=new Vec3d(client.player.getX(),client.player.getY(),client.player.getZ()).add(dx*8.0,0,dz*8.0);
            score += threatSeparation(client,probe);
            if (lava) score-=45;
            if (drop) score-=30;
            if (clear<4) score-=25;

            String reason = clear>=10 ? "long clear route"
                    : clear>=6 ? "usable clear route"
                    : clear>=3 ? "short route"
                    : "blocked";

            Result r=new Result(NAMES[i],clear,score,reason);
            if(r.score()>best.score()) best=r;
        }
        return best;
    }

    private static double threatSeparation(MinecraftClient client, Vec3d probe) {
        List<Entity> threats=new ArrayList<>();

        for(PlayerEntity p:client.world.getPlayers()) {
            if(p==client.player) continue;
            ThreatMemoryManager.Contact c=ThreatMemoryManager.get(p);
            if(c!=null && "FRIEND".equals(c.tag)) continue;
            if(p.squaredDistanceTo(client.player)<=32*32) threats.add(p);
        }

        threats.addAll(client.world.getEntitiesByClass(
                HostileEntity.class,
                client.player.getBoundingBox().expand(24),
                Entity::isAlive));

        if(threats.isEmpty()) return 10;

        double nearest=threats.stream()
                .mapToDouble(e->new Vec3d(e.getX(),e.getY(),e.getZ()).squaredDistanceTo(probe))
                .min().orElse(0);
        return Math.min(30,Math.sqrt(nearest)*1.4);
    }
}
