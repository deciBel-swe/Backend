package software.decibel.component;

import org.springframework.stereotype.Component;
import software.decibel.dtos.track.responses.TrackStatusResponse;

import java.util.concurrent.ConcurrentHashMap;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;

@Component
public class UploadStatusCache {

    private final Cache<String, TrackStatusResponse> cache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    public void saveStatus(String uploadId, TrackStatusResponse status) {
        cache.put(uploadId, status);
    }

    public TrackStatusResponse getStatus(String uploadId) {
        return cache.getIfPresent(uploadId);
    }

    public void clear(String uploadId) {
        cache.invalidate(uploadId);
    }
}
