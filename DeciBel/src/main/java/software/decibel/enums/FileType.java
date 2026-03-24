package software.decibel.enums;

// All types of files that can be saved into azure
// Add as you find more in future iterations
public enum FileType {
  AUDIO("audio"),
  TRACK_COVERS("track-covers"),
  AVATARS("avatars"),
  PROFILE_COVERS("profile-covers"),
  WAVEFORM_DATA("waveform-data");

  private final String path;

  FileType(String path) {
    this.path = path;
  }

  public String getPath() {
    return path;
  }
}
