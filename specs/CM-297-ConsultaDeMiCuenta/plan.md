# Plan — CM-297: `GET /api/v1/users/me`

## 1. Enfoque

1. **Fecha desconocida (D2) primero.** `Account` admite fecha nula al reconstruirse; `getBirthDate()` devuelve `Optional<BirthDate>`; `AccountMapper`
   lee y escribe `null`. Sin esto, el endpoint respondería 500 para las cuentas antiguas.
2. **Caso de uso** `GetCurrentAccountService.find(String firebaseUid)`: valida la identidad (blanco o > 128 → `IdentityRequiredException`), busca por
   `firebase_uid` y trata `ANONYMIZED` como inexistente.
3. **Controlador** `CurrentAccountController` (`GET /api/v1/users/me`) y DTO `CurrentAccountResponse`, con OpenAPI completo; el manejador traduce
   `IdentityRequiredException` al mismo 400 del encabezado ausente.
4. Pruebas por capa, E2E con PostgreSQL real, `docs/errores.md`, Postman.

Patrones: los del repo (caso de uso con puerto, `record` de respuesta con fábrica `de`, excepción de negocio con su código). Nada nuevo.

## 2. Archivos

| Crear | Modificar | No tocar |
|---|---|---|
| `application/service/GetCurrentAccountService.java`, `domain/exception/IdentityRequiredException.java`, `presentation/controller/CurrentAccountController.java`, `presentation/dto/CurrentAccountResponse.java`; pruebas `application/service/GetCurrentAccountServiceTest.java`, `presentation/controller/CurrentAccountControllerTest.java`, `CurrentAccountEndToEndTest.java` (raíz de pruebas, como `AccountRegistrationEndToEndTest`) | `domain/model/Account.java` (`getBirthDate`), `infrastructure/persistence/mapper/AccountMapper.java`, `application/service/AccountRecordingService.java` (llamador de `getBirthDate`), `presentation/advice/BusinessExceptionHandler.java`, `docs/errores.md`, `postman/cameia-cuentas.postman_collection.json`, `postman/README.md`; pruebas que llaman `getBirthDate()` | migraciones, `pom.xml`, `.github/**`, `AccountActivationController` |

## 3. Integración con la cadena de Cuentas

Base: punta de `CM-279-postman-eventos-cuenta` (C3c). Esa cadena agregó `AccountRecordingService` (usa `getBirthDate()` para el evento),
`postman/local.postman_environment.json` y la carpeta «Eventos de cuenta (local)». Esta CM agrega la carpeta «Mi cuenta» al final y una fila de
`postman/README.md`. Si C2 y C3 se reordenan antes de ejecutar esta CM, la base sigue siendo la última capa de CM-279 que exista en la cadena.

## 4. Riesgos

| Riesgo | Mitigación |
|---|---|
| `Optional` en `getBirthDate()` rompe llamadores de la cadena | `git grep -n "getBirthDate()" -- src` en la base antes de cambiar; cada llamador se ajusta en la misma tarjeta |
| El registro nuevo publica `fechaNacimiento`: con `Optional` podría salir nula | El registro siempre tiene fecha (`Account.register` la exige): `orElseThrow` con mensaje de invariante |
| Newman exige `.env` y emulador | PENDIENTE de Paula (P36-2); las pruebas automáticas cubren el contrato |

## 5. Estimación

≈ 4 h de ejecución.
