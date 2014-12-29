package it.polimi.client.azuretable;

import com.impetus.kundera.PersistenceProperties;
import com.impetus.kundera.client.Client;
import com.impetus.kundera.configure.schema.api.SchemaManager;
import com.impetus.kundera.loader.ClientLoaderException;
import com.impetus.kundera.loader.GenericClientFactory;
import com.impetus.kundera.metadata.model.PersistenceUnitMetadata;
import com.impetus.kundera.persistence.EntityReader;
import com.microsoft.windowsazure.services.core.storage.CloudStorageAccount;
import com.microsoft.windowsazure.services.table.client.CloudTableClient;
import it.polimi.client.azuretable.schemamanager.AzureTableSchemaManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.Map;
import java.util.Properties;

/**
 * Used by Kundera to instantiate the Client.
 *
 * @author Fabio Arcidiacono.
 * @see com.impetus.kundera.loader.GenericClientFactory
 */
public class AzureTableClientFactory extends GenericClientFactory {

    private static Logger logger;
    private EntityReader reader;
    private SchemaManager schemaManager;
    private CloudStorageAccount storageAccount;
    private CloudTableClient tableClient;

    static {
        logger = LoggerFactory.getLogger(AzureTableClientFactory.class);
    }

    @Override
    public void initialize(Map<String, Object> puProperties) {
        storageAccount = null;
        tableClient = null;
        reader = new AzureTableEntityReader(kunderaMetadata);
        setExternalProperties(puProperties);
    }

    @Override
    protected Object createPoolOrConnection() {
        String pu = getPersistenceUnit();
        PersistenceUnitMetadata puMetadata = kunderaMetadata.getApplicationMetadata().getPersistenceUnitMetadata(pu);
        Properties properties = puMetadata.getProperties();
        String accountName = null;
        String accountKey = null;
        if (externalProperties != null) {
            accountName = (String) externalProperties.get(PersistenceProperties.KUNDERA_USERNAME);
            accountKey = (String) externalProperties.get(PersistenceProperties.KUNDERA_PASSWORD);
        }
        if (accountName == null) {
            accountName = (String) properties.get(PersistenceProperties.KUNDERA_USERNAME);
        }
        if (accountKey == null) {
            accountKey = (String) properties.get(PersistenceProperties.KUNDERA_PASSWORD);
        }

        if (accountName == null) {
            throw new ClientLoaderException("Configuration error, missing storage account name as kundera.username in persistence.xml");
        }
        if (accountKey == null) {
            throw new ClientLoaderException("Configuration error, missing storage account key as kundera.password in persistence.xml");
        }
        String storageConnectionString = "DefaultEndpointsProtocol=http;AccountName=" + accountName + ";AccountKey=" + accountKey;
        try {
            storageAccount = CloudStorageAccount.parse(storageConnectionString);
            logger.info("Connected to Tables with connection string: " + storageConnectionString);
            tableClient = storageAccount.createCloudTableClient();
        } catch (URISyntaxException | InvalidKeyException e) {
            throw new ClientLoaderException("Unable to connect to Tables with connection string: " + storageConnectionString, e);
        }

        return tableClient;
    }

    @Override
    protected Client instantiateClient(String persistenceUnit) {
        return new AzureTableClient(kunderaMetadata, externalProperties, persistenceUnit, clientMetadata, indexManager, reader, tableClient);
    }

    @Override
    public boolean isThreadSafe() {
        return false;
    }

    @Override
    public void destroy() {
        if (indexManager != null) {
            indexManager.close();
        }
        storageAccount = null;
        tableClient = null;
        schemaManager = null;
        externalProperties = null;
    }

    @Override
    public SchemaManager getSchemaManager(Map<String, Object> puProperties) {
        if (schemaManager == null) {
            setExternalProperties(puProperties);
            schemaManager = new AzureTableSchemaManager(this.getClass().getName(), puProperties, kunderaMetadata);
        }
        return schemaManager;
    }

    @Override
    protected void initializeLoadBalancer(String loadBalancingPolicyName) {
        throw new UnsupportedOperationException("Load balancing feature is not supported in " + this.getClass().getSimpleName());
    }
}
