package software.decibel.customValidation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidImageFileValidator.class)
public @interface ValidImageFile {
  String message() default "Cover image must be JPG or PNG";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
