package software.decibel.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import software.decibel.dtos.discovery.StationPageResponse;
import software.decibel.services.StationService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/stations")
public class StationController {

  private final StationService stationService;

  @GetMapping("/genre")
  public ResponseEntity<StationPageResponse> getGenreStation(
      @RequestParam String genre,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(stationService.getGenreStation(genre, page, size));
  }

  @GetMapping("/artist")
  public ResponseEntity<StationPageResponse> getArtistStation(
      @RequestParam Long artistId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(stationService.getArtistStation(artistId, page, size));
  }
}
