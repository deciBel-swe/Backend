package software.decibel.exceptions.custom;

public class PlaylistAccessDeniedException extends RuntimeException {

    public PlaylistAccessDeniedException(String message) {
        super(message);
    }

}
