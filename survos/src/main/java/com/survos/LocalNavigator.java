package com.survos;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

public final class LocalNavigator {
    public enum Status { ARRIVED, MOVING, FAILED }

    private final List<BlockPos> path = new ArrayList<>();
    private BlockPos goal;
    private int goalRange;
    private int index;
    private Vec3d lastPos;
    private int stuckTicks;
    private int repaths;
    private String reason = "idle";

    public String reason() { return reason; }
    public List<BlockPos> path() { return List.copyOf(path); }

    public void reset(MinecraftClient client) {
        path.clear();
        goal = null;
        index = 0;
        stuckTicks = 0;
        repaths = 0;
        clearKeys(client);
        reason = "idle";
    }

    public Status moveNear(MinecraftClient client, BlockPos target, int range) {
        if (client.player == null || client.world == null) return Status.FAILED;
        PlayerEntity p = client.player;
        BlockPos here = new BlockPos(p.getBlockX(), p.getBlockY(), p.getBlockZ());
        if (within(here, target, range)) {
            clearKeys(client);
            reason = "arrived";
            return Status.ARRIVED;
        }

        boolean changed = goal == null || !goal.equals(target) || goalRange != range;
        if (changed || path.isEmpty() || index >= path.size()) {
            goal = target.toImmutable();
            goalRange = range;
            repath(client);
        }
        if (path.isEmpty()) {
            reason = "no local path";
            clearKeys(client);
            return Status.FAILED;
        }

        Vec3d now = p.getEntityPos();
        if (lastPos != null && now.squaredDistanceTo(lastPos) < 0.003) stuckTicks++;
        else stuckTicks = Math.max(0, stuckTicks - 2);
        lastPos = now;

        if (stuckTicks > 32) {
            stuckTicks = 0;
            repaths++;
            repath(client);
            if (path.isEmpty() || repaths > 4) {
                reason = "stuck";
                clearKeys(client);
                return Status.FAILED;
            }
        }

        while (index < path.size()) {
            BlockPos node = path.get(index);
            double dx = node.getX() + 0.5 - now.x;
            double dz = node.getZ() + 0.5 - now.z;
            if (dx * dx + dz * dz < 0.20 && Math.abs(node.getY() - p.getY()) < 1.4) index++;
            else break;
        }
        if (index >= path.size()) {
            repath(client);
            if (path.isEmpty()) return Status.FAILED;
        }

        BlockPos node = path.get(Math.min(index, path.size() - 1));
        lookAt(p, new Vec3d(node.getX() + 0.5, p.getEyeY(), node.getZ() + 0.5));
        client.options.forwardKey.setPressed(true);
        client.options.sprintKey.setPressed(true);
        client.options.jumpKey.setPressed(node.getY() > p.getBlockY() || blockedAhead(client, p));
        reason = "path " + (index + 1) + "/" + path.size();
        return Status.MOVING;
    }

    private void repath(MinecraftClient client) {
        path.clear();
        index = 0;
        if (client.player == null || client.world == null || goal == null) return;
        BlockPos start = new BlockPos(client.player.getBlockX(), client.player.getBlockY(), client.player.getBlockZ());
        List<BlockPos> found = findPath(client.world, start, goal, goalRange, 26, 4200);
        path.addAll(found);
        reason = path.isEmpty() ? "path not found" : "repath";
    }

    private static List<BlockPos> findPath(World world, BlockPos start, BlockPos target, int range, int radius, int maxNodes) {
        record Node(BlockPos pos, double g, double f) {}
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::f));
        Map<BlockPos, Double> g = new HashMap<>();
        Map<BlockPos, BlockPos> came = new HashMap<>();
        BlockPos s = nearestWalkable(world, start);
        if (s == null) return List.of();
        open.add(new Node(s, 0, heuristic(s, target)));
        g.put(s, 0.0);
        int visited = 0;

        while (!open.isEmpty() && visited++ < maxNodes) {
            Node n = open.poll();
            BlockPos pos = n.pos();
            if (within(pos, target, range)) return reconstruct(came, pos);
            if (Math.abs(pos.getX() - start.getX()) > radius || Math.abs(pos.getZ() - start.getZ()) > radius || Math.abs(pos.getY() - start.getY()) > 8) continue;

            for (int[] d : DIRS) {
                int nx = pos.getX() + d[0], nz = pos.getZ() + d[1];
                BlockPos next = chooseY(world, new BlockPos(nx, pos.getY(), nz));
                if (next == null) continue;
                double ng = n.g() + 1.0 + Math.abs(next.getY() - pos.getY()) * 0.35;
                Double old = g.get(next);
                if (old == null || ng < old) {
                    g.put(next, ng);
                    came.put(next, pos);
                    open.add(new Node(next, ng, ng + heuristic(next, target)));
                }
            }
        }
        return List.of();
    }

    private static final int[][] DIRS={{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};

    private static BlockPos chooseY(World w, BlockPos base) {
        BlockPos[] c={base,base.up(),base.down()};
        for (BlockPos p:c) if (walkable(w,p)) return p.toImmutable();
        return null;
    }

    private static BlockPos nearestWalkable(World w, BlockPos p) {
        if (walkable(w,p)) return p.toImmutable();
        if (walkable(w,p.up())) return p.up().toImmutable();
        if (walkable(w,p.down())) return p.down().toImmutable();
        return null;
    }

    public static boolean walkable(World w, BlockPos feet) {
        BlockState a=w.getBlockState(feet), b=w.getBlockState(feet.up()), below=w.getBlockState(feet.down());
        if (!a.getCollisionShape(w,feet).isEmpty()) return false;
        if (!b.getCollisionShape(w,feet.up()).isEmpty()) return false;
        if (!a.getFluidState().isEmpty() || !b.getFluidState().isEmpty()) return false;
        return !below.getCollisionShape(w,feet.down()).isEmpty() && below.getFluidState().isEmpty();
    }

    private static boolean blockedAhead(MinecraftClient client, PlayerEntity p) {
        double yaw=Math.toRadians(p.getYaw());
        int dx=(int)Math.round(-Math.sin(yaw)), dz=(int)Math.round(Math.cos(yaw));
        BlockPos front=new BlockPos(p.getBlockX()+dx,p.getBlockY(),p.getBlockZ()+dz);
        return !client.world.getBlockState(front).getCollisionShape(client.world,front).isEmpty()
                && client.world.getBlockState(front.up()).getCollisionShape(client.world,front.up()).isEmpty();
    }

    private static List<BlockPos> reconstruct(Map<BlockPos,BlockPos> came, BlockPos end) {
        LinkedList<BlockPos> out=new LinkedList<>();
        BlockPos cur=end;
        while (cur!=null) { out.addFirst(cur); cur=came.get(cur); }
        if (!out.isEmpty()) out.removeFirst();
        return out;
    }

    private static double heuristic(BlockPos a, BlockPos b) {
        return Math.abs(a.getX()-b.getX())+Math.abs(a.getZ()-b.getZ())+Math.abs(a.getY()-b.getY())*1.4;
    }

    private static boolean within(BlockPos a, BlockPos b, int r) {
        return Math.abs(a.getX()-b.getX())<=r && Math.abs(a.getZ()-b.getZ())<=r && Math.abs(a.getY()-b.getY())<=2;
    }

    public static void lookAt(PlayerEntity p, Vec3d to) {
        Vec3d from=p.getEyePos();
        double dx=to.x-from.x, dy=to.y-from.y, dz=to.z-from.z;
        double flat=Math.sqrt(dx*dx+dz*dz);
        p.setYaw((float)(MathHelper.atan2(dz,dx)*57.295776)-90f);
        p.setPitch(MathHelper.clamp((float)(-(MathHelper.atan2(dy,flat)*57.295776)),-90f,90f));
    }

    public static void clearKeys(MinecraftClient c) {
        if (c==null || c.options==null) return;
        c.options.forwardKey.setPressed(false);
        c.options.backKey.setPressed(false);
        c.options.leftKey.setPressed(false);
        c.options.rightKey.setPressed(false);
        c.options.jumpKey.setPressed(false);
        c.options.sprintKey.setPressed(false);
        c.options.sneakKey.setPressed(false);
        c.options.attackKey.setPressed(false);
        c.options.useKey.setPressed(false);
    }
}
