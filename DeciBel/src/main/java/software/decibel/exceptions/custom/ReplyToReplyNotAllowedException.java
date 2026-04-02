package software.decibel.exceptions.custom;

// Called when trying to reply to another reply (according to the docs replies are one level max)
public class ReplyToReplyNotAllowedException extends RuntimeException {
  public ReplyToReplyNotAllowedException() {
    super("Cannot reply to a reply");
  }
}
