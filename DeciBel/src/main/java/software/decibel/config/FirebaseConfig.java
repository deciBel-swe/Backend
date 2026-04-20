package software.decibel.config;

import java.io.InputStream;

import org.springframework.core.io.ClassPathResource;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.service-account-path:firebase-service-account.json}")
    private String serviceAccountPath;

    @PostConstruct
    public void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                InputStream serviceAccount
                        = new ClassPathResource(serviceAccountPath).getInputStream();

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("Firebase initialized successfully");
            }
        } catch (IOException e) {
            log.warn("Firebase service account file not found at '{}' — FCM notifications disabled",
                    serviceAccountPath);
        }
    }

    @Bean(destroyMethod = "")
    public Firestore getFirestore() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                initialize();
            }
            Firestore firestore = FirestoreClient.getFirestore();
            // Force a simple operation to check if it's really open
            // Using a lighter weight check if possible, or just catch specific closed exception
            try {
                firestore.listCollections();
            } catch (IllegalStateException e) {
                 if (e.getMessage() != null && e.getMessage().contains("closed")) {
                     throw e; // Rethrow to be caught by outer catch
                 }
            }
            return firestore;
        } catch (Exception e) {
            log.warn("Firestore client check failed or was closed: {}. Re-initializing Firebase...", e.getMessage());
            try {
                for (FirebaseApp app : new ArrayList<>(FirebaseApp.getApps())) {
                    app.delete();
                }
            } catch (Exception deleteEx) {
                log.error("Error deleting Firebase apps", deleteEx);
            }
            initialize();
            return FirestoreClient.getFirestore();
        }
    }

}
