package com.smartcampus.notify;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Keeps every open SSE connection in memory, grouped by order id, vendor id, and menu vendor id.
 * Requires @EnableScheduling on the application class (needed for the heartbeat).
 */
@Component
public class SseEmitterRegistry {

    // Connection is closed after this; the client reconnects automatically
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<Long, List<SseEmitter>> orderStreams = new ConcurrentHashMap<>();
    private final Map<Long, List<SseEmitter>> vendorStreams = new ConcurrentHashMap<>();
    private final Map<Long, List<SseEmitter>> menuStreams = new ConcurrentHashMap<>();

    public SseEmitter subscribeToOrder(long orderId) {
        return register(orderStreams, orderId);
    }

    public SseEmitter subscribeToVendor(long vendorId) {
        return register(vendorStreams, vendorId);
    }

    public SseEmitter subscribeToMenu(long vendorId) {
        return register(menuStreams, vendorId);
    }

    public void sendToOrder(long orderId, String event, Object data) {
        broadcast(orderStreams, orderId, event, data);
    }

    public void sendToVendor(long vendorId, String event, Object data) {
        broadcast(vendorStreams, vendorId, event, data);
    }

    public void sendToMenu(long vendorId, String event, Object data) {
        broadcast(menuStreams, vendorId, event, data);
    }

    public boolean hasOrderSubscribers(long orderId) {
        List<SseEmitter> emitters = orderStreams.get(orderId);
        return emitters != null && !emitters.isEmpty();
    }

    /** Send one event to one connection. Also used for the initial snapshot. */
    public void sendTo(SseEmitter emitter, String event, Object data) {
        try {
            // An emitter is not thread-safe: heartbeat and events may collide, so lock it
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
            }
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e); // client is gone; triggers cleanup below
        }
    }

    /** Comment line every 25 s so proxies and load balancers don't close idle connections. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        for (Map<Long, List<SseEmitter>> streams : List.of(orderStreams, vendorStreams, menuStreams)) {
            for (List<SseEmitter> emitters : streams.values()) {
                for (SseEmitter emitter : emitters) {
                    try {
                        synchronized (emitter) {
                            emitter.send(SseEmitter.event().comment("keepalive"));
                        }
                    } catch (IOException | IllegalStateException e) {
                        emitter.completeWithError(e);
                    }
                }
            }
        }
    }

    private SseEmitter register(Map<Long, List<SseEmitter>> streams, long key) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        streams.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // Always remove dead connections, otherwise the map grows forever
        Runnable cleanup = () -> streams.computeIfPresent(key, (k, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });

        emitter.onCompletion(cleanup);
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> cleanup.run());
        return emitter;
    }

    private void broadcast(Map<Long, List<SseEmitter>> streams, long key, String event, Object data) {
        List<SseEmitter> emitters = streams.get(key);
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            sendTo(emitter, event, data);
        }
    }
}
