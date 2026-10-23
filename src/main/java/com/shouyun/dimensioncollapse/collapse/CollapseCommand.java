package com.shouyun.dimensioncollapse.collapse;

import java.util.List;
import java.util.function.BiFunction;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator command for starting and controlling collapses until the item-based gameplay exists. */
public final class CollapseCommand {
    public static final float DEFAULT_INITIAL_RADIUS = 1.0f;
    public static final float DEFAULT_INITIAL_SPEED = 0.15f;
    public static final float DEFAULT_ACCELERATION = 0.04f;
    private static final double PICK_RANGE = 160.0;

    private CollapseCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("collapse")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("start")
                        .executes(ctx -> start(ctx, lookTarget(ctx.getSource()),
                                DEFAULT_INITIAL_RADIUS, DEFAULT_INITIAL_SPEED, DEFAULT_ACCELERATION))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> start(ctx, BlockPosArgument.getBlockPos(ctx, "pos"),
                                        DEFAULT_INITIAL_RADIUS, DEFAULT_INITIAL_SPEED, DEFAULT_ACCELERATION))
                                .then(Commands.argument("radius", FloatArgumentType.floatArg(0.0f, 4096.0f))
                                        .then(Commands.argument("speed", FloatArgumentType.floatArg(0.0f, 1000.0f))
                                                .then(Commands.argument("acceleration", FloatArgumentType.floatArg(0.0f, 100.0f))
                                                        .executes(ctx -> start(ctx, BlockPosArgument.getBlockPos(ctx, "pos"),
                                                                FloatArgumentType.getFloat(ctx, "radius"),
                                                                FloatArgumentType.getFloat(ctx, "speed"),
                                                                FloatArgumentType.getFloat(ctx, "acceleration"))))))))
                .then(Commands.literal("list").executes(CollapseCommand::list))
                .then(modify("freeze", "command.dimensioncollapse.frozen", CollapseSource::freeze))
                .then(modify("resume", "command.dimensioncollapse.resumed", CollapseSource::resume))
                .then(Commands.literal("stop")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ctx -> {
                            ServerLevel level = ctx.getSource().getLevel();
                            int id = IntegerArgumentType.getInteger(ctx, "id");
                            if (!CollapseData.get(level).remove(id)) return unknown(ctx, id);
                            CollapseManager.broadcast(level);
                            return reply(ctx, Component.translatable("command.dimensioncollapse.stopped", id));
                        })))
                .then(Commands.literal("clear").executes(ctx -> {
                    ServerLevel level = ctx.getSource().getLevel();
                    int count = CollapseData.get(level).clear();
                    CollapseManager.broadcast(level);
                    return reply(ctx, Component.translatable("command.dimensioncollapse.cleared", count));
                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> modify(String name, String messageKey,
                                                                     BiFunction<CollapseSource, Long, CollapseSource> transition) {
        return Commands.literal(name)
                .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ctx -> {
                    ServerLevel level = ctx.getSource().getLevel();
                    int id = IntegerArgumentType.getInteger(ctx, "id");
                    CollapseData data = CollapseData.get(level);
                    CollapseSource source = data.find(id).orElse(null);
                    if (source == null) return unknown(ctx, id);
                    long now = level.getGameTime();
                    CollapseSource updated = transition.apply(source, now);
                    data.replace(updated);
                    CollapseManager.broadcast(level);
                    return reply(ctx, Component.translatable(messageKey, id, String.format("%.1f", updated.radius(now))));
                }));
    }

    private static BlockPos lookTarget(CommandSourceStack source) {
        Entity entity = source.getEntity();
        if (entity == null) return BlockPos.containing(source.getPosition());
        HitResult hit = entity.pick(PICK_RANGE, 1.0f, false);
        return hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                ? blockHit.getBlockPos()
                : BlockPos.containing(hit.getLocation());
    }

    private static int start(CommandContext<CommandSourceStack> ctx, BlockPos pos, float r0, float v0, float a) {
        ServerLevel level = ctx.getSource().getLevel();
        CollapseSource source = CollapseManager.start(level, pos, r0, v0, a);
        return reply(ctx, Component.translatable("command.dimensioncollapse.started",
                source.id(), pos.getX(), pos.getY(), pos.getZ()));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        List<CollapseSource> sources = CollapseData.get(level).sources();
        if (sources.isEmpty()) {
            return reply(ctx, Component.translatable("command.dimensioncollapse.list.empty"));
        }
        long now = level.getGameTime();
        for (CollapseSource source : sources) {
            BlockPos c = source.center();
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    source.frozen() ? "command.dimensioncollapse.list.entry_frozen" : "command.dimensioncollapse.list.entry",
                    source.id(), c.getX(), c.getY(), c.getZ(),
                    String.format("%.1f", source.radius(now)), String.format("%.2f", source.speed(now))), false);
        }
        return sources.size();
    }

    private static int unknown(CommandContext<CommandSourceStack> ctx, int id) {
        ctx.getSource().sendFailure(Component.translatable("command.dimensioncollapse.unknown", id));
        return 0;
    }

    private static int reply(CommandContext<CommandSourceStack> ctx, Component message) {
        ctx.getSource().sendSuccess(() -> message, true);
        return 1;
    }
}
