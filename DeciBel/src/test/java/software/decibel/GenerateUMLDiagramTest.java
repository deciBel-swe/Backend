package software.decibel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.elnarion.util.plantuml.generator.classdiagram.PlantUMLClassDiagramGenerator;
import de.elnarion.util.plantuml.generator.classdiagram.config.PlantUMLClassDiagramConfigBuilder;

public class GenerateUMLDiagramTest {

    @Test
    void generateUML() throws Exception {

        PlantUMLClassDiagramConfigBuilder configBuilder = new PlantUMLClassDiagramConfigBuilder(
                List.of(
                        // Entities & Enums
                        "software.decibel.entities",
                        "software.decibel.enums",
                        // Controllers
                        "software.decibel.controllers",
                        "software.decibel.controllers.Comment",
                        "software.decibel.controllers.Track",
                        "software.decibel.controllers.User",
                        "software.decibel.controllers.messaging",
                        // Services
                        "software.decibel.services",
                        "software.decibel.services.admin",
                        "software.decibel.services.auth",
                        "software.decibel.services.engagement",
                        "software.decibel.services.messaging",
                        "software.decibel.services.notification",
                        "software.decibel.services.playlist",
                        "software.decibel.services.search",
                        "software.decibel.services.subscription",
                        "software.decibel.services.track",
                        "software.decibel.services.user",
                        // Repositories
                        "software.decibel.repositories",
                        // DTOs
                        "software.decibel.dtos.auth",
                        "software.decibel.dtos.auth.google",
                        "software.decibel.dtos.admin",
                        "software.decibel.dtos.comment",
                        "software.decibel.dtos.comment.replies",
                        "software.decibel.dtos.discovery",
                        "software.decibel.dtos.engagement",
                        "software.decibel.dtos.messaging",
                        "software.decibel.dtos.moderation",
                        "software.decibel.dtos.notifications",
                        "software.decibel.dtos.playlist",
                        "software.decibel.dtos.search",
                        "software.decibel.dtos.subscription",
                        "software.decibel.dtos.track.requests",
                        "software.decibel.dtos.track.responses",
                        "software.decibel.dtos.user",
                        // Mappers & Projections
                        "software.decibel.mappers",
                        "software.decibel.projections"
                )
        )
                .withClassLoader(Thread.currentThread().getContextClassLoader());

        PlantUMLClassDiagramGenerator generator
                = new PlantUMLClassDiagramGenerator(configBuilder.build());

        String diagram = generator.generateDiagramText();

        Files.writeString(Path.of("diagram.puml"), diagram);

        System.out.println("✅ diagram.puml generated at project root!");
    }
}
