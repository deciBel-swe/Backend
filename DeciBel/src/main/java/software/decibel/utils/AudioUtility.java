package software.decibel.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.io.FilenameUtils;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.decibel.exceptions.custom.AudioDurationReadingException;

@Component
public class AudioUtility {

  // Returns duration in seconds
  public int getAudioFileDurationInSeconds(
      byte[] fileBytes, String originalFilename, String title) {
    Path tempFile = null;
    try {

      // extension important in reading audio files cant be null
      String extension = FilenameUtils.getExtension(originalFilename);
      // save file temporarily on disk
      tempFile = Files.createTempFile("tmp", "." + extension);
      // write bytes to temp
      Files.write(tempFile, fileBytes);

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

  // Extracts preview from audio file (using ffpeg which is downloaded on the server and used via
  // command line)
  public byte[] extractPreview(byte[] inputBytes, int previewDuration) {
    try {
      // create temp files for command line
      Path input = Files.createTempFile("input-", ".tmp");
      Path output = Files.createTempFile("preview-", ".mp3");

      // write all bytes in input
      Files.write(input, inputBytes);
      String ffmpeg =
          System.getProperty("os.name").toLowerCase().contains("win")
              ? "C:\\ffmpeg\\bin\\ffmpeg.exe"
              : "ffmpeg";
      Process process =
          new ProcessBuilder(
                  ffmpeg,
                  "-y",
                  "-i",
                  input.toString(),
                  "-t",
                  String.valueOf(previewDuration),
                  "-c:a",
                  "mp3",
                  output.toString())
              .start();

      process.waitFor();

      // write results in output and read in results
      byte[] result = Files.readAllBytes(output);
      // delete files
      Files.deleteIfExists(input);
      Files.deleteIfExists(output);

      return result;

    } catch (Exception e) {
      throw new RuntimeException("Preview extraction failed", e);
    }
  }

  // Returns duration in seconds (overload for MultipartFile)
  public int getAudioFileDurationInSeconds(MultipartFile file, String title) {
    try {
        return getAudioFileDurationInSeconds(file.getBytes(), file.getOriginalFilename(), title);
    } catch (IOException e) {
        throw new AudioDurationReadingException(title, e);
    }
  }
}
