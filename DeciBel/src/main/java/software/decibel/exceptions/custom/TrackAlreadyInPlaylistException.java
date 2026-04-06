package software.decibel.exceptions.custom;

public class TrackAlreadyInPlaylistException extends RuntimeException {

    public TrackAlreadyInPlaylistException(String message) {
        super(message);
    }

}
