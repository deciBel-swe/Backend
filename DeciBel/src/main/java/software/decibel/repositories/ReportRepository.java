package software.decibel.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import software.decibel.entities.Report;

public interface ReportRepository extends JpaRepository<Report, Long> {
}
