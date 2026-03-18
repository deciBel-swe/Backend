package software.decibel.utils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.decibel.enums.FileType;
import software.decibel.exceptions.custom.AzureFileStorageException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class WaveFormUtility {

  private final ObjectMapper objectMapper;
  private final FileUtilityAzure fileUtilityAzure;

  // Given waveformdata -> saves into json file at azure and return url string
  // We will transport the data as a stream of bytes
  public String saveWaveformToAzure(List<Float> waveformData, String trackName) {
    try {
      // Convert list to JSON
      String json = objectMapper.writeValueAsString(waveformData);
      // Convert into a stream of bytes
      InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

      // Upload and return URL
      return fileUtilityAzure.saveFileFromStream(
          stream, json.length(), FileType.WAVEFORM_DATA, trackName);

    } catch (Exception e) {
      throw new AzureFileStorageException("Could not save waveform data to Azure", e);
    }
  }
}
