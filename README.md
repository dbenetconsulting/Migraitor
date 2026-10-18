# Migraitor

Herramienta de migración documental con orígenes y destinos enchufables. Hoy trae dos tipos:

| Código (`jobs.migtype`) | Origen | Destino | Cliente |
|---|---|---|---|
| `CStoCS` | Content Server on‑premise (REST v1) | Content Server cloud (REST v2) | Deichmann |
| `SAPALNoDep` | SAP HCM ArchiveLink (RFC `ZRfcMigrationOt` vía SOAP) | Content Server cloud | Viega |

Cada tipo de migración es un módulo que implementa `MigrationType`. El destino va detrás de `TargetSystem` y por ahora solo existe el conector de Content Server. Para añadir un tipo o un destino nuevo, sigue [docs/NUEVO-TIPO.md](docs/NUEVO-TIPO.md). El diseño completo está en [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md).

## Requisitos

- Java 11 o superior en el servidor. El jar se compila para Java 11.
- SQL Server o PostgreSQL. Es la misma base de datos de v1: las tablas y columnas existentes no cambian.
- Maven 3.8+ para compilar.

## Compilar

```bash
mvn clean package          # incluye tests (H2 + WireMock)
# resultado: target/migraitor.jar
```

## Instalar en el servidor del cliente

1. **Base de datos.** Ejecuta `docs/sql/sqlserver.sql` o `docs/sql/postgresql.sql`. Los scripts son idempotentes y valen tanto para una base de datos nueva como para una de v1: solo crean lo que falta (columnas `hash_source` y `error_detail`, `sapalnodep_metadata.status`, las tablas nuevas y los índices).
2. **Secretos.** Copia `docs/credentials.example.properties`, rellénalo y guárdalo fuera del jar. Usa las mismas claves que v1 y añade tres nuevas:
   - `uiusername` y `uipassword`: el login de la UI;
   - `configkey`: la clave AES para cifrar las contraseñas que se guardan desde la UI (`openssl rand -base64 32`).
3. **Certificado HTTPS.** Pon el keystore PKCS12 junto al jar o indica su ruta con `MIGRAITOR_KEYSTORE`. Su contraseña va en el fichero de secretos con la clave `keystorepassword`.
4. **Arrancar:**

```bash
java -jar migraitor.jar \
  --spring.profiles.active=deichmann \
  --management.credentials=D:/Migraitor/credentials.properties \
  --LOG_PATH=D:/Migraitor/logs
```

5. Abre `https://servidor:8443/migrationtool` e inicia sesión.

Perfiles disponibles:
- `deichmann`: CStoCS, carpeta `999 Altakte` y WebReport por REST;
- `viega`: SAPALNoDep, con conversión de TIFF a PDF.

Cualquier valor de `application.properties` se puede sobrescribir con `--clave=valor` o con un `application.properties` junto al jar.

## Uso (Deichmann)

1. **Connections.** Configura el CS origen y el destino: URL, edición, usuario, contraseña y el id del WebReport. Usa **Test connection**, que también lista los parámetros del WebReport.
2. **Import:**
   - paso 1: lanza el WebReport origen o sube un CSV;
   - paso 2: haz lo mismo en el destino;
   - paso 3: **Match**.

   Los documentos cruzados quedan como `PENDING` en `migration` y `cs_metadata`. Los que no cruzan aparecen con su motivo (`NO_EMPLOYEE`, `NO_FOLDER`, `AMBIGUOUS_FOLDER`, `INVALID`) y se pueden descargar en CSV.
3. **Launch job.** Indica el nombre, el tipo, el número de documentos y el nivel de log. Con una fecha, el job queda programado.
4. **Jobs.** Muestra el progreso, los errores por documento con sus pasos, **Abort** y **Retry errors**.

## Qué cambia respecto a v1

- **`MigrationServiceImpl` se divide en piezas.**
  - `JobService` lanza, aborta y reintenta jobs, y los recupera tras un reinicio.
  - `BatchClaimer` reserva documentos de forma atómica, así que dos jobs no cogen el mismo documento.
  - `DocumentProcessor` es la pipeline común.
  - Cada tipo solo dice cómo descargar y qué metadatos poner.
- **Estados por documento.** Cada error tiene su estado (`ERROR_DOWNLOAD`, `ERROR_UPLOAD`, `ERROR_METADATA`, …) y su `error_detail`. Un error de metadatos ya no queda tapado por un `MIGRATED`. Si el documento se subió pero falló un paso posterior, el reintento no lo vuelve a subir.
- **Abort y retry funcionan.** Un reinicio ya no deja documentos colgados en `IN_PROGRESS`.
- **Ficheros temporales y hash.** Los binarios se procesan por streaming a ficheros temporales que se borran al terminar; ya no quedan copias en `S:\`. El hash se calcula durante la descarga.
- **Seguridad.**
  - La UI tiene login y CSRF, y `/actuator/shutdown` deja de ser público.
  - Las contraseñas no se imprimen, y las que se guardan desde la UI van cifradas.
  - El SSL "trust all" solo se activa con `migration.trust-self-signed=true`.
- **Log por pasos** en la tabla `migration_event`, consultable desde la UI.
- **Importación Deichmann automatizada** con WebReports y cruce. Sustituye la descarga manual de LiveReports y el cruce en Excel.
- **Nueva UI** sin dependencias externas, con autorefresco y mensajes de error legibles.
