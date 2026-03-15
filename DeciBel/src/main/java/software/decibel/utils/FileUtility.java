package software.decibel.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.decibel.exceptions.custom.file.FileSizeTooLargeException;
import software.decibel.exceptions.custom.file.FileStorageException;
import software.decibel.exceptions.custom.file.FileTypeNotAllowedException;

// Utility to save files (images/audio) and validate them
// We will assume all uploads will be saved on the local disk
// Can modify code later to save files on AWS server

@Component
public class FileUtility {

  // Folder location of all uploads (images/audio)
  private final Path UPLOAD_DIR = Paths.get("uploads").toAbsolutePath().normalize();
  // Max audio size is set to 100 MB
  private final long MAX_AUDIO_SIZE = 100 * 1024 * 1024;
  private final String AUDIO_UNITS = "MB";

  // List of acceptable audio types (can be appended)
  private final List<String> AUDIO_TYPES = List.of("audio/mpeg", "audio/wav");
  // List of acceptable image types (can be appended)
  private final List<String> IMAGE_TYPES = List.of("image/jpeg", "image/png");

  // Validates that audio file is mp3/wav & below max size
  public void validateAudio(MultipartFile file) {

    if (!AUDIO_TYPES.contains(file.getContentType())) {
      throw new FileTypeNotAllowedException("Audio file must be MP3 or WAV");
    }

    if (file.getSize() > MAX_AUDIO_SIZE) {
      throw new FileSizeTooLargeException(MAX_AUDIO_SIZE, file.getSize(), AUDIO_UNITS);
    }
  }

  // Validates that image file is jpg/png
  public void validateImage(MultipartFile file) {

    // In case image file doesn't exist no checks needed
    if (file == null || file.isEmpty()) return;

    if (!IMAGE_TYPES.contains(file.getContentType())) {
      throw new FileTypeNotAllowedException("Cover image must be JPG or PNG");
    }
  }

  // Saves file inside UPLOAD_DIR using a unique filename and returns file path object
  public Path saveFile(MultipartFile file) {

    if (file == null || file.isEmpty()) {
      throw new FileTypeNotAllowedException("File is empty");
    }

    // Saving file may cause an exception - better be handled in try catch and throw custom error
    // file.transferTo & file.createDirectories may cause IOException
    try {
      String fileName; // store the generated unique filename
      Path filePath; // store the full path where file will be saved

      // Generate a unique filename and ensure it does not already exist
      do {

        // ex: 123_song.mp3
        fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();

        // ex: uploads/123e4567_song.mp3
        filePath = UPLOAD_DIR.resolve(fileName);
        // repeat if somehow the file already exists
      } while (Files.exists(filePath));

      // create the upload directory if it does not exist
      Files.createDirectories(filePath.getParent());

      // save file to disk
      file.transferTo(filePath.toFile());

      // returns file path object

      return filePath;
    } catch (IOException e) {
      throw new FileStorageException(e);
    }
  }
}
