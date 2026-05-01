package software.decibel.component;

import org.springframework.stereotype.Component;
import software.decibel.dtos.track.responses.TrackStatusResponse;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class UploadStatusCache {

    private final ConcurrentHashMap<String, TrackStatusResponse> cache = new ConcurrentHashMap<>();

    public void saveStatus(String uploadId, TrackStatusResponse status) {
        cache.put(uploadId, status);
    }

    public TrackStatusResponse getStatus(String uploadId) {
        return cache.get(uploadId);
    }

    public void clear(String uploadId) {
        cache.remove(uploadId);
    }

}
