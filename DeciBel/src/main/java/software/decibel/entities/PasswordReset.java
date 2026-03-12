package software.decibel.entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class PasswordResetToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String token;

    private LocalDateTime expiryDate;

    @OneToOne
    private User user;
}
