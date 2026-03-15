package software.decibel.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.io.FilenameUtils;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.decibel.exceptions.custom.file.AudioDurationReadingException;

@Component
public class AudioUtility {

  // Returns duration in seconds
  public int getAudioFileDurationInSeconds(MultipartFile file, String title) {
    Path tempFile = null;
    try {

      // extension important in reading audio files cant be null
      String extension = FilenameUtils.getExtension(file.getOriginalFilename());
      // save file temporarily on disk
      tempFile = Files.createTempFile("tmp", "." + extension);
      // transfer audio file to temp (contents)
      file.transferTo(tempFile.toFile());

      // return length in seconds
      AudioFile audioFile = AudioFileIO.read(tempFile.toFile());
      return audioFile.getAudioHeader().getTrackLength();

      // Catch any errors while reading audiofile
    } catch (Exception e) {
      throw new AudioDurationReadingException(title, e);
    } finally {
      // always delete temp file
      try {
        if (tempFile != null) Files.deleteIfExists(tempFile);
        // if it wasn't deleted not a big deal
      } catch (IOException e) {
      }
    }
  }
}
