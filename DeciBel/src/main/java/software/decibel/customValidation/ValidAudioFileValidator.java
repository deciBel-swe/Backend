package software.decibel.customValidation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

// TODO: Break down validations into separate ones to be reused in future iterations
public class ValidAudioFileValidator implements ConstraintValidator<ValidAudioFile, MultipartFile> {

  private static final List<String> ALLOWED_TYPES =
      List.of("audio/mp3", "audio/wav", "audio/wave");
  private static final long MAX_SIZE = 100L * 1024 * 1024; // 100MB in bytes

  @Override
  public boolean isValid(MultipartFile file, ConstraintValidatorContext cvc) {

    // disable default msg
    cvc.disableDefaultConstraintViolation();

    // each validation has its msg

    if (file == null || file.isEmpty()) {
      cvc.buildConstraintViolationWithTemplate("Audio file is required").addConstraintViolation();
      return false;
    }

    if (!ALLOWED_TYPES.contains(file.getContentType())) {
      cvc.buildConstraintViolationWithTemplate("Audio file must be MP3 or WAV")
          .addConstraintViolation();
      return false;
    }

    if (file.getSize() > MAX_SIZE) {
      cvc.buildConstraintViolationWithTemplate("Audio file must not exceed 100MB")
          .addConstraintViolation();
      return false;
    }

    return true;
  }
}
