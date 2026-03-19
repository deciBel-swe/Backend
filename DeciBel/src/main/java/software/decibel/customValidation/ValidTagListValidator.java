package software.decibel.customValidation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public class ValidTagListValidator implements ConstraintValidator<ValidTagList, String> {

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    // tags arent a requirement
    if (value == null || value.isBlank()) return true;

    List<String> tags;
    try {
      tags = new ObjectMapper().readValue(value, new TypeReference<List<String>>() {});
    } catch (Exception e) {
      context.disableDefaultConstraintViolation();
      context
          .buildConstraintViolationWithTemplate(
              "Tags must be a valid JSON array e.g. [\"rock\",\"pop\"]")
          .addConstraintViolation();
      return false;
    }

    for (String tag : tags) {
      if (tag == null || tag.isBlank()) {
        context.disableDefaultConstraintViolation();
        context
            .buildConstraintViolationWithTemplate("Tags cannot be blank")
            .addConstraintViolation();
        return false;
      }
    }

    return true;
  }
}
