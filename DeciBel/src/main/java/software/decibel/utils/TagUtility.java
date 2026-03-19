package software.decibel.utils;

import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public class TagUtility {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public static List<String> parseTags(String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    } catch (Exception e) {
      return List.of();
    }
  }
}
