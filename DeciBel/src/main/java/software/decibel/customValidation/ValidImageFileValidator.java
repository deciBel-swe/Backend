package software.decibel.customValidation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

// TODO: Break down validations into separate ones to be reused in future iterations

public class ValidImageFileValidator implements ConstraintValidator<ValidImageFile, MultipartFile> {

  private static final List<String> ALLOWED_TYPES = List.of("image/jpeg", "image/png");

  @Override
  public boolean isValid(MultipartFile file, ConstraintValidatorContext ctx) {

    // if image isn't even provided no need to check type
    if (file == null || file.isEmpty()) return true;

    if (!ALLOWED_TYPES.contains(file.getContentType())) {
      ctx.disableDefaultConstraintViolation();
      ctx.buildConstraintViolationWithTemplate("Cover image must be JPG or PNG")
          .addConstraintViolation();
      return false;
    }

    return true;
  }
}
