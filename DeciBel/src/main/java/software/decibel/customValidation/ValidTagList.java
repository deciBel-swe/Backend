package software.decibel.customValidation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidTagListValidator.class)
public @interface ValidTagList {
  String message() default "Invalid tags";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
