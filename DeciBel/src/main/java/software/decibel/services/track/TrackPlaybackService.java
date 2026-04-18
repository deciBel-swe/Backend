package software.decibel.services.track;

import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.decibel.dtos.track.responses.*;
import software.decibel.entities.User;
import software.decibel.enums.*;
import software.decibel.exceptions.custom.FreeUserOutOfFreeTracks;
import software.decibel.utils.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrackPlaybackService {
  private final FileUtilityAzure fileUtilityAzure;
  private final AudioUtility audioUtility;
  int PREVIEW_SECONDS = 10;

  // Function that returns allowed track access (PLAYABLE, BLOCKED, PREVIEW) depending on user's
  // tier and free tracks left
  // current business logic:
  // pro -> as they wish
  // free -> only get 3 non-blocked uploads at a time
  public TrackAccess resolveUploadAccess(User user, TrackAccess requestedAccess) {

    // pro does anything
    if (user.getTier() == AccountTier.PRO) {
      return requestedAccess;
    }

    // free user -> BLOCKED (as they wish)
    else if (requestedAccess == TrackAccess.BLOCKED) {
      return TrackAccess.BLOCKED;
    }

    // free user -> PLAYABLE or PREVIEW → check slots
    else if (user.getFreeTracksLeft() > 0) {
      return requestedAccess;
    }

    // free user -> no slots → cant upload not blocked
    throw new FreeUserOutOfFreeTracks(user.getId());
  }

  public void updateFreeTracksLeft(
      User user, TrackAccess initialAccess, TrackAccess resolvedAccess) {

    // only free users are affected
    if (user.getTier() == AccountTier.PRO) {
      return;
    }

    // normalize null (default is blocked)
    if (initialAccess == null) {
      initialAccess = TrackAccess.BLOCKED;
    }

    // no change (ex. blocked -> blocked.. playable -> preview)
    if (initialAccess == resolvedAccess) {
      return;
    }

    // blocked -> not blocked (consume slot)
    if (initialAccess == TrackAccess.BLOCKED && !(resolvedAccess == TrackAccess.BLOCKED)) {
      user.setFreeTracksLeft(user.getFreeTracksLeft() - 1);
      return;
    }

    // not blocked -> blocked(refund slot)
    if (!(initialAccess == TrackAccess.BLOCKED) && resolvedAccess == TrackAccess.BLOCKED) {
      user.setFreeTracksLeft(user.getFreeTracksLeft() + 1);
    }
  }

  public byte[] extractPreview(byte[] inputBytes, int previewDurationSeconds) {

    Path input = null;
    Path output = null;

    try {
      System.out.println("FFMPEG: starting preview extraction");

      input = Files.createTempFile("input-", ".mp3");
      output = Files.createTempFile("preview-", ".mp3");

      Files.write(input, inputBytes);

      Process process =
          new ProcessBuilder(
                  "ffmpeg",
                  "-y",
                  "-i",
                  input.toString(),
                  "-t",
                  String.valueOf(previewDurationSeconds),
                  "-c:a",
                  "libmp3lame",
                  "-b:a",
                  "128k",
                  output.toString())
              .redirectErrorStream(true)
              .start();

      // READ ffmpeg logs (VERY IMPORTANT for debugging)
      String logs = new String(process.getInputStream().readAllBytes());
      System.out.println("FFMPEG LOGS:\n" + logs);

      int exitCode = process.waitFor();

      if (exitCode != 0) {
        throw new RuntimeException("FFmpeg failed with exit code " + exitCode + "\n" + logs);
      }

      byte[] result = Files.readAllBytes(output);

      if (result.length == 0) {
        throw new RuntimeException("FFmpeg produced empty preview file");
      }

      System.out.println("FFMPEG: preview extracted successfully");

      return result;

    } catch (Exception e) {
      throw new RuntimeException("Preview extraction failed", e);

    } finally {
      try {
        if (input != null) Files.deleteIfExists(input);
        if (output != null) Files.deleteIfExists(output);
      } catch (Exception ignored) {
      }
    }
  }

  // what user will see depending on access + tier
  // access will stay the same unless you're pro + it's blocked -> playable
  public TrackAccess resolveUserAccess(AccountTier tier, TrackAccess access) {

    if (tier == AccountTier.PRO && access == TrackAccess.BLOCKED) {
      return TrackAccess.PLAYABLE;
    }
    return access;
  }
}
