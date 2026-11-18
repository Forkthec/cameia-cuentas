package tech.cameia.cuentas.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;
import org.hibernate.validator.constraintvalidation.HibernateConstraintViolationBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintValidatorContext;

import tech.cameia.cuentas.domain.exception.ErrorCode;
import tech.cameia.cuentas.domain.model.CurrentPolicy;

/**
 * Comprueba que la restricción compara contra la versión vigente y publica el código del dominio.
 */
@DisplayName("CurrentPolicyVersionValidator")
class CurrentPolicyVersionValidatorTest {

    private final CurrentPolicyVersionValidator validator =
            new CurrentPolicyVersionValidator(new CurrentPolicy("2026-11-v1"));

    @Test
    @DisplayName("deja pasar el valor nulo y el blanco, que reporta NotBlank")
    void isValid_shouldReturnTrue_whenNullOrBlank() {
        ConstraintValidatorContext context = mock(ConstraintValidatorContext.class);

        assertThat(validator.isValid(null, context)).isTrue();
        assertThat(validator.isValid("", context)).isTrue();
        assertThat(validator.isValid("   ", context)).isTrue();
        verifyNoInteractions(context);
    }

    @Test
    @DisplayName("acepta la versión vigente")
    void isValid_shouldReturnTrue_whenVersionIsCurrent() {
        assertThat(validator.isValid("2026-11-v1", mock(ConstraintValidatorContext.class))).isTrue();
    }

    @Test
    @DisplayName("rechaza otra versión y publica POLICY_VERSION_OUTDATED como carga")
    void isValid_shouldPublishOutdatedCode_whenVersionDiffers() {
        ConstraintValidatorContext context = mock(ConstraintValidatorContext.class);
        HibernateConstraintValidatorContext hibernate = mock(HibernateConstraintValidatorContext.class);
        HibernateConstraintViolationBuilder builder = mock(HibernateConstraintViolationBuilder.class,
                RETURNS_SELF);
        when(context.unwrap(HibernateConstraintValidatorContext.class)).thenReturn(hibernate);
        when(hibernate.withDynamicPayload(any())).thenReturn(hibernate);
        when(hibernate.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);

        boolean valid = validator.isValid("2026-10-v1", context);

        assertThat(valid).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(hibernate).withDynamicPayload(ErrorCode.POLICY_VERSION_OUTDATED);
        verify(hibernate).buildConstraintViolationWithTemplate(
                "La política cambió. Recarga la página y vuelve a aceptarla.");
        verify(builder).addConstraintViolation();
    }
}
