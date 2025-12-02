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
public class PageLogsListener implements EventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(PageLogsListener.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final ConcurrentHashMap<String, Long> CACHE = new ConcurrentHashMap<>();
    private static final long DEBOUNCE_MS = 2000;

    @Override
    public void handleEvent(Event event) {
        String topic = event.getTopic();
        String path = extractPath(event);

        // 1. Must be under /content (adjust if you have a specific site)
        if (path == null || !path.startsWith("/content/")) return;

        // 2. BLOCK INTERNAL NODES (jcr:content, cq:tags, etc.)
        if (path.contains("/jcr:content") ||
                path.contains("/_jcr") ||
                path.contains("/tags") ||
                path.contains("/metadata")) {
            return;
        }

        String userId = (String) event.getProperty("userid");
        if (userId == null || userId.isEmpty()) userId = "system";

        String time = LocalDateTime.now().format(DTF);

        // 3. DEBOUNCE: Only log once per page per action
        String cacheKey = path + "|" + topic;
        long now = System.currentTimeMillis();
        if (CACHE.putIfAbsent(cacheKey, now) != null) {
            if (now - CACHE.get(cacheKey) < DEBOUNCE_MS) {
                return;
            }
        }
        CACHE.put(cacheKey, now);

        // 4. LOG BASED ON EVENT TYPE
        if (ReplicationAction.EVENT_TOPIC.equals(topic)) {
            ReplicationAction action = ReplicationAction.fromEvent(event);
            if (action != null && action.getPath() != null) {
                if (action.getType() == ReplicationActionType.ACTIVATE) {
                    LOG.info("PAGE PUBLISHED    | User: {} | Path: {} | Time: {}", userId, action.getPath(), time);
                } else if (action.getType() == ReplicationActionType.DEACTIVATE) {
                    LOG.info("PAGE UNPUBLISHED  | User: {} | Path: {} | Time: {}", userId, action.getPath(), time);
                }
            }
        }
        else if ("org/apache/sling/api/resource/Resource/ADDED".equals(topic)) {
            LOG.info("PAGE CREATED      | User: {} | Path: {} | Time: {}", userId, path, time);
        }
        else if ("org/apache/sling/api/resource/Resource/CHANGED".equals(topic)) {
            LOG.info("PAGE MODIFIED     | User: {} | Path: {} | Time: {}", userId, path, time);
        }
        else if ("org/apache/sling/api/resource/Resource/REMOVED".equals(topic)) {
            LOG.info("PAGE DELETED      | User: {} | Path: {} | Time: {}", userId, path, time);
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
}

