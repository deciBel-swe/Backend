package software.decibel.dtos.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// Data Transfer Object for forgot password requests.
public record ForgotPasswordRequest(
        @Email
        @NotBlank
        String email
        ) {

}
//http://localhost:8081/?iss=https%3A%2F%2Faccounts.google.com&code=4%2F0AfrIepBHZolMrP31ir8SSsSCpS4O0R-cPGIxpw-stgVOj-c5qVeD8XOyBhlUB2eBR5LU1g&scope=email+profile+https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fuserinfo.profile+https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fuserinfo.email+openid&authuser=1&prompt=consent
