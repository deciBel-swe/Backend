package software.decibel.customValidation;

import java.util.regex.Pattern;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidEmailValidator implements ConstraintValidator<ValidEmail, String> {

    // Standard email format regex
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$"
    );

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        if (email == null || email.isBlank()) {
            return true; // let @NotBlank handle nulls
        }
        context.disableDefaultConstraintViolation();

        // Step 1 — format check
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            context.buildConstraintViolationWithTemplate("Email format is invalid")
                    .addConstraintViolation();
            return false;
        }

        // Step 2 — DNS MX record check
        String domain = email.substring(email.indexOf('@') + 1);
        if (!hasMxRecord(domain)) {
            context.buildConstraintViolationWithTemplate("Email domain does not exist or cannot receive emails")
                    .addConstraintViolation();
            return false;
        }

        return true;
    }

    private boolean hasMxRecord(String domain) {
        try {
            InitialDirContext ctx = new InitialDirContext();
            Attributes attrs = ctx.getAttributes("dns:/" + domain, new String[]{"MX"});
            return attrs.get("MX") != null;
        } catch (NamingException e) {
            return false;
        }
    }
}
