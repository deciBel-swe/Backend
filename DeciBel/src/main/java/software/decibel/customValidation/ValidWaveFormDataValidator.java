package software.decibel.customValidation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;

// Custom validation for waveform data which is a json array (string) of floats
public class ValidWaveFormDataValidator implements ConstraintValidator<ValidWaveFormData, String> {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public boolean isValid(String value, ConstraintValidatorContext ctx) {
    if (value == null || value.isBlank()) {
      ctx.disableDefaultConstraintViolation();
      ctx.buildConstraintViolationWithTemplate("Waveform data must not be null or empty")
          .addConstraintViolation();
      return false;
    }

    List<Float> values;
    try {
      values = objectMapper.readValue(value, new TypeReference<List<Float>>() {});
    } catch (Exception e) {
      ctx.disableDefaultConstraintViolation();
      ctx.buildConstraintViolationWithTemplate("Waveform data must be a valid JSON array of floats")
          .addConstraintViolation();
      return false;
    }

    if (values.isEmpty()) {
      ctx.disableDefaultConstraintViolation();
      ctx.buildConstraintViolationWithTemplate("Waveform data must not be empty")
          .addConstraintViolation();
      return false;
    }

    for (Float v : values) {
      if (v == null || v < 0.0f || v > 1.0f) {
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate("Waveform values must be between 0.0 and 1.0")
            .addConstraintViolation();
        return false;
      }
    }

    return true;
  }
}
