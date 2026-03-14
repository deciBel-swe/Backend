package software.decibel.services;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import software.decibel.dtos.auth.DeviceInfo;
import software.decibel.entities.Session;
import software.decibel.entities.Token;
import software.decibel.entities.User;
import software.decibel.repositories.SessionRepository;

@Service
public class SessionService {

    private final SessionRepository sessionRepository;

    public SessionService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public Session createSession(User user, Token refreshToken, DeviceInfo deviceInfo) {
        Session session = Session.builder()
                .user(user)
                .refreshToken(refreshToken)
                .deviceType(deviceInfo.deviceType())
                .deviceFingerprint(deviceInfo.fingerPrint())
                .deviceName(deviceInfo.deviceName())
                .lastUsedAt(LocalDateTime.now())
                .build();
        return sessionRepository.save(session);
    }
}
