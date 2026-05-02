package software.decibel.component.seeders;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.decibel.entities.AuthIdentity;
import software.decibel.entities.Report;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.AuthProvider;
import software.decibel.enums.AuthType;
import software.decibel.enums.ReportStatus;
import software.decibel.enums.ReportTargetType;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.repositories.ReportRepository;
import software.decibel.repositories.TrackRepository;

/**
 * Seeds track reports for the 20 bulk users. Every 4th user (users 4, 8, 12,
 * 16, 20) submits one report against a track they did not upload.
 *
 * NOTE: ReportService.reportTrack() calls JwtService.getCurrentUserId() which
 * requires a live security context unavailable in a seeder. Report entities are
 * built and saved directly via the repository, populating the same fields the
 * service would set.
 *
 * Order 13 — runs last. Tracks must already exist (BulkTrackSeeder, Order 7).
 */
@Component
@Order(13)
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class BulkReportSeeder implements ApplicationListener<ApplicationReadyEvent> {

    private final AuthIdentityRepository authIdentityRepository;
    private final ReportRepository reportRepository;
    private final TrackRepository trackRepository;

    private static final String[] REPORT_REASONS = {
        "Spam",
        "Inappropriate content",
        "Copyright infringement",
        "Harassment",
        "Misleading information",};

    private static final String[] REPORT_DESCRIPTIONS = {
        "This content appears to be spam or self-promotion.",
        "This track contains content that violates community guidelines.",
        "This track uses copyrighted material without permission.",
        "This comment contains targeted harassment.",
        "The description is misleading and inaccurate.",};

    @Override
    @Transactional
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (reportRepository.count() > 0) {
            log.info("[BulkReportSeeder] Reports already exist — skipping.");
            return;
        }

        List<User> users = loadAllBulkUsers();
        if (users.isEmpty()) {
            log.warn("[BulkReportSeeder] No bulk users found — skipping.");
            return;
        }

        List<Track> tracks = trackRepository.findAll();
        if (tracks.isEmpty()) {
            log.warn("[BulkReportSeeder] No tracks found — skipping.");
            return;
        }

        int created = 0;

        for (int i = 3; i < users.size(); i += 4) { // indices 3, 7, 11, 15, 19 → users 4, 8, 12, 16, 20
            User reporter = users.get(i);

            // Pick a track not uploaded by this reporter, offset per reporter
            // so each one targets a different track
            Track targetTrack = tracks.stream()
                    .filter(t -> !t.getUploader().getId().equals(reporter.getId()))
                    .skip((i / 4) % Math.max(1, tracks.size() / 2))
                    .findFirst()
                    .orElse(null);

            if (targetTrack == null) {
                log.warn("[BulkReportSeeder] No reportable track found for user {} — skipping.",
                        reporter.getId());
                continue;
            }

            int reasonIdx = (i / 4) % REPORT_REASONS.length;

            if (reportRepository.existsByReporterIdAndTargetIdAndTargetTypeAndStatus(
                    reporter.getId(), targetTrack.getId(), ReportTargetType.TRACK, ReportStatus.OPEN)) {
                log.info("[BulkReportSeeder] Open report already exists for user {} → track {} — skipping.",
                        reporter.getId(), targetTrack.getId());
                continue;
            }

            reportRepository.save(Report.builder()
                    .reporterId(reporter.getId())
                    .targetId(targetTrack.getId())
                    .targetType(ReportTargetType.TRACK)
                    .reason(REPORT_REASONS[reasonIdx])
                    .description(REPORT_DESCRIPTIONS[reasonIdx])
                    .status(ReportStatus.OPEN)
                    .build());

            created++;
            log.info("[BulkReportSeeder] Seeded report: user {} → track {} (reason: {})",
                    reporter.getId(), targetTrack.getId(), REPORT_REASONS[reasonIdx]);
        }

        if (created == 0) {
            log.info("[BulkReportSeeder] All bulk reports already exist — skipping.");
        } else {
            log.info("[BulkReportSeeder] Done. Reports seeded: {}", created);
        }
    }

    private List<User> loadAllBulkUsers() {
        return IntStream.rangeClosed(1, 20)
                .mapToObj(i -> "bulk.user" + i + "@decibel.dev")
                .map(email -> authIdentityRepository
                .findByEmailIgnoreCaseAndProviderAndType(
                        email, AuthProvider.LOCAL, AuthType.PASSWORD)
                .map(AuthIdentity::getUser)
                .orElse(null))
                .filter(u -> u != null)
                .collect(Collectors.toList());
    }
}
