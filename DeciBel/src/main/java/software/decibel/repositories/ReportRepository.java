package software.decibel.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import software.decibel.entities.Report;
import software.decibel.enums.ReportStatus;
import software.decibel.enums.ReportTargetType;

public interface ReportRepository extends JpaRepository<Report, Long> {
    boolean existsByReporterIdAndTargetIdAndTargetTypeAndStatus(
            Long reporterId,
            Long targetId,
            ReportTargetType targetType,
            ReportStatus status);
}
