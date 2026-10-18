# Instalación de Migraitor en el servidor del cliente

Guía para Windows Server, que es donde corría v1 (`S:\MigrationApp`). En Linux los pasos son los mismos, cambiando las rutas.

## Qué cambia respecto a v1

| | v1 | Migraitor |
|---|---|---|
| Jar | `migrationTool.jar` | `migraitor.jar` |
| Configuración | Rutas fijas dentro del jar (`S:/MigrationApp/...`). Para cambiarlas había que recompilar | Nada fijo dentro del jar. Todo se pasa al arrancar o va en un `application.properties` junto al jar |
| Contraseña del keystore | Escrita en `application.properties` | Clave `keystorepassword` en el fichero de secretos |
| Fichero de secretos | Mismo formato `clave=valor` | **El mismo fichero, con 3 claves nuevas obligatorias y 1 recomendada** (ver paso 3) |
| Base de datos | Tablas creadas a mano | Hay que ejecutar **una vez** `docs/sql/sqlserver.sql`, que es idempotente (paso 2) |
| Login de la UI | No había | Usuario y contraseña del fichero de secretos |
| Apagar | `POST /actuator/shutdown` sin login | Parar el servicio o `Ctrl+C`. `shutdown` ahora exige login |
| Logs | `S:/MigrationApp/logs/migration.log` | `<LOG_PATH>/migraitor.log`, con rotación diaria y por tamaño |
| Copias de documentos | Quedaban en `S:\MigrationApp\jar` | Ficheros temporales que se borran al terminar cada documento |
| URL | `https://servidor:8443/` | `https://servidor:8443/migrationtool` (con login) |

## 1. Requisitos

- **Java 11 o superior.** Compruébalo con `java -version`. Vale el mismo Java que usaba v1.
- Acceso desde el servidor a:
  - la base de datos;
  - el Content Server de origen y el de destino (si hace falta, a través del proxy);
  - SAP, en el caso de Viega.
- El certificado PKCS12 que ya usaba v1 (`keystore-migration.p12`, alias `migrationToolCert`).

Estructura de carpetas recomendada:

```
D:\Migraitor\
  migraitor.jar
  credentials.properties      <- secretos (permisos solo para la cuenta del servicio)
  keystore-migration.p12
  application.properties      <- opcional: ajustes propios del cliente
  start.bat
  logs\
  work\                       <- temporales de los documentos en curso
```

## 2. Base de datos (una vez)

1. **Haz copia de seguridad** de la base de datos de v1.
2. En SQL Server Management Studio, conectado a la base de datos de migración, ejecuta `docs/sql/sqlserver.sql` (o `postgresql.sql` si es PostgreSQL). El script:
   - no borra ni modifica datos;
   - en una base de datos de v1 añade `migration.hash_source`, `migration.error_detail` y `sapalnodep_metadata.status`;
   - crea las tablas nuevas (`migration_event`, `cs_connection`, `import_run`, `src_document`, `tgt_folder`) y los índices;
   - en una base de datos vacía crea también las tablas de v1.

   Se puede ejecutar varias veces sin problema.
3. El usuario de base de datos de la aplicación necesita `SELECT`, `INSERT`, `UPDATE` y `DELETE` en todas esas tablas. No necesita permisos de DDL: la aplicación valida el esquema (`ddl-auto=validate`) y no lo modifica.

> Si al arrancar aparece `Schema-validation: missing column ...` o `missing table ...`, falta ejecutar el script del paso 2.

## 3. Fichero de secretos

Parte del `credentials.properties` de v1. Puedes copiarlo tal cual y añadir esto:

```properties
# NUEVAS - obligatorias
uiusername=admin
uipassword=<contraseña de la UI>
configkey=<32 bytes aleatorios en Base64>

# NUEVA - recomendada (antes estaba en application.properties)
keystorepassword=<contraseña del .p12>
```

Para generar `configkey`, usa cualquiera de estas opciones:
- PowerShell: `[Convert]::ToBase64String((1..32 | % { Get-Random -Max 256 }) -as [byte[]])`
- Con OpenSSL: `openssl rand -base64 32`

**No cambies `configkey` después de guardar conexiones desde la UI**: las contraseñas cifradas con la clave anterior dejarían de poder leerse.

Claves que se mantienen igual que en v1:

| Uso | Claves |
|---|---|
| Base de datos | `sqlserverurl`, `sqlserverusername`, `sqlserverpassword` (o `postgresql*`) |
| Proxy | `proxyIp`, `proxyPort` |
| CS destino | `cstargethostname`, `cstargetauthusername`, `cstargetauthpassword`, `csedition`, `proxy` |
| CS origen (Deichmann) | `cssourcehostname`, `cssourceauthusername`, `cssourceauthpassword`, más las nuevas opcionales `cssourceedition` (por defecto `onprem`) y `cssourceproxy` (por defecto `false`) |
| SAP (Viega) | `sapendpointws`, `sapusername`, `sappassword`, `soapaction` (opcional) |
| Categorías (Viega) | `catmigration*`, `catrecordDetails*`, `catdocumentTypeDetails*` |

`*hostname` va **sin** `https://` y sin ruta, por ejemplo `cliente.opentext.cloud`. La ruta (`/cs/cs` o `/OTCS/cs.exe`) se añade según la edición.

Las claves de Content Server son opcionales si configuras las conexiones desde la pestaña **Connections** de la UI. Si hay conexión guardada en la UI, se usa esa; si no, las claves del fichero.

La plantilla completa está en `docs/credentials.example.properties`.

## 4. Configuración propia del cliente (opcional)

Todo tiene un valor por defecto razonable. Si hace falta cambiar algo, crea `D:\Migraitor\application.properties` junto al jar. Lo que pongas ahí tiene prioridad sobre el jar. Ejemplo:

```properties
server.port=8443
migration.db-type=sqlserver
# documentos en paralelo dentro de un job / jobs a la vez
migration.threads=3
migration.max-parallel-jobs=2
migration.work-dir=D:/Migraitor/work
# arrancar el planificador ya activo
migration.scheduler.enabled=true
```

Los perfiles de cliente ya vienen dentro del jar:
- `deichmann`: CStoCS, carpeta `999 Altakte` y la llamada al WebReport.
- `viega`: SAP ArchiveLink, con conversión de TIFF a PDF.

## 5. Arrancar

`D:\Migraitor\start.bat`:

```bat
@echo off
cd /d D:\Migraitor
java -Xms512m -Xmx2g -jar migraitor.jar ^
  --spring.profiles.active=deichmann ^
  --management.credentials=D:/Migraitor/credentials.properties ^
  --server.ssl.key-store=D:/Migraitor/keystore-migration.p12 ^
  --LOG_PATH=D:/Migraitor/logs
```

Para Viega, usa `--spring.profiles.active=viega`.

Se arranca bien si en la consola o en `logs\migraitor.log` aparece:

```
Started MigraitorApplication in X seconds
```

Si falta `uipassword`, la aplicación arranca igual y escribe en el log una contraseña temporal. Ponla en el fichero de secretos y reinicia.

### Como servicio de Windows (recomendado)

Así la aplicación sigue funcionando al cerrar la sesión y arranca sola con el servidor. Puedes usar [WinSW](https://github.com/winsw/winsw) o NSSM apuntando al mismo comando de `start.bat`. Con NSSM:

```bat
nssm install Migraitor "C:\Program Files\Java\jdk-11\bin\java.exe" -Xmx2g -jar D:\Migraitor\migraitor.jar --spring.profiles.active=deichmann --management.credentials=D:/Migraitor/credentials.properties --server.ssl.key-store=D:/Migraitor/keystore-migration.p12 --LOG_PATH=D:/Migraitor/logs
nssm set Migraitor AppDirectory D:\Migraitor
nssm start Migraitor
```

## 6. Comprobar

1. `https://servidor:8443/actuator/health` debe devolver `{"status":"UP"}`. Comprueba la base de datos y no pide login.
2. Abre `https://servidor:8443/migrationtool` e inicia sesión con `uiusername` y `uipassword`.
3. **Home** muestra los documentos de v1 por estado. Si v1 dejó algún job a medias:
   - el job aparece como `INTERRUPTED`;
   - sus documentos `IN_PROGRESS` vuelven a `PENDING`, o pasan a `ERROR_INTERRUPTED` si ya estaban subidos.
4. **Connections** (Deichmann): rellena el origen y el destino y pulsa **Test connection**. Si pones el id del WebReport, también lista sus parámetros.
5. Prueba con un job pequeño, por ejemplo de 5 documentos y nivel de log 2. En **Jobs**, revisa los pasos de cada documento.

## 7. Actualizar a una versión nueva

1. Para el servicio y sustituye `migraitor.jar`.
2. Si la versión nueva trae cambios en `docs/sql/`, vuelve a ejecutar el script; es idempotente.
3. Arranca el servicio. Los jobs que estaban en marcha se recuperan solos, como se explica en el paso 6.

## Problemas típicos

| Síntoma | Causa |
|---|---|
| `Secrets file not readable` | Ruta mal en `--management.credentials`, o la cuenta del servicio no tiene permiso de lectura |
| `Secret not found for key: X` | Falta la clave `X` en el fichero de secretos |
| `Schema-validation: missing column/table` | Falta ejecutar `docs/sql/sqlserver.sql` |
| `Add 'configkey' ... before saving passwords` | Falta `configkey` |
| `Cannot decrypt the stored password` | Se ha cambiado `configkey`: vuelve a guardar las contraseñas en **Connections** |
| `Authentication failed ... HTTP 401` | Usuario o contraseña del CS. Pruébalo con **Test connection** |
| `Cannot reach ...` | Red o proxy: revisa `proxyIp`/`proxyPort` y si la conexión tiene marcado *Use the proxy* |
| Error de certificado al llamar al CS | El CS usa un certificado que Java no reconoce. Impórtalo en el truststore de Java. `migration.trust-self-signed=true` solo es aceptable en pruebas |
| `WebReport ... did not return JSON` | El reportview del WebReport no devuelve JSON (ver `docs/ARQUITECTURA.md`) |
