package software.decibel.customValidation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidWaveFormDataValidator.class)
public @interface ValidWaveFormData {
  String message() default "Invalid waveform data";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
