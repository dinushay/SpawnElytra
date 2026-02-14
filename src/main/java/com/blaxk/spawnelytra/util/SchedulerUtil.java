package com.blaxk.spawnelytra.util;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public enum SchedulerUtil {
    ;

    private static final Runnable RETIRED_CALLBACK = () -> { };

    private static final boolean FOLIA = hasMethod(Bukkit.class, "getGlobalRegionScheduler")
        && hasMethod(Bukkit.class, "getAsyncScheduler");

    public interface TaskHandle {
        void cancel();
    }

    private static final class BukkitTaskHandle implements TaskHandle {
        private final BukkitTask handle;
        BukkitTaskHandle(final BukkitTask handle) { this.handle = handle; }
        @Override public void cancel() { if (handle != null) this.handle.cancel(); }
    }

    private static final class ReflectiveTaskHandle implements TaskHandle {
        private final Object handle;

        ReflectiveTaskHandle(final Object handle) {
            this.handle = handle;
        }

        @Override
        public void cancel() {
            if (this.handle == null) {
                return;
            }

            try {
                final Method cancel = this.handle.getClass().getMethod("cancel");
                cancel.invoke(this.handle);
            } catch (final ReflectiveOperationException ignored) {
            }
        }
    }

    public static TaskHandle runAsync(final Plugin plugin, final Runnable task) {
        if (!FOLIA) {
            final BukkitTask t = Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            return new BukkitTaskHandle(t);
        }

        try {
            final Object asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            final Method runNow = asyncScheduler.getClass().getMethod("runNow", Plugin.class, Consumer.class);
            final Object scheduledTask = runNow.invoke(asyncScheduler, plugin, (Consumer<Object>) ignored -> task.run());
            return new ReflectiveTaskHandle(scheduledTask);
        } catch (final ReflectiveOperationException e) {
            plugin.getLogger().warning("Failed to use Folia async scheduler, falling back to Bukkit scheduler.");
            final BukkitTask t = Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            return new BukkitTaskHandle(t);
        }
    }

    public static TaskHandle runAsyncRepeating(final Plugin plugin, final Runnable task, final long initialDelayTicks, final long periodTicks) {
        if (!FOLIA) {
            final BukkitTask t = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, initialDelayTicks, periodTicks);
            return new BukkitTaskHandle(t);
        }

        try {
            final Object asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            final Method runAtFixedRate = asyncScheduler.getClass().getMethod(
                "runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class
            );

            final Object scheduledTask = runAtFixedRate.invoke(
                asyncScheduler,
                plugin,
                (Consumer<Object>) ignored -> task.run(),
                ticksToMillis(initialDelayTicks),
                ticksToMillis(periodTicks),
                TimeUnit.MILLISECONDS
            );

            return new ReflectiveTaskHandle(scheduledTask);
        } catch (final ReflectiveOperationException e) {
            plugin.getLogger().warning("Failed to use Folia async repeating scheduler, falling back to Bukkit scheduler.");
            final BukkitTask t = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, initialDelayTicks, periodTicks);
            return new BukkitTaskHandle(t);
        }
    }

    public static TaskHandle runNow(final Plugin plugin, final Runnable task) {
        if (!FOLIA) {
            final BukkitTask t = Bukkit.getScheduler().runTask(plugin, task);
            return new BukkitTaskHandle(t);
        }

        return runGlobalNow(plugin, task);
    }

    public static TaskHandle runSync(final Plugin plugin, final Runnable task) {
        return runNow(plugin, task);
    }

    public static TaskHandle runAtEntityNow(final Plugin plugin, final Player entity, final Runnable task) {
        if (!FOLIA) {
            return new BukkitTaskHandle(Bukkit.getScheduler().runTask(plugin, task));
        }

        return runEntityNow(plugin, entity, task);
    }

    public static TaskHandle runAtEntityLater(final Plugin plugin, final Player entity, final long delayTicks, final Runnable task) {
        if (!FOLIA) {
            return new BukkitTaskHandle(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
        }

        return runEntityLater(plugin, entity, delayTicks, task);
    }

    public static TaskHandle runAtEntityTimer(final Plugin plugin, final Player entity, final long initialDelayTicks, final long periodTicks, final Runnable task) {
        if (!FOLIA) {
            return new BukkitTaskHandle(Bukkit.getScheduler().runTaskTimer(plugin, task, initialDelayTicks, periodTicks));
        }

        return runEntityTimer(plugin, entity, initialDelayTicks, periodTicks, task);
    }

    private static TaskHandle runGlobalNow(final Plugin plugin, final Runnable task) {
        try {
            final Object globalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            final Method run = globalRegionScheduler.getClass().getMethod("run", Plugin.class, Consumer.class);
            final Object scheduledTask = run.invoke(globalRegionScheduler, plugin, (Consumer<Object>) ignored -> task.run());
            return new ReflectiveTaskHandle(scheduledTask);
        } catch (final ReflectiveOperationException e) {
            plugin.getLogger().warning("Failed to use Folia global scheduler.");
            return new ReflectiveTaskHandle(null);
        }
    }

    private static TaskHandle runEntityNow(final Plugin plugin, final Player entity, final Runnable task) {
        try {
            final Object entityScheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            final Method run = entityScheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            final Object scheduledTask = run.invoke(entityScheduler, plugin, (Consumer<Object>) ignored -> task.run(), RETIRED_CALLBACK);
            return new ReflectiveTaskHandle(scheduledTask);
        } catch (final ReflectiveOperationException e) {
            plugin.getLogger().warning("Failed to use Folia entity scheduler, using global Folia scheduler as fallback.");
            return runGlobalNow(plugin, task);
        }
    }

    private static TaskHandle runEntityLater(final Plugin plugin, final Player entity, final long delayTicks, final Runnable task) {
        try {
            final Object entityScheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            final Method runDelayed = entityScheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);
            final Object scheduledTask = runDelayed.invoke(entityScheduler, plugin, (Consumer<Object>) ignored -> task.run(), RETIRED_CALLBACK, delayTicks);
            return new ReflectiveTaskHandle(scheduledTask);
        } catch (final ReflectiveOperationException e) {
            plugin.getLogger().warning("Failed to use Folia delayed entity scheduler, using global Folia scheduler as fallback.");
            return runGlobalLater(plugin, delayTicks, task);
        }
    }

    private static TaskHandle runEntityTimer(final Plugin plugin, final Player entity, final long initialDelayTicks, final long periodTicks, final Runnable task) {
        try {
            final Object entityScheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            final Method runAtFixedRate = entityScheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Consumer.class, Runnable.class, long.class, long.class);
            final Object scheduledTask = runAtFixedRate.invoke(entityScheduler, plugin, (Consumer<Object>) ignored -> task.run(), RETIRED_CALLBACK, initialDelayTicks, periodTicks);
            return new ReflectiveTaskHandle(scheduledTask);
        } catch (final ReflectiveOperationException e) {
            plugin.getLogger().warning("Failed to use Folia repeating entity scheduler, using global Folia scheduler as fallback.");
            return runGlobalTimer(plugin, initialDelayTicks, periodTicks, task);
        }
    }

    private static TaskHandle runGlobalLater(final Plugin plugin, final long delayTicks, final Runnable task) {
        try {
            final Object globalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            final Method runDelayed = globalRegionScheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
            final Object scheduledTask = runDelayed.invoke(globalRegionScheduler, plugin, (Consumer<Object>) ignored -> task.run(), delayTicks);
            return new ReflectiveTaskHandle(scheduledTask);
        } catch (final ReflectiveOperationException e) {
            plugin.getLogger().warning("Failed to use Folia delayed global scheduler.");
            return new ReflectiveTaskHandle(null);
        }
    }

    private static TaskHandle runGlobalTimer(final Plugin plugin, final long initialDelayTicks, final long periodTicks, final Runnable task) {
        try {
            final Object globalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            final Method runAtFixedRate = globalRegionScheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
            final Object scheduledTask = runAtFixedRate.invoke(globalRegionScheduler, plugin, (Consumer<Object>) ignored -> task.run(), initialDelayTicks, periodTicks);
            return new ReflectiveTaskHandle(scheduledTask);
        } catch (final ReflectiveOperationException e) {
            plugin.getLogger().warning("Failed to use Folia repeating global scheduler.");
            return new ReflectiveTaskHandle(null);
        }
    }

    private static boolean hasMethod(final Class<?> type, final String methodName) {
        try {
            type.getMethod(methodName);
            return true;
        } catch (final NoSuchMethodException e) {
            return false;
        }
    }

    private static long ticksToMillis(final long ticks) {
        return Math.max(0L, ticks) * 50L;
    }
}
