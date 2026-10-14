package ru.microservicesApp.orderService.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = PasswordByteLengthValidator.class)
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface PasswordByteLength {
    String message() default "Password must not exceed {max} UTF-8 bytes";

    int max() default 72;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
