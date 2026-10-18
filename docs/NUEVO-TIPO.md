# Cómo añadir un tipo de migración nuevo

Un tipo es un origen. La pipeline común, en `DocumentProcessor`, se encarga de:
- reservar los documentos;
- calcular el hash;
- subir y renombrar si el nombre ya existe;
- aplicar categorías y la clasificación RM;
- verificar el hash en el destino;
- fijar los estados y escribir el log.

El tipo solo aporta lo que es propio de su origen.

## 1. Tabla de metadatos

Crea una tabla `<tipo>_metadata` con `mig_id` como clave, que es el mismo `mig_id` de `migration`. Un documento `PENDING` pertenece a tu tipo cuando tiene fila en esa tabla. Añade el `CREATE` a los dos scripts de `docs/sql/` en forma idempotente.

## 2. Entidad y repositorio

`entities/XxxMetadataEntity.java` (`@Table(name = "xxx_metadata")`) y un repositorio con `findByMigIdIn(Collection<Long>)`.

## 3. La clase del tipo

Crea un paquete `types/xxx/` y dentro un `@Component` que implemente `MigrationType<XxxMetadataEntity>`:

```java
@Component
public class XxxType implements MigrationType<XxxMetadataEntity> {
    public static final String CODE = "XXX";              // se guarda en jobs.migtype: no lo cambies nunca
    public String code()          { return CODE; }
    public String displayName()   { return "Sistema X → Content Server"; }
    public String metadataTable() { return "xxx_metadata"; }

    public Map<Long, XxxMetadataEntity> loadMetadata(Collection<Long> ids) { ... }

    // Descarga el binario a files.newFile(...) y devuelve tamaño, mime y sha256.
    public SourceFile download(MigrationEntity doc, XxxMetadataEntity meta, WorkFiles files) { ... }

    public String targetName(MigrationEntity doc, XxxMetadataEntity meta) { ... }
    public String alternativeName(String base, int attempt) { return base + "_" + attempt; }
    public List<CategoryValues> categories(MigrationEntity doc, XxxMetadataEntity meta, SmartTypesEntity st) { ... }
}
```

Métodos opcionales:
- `requiresSmartType()`: `false` si el tipo puede ir sin clase documental.
- `supportsImport()`: `true` si tiene paso de importación en la UI.
- `target()`: el destino; por defecto `"otcs"`.

Spring lo registra solo. Aparece en los desplegables de la UI y se puede lanzar sin tocar nada más.

## 4. Configuración

Los valores propios del tipo van en una clase `@ConfigurationProperties("migration.xxx")`. Los secretos (usuarios, contraseñas, endpoints) se leen con `Secrets` desde el fichero de secretos. Si el tipo es de un cliente concreto, crea `application-<cliente>.properties`.

## 5. Tests

Copia `CsToCsEndToEndTest` y cambia los stubs de WireMock por los de tu origen. El test arranca la aplicación con H2, lanza un job real y comprueba estados, hashes y categorías.

## Un destino nuevo (no Content Server)

1. Implementa `target/TargetSystem`: `upload`, `applyCategory`, `applyClassification` y `contentSha256`.
2. Crea un `@Component` `TargetConnector` con un `kind()` propio, por ejemplo `"sharepoint"`.
3. En el tipo que escriba allí, sobrescribe `target()` para que devuelva ese `kind`.

El núcleo no cambia.
