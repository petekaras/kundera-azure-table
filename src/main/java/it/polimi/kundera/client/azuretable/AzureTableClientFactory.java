package it.polimi.kundera.client.azuretable;

import com.impetus.kundera.PersistenceProperties;
import com.impetus.kundera.client.Client;
import com.impetus.kundera.configure.ClientProperties;
import com.impetus.kundera.configure.schema.api.SchemaManager;
import com.impetus.kundera.loader.ClientLoaderException;
import com.impetus.kundera.loader.GenericClientFactory;
import com.impetus.kundera.metadata.model.PersistenceUnitMetadata;
import com.impetus.kundera.persistence.EntityReader;
import com.microsoft.windowsazure.services.core.storage.CloudStorageAccount;
import com.microsoft.windowsazure.services.table.client.CloudTableClient;
import it.polimi.kundera.client.azuretable.config.AzureTableConstants;
import it.polimi.kundera.client.azuretable.config.AzureTablePropertyReader;
import it.polimi.kundera.client.azuretable.config.AzureTablePropertyReader.AzureTableSchemaMetadata;
import it.polimi.kundera.client.azuretable.schemamanager.AzureTableSchemaManager;
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

    private static Logger logger = LoggerFactory.getLogger(AzureTableClientFactory.class);
    private EntityReader reader;
    private SchemaManager schemaManager;
    private CloudStorageAccount storageAccount;
    private CloudTableClient tableClient;

    @Override
    public void initialize(Map<String, Object> puProperties) {
        storageAccount = null;
        tableClient = null;
        reader = new AzureTableEntityReader(kunderaMetadata);
        initializePropertyReader();
        setExternalProperties(puProperties);
    }

    @Override
    protected Object createPoolOrConnection() {
        String storageConnectionString;
        Properties tableProperties = getClientSpecificProperties();
        if (tableProperties != null) {
            if (userStorageEmulator(tableProperties)) {
                storageConnectionString = buildEmulatorConnectionString(parseEmulatorProxy(tableProperties));
            } else {
                storageConnectionString = buildConnectionString(useHttp(tableProperties));
            }
            AzureTableConstants.setPartitionKey(parsePartitionKey(tableProperties));
        } else {
            storageConnectionString = buildConnectionString(false);
        }

        try {
            storageAccount = CloudStorageAccount.parse(storageConnectionString);
            logger.info("Connected to Azure Tables with connection string: " + storageConnectionString);
            tableClient = storageAccount.createCloudTableClient();
            return tableClient;
        } catch (URISyntaxException | InvalidKeyException e) {
            throw new ClientLoaderException("Unable to connect to Azure Tables with connection string: " + storageConnectionString, e);
        }
    }

    private String buildEmulatorConnectionString(String emulatorProxy) {
        return "UseDevelopmentStorage=true;DevelopmentStorageProxyUri=" + emulatorProxy;
    }

    private String buildConnectionString(boolean useHttp) {
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

        if (accountName == null || accountKey == null) {
            throw new ClientLoaderException("Configuration error, check kundera.username kundera.password and in persistence.xml");
        }

        String protocol = useHttp ? AzureTableConstants.HTTP : AzureTableConstants.HTTPS;
        return "DefaultEndpointsProtocol=" + protocol + ";AccountName=" + accountName + ";AccountKey=" + accountKey;
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
            initializePropertyReader();
            setExternalProperties(puProperties);
            schemaManager = new AzureTableSchemaManager(this.getClass().getName(), puProperties, kunderaMetadata);
        }
        return schemaManager;
    }

    @Override
    protected void initializeLoadBalancer(String loadBalancingPolicyName) {
        throw new UnsupportedOperationException("Load balancing feature is not supported in " + this.getClass().getSimpleName());
    }

    private void initializePropertyReader() {
        if (propertyReader == null) {
            propertyReader = new AzureTablePropertyReader(externalProperties,
                    kunderaMetadata.getApplicationMetadata().getPersistenceUnitMetadata(getPersistenceUnit()));
            propertyReader.read(getPersistenceUnit());
        }
    }

    private boolean userStorageEmulator(Properties properties) {
        String emulator = (String) properties.get(AzureTableConstants.STORAGE_EMULATOR);
        if (emulator != null && !emulator.isEmpty()) {
            try {
                return Boolean.parseBoolean(emulator);
            } catch (NumberFormatException e) {
                throw new ClientLoaderException("Invalid storage emulator value " + emulator + ": ", e);
            }
        }
        return false;
    }

    private String parseEmulatorProxy(Properties properties) {
        String devProxy = (String) properties.get(AzureTableConstants.EMULATOR_PROXY);
        if (devProxy != null && !devProxy.isEmpty()) {
            return devProxy;
        }
        return AzureTableConstants.LOCALHOST;
    }

    private boolean useHttp(Properties properties) {
        String protocol = (String) properties.get(AzureTableConstants.PROTOCOL);
        if (protocol != null && !protocol.isEmpty()) {
            if (protocol.equalsIgnoreCase(AzureTableConstants.HTTP)) {
                return true;
            } else if (protocol.equalsIgnoreCase(AzureTableConstants.HTTPS)) {
                return false;
            }
            throw new ClientLoaderException("Invalid protocol " + protocol);
        }
        return false;
    }

    private String parsePartitionKey(Properties properties) {
        String partition = (String) properties.get(AzureTableConstants.PARTITION_KEY);
        if (partition != null && !partition.isEmpty()) {
            return partition;
        }
        return AzureTableConstants.DEFAULT_PARTITION;
    }

    private Properties getClientSpecificProperties() {
        AzureTableSchemaMetadata metadata = AzureTablePropertyReader.asm;
        ClientProperties clientProperties = metadata != null ? metadata.getClientProperties() : null;
        if (clientProperties != null) {
            ClientProperties.DataStore dataStore = metadata.getDataStore();
            if (dataStore != null && dataStore.getConnection() != null) {
                return dataStore.getConnection().getProperties();
            }
        }
        return null;
    }
}
