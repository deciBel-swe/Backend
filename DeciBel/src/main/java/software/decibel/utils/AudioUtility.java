package software.decibel.utils;

import java.nio.file.Path;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.springframework.stereotype.Component;
import software.decibel.exceptions.custom.file.AudioDurationReadingException;

@Component
public class AudioUtility {

  // Returns duration in seconds
  public int getAudioFileDurationInSeconds(Path filePath, String title) {
    try {
      AudioFile audioFile = AudioFileIO.read(filePath.toFile());
      return audioFile.getAudioHeader().getTrackLength();

    } catch (Exception e) {
      throw new AudioDurationReadingException(title, e);
    }
  }
}
