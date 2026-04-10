package software.decibel.exceptions.custom;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateTrackException extends RuntimeException {

    public DuplicateTrackException(String message) {
        super(message);
    }
}
