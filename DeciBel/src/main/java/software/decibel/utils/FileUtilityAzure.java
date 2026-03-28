package software.decibel.utils;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobStorageException;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.decibel.enums.FileType;
import software.decibel.exceptions.custom.AzureFileStorageException;

// Utility class to save files using microsoft azure
// further info:
// https://medium.com/@agrawalshrey21/performing-crud-operations-on-azure-blob-storage-from-a-spring-application-f53d70055edd
@Component
public class FileUtilityAzure {

  // Client that connects to your specific container in Azure
  private final BlobContainerClient blobContainerClient;

  // Constructor (get values from application.properties)
  public FileUtilityAzure(
      @Value("${azure.storage.connection-string}") String connectionString,
      @Value("${azure.storage.blob-container-name}") String containerName) {

    // Build the connection using connection string and container name
    this.blobContainerClient =
        new BlobContainerClientBuilder()
            .connectionString(connectionString)
            .containerName(containerName)
            .buildClient();
  }

  // Saves file to azure and returns the URL of the uploaded file
  public String saveFile(MultipartFile file, FileType fileType) {

    try {
      // Generate unique filename to avoid collisions
      // ex:
      // "audio/fae40b70-2913-470c-9307-47656c8e81cc_wind.mp3",
      String fileName =
          fileType.getPath()
              + "/"
              + UUID.randomUUID()
              + "_"
              + cleanFileName(file.getOriginalFilename());

      // Get a connection for this file inside container (doesnt exist yet)
      BlobClient blobClient = blobContainerClient.getBlobClient(fileName);

      //  upload file's bytes, size, and true -> overwrite if file exists
      blobClient.upload(file.getInputStream(), file.getSize(), true);

      // correct display of url
      return blobContainerClient.getBlobContainerUrl() + "/" + fileName;

    } catch (IOException e) {
      throw new AzureFileStorageException(
          "Could not save '" + file.getOriginalFilename() + "' from Microsoft Azure", e);
    }
  }

  // Saves a file into azure storage from a stream of bytes
  public String saveFileFromStream(
      InputStream inputStream, long size, FileType fileType, String fileTitle) {
    return saveFileFromStream(inputStream, size, fileType, fileTitle, null);
  }

  // Saves a file into azure storage from a stream of bytes with optional progress callback
  public String saveFileFromStream(
      InputStream inputStream,
      long size,
      FileType fileType,
      String fileTitle,
      ProgressCallback callback) {
    // Generate unique filename to avoid collisions
    String extension = cleanFileName(fileTitle).endsWith(".json") ? "" : ".json";
    if (fileType == FileType.AUDIO || fileType == FileType.TRACK_COVERS) {
      extension = ""; // already has extension or handled by cleanFileName
    }

    String fileName =
        fileType.getPath() + "/" + UUID.randomUUID() + "_" + cleanFileName(fileTitle) + extension;

    try {
      InputStream streamToUpload =
          callback != null ? new ProgressInputStream(inputStream, size, callback) : inputStream;
      BlobClient blobClient = blobContainerClient.getBlobClient(fileName);
      blobClient.upload(streamToUpload, size, true);
      return blobContainerClient.getBlobContainerUrl() + "/" + fileName;
    } catch (Exception e) {
      throw new AzureFileStorageException("Could not save file '" + fileName + "' to Azure", e);
    }
  }

  // Deletes file @ azure using url
  public void deleteFileByUrl(String url) {
    String fileName = url.replace(blobContainerClient.getBlobContainerUrl() + "/", "");

    try {
      blobContainerClient.getBlobClient(fileName).delete();

    } catch (BlobStorageException ex) {
      throw new AzureFileStorageException(
          "Could not delete '" + fileName + "' from Microsoft Azure", ex);
    }
  }

  // Function to make filename usable in url
  // file title -> file_title
  private String cleanFileName(String fileName) {
    return fileName.trim().replaceAll("\\s+", "_");
  }
}
