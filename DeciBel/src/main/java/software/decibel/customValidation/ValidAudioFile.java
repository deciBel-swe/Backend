package software.decibel.customValidation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// for further explanation :
// https://medium.com/@bereketberhe27/spring-boot-custom-validation-7af89a64f805
// Custom validation for audio file - as there are no validation available for multipart files
// naturally
// and to prevent moving validation to the service layer

@Target(ElementType.FIELD) // applies to fields (multipart files)
@Retention(RetentionPolicy.RUNTIME) // validation @runtime
@Constraint(validatedBy = ValidAudioFileValidator.class) // the class that implements the valiation
public @interface ValidAudioFile {
  String message() default "Audio file must be MP3 or WAV and must not be empty";

  // boilerplate forced to include

  // for grouping validations and specifying when to run validation
  Class<?>[] groups() default {};

  // to attach priority
  Class<? extends Payload>[] payload() default {};
}
