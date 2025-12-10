package star.sequoia2.utils;

import com.collarmc.pounce.EventBus;
import com.collarmc.pounce.Subscribe;
import star.sequoia2.events.PlayerTickEvent;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import static star.sequoia2.client.SeqClient.mc;

/**
 * Minimal tick-based scheduler for seqmod. Runs actions after N client ticks.
 */
public final class TickScheduler {
    private static final ConcurrentLinkedQueue<Scheduled> queue = new ConcurrentLinkedQueue<>();
    private static final TickScheduler INSTANCE = new TickScheduler();

    private TickScheduler() {}

    public static void init(EventBus bus) {
        bus.subscribe(INSTANCE);
    }

    public static void scheduleTicks(Runnable action, int ticks) {
        if (action == null) return;
        if (ticks <= 0) {
            mc.execute(action);
            return;
        }
        queue.add(new Scheduled(Math.max(1, ticks), action));
    }

    @Subscribe
    public void onTick(PlayerTickEvent event) {
        Iterator<Scheduled> it = queue.iterator();
        while (it.hasNext()) {
            Scheduled s = it.next();
            int remaining = s.ticks - 1;
            if (remaining <= 0) {
                it.remove();
                mc.execute(s.action);
            } else {
                s.ticks = remaining;
            }
        }
    }

    private static final class Scheduled {
        int ticks;
        final Runnable action;

        Scheduled(int ticks, Runnable action) {
            this.ticks = ticks;
            this.action = action;
        }
    }
}
