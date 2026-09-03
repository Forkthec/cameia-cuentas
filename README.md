\# cameia-cuentas



Microservicio de Cuentas de CAMEIA. Gestiona la cuenta local, planes, suscripción, pagos y entitlements sin custodiar contraseñas.



> \*\*Estado:\*\* repositorio creado para el Sprint 1. La configuración técnica corresponde a \[CM-103](https://f0rktech.atlassian.net/browse/CM-103); las capacidades descritas son alcance arquitectónico hasta que exista evidencia de implementación.



\## Alcance del Sprint 1



\- Base técnica del microservicio: `CM-103`.

\- Registro de nuevo usuario: \[CM-14](https://f0rktech.atlassian.net/browse/CM-14).

\- Inicio de sesión con correo y contraseña: \[CM-15](https://f0rktech.atlassian.net/browse/CM-15).



Los pagos, renovaciones y reglas completas de cuota solo entran cuando Jira los incorpore al incremento.



\## Responsabilidades



\- Mantener el estado local de la cuenta asociado a `firebaseUid`.

\- Integrarse con Firebase sin almacenar contraseñas.

\- Gestionar planes, suscripciones y transacciones cuando entren en alcance.

\- Publicar cambios de plan/cuenta mediante contratos aprobados.

\- Mantener su propia persistencia sin claves foráneas hacia otros contextos.



No registra el consumo detallado de IA ni implementa perfiles o entrevistas.



\## Contexto arquitectónico



```mermaid

flowchart LR

&#x20;   G\[cameia-gateway] --> C\[cameia-cuentas]

&#x20;   C --> F\[Firebase Authentication]

&#x20;   C --> DB\[(PostgreSQL Cuentas)]

&#x20;   C -. pagos previstos .-> W\[Wompi]

&#x20;   C -. eventos aprobados .-> R\[RabbitMQ]

```



\## Tecnología prevista



| Elemento | Línea base |

|---|---|

| Lenguaje | Java 21 |

| Framework | Spring Boot 4.1.1 |

| Build | Maven; wrapper pendiente de confirmar |

| Persistencia | PostgreSQL 16, base/rol propios |

| Integraciones | Firebase Admin, Wompi y RabbitMQ según alcance |

| Ejecución objetivo | Contenedor OCI en Cloud Run |



\## Contratos y datos



\- Identificador compartido entre contextos: `firebaseUid`.

\- Credenciales y contraseñas permanecen en Firebase.

\- Suscripciones, pagos y eventos deben ser idempotentes.

\- Los nombres, esquemas y bindings de eventos se versionarán cuando sean aprobados.



\## Ejecución local



```text

Instalación: pendiente de confirmar en CM-103

Pruebas: pendiente de confirmar en CM-103

Build: pendiente de confirmar en CM-103

Inicio: pendiente de confirmar en CM-103

Health check: pendiente de confirmar en CM-103

```



\## Configuración y seguridad



\- No guardar credenciales Firebase/Wompi, secretos ni `.env` en Git.

\- Validar firma e idempotencia de webhooks cuando entren en alcance.

\- No registrar tokens o información de pago sensible.

\- Usar una base y un rol independientes de los demás microservicios.



\## Calidad esperada



\- Pruebas de registro, estados de cuenta, autenticación delegada y autorización.

\- Pruebas negativas para tokens inválidos y acceso cruzado.

\- Pruebas de idempotencia para pagos/eventos cuando apliquen.

\- CI con build, pruebas y seguridad después de confirmar comandos reales.



\## Contribución



\- `main` es estable y solo recibe promociones `develop → main` mediante Merge commit.

\- `develop` integra ramas `CA-<numero>-<descripcion-kebab-case>` mediante Squash.

\- Todo cambio ordinario entra mediante PR y revisión distinta del autor; la rama `CA-\*` se elimina después.



\## Cuándo actualizar este README



Actualizarlo en el mismo PR que cambie propósito, stack, comandos, variables, endpoints, esquema de datos, eventos, integraciones, pruebas, despliegue o responsables. Si no aplica, justificarlo en el PR.



