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

  // Function that returns allowed track access (PLAYABLE, BLOCKED, PREVIEW) depending on user's
  // tier and free tracks left
  // current business logic:
  // pro -> as they wish
  // free -> only get 3 non-blocked uploads at a time
  public TrackAccess resolveUploadAccess(User user, TrackAccess requestedAccess) {

    // pro users can set any access level they want
    if (user.getTier() == AccountTier.PRO) {
      return requestedAccess;
    }

    // free users can always set tracks to BLOCKED (doesn't consume a slot)
    if (requestedAccess == TrackAccess.BLOCKED) {
      return TrackAccess.BLOCKED;
    }

    // free users can upload as PLAYABLE or PREVIEW if they have slots available
    if (user.getFreeTracksLeft() > 0) {
      return requestedAccess;
    }

    // free user has no slots left and is trying to upload a non-blocked track
    throw new FreeUserOutOfFreeTracks(user.getId());
  }

  // For patch requests
  // existingAccess: the current access level of the track
  // requestedAccess: the new access level the user wants to change to
  public TrackAccess resolvePatchAccess(
      User user, TrackAccess existingAccess, TrackAccess requestedAccess) {

    // pro users can change to any access level they want
    if (user.getTier() == AccountTier.PRO) {
      return requestedAccess;
    }

    // free users can always change to BLOCKED (frees up a slot)
    if (requestedAccess == TrackAccess.BLOCKED) {
      return requestedAccess;
    }

    // free users can transfer between PREVIEW <-> PLAYABLE without consuming additional slots

    if (existingAccess == TrackAccess.PREVIEW || existingAccess == TrackAccess.PLAYABLE) {
      if (requestedAccess == TrackAccess.PREVIEW || requestedAccess == TrackAccess.PLAYABLE) {
        return requestedAccess;
      }
    }

    // free users can only unblock a BLOCKED track if they have a free slot available
    if (existingAccess == TrackAccess.BLOCKED && user.getFreeTracksLeft() > 0) {
      return requestedAccess;
    }

    // free user has no slots left and is trying to unblock a track
    throw new FreeUserOutOfFreeTracks(user.getId());
  }

 
  public void updateFreeTracksLeft(
      User user, TrackAccess initialAccess, TrackAccess resolvedAccess) {

    // pro tier users have unlimited uploads, no tracking needed
    if (user.getTier() == AccountTier.PRO) {
      return;
    }

    // normalize null to BLOCKED (default state for tracks)
    if (initialAccess == null) {
      initialAccess = TrackAccess.BLOCKED;
    }

    // no change in access level means no change in slot usage
    if (initialAccess == resolvedAccess) {
      return;
    }

    // transition: BLOCKED -> PLAYABLE or BLOCKED -> PREVIEW (consume 1 slot)
    if (initialAccess == TrackAccess.BLOCKED
        && (resolvedAccess == TrackAccess.PLAYABLE || resolvedAccess == TrackAccess.PREVIEW)) {
      user.setFreeTracksLeft(user.getFreeTracksLeft() - 1);
      return;
    }

    // transition: PLAYABLE -> BLOCKED or PREVIEW -> BLOCKED (refund 1 slot)
    if ((initialAccess == TrackAccess.PLAYABLE || initialAccess == TrackAccess.PREVIEW)
        && resolvedAccess == TrackAccess.BLOCKED) {
      user.setFreeTracksLeft(user.getFreeTracksLeft() + 1);
    }

    // transition: PLAYABLE <-> PREVIEW (no slot impact, both are non-blocked states)
    // this covers: PLAYABLE -> PREVIEW or PREVIEW -> PLAYABLE
    // no action needed
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
