package tech.cameia.cuentas.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import tech.cameia.cuentas.application.command.RegisterUserCommand;
import tech.cameia.cuentas.domain.exception.DependencyUnavailableException;
import tech.cameia.cuentas.domain.exception.EmailAlreadyRegisteredException;
import tech.cameia.cuentas.domain.exception.InvalidBirthDateException;
import tech.cameia.cuentas.domain.exception.InvalidPersonNameException;
import tech.cameia.cuentas.domain.exception.WeakPasswordException;
import tech.cameia.cuentas.domain.model.Account;
import tech.cameia.cuentas.domain.model.AccountStatus;
import tech.cameia.cuentas.domain.model.BirthDate;
import tech.cameia.cuentas.domain.model.DirectoryUser;
import tech.cameia.cuentas.domain.model.EmailAddress;
import tech.cameia.cuentas.domain.model.Pronoun;
import tech.cameia.cuentas.domain.model.RawPassword;
import tech.cameia.cuentas.domain.policy.AgePolicy;
import tech.cameia.cuentas.domain.policy.PasswordPolicy;
import tech.cameia.cuentas.domain.port.AccountRepository;
import tech.cameia.cuentas.infrastructure.client.InMemoryFirebaseUserDirectory;
import tech.cameia.cuentas.infrastructure.messaging.InMemoryEventPublisher;
import tech.cameia.cuentas.infrastructure.persistence.InMemoryOutboxRepository;

/**
 * Prueba del caso de uso de registro descrito en
 * {@code specs/CM-14-RegistroUsuario/spec.md} (REQ-CU-02 a REQ-CU-07).
 *
 * <p>Usa dobles en memoria de los dos puertos: lo que se verifica es el orden de las
 * llamadas y qué pasa cuando una falla, no el comportamiento de Firebase ni de
 * PostgreSQL.</p>
 */
@ExtendWith(OutputCaptureExtension.class)
class RegisterUserServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-08T12:00:00Z");
    private static final String CORREO = "ana@cameia.tech";

    private final InMemoryFirebaseUserDirectory directorio = new InMemoryFirebaseUserDirectory();
    private final RepositorioEnMemoria repositorio = new RepositorioEnMemoria();
    private final InMemoryOutboxRepository outbox = new InMemoryOutboxRepository();
    private final InMemoryEventPublisher publisher = new InMemoryEventPublisher();

    private RegisterUserService servicio;

    @BeforeEach
    void prepararServicio() {
        servicio = servicioCon(directorio);
    }

    private RegisterUserService servicioCon(InMemoryFirebaseUserDirectory directorioDePrueba) {
        Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
        return servicioCon(directorioDePrueba, new OutboxRelayService(outbox, publisher, reloj));
    }

    private RegisterUserService servicioCon(InMemoryFirebaseUserDirectory directorioDePrueba, OutboxRelayService relay) {
        Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
        return new RegisterUserService(directorioDePrueba, repositorio,
                new AccountRecordingService(repositorio, outbox, reloj), relay, new AgePolicy(),
                new PasswordPolicy(Set.of("123456789012", "password1234", "qwertyuiop123")), reloj);
    }

    @Test
    void unCorreoNuevoCreaLaCuentaYDiceQueLaCreo() {
        RegisterUserResult resultado = servicio.register(comando());

        assertThat(resultado.created()).isTrue();
        assertThat(directorio.cantidadDeUsuarios()).isEqualTo(1);
        assertThat(repositorio.guardadas).hasSize(1);
    }

    @Test
    @DisplayName("Un registro nuevo agrega el evento cuenta.creada con la cuenta, el correo y la correlación")
    void register_shouldRecordAccountCreated_whenAccountIsNew() {
        RegisterUserResult resultado = servicio.register(comando());

        assertThat(outbox.appended()).hasSize(1);
        assertThat(outbox.appended().get(0).firebaseUid()).isEqualTo(resultado.account().getFirebaseUid());
        assertThat(outbox.appended().get(0).email().value()).isEqualTo("ana@cameia.tech");
        assertThat(outbox.appended().get(0).correlationId().value()).isEqualTo("req-1");
    }

    @Test
    @DisplayName("Repetir el registro de una cuenta pendiente no agrega otro evento")
    void register_shouldNotRecordEvent_whenPendingAccountIsReturned() {
        servicio.register(comando());

        servicio.register(comando());

        assertThat(outbox.appended()).hasSize(1);
    }

    @Test
    @DisplayName("Un correo de una cuenta activa responde conflicto y no agrega evento")
    void register_shouldNotRecordEvent_whenEmailBelongsToActiveAccount() {
        String uid = directorio.crearSinCuentaLocal(CORREO, AHORA.minusSeconds(5));
        repositorio.guardadas.put(uid, Account.rebuild(UUID.randomUUID(), uid, "Ana", "Pérez",
                new BirthDate(LocalDate.of(1995, 4, 12)), null, null, AccountStatus.ACTIVE));

        assertThatThrownBy(() -> servicio.register(comando())).isInstanceOf(EmailAlreadyRegisteredException.class);

        assertThat(outbox.appended()).isEmpty();
    }

    @Test
    @DisplayName("Si Firebase no está disponible no se agrega ningún evento")
    void register_shouldNotRecordEvent_whenFirebaseIsUnavailable() {
        directorio.quedarIndisponibleAlEscribirElPlan();

        assertThatThrownBy(() -> servicio.register(comando())).isInstanceOf(DependencyUnavailableException.class);

        assertThat(outbox.appended()).isEmpty();
    }

    @Test
    @DisplayName("Un registro nuevo publica el evento de inmediato cuando el broker confirma")
    void register_shouldPublishEvent_whenBrokerConfirms() {
        RegisterUserResult resultado = servicio.register(comando());

        assertThat(publisher.published()).hasSize(1);
        assertThat(publisher.published().get(0).id()).isEqualTo(resultado.eventId());
        assertThat(outbox.pending()).isEmpty();
    }

    @Test
    @DisplayName("Si el broker no confirma, la cuenta se crea igual y el evento queda pendiente con un intento")
    void register_shouldStillCreateAccount_whenPublicationIsNotConfirmed() {
        publisher.failNext(1);

        RegisterUserResult resultado = servicio.register(comando());

        assertThat(resultado.created()).isTrue();
        assertThat(directorio.borrados()).isZero();
        assertThat(outbox.pending()).hasSize(1);
        assertThat(outbox.pending().get(0).attempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("Si el relevo lanza una excepción, la cuenta se crea igual, sin compensar, y queda el aviso")
    void register_shouldStillCreateAccount_whenRelayThrows(CapturedOutput salida) {
        OutboxRelayService relayRoto = new OutboxRelayService(new InMemoryOutboxRepository() {
            @Override
            public java.util.Optional<tech.cameia.cuentas.domain.event.OutboundEvent> findPendingById(UUID id) {
                throw new IllegalStateException("fallo simulado");
            }
        }, publisher);
        RegisterUserService servicioConRelayRoto = servicioCon(directorio, relayRoto);

        RegisterUserResult resultado = servicioConRelayRoto.register(comando());

        assertThat(resultado.created()).isTrue();
        assertThat(directorio.borrados()).isZero();
        assertThat(salida.getOut()).contains("Falló la publicación inmediata");
    }

    @Test
    @DisplayName("Repetir el registro de una cuenta pendiente no vuelve a publicar")
    void register_shouldNotPublish_whenPendingAccountIsReturned() {
        servicio.register(comando());

        RegisterUserResult repetido = servicio.register(comando());

        assertThat(repetido.eventId()).isNull();
        assertThat(publisher.published()).hasSize(1);
    }

    @Test
    @DisplayName("Si el evento no se puede guardar se borra la credencial y se propaga el fallo")
    void register_shouldCompensateCredential_whenEventCannotBeStored() {
        outbox.failNextAppend();

        assertThatThrownBy(() -> servicio.register(comando())).isInstanceOf(IllegalStateException.class)
                .hasMessage("fallo simulado");

        assertThat(directorio.borrados()).isEqualTo(1);
        assertThat(directorio.cantidadDeUsuarios()).isZero();
    }

    @Test
    void repetirElRegistroConLaCuentaPendienteDevuelveLaMismaCuentaSinEscribirNada() {
        RegisterUserResult primero = servicio.register(comando());

        RegisterUserResult segundo = servicio.register(comando());

        assertThat(segundo.created()).isFalse();
        assertThat(segundo.account().getId()).isEqualTo(primero.account().getId());
        assertThat(directorio.creaciones()).isEqualTo(1);
        assertThat(directorio.borrados()).isZero();
        assertThat(repositorio.guardados).isEqualTo(1);
    }

    @Test
    void unCorreoConOtraGrafiaEsElMismoCorreo() {
        RegisterUserResult primero = servicio.register(comando());

        RegisterUserResult segundo = servicio.register(comando("  ANA@Cameia.Tech ", "frase secreta larga"));

        assertThat(segundo.created()).isFalse();
        assertThat(segundo.account().getId()).isEqualTo(primero.account().getId());
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"ACTIVE", "DISABLED", "ANONYMIZED"})
    void unaCuentaActivaBloqueadaOAnonimizadaDaElMismoConflicto(AccountStatus estado) {
        String uid = directorio.crearSinCuentaLocal(CORREO, AHORA.minusSeconds(5));
        repositorio.guardadas.put(uid, Account.rebuild(UUID.randomUUID(), uid, "Ana", "Pérez",
                new BirthDate(LocalDate.of(1995, 4, 12)), null, null, estado));

        assertThatThrownBy(() -> servicio.register(comando()))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessage("Ese correo ya tiene una cuenta.");
        assertThat(repositorio.guardados).isZero();
    }

    @Test
    void unUsuarioDeshabilitadoEnFirebaseConFilaPendienteDaConflicto() {
        String uid = servicio.register(comando()).account().getFirebaseUid();
        directorio.deshabilitar(uid);

        assertThatThrownBy(() -> servicio.register(comando()))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        assertThat(repositorio.guardadas.get(uid).isPendingVerification()).isTrue();
        assertThat(repositorio.guardados).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(longs = {299, 300, 301})
    void unaCredencialSinCuentaLocalAntigua(long segundos, CapturedOutput salida) {
        String uid = directorio.crearSinCuentaLocal(CORREO, AHORA.minusSeconds(segundos));

        assertThatThrownBy(() -> servicio.register(comando()))
                .isInstanceOfSatisfying(EmailAlreadyRegisteredException.class, error -> {
                    assertThat(error.getFirebaseUid()).isEqualTo(uid);
                    assertThat(error.requiresReconciliation()).isEqualTo(segundos >= 300);
                });
        assertThat(salida.getOut()).doesNotContain(uid);
    }

    @Test
    void unaCredencialConFechaDeCreacionDesconocidaPideConciliar() {
        directorio.crearSinCuentaLocal(CORREO, Instant.EPOCH);

        assertThatThrownBy(() -> servicio.register(comando()))
                .isInstanceOfSatisfying(EmailAlreadyRegisteredException.class,
                        error -> assertThat(error.requiresReconciliation()).isTrue());
    }

    @Test
    void siElSdkReintentaUnaCreacionCompletadaLaCredencialSeTomaPorPropia(CapturedOutput salida) {
        directorio.simularReintentoDelSdk();

        RegisterUserResult resultado = servicio.register(comando());

        String uid = resultado.account().getFirebaseUid();
        assertThat(resultado.created()).isTrue();
        assertThat(directorio.existe(uid)).isTrue();
        assertThat(directorio.planDe(uid)).isEqualTo("FREE");
        assertThat(repositorio.guardadas).containsOnlyKeys(uid);
        assertThat(directorio.cantidadDeUsuarios()).isEqualTo(1);
        assertThat(directorio.borrados()).isZero();
        assertThat(salida.getOut()).contains("tras un reintento del directorio").doesNotContain(CORREO);
    }

    @Test
    void siLaCreacionNoRespondePeroSeCompletoLaCredencialPropiaSeBorra() {
        directorio.perderLaRespuestaDeLaCreacion();

        assertThatThrownBy(() -> servicio.register(comando())).isInstanceOf(DependencyUnavailableException.class);

        assertThat(directorio.creaciones()).isEqualTo(1);
        assertThat(directorio.cantidadDeUsuarios()).isZero();
        assertThat(directorio.borrados()).isEqualTo(1);
        assertThat(repositorio.guardadas).isEmpty();
    }

    @Test
    void despuesDeUnaCreacionSinRespuestaElReintentoDeLaPersonaSeRegistra() {
        directorio.perderLaRespuestaDeLaCreacion();
        assertThatThrownBy(() -> servicio.register(comando())).isInstanceOf(DependencyUnavailableException.class);
        directorio.dejarDeFallar();

        RegisterUserResult reintento = servicio.register(comando());

        assertThat(reintento.created()).isTrue();
        assertThat(directorio.cantidadDeUsuarios()).isEqualTo(1);
        assertThat(repositorio.guardadas).hasSize(1);
    }

    @Test
    void siElBorradoTrasUnaCreacionSinRespuestaFallaSigueLaIndisponibilidadYSoloHayUnAviso(CapturedOutput salida) {
        directorio.perderLaRespuestaDeLaCreacion();
        directorio.fallarAlBorrar();

        assertThatThrownBy(() -> servicio.register(comando())).isInstanceOf(DependencyUnavailableException.class);

        assertThat(salida.getOut()).contains("No se pudo confirmar el borrado").doesNotContain(CORREO)
                .doesNotContain("requiere conciliación manual");
    }

    @Test
    void siElSdkReintentaYLuegoFallaElPlanLaCredencialPropiaSeBorra() {
        directorio.simularReintentoDelSdk();
        directorio.fallarAlEscribirElPlan();

        assertThatThrownBy(() -> servicio.register(comando())).isInstanceOf(IllegalStateException.class);

        assertThat(directorio.cantidadDeUsuarios()).isZero();
        assertThat(repositorio.guardadas).isEmpty();
    }

    @Test
    void elConflictoDeUnaCuentaNoPendienteNoDejaSuEstadoEnElLog(CapturedOutput salida) {
        String uid = directorio.crearSinCuentaLocal(CORREO, AHORA.minusSeconds(5));
        repositorio.guardadas.put(uid, Account.rebuild(UUID.randomUUID(), uid, "Ana", "Pérez",
                new BirthDate(LocalDate.of(1995, 4, 12)), null, null, AccountStatus.DISABLED));

        assertThatThrownBy(() -> servicio.register(comando())).isInstanceOf(EmailAlreadyRegisteredException.class);

        assertThat(salida.getOut()).doesNotContain("DISABLED").doesNotContain("estado de la cuenta").doesNotContain(CORREO);
    }

    @Test
    void siFirebaseNoRespondeAlConsultarNoSeCreaNada() {
        directorio.fallarAlConsultar();

        assertThatThrownBy(() -> servicio.register(comando())).isInstanceOf(DependencyUnavailableException.class);

        assertThat(directorio.cantidadDeUsuarios()).isZero();
        assertThat(repositorio.guardadas).isEmpty();
    }

    @Test
    void laCarreraSeResuelveConUnaSolaConsultaMas() {
        String uid = directorio.crearSinCuentaLocal(CORREO, AHORA.minusSeconds(5));
        repositorio.guardadas.put(uid, cuentaPendiente(uid));

        RegisterUserResult resultado = servicioCon(ciegoLasPrimerasConsultas(1)).register(comando());

        assertThat(resultado.created()).isFalse();
        assertThat(resultado.account().getFirebaseUid()).isEqualTo(uid);
    }

    @Test
    void laCarreraSinFilaNuncaPideConciliarAunqueLaCredencialSeaAntigua() {
        directorio.crearSinCuentaLocal(CORREO, AHORA.minusSeconds(600));

        assertThatThrownBy(() -> servicioCon(ciegoLasPrimerasConsultas(1)).register(comando()))
                .isInstanceOfSatisfying(EmailAlreadyRegisteredException.class,
                        error -> assertThat(error.requiresReconciliation()).isFalse());
    }

    @Test
    void siLaSegundaConsultaDeLaCarreraEstaVaciaDaConflictoSinCrearNada() {
        directorio.crearSinCuentaLocal(CORREO, AHORA.minusSeconds(5));

        assertThatThrownBy(() -> servicioCon(ciegoLasPrimerasConsultas(2)).register(comando()))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        assertThat(directorio.creaciones()).isEqualTo(1);
    }

    @Test
    void unCuerpoInvalidoNoLlegaAFirebase() {
        servicio.register(comando());
        int consultasPrevias = directorio.consultas();

        assertThatThrownBy(() -> servicio.register(comando(CORREO, "123"))).isInstanceOf(WeakPasswordException.class);

        assertThat(directorio.consultas()).isEqualTo(consultasPrevias);
    }

    @Test
    void elRegistroRepetidoIgnoraLosOtrosDatosYNoUsaLaContrasenia(CapturedOutput salida) {
        RegisterUserResult primero = servicio.register(comando());
        RegisterUserCommand otrosDatos = new RegisterUserCommand("Luz", "Gómez", LocalDate.of(1990, 1, 1), CORREO,
                "otra frase muy distinta", "+573009876543", Pronoun.HE, null);

        RegisterUserResult segundo = servicio.register(otrosDatos);

        assertThat(segundo.account().getId()).isEqualTo(primero.account().getId());
        assertThat(segundo.account().getFirstName()).isEqualTo("Ana");
        assertThat(repositorio.guardados).isEqualTo(1);
        assertThat(directorio.contrasenaDe(primero.account().getFirebaseUid())).isEqualTo("frase secreta larga");
        assertThat(salida.getOut()).doesNotContain("frase secreta larga").doesNotContain("otra frase muy distinta");
    }

    /**
     * Directorio que simula una carrera: las primeras {@code vacias} consultas por correo responden
     * que no existe, aunque la credencial ya esté creada; las siguientes y toda creación van al
     * directorio real de la prueba.
     */
    private InMemoryFirebaseUserDirectory ciegoLasPrimerasConsultas(int vacias) {
        return new InMemoryFirebaseUserDirectory() {
            private int restantes = vacias;

            @Override
            public Optional<DirectoryUser> findByEmail(EmailAddress email) {
                if (restantes-- > 0) {
                    return Optional.empty();
                }
                return directorio.findByEmail(email);
            }

            @Override
            public void createUser(String firebaseUid, EmailAddress email, RawPassword password) {
                directorio.createUser(firebaseUid, email, password);
            }
        };
    }

    private Account cuentaPendiente(String uid) {
        return Account.rebuild(UUID.randomUUID(), uid, "Ana", "Pérez", new BirthDate(LocalDate.of(1995, 4, 12)),
                null, null, AccountStatus.PENDING_VERIFICATION);
    }

    @Test
    void registraLaCuentaPendienteDeVerificarYConPlanGratuito() {
        Account cuenta = servicio.register(comando()).account();

        assertThat(cuenta.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(cuenta.getFirstName()).isEqualTo("Ana");
        assertThat(directorio.existe(cuenta.getFirebaseUid())).isTrue();
        assertThat(directorio.planDe(cuenta.getFirebaseUid())).isEqualTo("FREE");
        assertThat(repositorio.guardadas).hasSize(1);
    }

    @Test
    void noTocaFirebaseCuandoLaFechaDeNacimientoNoPermiteRegistrarse() {
        RegisterUserCommand menorDeEdad = new RegisterUserCommand("Ana", "Pérez",
                LocalDate.now().minusYears(15), "ana@cameia.tech", "frase secreta larga",
                null, null, null);

        assertThatThrownBy(() -> servicio.register(menorDeEdad))
                .isInstanceOf(InvalidBirthDateException.class);
        assertThat(repositorio.guardadas).isEmpty();
    }

    @Test
    void noTocaFirebaseCuandoLaContraseniaEsDebil() {
        RegisterUserCommand contraseniaCorta = new RegisterUserCommand("Ana", "Pérez",
                LocalDate.of(1995, 4, 12), "ana@cameia.tech", "corta", null, null, null);

        assertThatThrownBy(() -> servicio.register(contraseniaCorta))
                .isInstanceOf(WeakPasswordException.class);
        assertThat(repositorio.guardadas).isEmpty();
    }

    @Test
    void unNombreConNumerosNoCreaCredencialNiCuenta() {
        RegisterUserCommand nombreInvalido = new RegisterUserCommand("Ana3", "Pérez", LocalDate.of(1995, 4, 12),
                "ana@cameia.tech", "frase secreta larga", null, Pronoun.SHE, null);

        assertThatThrownBy(() -> servicio.register(nombreInvalido))
                .isInstanceOf(InvalidPersonNameException.class)
                .extracting(error -> ((InvalidPersonNameException) error).getField())
                .isEqualTo("firstName");
        assertThat(directorio.cantidadDeUsuarios()).isZero();
        assertThat(repositorio.guardadas).isEmpty();
    }

    @Test
    void unApellidoConGuionBajoNoCreaCredencialNiCuenta() {
        RegisterUserCommand apellidoInvalido = new RegisterUserCommand("Ana", "Pérez_", LocalDate.of(1995, 4, 12),
                "ana@cameia.tech", "frase secreta larga", null, Pronoun.SHE, null);

        assertThatThrownBy(() -> servicio.register(apellidoInvalido))
                .isInstanceOf(InvalidPersonNameException.class)
                .extracting(error -> ((InvalidPersonNameException) error).getField())
                .isEqualTo("lastName");
        assertThat(directorio.cantidadDeUsuarios()).isZero();
        assertThat(repositorio.guardadas).isEmpty();
    }

    @Test
    void unNombreConTildesYGuionSeGuardaNormalizado() {
        RegisterUserCommand conTildes = new RegisterUserCommand("María  José", "Gómez-Ruiz",
                LocalDate.of(1995, 4, 12), "ana@cameia.tech", "frase secreta larga", null, Pronoun.SHE, null);

        Account cuenta = servicio.register(conTildes).account();

        assertThat(cuenta.getFirstName()).isEqualTo("María José");
        assertThat(cuenta.getLastName()).isEqualTo("Gómez-Ruiz");
    }

    @Test
    void borraLaCredencialCuandoFallaLaEscrituraDelPlan() {
        directorio.fallarAlEscribirElPlan();

        assertThatThrownBy(() -> servicio.register(comando())).isInstanceOf(IllegalStateException.class);

        assertThat(repositorio.guardadas).isEmpty();
        // La credencial se borró, así que el correo vuelve a estar libre: el segundo
        // intento no choca con el correo ya registrado.
        directorio.dejarDeFallar();
        assertThat(servicio.register(comando()).account().getFirebaseUid()).isNotBlank();
    }

    @Test
    void firebaseNoDisponibleAlEscribirElPlanCompensaYPropagaLaMismaExcepcion() {
        directorio.quedarIndisponibleAlEscribirElPlan();

        assertThatThrownBy(() -> servicio.register(comando())).isInstanceOf(DependencyUnavailableException.class);

        assertThat(repositorio.guardadas).isEmpty();
        // La credencial se borró: un segundo intento no choca con un correo ocupado.
        directorio.dejarDeFallar();
        assertThat(servicio.register(comando()).account().getFirebaseUid()).isNotBlank();
    }

    @Test
    void borraLaCredencialCuandoFallaLaBaseDeDatos() {
        repositorio.fallarAlGuardar();

        assertThatThrownBy(() -> servicio.register(comando())).isInstanceOf(IllegalStateException.class);

        assertThat(repositorio.guardadas).isEmpty();
        assertThat(directorio.existe(repositorio.ultimoUid)).isFalse();
    }

    @Test
    void siLaCompensacionTambienFallaElRegistroSigueFallando() {
        repositorio.fallarAlGuardar();
        directorio.fallarAlBorrar();

        // El registro propaga el fallo original, no el del borrado: quien llama necesita
        // saber que el registro no se completó. La credencial huérfana queda en el log.
        assertThatThrownBy(() -> servicio.register(comando()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PostgreSQL");
    }

    @Test
    void laCompensacionFallidaDejaElUidEnElLogSinElCorreoNiLaContrasenia(CapturedOutput salida) {
        repositorio.fallarAlGuardar();
        directorio.fallarAlBorrar();

        assertThatThrownBy(() -> servicio.register(comando())).isInstanceOf(IllegalStateException.class);

        assertThat(salida.getOut())
                .contains("ERROR")
                .contains(repositorio.ultimoUid)
                .contains("conciliación manual")
                .doesNotContain("ana@cameia.tech")
                .doesNotContain("frase secreta larga");
    }

    @Test
    void unaContraseniaDebilNoQuedaEnElLog(CapturedOutput salida) {
        RegisterUserCommand comun = new RegisterUserCommand("Ana", "Pérez", LocalDate.of(1995, 4, 12),
                "ana@cameia.tech", "password1234", null, Pronoun.SHE, null);

        assertThatThrownBy(() -> servicio.register(comun)).isInstanceOf(WeakPasswordException.class);

        assertThat(salida.getOut()).doesNotContain("password1234").doesNotContain("ana@cameia.tech");
    }

    private RegisterUserCommand comando() {
        return comando(CORREO, "frase secreta larga");
    }

    private RegisterUserCommand comando(String correo, String contrasenia) {
        return new RegisterUserCommand("Ana", "Pérez", LocalDate.of(1995, 4, 12),
                correo, contrasenia, "+573001234567", Pronoun.SHE, "req-1");
    }

    /** Repositorio en memoria que puede simular un fallo de la base de datos. */
    private static class RepositorioEnMemoria implements AccountRepository {

        private final Map<String, Account> guardadas = new HashMap<>();
        private int guardados;
        private boolean fallar;
        private String ultimoUid;

        @Override
        public Account save(Account account) {
            ultimoUid = account.getFirebaseUid();
            if (fallar) {
                throw new IllegalStateException("PostgreSQL no está disponible");
            }
            guardadas.put(account.getFirebaseUid(), account);
            guardados++;
            return account;
        }

        @Override
        public Optional<Account> findByFirebaseUid(String firebaseUid) {
            return Optional.ofNullable(guardadas.get(firebaseUid));
        }

        void fallarAlGuardar() {
            this.fallar = true;
        }
    }
}
