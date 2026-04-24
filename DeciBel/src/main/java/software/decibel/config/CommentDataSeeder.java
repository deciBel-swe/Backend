// package software.decibel.config;
//
// import java.time.LocalDate;
// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.boot.CommandLineRunner;
// import org.springframework.context.annotation.Profile;
// import org.springframework.stereotype.Component;
// import software.decibel.entities.*;
// import software.decibel.enums.*;
// import software.decibel.repositories.*;
//
// @Component
// @RequiredArgsConstructor
// @Slf4j
// @Profile("dev")
// public class CommentDataSeeder implements CommandLineRunner {
//
//  private final UserRepository userRepository;
//  private final TrackRepository trackRepository;
//  private final CommentRepository commentRepository;
//  private final BlockRepository blockRepository;
//  private final AuthIdentityRepository authIdentityRepository;
//  private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
//
//  @Override
//  public void run(String... args) {
//    if (commentRepository.count() > 0) {
//      log.info("Comment database already seeded, skipping...");
//      return;
//    }
//
//    log.info("Seeding comment data...");
//
//    // ── Users ─────────────────────────────────────────────
//    User currentUser = user("current_user", "Current User"); // The test user (has auth)
//    User artistOne = user("artist_one", "Artist One"); // uploads public tracks
//    User artistTwo = user("artist_two", "Artist Two"); // uploads public + private tracks
//    User blockedUser = user("blocked_user", "Blocked User"); // blocked by currentUser
//    User blockerUser = user("blocker_user", "Blocker User"); // has blocked currentUser
//
//    // ── Auth (login as currentUser) ───────────────────────
//    // POST /api/login/local { "email": "Augustus.Veum@yahoo.com", "password": "DnL7LvsxCBeiW37!"
// }
//    createLocalAuth(currentUser, "Augustus.Veum@yahoo.com", "DnL7LvsxCBeiW37!");
//
//    // ── Blocks ────────────────────────────────────────────
//    block(currentUser, blockedUser); // currentUser blocked blockedUser
//    block(blockerUser, currentUser); // blockerUser blocked currentUser
//
//    // ── Tracks ────────────────────────────────────────────
//
//    // artistOne: public track (should be commentable)
//    Track publicTrackOne =
//        track(
//            "Public Banger",
//            "Hip-Hop",
//            300,
//            artistOne,
//            Visibility.PUBLIC,
//            TrackState.FINISHED,
//            true);
//
//    // artistOne: another public track
//    Track publicTrackTwo =
//        track(
//            "Another Public Hit",
//            "Pop",
//            240,
//            artistOne,
//            Visibility.PUBLIC,
//            TrackState.FINISHED,
//            true);
//
//    // artistTwo: public track (should be commentable)
//    Track publicTrackByArtistTwo =
//        track(
//            "Public from Artist Two",
//            "Hip-Hop",
//            280,
//            artistTwo,
//            Visibility.PUBLIC,
//            TrackState.FINISHED,
//            true);
//
//    // artistTwo: private track (should NOT be commentable)
//    Track privateTrackByArtistTwo =
//        track(
//            "Private finished",
//            "Jazz",
//            360,
//            artistTwo,
//            Visibility.PRIVATE,
//            TrackState.FINISHED,
//            false);
//
//    // blockedUser: public track (currentUser should NOT be able to comment - they blocked this
//    // user)
//    Track publicTrackByBlockedUser =
//        track(
//            "Track by Blocked User",
//            "Electronic",
//            250,
//            blockedUser,
//            Visibility.PUBLIC,
//            TrackState.FINISHED,
//            true);
//
//    // blockerUser: public track (currentUser should NOT be able to comment - this user blocked
//    // them)
//    Track publicTrackByBlockerUser =
//        track(
//            "Track by Blocker User",
//            "Ambient",
//            320,
//            blockerUser,
//            Visibility.PUBLIC,
//            TrackState.FINISHED,
//            true);
//
//    // currentUser: own public track (for testing if they can comment on their own track)
//    Track currentUserOwnTrack =
//        track(
//            "My Own Track",
//            "Hip-Hop",
//            200,
//            currentUser,
//            Visibility.PUBLIC,
//            TrackState.FINISHED,
//            true);
//
//    // ── Comments on publicTrackOne ─────────────────────────────────────────
//
//    // Comment 1: By artistOne (the track uploader) - timestamp at 30 sec
//    Comment comment1OnTrack1 =
//        comment(
//            "This is my track! Check it out.",
//            30,
//            artistOne,
//            publicTrackOne,
//            null); // top-level comment
//
//    // Comment 2: By currentUser - timestamp at 60 sec
//    Comment comment2OnTrack1 =
//        comment(
//            "Amazing production quality!",
//            60,
//            currentUser,
//            publicTrackOne,
//            null); // top-level comment
//
//    // Comment 3: By artistTwo - timestamp at null (no timestamp)
//    Comment comment3OnTrack1 =
//        comment(
//            "Fire track! Love the beat.",
//            null,
//            artistTwo,
//            publicTrackOne,
//            null); // top-level comment
//
//    // Reply to comment2 (by artistOne)
//    Comment reply1ToComment2 =
//        comment(
//            "Thanks for the feedback!", null, artistOne, publicTrackOne, comment2OnTrack1); //
// reply
//
//    // Reply to comment2 (by artistTwo)
//    Comment reply2ToComment2 =
//        comment(
//            "I agree with you completely.",
//            null,
//            artistTwo,
//            publicTrackOne,
//            comment2OnTrack1); // reply
//
//    // Reply to comment3 (by currentUser)
//    Comment reply1ToComment3 =
//        comment(
//            "Right? The beat is insane!",
//            null,
//            currentUser,
//            publicTrackOne,
//            comment3OnTrack1); // reply
//
//    // ── Comments on publicTrackTwo ─────────────────────────────────────────
//
//    // Comment on second track by currentUser
//    Comment comment1OnTrack2 =
//        comment(
//            "Great song, different vibe from the first one.",
//            120,
//            currentUser,
//            publicTrackTwo,
//            null); // top-level comment
//
//    // ── Comments on publicTrackByArtistTwo ──────────────────────────────────
//
//    // Comment by currentUser on artistTwo's track
//    Comment comment1OnTrack3 =
//        comment(
//            "Really digging this one too!",
//            null,
//            currentUser,
//            publicTrackByArtistTwo,
//            null); // top-level comment
//
//    // ── Expected Results ──────────────────────────────────
//
//    log.info("=== AUTHENTICATION ===");
//    log.info(
//        "Login → POST /api/login/local  email: Augustus.Veum@yahoo.com  password:
// DnL7LvsxCBeiW37!");
//    log.info("currentUser id: {}", currentUser.getId());
//    log.info("artistOne id: {}", artistOne.getId());
//    log.info("artistTwo id: {}", artistTwo.getId());
//    log.info("blockedUser id: {}", blockedUser.getId());
//    log.info("blockerUser id: {}", blockerUser.getId());
//
//    log.info("");
//    log.info("=== TRACKS ===");
//    log.info("publicTrackOne id: {} (PUBLIC, by artistOne)", publicTrackOne.getId());
//    log.info("publicTrackTwo id: {} (PUBLIC, by artistOne)", publicTrackTwo.getId());
//    log.info(
//        "publicTrackByArtistTwo id: {} (PUBLIC, by artistTwo)", publicTrackByArtistTwo.getId());
//    log.info(
//        "privateTrackByArtistTwo id: {} (PRIVATE, by artistTwo)",
// privateTrackByArtistTwo.getId());
//    log.info(
//        "publicTrackByBlockedUser id: {} (PUBLIC, by blockedUser)",
//        publicTrackByBlockedUser.getId());
//    log.info(
//        "publicTrackByBlockerUser id: {} (PUBLIC, by blockerUser)",
//        publicTrackByBlockerUser.getId());
//    log.info("currentUserOwnTrack id: {} (PUBLIC, by currentUser)", currentUserOwnTrack.getId());
//
//    log.info("");
//    log.info("=== ENDPOINT TESTING GUIDE ===");
//
//    log.info("");
//    log.info("--- POST /tracks/{trackId}/comments ---");
//    log.info("✅ SHOULD SUCCEED:");
//    log.info(
//        "  - currentUser comment on publicTrackOne (id: {}) → Status 201",
// publicTrackOne.getId());
//    log.info(
//        "  - currentUser comment on publicTrackTwo (id: {}) → Status 201",
// publicTrackTwo.getId());
//    log.info(
//        "  - currentUser comment on publicTrackByArtistTwo (id: {}) → Status 201",
//        publicTrackByArtistTwo.getId());
//    log.info(
//        "  - currentUser comment on own track (id: {}) → Status 201",
// currentUserOwnTrack.getId());
//    log.info("  - With valid timestampSeconds (< duration) → Status 201");
//    log.info("  - Without timestampSeconds (null) → Status 201");
//
//    log.info("");
//    log.info("❌ SHOULD FAIL (4xx errors):");
//    log.info(
//        "  - currentUser comment on privateTrackByArtistTwo (id: {}) → 403 Forbidden (not
// public)",
//        privateTrackByArtistTwo.getId());
//    log.info(
//        "  - currentUser comment on publicTrackByBlockedUser (id: {}) → 403 Forbidden (user is
// blocked)",
//        publicTrackByBlockedUser.getId());
//    log.info(
//        "  - currentUser comment on publicTrackByBlockerUser (id: {}) → 403 Forbidden (blocked by
// user)",
//        publicTrackByBlockerUser.getId());
//    log.info("  - Comment with empty body → 400 Bad Request (@NotBlank validation)");
//    log.info("  - Comment with null body → 400 Bad Request (@NotNull validation)");
//    log.info(
//        "  - Comment with timestampSeconds > track duration → 422 Unprocessable
// (InvalidTimestampException)");
//    log.info("  - Comment on non-existent track → 404 Not Found");
//    log.info("  - No authentication token → 401 Unauthorized");
//
//    log.info("");
//    log.info("--- GET /tracks/{trackId}/comments ---");
//    log.info("✅ SHOULD SUCCEED:");
//    log.info(
//        "  - GET /tracks/{}/comments → Status 200 with page of comments", publicTrackOne.getId());
//    log.info(
//        "    Expected: comment1, comment2, comment3 (sorted by createdAt DESC - newest first)");
//    log.info(
//        "  - GET /tracks/{}/comments?page=0&size=20 → Status 200 (default pagination)",
//        publicTrackOne.getId());
//    log.info(
//        "  - GET /tracks/{}/comments?page=0&size=1 → Status 200 (paginated to 1 comment)",
//        publicTrackOne.getId());
//
//    log.info("");
//    log.info("❌ SHOULD FAIL (4xx errors):");
//    log.info("  - GET /tracks/{trackId}/comments on non-existent track → 404 Not Found");
//    log.info("  - No authentication token → 401 Unauthorized (if auth required)");
//
//    log.info("");
//    log.info("--- POST /comments/{commentId}/replies ---");
//    log.info("✅ SHOULD SUCCEED:");
//    log.info("  - currentUser reply to comment2 (id: {}) → Status 201", comment2OnTrack1.getId());
//    log.info("  - With valid body and no timestamp → Status 201");
//    log.info("  - Reply from different users on same comment → All succeed");
//
//    log.info("");
//    log.info("❌ SHOULD FAIL (4xx errors):");
//    log.info(
//        "  - Reply to reply (nested replies) → 400 Bad Request
// (ReplyToReplyNotAllowedException)");
//    log.info(
//        "  - currentUser reply to comment on track by blockedUser → 403 Forbidden (user
// blocked)");
//    log.info(
//        "  - currentUser reply to comment on track by blockerUser → 403 Forbidden (blocked by
// user)");
//    log.info("  - Reply with empty body → 400 Bad Request (@NotBlank validation)");
//    log.info("  - Reply to non-existent comment → 404 Not Found");
//    log.info("  - No authentication token → 401 Unauthorized");
//
//    log.info("");
//    log.info("--- GET /comments/{commentId}/replies ---");
//    log.info("✅ SHOULD SUCCEED:");
//    log.info(
//        "  - GET /comments/{}/replies → Status 200 with page of replies",
// comment2OnTrack1.getId());
//    log.info(
//        "    Expected: reply1ToComment2, reply2ToComment2 (sorted by createdAt ASC - oldest
// first)");
//    log.info(
//        "  - GET /comments/{}/replies?page=0&size=20 → Status 200 (default pagination)",
//        comment2OnTrack1.getId());
//
//    log.info("");
//    log.info("❌ SHOULD FAIL (4xx errors):");
//    log.info(
//        "  - GET /comments/{commentId}/replies on reply (not top-level comment) → 400 Bad Request
// (ReplyToReplyNotAllowedException)");
//    log.info("    Example: GET /comments/{} (this is a reply itself)", reply1ToComment2.getId());
//    log.info("  - GET /comments/{commentId}/replies on non-existent comment → 404 Not Found");
//    log.info("  - No authentication token → 401 Unauthorized (if auth required)");
//
//    log.info("");
//    log.info("=== EDGE CASES TO TEST ===");
//    log.info("1. Timestamp validation:");
//    log.info("   - Comment with timestampSeconds = 0 → Should be valid");
//    log.info("   - Comment with timestampSeconds = track duration → Should be INVALID (> check)");
//    log.info("   - Comment with timestampSeconds = track duration - 1 → Should be valid");
//    log.info("   - publicTrackOne has duration 300s, comment1OnTrack1 has timestamp 30s (valid)");
//
//    log.info("");
//    log.info("2. Comment count updates:");
//    log.info("   - After adding comment, track.commentCount should increment");
//    log.info(
//        "   - After adding reply (which is also a comment on track), track.commentCount should
// increment");
//
//    log.info("");
//    log.info("3. Pagination order:");
//    log.info("   - Track comments: newest first (descending by createdAt)");
//    log.info("   - Reply comments: oldest first (ascending by createdAt)");
//
//    log.info("");
//    log.info("4. Blocking logic:");
//    log.info("   - currentUser blocked blockedUser → cannot comment/reply on blockedUser's
// tracks");
//    log.info("   - blockerUser blocked currentUser → cannot comment/reply on blockerUser's
// tracks");
//    log.info("   - But artistOne, artistTwo can comment/reply freely (no blocks)");
//
//    log.info("");
//    log.info("5. Visibility logic:");
//    log.info("   - Can only comment on PUBLIC tracks (not PRIVATE)");
//    log.info("   - privateTrackByArtistTwo is PRIVATE → should be 403 Forbidden");
//
//    log.info("");
//    log.info("Seeding comment data complete.");
//  }
//
//  private User user(String username, String displayName) {
//    return userRepository.save(
//        User.builder()
//            .username(username)
//            .displayName(displayName)
//            .tier(AccountTier.FREE)
//            .isPrivate(false)
//            .showHistory(true)
//            .build());
//  }
//
//  private Track track(
//      String title,
//      String genre,
//      int duration,
//      User uploader,
//      Visibility visibility,
//      TrackState state,
//      boolean published) {
//    return trackRepository.save(
//        Track.builder()
//            .title(title)
//            .genre(genre)
//            .durationSeconds(duration)
//            .uploader(uploader)
//            .visibility(visibility)
//            .state(state)
//            .published(published)
//            .releaseDate(LocalDate.now())
//            .slug(title.toLowerCase().replace(" ", "-"))
//            .likeCount(0)
//            .playCount(0)
//            .commentCount(0)
//            .build());
//  }
//
//  private Comment comment(
//      String content, Integer timestampSeconds, User user, Track track, Comment parentComment) {
//    Comment savedComment =
//        commentRepository.save(
//            Comment.builder()
//                .content(content)
//                .timestampSeconds(timestampSeconds)
//                .user(user)
//                .track(track)
//                .parentComment(parentComment)
//                .build());
//
//    // Update track comment count
//    track.setCommentCount(track.getCommentCount() + 1);
//    trackRepository.save(track);
//
//    return savedComment;
//  }
//
//  private void block(User blocker, User blocked) {
//    blockRepository.save(Block.builder().blocker(blocker).blocked(blocked).build());
//  }
//
//  private void createLocalAuth(User user, String email, String password) {
//    authIdentityRepository.save(
//        AuthIdentity.builder()
//            .user(user)
//            .email(email)
//            .passwordHash(passwordEncoder.encode(password))
//            .emailVerified(true)
//            .provider(AuthProvider.LOCAL)
//            .type(AuthType.PASSWORD)
//            .build());
//  }
// }
