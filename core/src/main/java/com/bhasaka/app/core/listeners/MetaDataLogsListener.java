package com.bhasaka.app.core.listeners;


import com.day.cq.replication.ReplicationAction;
import com.day.cq.replication.ReplicationActionType;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;

@Component(
        service = EventHandler.class,
        immediate = true,
        property = {
                EventConstants.EVENT_TOPIC + "=" + ReplicationAction.EVENT_TOPIC,
                EventConstants.EVENT_TOPIC + "=" + "org/apache/sling/api/resource/Resource/ADDED",
                EventConstants.EVENT_TOPIC + "=" + "org/apache/sling/api/resource/Resource/CHANGED",
                EventConstants.EVENT_TOPIC + "=" + "org/apache/sling/api/resource/Resource/REMOVED"
        }
)
public class MetaDataLogsListener implements EventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(MetaDataLogsListener.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Prevents duplicate logs within 8 seconds for the same asset + action
    private static final ConcurrentHashMap<String, Long> CACHE = new ConcurrentHashMap<>();
    private static final long DEBOUNCE_MS = 2000;

    @Override
    public void handleEvent(Event event) {
        String topic = event.getTopic();
        String path = extractPath(event);

        // 1. Must be under /content/dam
        if (path == null || !path.startsWith("/content/dam/")) return;

        // 2. BLOCK ALL INTERNAL NODES (this kills 99% of noise)
        if (path.contains("/jcr:content") ||
                path.contains("/renditions") ||
                path.contains("/metadata") ||
                path.contains("/related")) {
            return;
        }

        // 3. Must be a real file with extension (blocks folders)
        if (!path.matches(".*/[^/]+\\.[a-zA-Z0-9]+$")) {
            return;
        }

        String userId = (String) event.getProperty("userid");
        if (userId == null || userId.isEmpty()) userId = "system";

        String time = LocalDateTime.now().format(DTF);

        // 4. DEBOUNCE: Only log once per asset per action in 8 seconds
        String cacheKey = path + "|" + topic;
        long now = System.currentTimeMillis();
        if (CACHE.putIfAbsent(cacheKey, now) != null) {
            if (now - CACHE.get(cacheKey) < DEBOUNCE_MS) {
                return; // Already logged recently → skip
            }
        }
        CACHE.put(cacheKey, now);

        // FINAL CLEAN LOG — ONLY ONE PER REAL ACTION
        if (ReplicationAction.EVENT_TOPIC.equals(topic)) {
            ReplicationAction action = ReplicationAction.fromEvent(event);
            if (action != null && action.getPath() != null && isValidAssetPath(action.getPath())) {
                if (action.getType() == ReplicationActionType.ACTIVATE) {
                    LOG.info("ASSET PUBLISHED  | User: {} | Path: {} | Time: {}", userId, action.getPath(), time);
                } else if (action.getType() == ReplicationActionType.DEACTIVATE) {
                    LOG.info("ASSET UNPUBLISHED | User: {} | Path: {} | Time: {}", userId, action.getPath(), time);
                }
            }
        }
        else if ("org/apache/sling/api/resource/Resource/ADDED".equals(topic)) {
            LOG.info("ASSET CREATED    | User: {} | Path: {} | Time: {}", userId, path, time);
        }
        else if ("org/apache/sling/api/resource/Resource/CHANGED".equals(topic)) {
            LOG.info("ASSET MODIFIED   | User: {} | Path: {} | Time: {}", userId, path, time);
        }
        else if ("org/apache/sling/api/resource/Resource/REMOVED".equals(topic)) {
            LOG.info("ASSET DELETED    | User: {} | Path: {} | Time: {}", userId, path, time);
        }
    }

    private String extractPath(Event event) {
        Object p = event.getProperty("path");
        if (p instanceof String) return (String) p;
        if (p instanceof String[]) return ((String[]) p)[0];

        Object paths = event.getProperty("paths");
        if (paths instanceof String) return (String) paths;
        if (paths instanceof String[]) return ((String[]) paths)[0];

        return null;
    }

    private boolean isValidAssetPath(String path) {
        return path != null &&
                path.startsWith("/content/dam/") &&
                !path.contains("/jcr:content") &&
                path.matches(".*/[^/]+\\.[a-zA-Z0-9]+$");
    }
}