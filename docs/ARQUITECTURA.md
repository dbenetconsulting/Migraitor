# Arquitectura

```
web (API REST + UI)          core                         types/<tipo>            target
───────────────────    ───────────────────────────    ───────────────────    ─────────────────
JobApi ───────────────▶ JobService ──▶ BatchClaimer    CsToCsType             TargetSystem
ConnectionApi           │               (UPDATE atómico) SapAlNoDepType          └ OtcsTarget
ImportApi ──▶ CsToCsImporter            DocumentProcessor ──▶ MigrationType ──▶ (Content Server)
ToolsApi                │                    │
                        └ JobScheduler       └ migration_event (log por pasos)
```

## Ciclo de un job

1. `JobService.start` crea la fila en `jobs` y lanza el job en el pool (`migration.max-parallel-jobs`).
2. `BatchClaimer` pasa en una sola sentencia hasta N documentos `PENDING` del tipo a `IN_PROGRESS` con su `job_id`:
   - SQL Server: `UPDATE TOP (n) … WITH (UPDLOCK, READPAST)`;
   - PostgreSQL: `FOR UPDATE SKIP LOCKED`.
3. Cada documento se procesa en paralelo (`migration.threads`) con este orden:

| Paso | Estado si falla |
|---|---|
| comprobar metadatos y clase documental | `ERROR_GET_MIGID` / `ERROR_SMART_TYPE` |
| descargar a fichero temporal con hash | `ERROR_DOWNLOAD` |
| subir (renombra si existe, hasta `max-name-attempts`) y **guardar `doc_id_dest` en el acto** | `ERROR_UPLOAD` |
| categorías | `ERROR_METADATA` |
| clasificación RM | `ERROR_APPLY_CLASSIF` |
| verificar hash leyendo del destino | `ERROR_HASH_MISMATCH` / `ERROR_HASH_DEST` |

   Si el documento ya tiene `doc_id_dest` (reintento), se saltan la descarga y la subida y se repiten solo los pasos posteriores. Los avisos (renombrado, TIFF reconstruido) dejan el documento en `MIGRATED_WITH_WARNINGS`.
4. Al terminar o abortar, lo que siga en `IN_PROGRESS` vuelve a `PENDING`, o pasa a `ERROR_INTERRUPTED` si ya se subió. Lo mismo ocurre al arrancar después de una caída.

## Importación CStoCS (Deichmann) con WebReports

**La REST API estándar de Content Server no ejecuta LiveReports directamente.** La vía estándar es un **WebReport** que use el LiveReport (o su SQL) como fuente de datos y devuelva filas en JSON. Se llama con:

```
GET {cs}/api/v1/nodes/{idWebReport}/output?format=webreport&inputLabel1={ultimoDataID}&inputLabel2={tamPagina}
GET {cs}/api/v1/nodes/{idWebReport}/parameters          (qué entradas espera; lo usa "Test connection")
```

También se puede usar `/api/v1/webreports/{nickname}`; para ello cambia `migration.cstocs.importer.report-path`.

Con `format=json`, CS envuelve la salida como `{"data":"<texto>"}`. Migraitor acepta ambos formatos y busca las filas en `data`, `results`, `rows` o `myRows`, o en un array suelto. Las claves de cada fila son los nombres de columna.

**Paginación por keyset.** Cada página pide `DataID > último` ordenado por DataID. Así no se repiten ni se pierden filas aunque haya inserciones en medio, cosa que con OFFSET sí podría pasar. Si una página no avanza, el run se detiene con un error claro.

### WebReport ORIGEN (documentos)

LiveReport con dos entradas: `%1` es el último DataID leído (entero) y `%2` el tamaño de página. Columnas esperadas (configurables en `migration.cstocs.importer.col-*`):
`DATAID, EMPLOYEEID, NAME, DOCTYPE, DATASIZE, MIMETYPE, CREATEDATE, FOLDERNAME, PARENTID, DATEOFTERMINATION`.

Plantilla para SQL Server. **De dónde sale el employee id depende de cómo esté montado el expediente en el cliente**: aquí se toma del nombre del workspace del empleado (abuelo del documento). Ajústalo, por ejemplo, a un atributo de categoría:

```sql
SELECT TOP (%2)
       d.DataID            AS DATAID,
       ws.Name             AS EMPLOYEEID,      -- ajustar: nombre del workspace o atributo
       d.Name              AS NAME,
       ''                  AS DOCTYPE,
       v.DataSize          AS DATASIZE,
       v.MimeType          AS MIMETYPE,
       CONVERT(varchar(19), d.CreateDate, 120) AS CREATEDATE,
       f.Name              AS FOLDERNAME,
       d.ParentID          AS PARENTID,
       ''                  AS DATEOFTERMINATION
FROM   DTree d
JOIN   DTree f      ON f.DataID = d.ParentID
JOIN   DTree ws     ON ws.DataID = f.ParentID
JOIN   DVersData v  ON v.DocID = d.DataID AND v.Version = d.VersionNum
WHERE  d.SubType = 144
  AND  d.DataID > %1
  AND  ws.SubType = 848                         -- business workspace del empleado (ajustar)
ORDER BY d.DataID
```

### WebReport DESTINO (carpetas de empleados)

Mismas entradas (`%1` es el último DataID de carpeta). Columnas: `EMPLOYEEID, EMPLOYEENAME, DATAID, NAME, PARENTNAME`. Se devuelven las carpetas que cuelgan de `999 Altakte` dentro de cada empleado:

```sql
SELECT TOP (%2)
       ws.Name   AS EMPLOYEEID,      -- ajustar igual que en origen
       ws.Name   AS EMPLOYEENAME,
       f.DataID  AS DATAID,
       f.Name    AS NAME,
       p.Name    AS PARENTNAME
FROM   DTree f
JOIN   DTree p  ON p.DataID = f.ParentID AND p.Name = '999 Altakte'
JOIN   DTree ws ON ws.DataID = p.ParentID
WHERE  f.SubType = 0
  AND  f.DataID > %1
ORDER BY f.DataID
```

### Reportview del WebReport

El reportview tiene que devolver JSON. Lo más sencillo es partir de la plantilla de ejemplo de OpenText *"WebReport CS16+ UI NodesTableReport template"* y usar la etiqueta `[LL_WEBREPORT_INSERTJSON /]`, que convierte las filas de la fuente de datos en JSON. Pon el tipo MIME de exportación a `application/json` y destino *browser*. Comprueba la salida en el navegador con la misma URL REST antes de configurarlo en Migraitor.

Sin licencia de WebReports, la alternativa es exportar el LiveReport a CSV y subirlo en la pestaña Import, con las mismas columnas.

### Cruce (Match)

- **Clave:** `employee id` + `nombre de carpeta`.
  - Employee id: sin espacios y sin ceros a la izquierda si es numérico.
  - Nombre de carpeta: sin distinguir mayúsculas, con espacios normalizados y en Unicode NFC.
- **Solo cuentan las carpetas destino cuyo padre es `migration.cstocs.importer.target-parent-folder`** (por defecto `999 Altakte`), si el export trae `PARENTNAME`.
- **Resultado por documento:** `MATCHED`, `NO_EMPLOYEE`, `NO_FOLDER`, `AMBIGUOUS_FOLDER`, `ALREADY_STAGED` (su DataID ya estaba en `cs_metadata`) o `INVALID`.
  - Los `MATCHED` crean una fila `migration` (`PENDING`, `folderid` = carpeta destino) y otra `cs_metadata` (`doc_id` = DataID origen).
- **Se puede repetir.** Solo se procesan las filas `NEW`. Para recuperar `NO_FOLDER` después de crear las carpetas, haz un export destino nuevo y otro export origen.

## Tablas

| Tabla | Uso |
|---|---|
| `jobs`, `migration`, `cs_metadata`, `sapalnodep_metadata`, `smartdoctypes` | Las de v1. Se añaden `migration.hash_source`, `migration.error_detail` y `sapalnodep_metadata.status` |
| `migration_event` | Log por documento y paso |
| `cs_connection` | Conexiones origen/destino por tipo; contraseña AES‑GCM |
| `import_run`, `src_document`, `tgt_folder` | Staging de la importación CStoCS |

## IA (pendiente, diseño)

El proveedor será configurable: `ai.provider=none | embedded | ollama | claude`, con `none` por defecto, para que el cliente decida. Con `embedded`, el modelo GGUF se carga en la propia JVM mediante llama.cpp, sin servicio extra. El fichero del modelo va junto al jar, no dentro. La propuesta de usos está en el informe de mejoras.
