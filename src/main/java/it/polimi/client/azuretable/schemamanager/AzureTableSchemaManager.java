package it.polimi.client.azuretable.schemamanager;

import com.impetus.kundera.KunderaException;
import com.impetus.kundera.PersistenceProperties;
import com.impetus.kundera.configure.schema.TableInfo;
import com.impetus.kundera.configure.schema.api.AbstractSchemaManager;
import com.impetus.kundera.configure.schema.api.SchemaManager;
import com.impetus.kundera.loader.ClientLoaderException;
import com.impetus.kundera.persistence.EntityManagerFactoryImpl;
import com.microsoft.windowsazure.services.core.storage.CloudStorageAccount;
import com.microsoft.windowsazure.services.core.storage.StorageException;
import com.microsoft.windowsazure.services.table.client.CloudTable;
import com.microsoft.windowsazure.services.table.client.CloudTableClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.List;
import java.util.Map;

/**
 * Provide support for automatic schema generation through "kundera_ddl_auto_prepare" property in persistence.xml.
 *
 * @author Fabio Arcidiacono.
 * @see com.impetus.kundera.configure.schema.api.AbstractSchemaManager
 * @see com.impetus.kundera.configure.schema.api.SchemaManager
 */
public class AzureTableSchemaManager extends AbstractSchemaManager implements SchemaManager {

    private static final Logger logger;
    private CloudTableClient tableClient;

    static {
        logger = LoggerFactory.getLogger(AzureTableSchemaManager.class);
    }

    public AzureTableSchemaManager(String clientFactory, Map<String, Object> externalProperties, EntityManagerFactoryImpl.KunderaMetadata kunderaMetadata) {
        super(clientFactory, externalProperties, kunderaMetadata);
    }

    /*
     * Need re-implementation because AbstractSchemaManager.exportSchema()
     * do unsafe split on hostName so if it is null a NullPointerException is thrown and
     * no DDL can be done since is not necessary to specify hostname for azure tables
     */
    @Override
    public void exportSchema(final String persistenceUnit, List<TableInfo> schemas) {
        this.puMetadata = kunderaMetadata.getApplicationMetadata().getPersistenceUnitMetadata(persistenceUnit);
        if (externalProperties != null) {
            this.userName = (String) externalProperties.get(PersistenceProperties.KUNDERA_USERNAME);
            this.password = (String) externalProperties.get(PersistenceProperties.KUNDERA_PASSWORD);
            this.operation = (String) externalProperties.get(PersistenceProperties.KUNDERA_DDL_AUTO_PREPARE);
        }
        if (this.userName == null) {
            this.userName = this.puMetadata.getProperty(PersistenceProperties.KUNDERA_USERNAME);
        }
        if (this.password == null) {
            this.password = this.puMetadata.getProperty(PersistenceProperties.KUNDERA_PASSWORD);
        }
        if (this.operation == null) {
            this.operation = this.puMetadata.getProperty(PersistenceProperties.KUNDERA_DDL_AUTO_PREPARE);
        }
        if (this.operation != null && initiateClient()) {
            this.tableInfos = schemas;
            handleOperations(schemas);
        }
    }

    /*
    * same as super.handleOperations() but cannot use since is private
    */
    private void handleOperations(List<TableInfo> tablesInfo) {
        SchemaOperationType operationType = SchemaOperationType.getInstance(operation);

        switch (operationType) {
            case createdrop:
                create_drop(tablesInfo);
                break;
            case create:
                create(tablesInfo);
                break;
            case update:
                update(tablesInfo);
                break;
            case validate:
                validate(tablesInfo);
                break;
        }
    }

    @Override
    protected boolean initiateClient() {
        String storageConnectionString = "DefaultEndpointsProtocol=http;AccountName=" + this.userName + ";AccountKey=" + this.password;
        try {
            CloudStorageAccount storageAccount = CloudStorageAccount.parse(storageConnectionString);
            logger.info("Connected to Tables with connection string: " + storageConnectionString);
            tableClient = storageAccount.createCloudTableClient();
        } catch (URISyntaxException | InvalidKeyException e) {
            throw new ClientLoaderException("Unable to connect to Tables with connection string: " + storageConnectionString, e);
        }
        return true;
    }

    /*
     * validates schema tables based on entity definition. Throws SchemaGenerationException if validation fails.
     */
    @Override
    protected void validate(List<TableInfo> tablesInfo) {
        throw new UnsupportedOperationException("DDL validate is unsupported for Table");
    }

    /*
     * updates schema tables based on entity definition.
     */
    @Override
    protected void update(List<TableInfo> tableInfo) {
        throw new UnsupportedOperationException("DDL validate is unsupported for Table");
    }

    /*
     * drops (if exists) schema and then creates schema tables based on entity definitions.
     */
    @Override
    protected void create(List<TableInfo> tableInfo) {
        dropSchema();
        for (TableInfo info : tableInfo) {
            try {
                CloudTable table = tableClient.getTableReference(info.getTableName());
                table.createIfNotExist();
            } catch (URISyntaxException | StorageException e) {
                throw new KunderaException("Some error occurred while creating table " + info.getTableName(), e);
            }
        }
    }

    /*
     * drops (if exists) schema, creates schema tables based on entity definitions.
     */
    @Override
    protected void create_drop(List<TableInfo> tableInfo) {
        create(tableInfo);
    }

    /*
     * Method required to drop auto create schema, in case
     * of schema operation as {create-drop}.
     */
    @Override
    public void dropSchema() {
        for (String table : tableClient.listTables()) {
            try {
                tableClient.getTableReference(table).deleteIfExists();
            } catch (URISyntaxException | StorageException e) {
                throw new KunderaException("Some error occurred while dropping schema", e);
            }
        }
    }

    @Override
    public boolean validateEntity(Class clazz) {
        return true;
    }
}
