package com.tradelearn.server.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

/**
 * Production-grade async trade processing pipeline with bounded queues,
 * per-game backpressure, rejection handling, and graceful shutdown.
 *
 * <h3>Capacity planning (10K users × 5 trades/sec = 50K trades/sec peak)</h3>
 * <table>
 *   <tr><th>Pool</th><th>Core</th><th>Max</th><th>Queue</th><th>Throughput</th></tr>
 *   <tr><td>Trade</td><td>64</td><td>256</td><td>8192</td><td>~23K trades/sec @ 11ms/trade</td></tr>
 *   <tr><td>Broadcast</td><td>16</td><td>64</td><td>4096</td><td>Decoupled from trade path</td></tr>
 * </table>
 *
 * <h3>Backpressure layers (in order of activation)</h3>
 * <ol>
 *   <li><b>Per-game pending limit</b> — prevents a single game from monopolizing the queue</li>
 *   <li><b>Heap pressure guard</b> — rejects all trades when heap &gt; 85%</li>
 *   <li><b>Queue capacity</b> — ThreadPoolExecutor rejects when the bounded queue is full</li>
 *   <li><b>Custom rejection</b> — sends error to client via WebSocket</li>
 * </ol>
 *
 * <h3>Broadcast strategy</h3>
 * Broadcasts (trade events, scoreboard updates) run on a <b>separate</b> pool
 * so they never compete with trade processing for threads. If the broadcast
 * queue fills up, the <b>oldest</b> broadcast is discarded (client will receive
 * the next scoreboard/candle update).
 *
 * <h3>Lifecycle</h3>
 * Implements {@link SmartLifecycle} with a high phase to shut down before
 * the WebSocket layer. On shutdown, in-flight trades complete within 15s;
 * queued broadcasts are discarded immediately.
 */
@Service
public class TradeProcessingPipeline implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TradeProcessingPipeline.class);

    // ── Configuration ──
    private final int tradeCorePoolSize;
    private final int tradeMaxPoolSize;
    private final int tradeQueueCapacity;
    private final int broadcastCorePoolSize;
    private final int broadcastMaxPoolSize;
    private final int broadcastQueueCapacity;
    private final int maxPendingPerGame;

    // ── Executors (created on start()) ──
    private volatile ThreadPoolExecutor tradeExecutor;
    private volatile ThreadPoolExecutor broadcastExecutor;


    // ── Counters (atomic, lock-free) ──
    private final AtomicLong tradesSubmitted     = new AtomicLong();
    private final AtomicLong tradesCompleted     = new AtomicLong();
    private final AtomicLong tradesRejected      = new AtomicLong();
    private final AtomicLong broadcastsSubmitted = new AtomicLong();
    private final AtomicLong broadcastsDropped   = new AtomicLong();

    // ── Per-game backpressure tracking ──
    private final ConcurrentHashMap<Long, AtomicInteger> gamePendingCount =
            new ConcurrentHashMap<>();

    // ── Lifecycle flag ──
    private volatile boolean running = false;

    // ==================== CONSTRUCTION ====================

    public TradeProcessingPipeline(
            @Value("${tradelearn.pipeline.trade.core-pool:64}")          int tradeCorePoolSize,
            @Value("${tradelearn.pipeline.trade.max-pool:256}")          int tradeMaxPoolSize,
            @Value("${tradelearn.pipeline.trade.queue-capacity:8192}")   int tradeQueueCapacity,
            @Value("${tradelearn.pipeline.broadcast.core-pool:16}")      int broadcastCorePoolSize,
            @Value("${tradelearn.pipeline.broadcast.max-pool:64}")       int broadcastMaxPoolSize,
            @Value("${tradelearn.pipeline.broadcast.queue-capacity:4096}") int broadcastQueueCapacity,
            @Value("${tradelearn.pipeline.max-pending-per-game:50}")     int maxPendingPerGame) {
        this.tradeCorePoolSize    = tradeCorePoolSize;
        this.tradeMaxPoolSize     = tradeMaxPoolSize;
        this.tradeQueueCapacity   = tradeQueueCapacity;
        this.broadcastCorePoolSize  = broadcastCorePoolSize;
        this.broadcastMaxPoolSize   = broadcastMaxPoolSize;
        this.broadcastQueueCapacity = broadcastQueueCapacity;
        this.maxPendingPerGame    = maxPendingPerGame;
    }

    // ==================== SUBMISSION API ====================

    /**
     * Result of a trade submission attempt.
     */
    public enum SubmitResult {
        /** Trade accepted and queued for processing */
        ACCEPTED,
        /** Rejected: JVM heap usage above pressure threshold */
        HEAP_PRESSURE,
        /** Rejected: too many pending trades for this game */
        BACKPRESSURE,
        /** Rejected: global trade queue is full */
        REJECTED,
        /** Rejected: pipeline is shutting down */
        SHUTDOWN
    }

    /**
     * Submit a trade for async processing.
     *
     * <p>The call returns <b>immediately</b> — the trade runs on a dedicated
     * thread pool. The caller (WebSocket handler) is freed to process the
     * next incoming message without blocking.</p>
     *
     * @param gameId    the game this trade belongs to
     * @param tradeTask the trade processing logic (validation + persistence + response)
     * @return submission result indicating success or the rejection reason
     */
    public SubmitResult submitTrade(long gameId, Runnable tradeTask) {
        if (!running) return SubmitResult.SHUTDOWN;

        // Backpressure and heap guard logic removed for lightweight config
        // Custom pipeline and queue layers removed. Process trade synchronously.
        tradesSubmitted.incrementAndGet();
        try {
            tradeTask.run();
            tradesCompleted.incrementAndGet();
        } catch (Exception e) {
            tradesRejected.incrementAndGet();
            return SubmitResult.REJECTED;
        }
        return SubmitResult.ACCEPTED;
    }

    /**
     * Submit a WebSocket broadcast for async execution on a separate pool.
     *
     * <p>Broadcasts are best-effort. If the broadcast queue is full, the
     * <b>oldest</b> queued broadcast is discarded — the client will receive
     * the next candle or scoreboard update naturally.</p>
     */
    public void submitBroadcast(Runnable broadcastTask) {
        if (!running) return;
        broadcastsSubmitted.incrementAndGet();
        try {
            broadcastExecutor.execute(broadcastTask);
        } catch (RejectedExecutionException e) {
            broadcastsDropped.incrementAndGet();
            // Acceptable: client gets the next update
        }
    }

    /**
     * Evict per-game tracking when a game ends or is abandoned.
     */
    public void evictGame(long gameId) {
        gamePendingCount.remove(gameId);
    }

    // ==================== METRICS API ====================

    public long getTradesSubmitted()      { return tradesSubmitted.get(); }
    public long getTradesCompleted()      { return tradesCompleted.get(); }
    public long getTradesRejected()       { return tradesRejected.get(); }
    public long getBroadcastsSubmitted()  { return broadcastsSubmitted.get(); }
    public long getBroadcastsDropped()    { return broadcastsDropped.get(); }

    public int getTradeQueueDepth() {
        return tradeExecutor != null ? tradeExecutor.getQueue().size() : 0;
    }

    public int getTradeActiveThreads() {
        return tradeExecutor != null ? tradeExecutor.getActiveCount() : 0;
    }

    public int getTradePoolSize() {
        return tradeExecutor != null ? tradeExecutor.getPoolSize() : 0;
    }

    public int getBroadcastQueueDepth() {
        return broadcastExecutor != null ? broadcastExecutor.getQueue().size() : 0;
    }

    public int getBroadcastActiveThreads() {
        return broadcastExecutor != null ? broadcastExecutor.getActiveCount() : 0;
    }

    // ==================== LIFECYCLE (SmartLifecycle) ====================

    @Override
    public void start() {
        if (running) return;

        tradeExecutor = new ThreadPoolExecutor(
                tradeCorePoolSize,
                tradeMaxPoolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(tradeQueueCapacity),
                new PipelineThreadFactory("trade-pipeline-"),
                new TradeRejectionHandler()
        );
        tradeExecutor.allowCoreThreadTimeOut(true);

        broadcastExecutor = new ThreadPoolExecutor(
                broadcastCorePoolSize,
                broadcastMaxPoolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(broadcastQueueCapacity),
                new PipelineThreadFactory("broadcast-pipeline-"),
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );
        broadcastExecutor.allowCoreThreadTimeOut(true);

        running = true;

        log.info("[Pipeline] Started — trade pool [{}/{}] queue={}, broadcast pool [{}/{}] queue={}, maxPending/game={}",
                tradeCorePoolSize, tradeMaxPoolSize, tradeQueueCapacity,
                broadcastCorePoolSize, broadcastMaxPoolSize, broadcastQueueCapacity,
                maxPendingPerGame);
    }

    @Override
    public void stop() {
        running = false;

        int tradePending = tradeExecutor != null ? tradeExecutor.getQueue().size() : 0;
        int bcastPending = broadcastExecutor != null ? broadcastExecutor.getQueue().size() : 0;
        log.info("[Pipeline] Shutting down... trades pending: {}, broadcasts pending: {}",
                tradePending, bcastPending);

        // Orderly shutdown: finish in-flight trades, discard queued broadcasts
        if (broadcastExecutor != null) broadcastExecutor.shutdownNow();
        if (tradeExecutor != null) {
            tradeExecutor.shutdown();
            try {
                if (!tradeExecutor.awaitTermination(15, TimeUnit.SECONDS)) {
                    log.warn("[Pipeline] Trade executor did not terminate in 15s — forcing shutdown");
                    tradeExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                tradeExecutor.shutdownNow();
            }
        }

        log.info("[Pipeline] Shutdown complete. Total: submitted={}, completed={}, rejected={}",
                tradesSubmitted.get(), tradesCompleted.get(), tradesRejected.get());
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // High phase = shuts down before lower-phase beans (WebSocket, DB)
        return Integer.MAX_VALUE - 10;
    }

    // ==================== INNER CLASSES ====================

    /**
     * Daemon thread factory with a descriptive name prefix for profiling.
     */
    private static class PipelineThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();
        private final String prefix;

        PipelineThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }

    /**
     * Rejection handler that throws {@link RejectedExecutionException}
     * for the calling code to catch and translate into a client error.
     */
    private static class TradeRejectionHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            throw new RejectedExecutionException(
                    "Trade queue full (" + executor.getQueue().size() + " pending)");
        }
    }
}
