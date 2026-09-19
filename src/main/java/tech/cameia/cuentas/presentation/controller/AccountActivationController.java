package tech.cameia.cuentas.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import tech.cameia.cuentas.application.command.ActivateAccountCommand;
import tech.cameia.cuentas.application.service.ActivateAccountService;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.presentation.dto.ActivatedAccountResponse;

/**
 * Activa la cuenta de quien ya verificó su correo.
 *
 * <p>Exige identidad: el API Gateway valida el token de Firebase y propaga el usuario en
 * {@code X-User-Id}. Este servicio nunca recibe ni interpreta el token.</p>
 *
 * <p>El encabezado {@code X-User-Email-Verified} es opcional porque el Gateway todavía no
 * lo emite. Cuando llega en {@code true} se toma como prueba, porque solo el Gateway pudo
 * ponerlo tras validar el token; en cualquier otro caso se consulta a Firebase.</p>
 */
@RestController
class AccountActivationController {

    private final ActivateAccountService servicio;

    AccountActivationController(ActivateAccountService servicio) {
        this.servicio = servicio;
    }

    /**
     * Pasa la cuenta a activa tras la verificación del correo.
     *
     * <p>Es idempotente: repetir la llamada sobre una cuenta ya activa responde lo mismo
     * sin volver a escribir nada.</p>
     *
     * @param firebaseUid identificador del usuario, emitido por el Gateway
     * @param correoVerificado lo que el Gateway afirma sobre el correo, si lo propaga
     * @return {@code 200 OK} con el identificador de la cuenta y su estado
     */
    @PostMapping("/api/v1/users/me/verification")
    ResponseEntity<ActivatedAccountResponse> activate(
            @RequestHeader("X-User-Id") String firebaseUid,
            @RequestHeader(value = "X-User-Email-Verified", required = false) Boolean correoVerificado) {

        Account cuenta = servicio.activate(new ActivateAccountCommand(firebaseUid, correoVerificado));
        return ResponseEntity.ok(ActivatedAccountResponse.de(cuenta));
    }
}
