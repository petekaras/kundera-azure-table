package it.polimi.kundera.client.azuretable;

/**
 * Aggregate partition key and row key into a single object.
 *
 * @author Fabio Arcidiacono.
 */
public class AzureTableKey {

    public static final int KEY_PARTS = 2;
    public static final String SEPARATOR = "_";
    private String rowKey;
    private String partitionKey;

    public AzureTableKey(String partitionKey, String rowKey) {
        this.partitionKey = partitionKey;
        this.rowKey = rowKey;
    }

    /**
     * Create a new instance of {@link AzureTableKey} from its string representation.
     *
     * @param rawKey the string representation of the key
     *
     * @throws java.lang.NullPointerException     if {@code rawKey} is null
     * @throws java.lang.IllegalArgumentException if {@code rawKey} is malformed
     */
    public AzureTableKey(String rawKey) {
        if (rawKey == null) {
            throw new NullPointerException("key cannot be null");
        }
        String[] parts = rawKey.split(SEPARATOR);
        if (parts.length != KEY_PARTS) {
            throw new IllegalArgumentException("key [" + rawKey + "], is malformed and cannot be parsed");
        }
        this.partitionKey = parts[0];
        this.rowKey = parts[1];
    }

    public String getRowKey() {
        return rowKey;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    /**
     * Helper method to generate a String key given partition key and row key.
     *
     * @param partitionKey the partition key
     * @param rowKey       the row key
     *
     * @return a string representation of the whole key
     */
    public static String asString(String partitionKey, String rowKey) {
        return new AzureTableKey(partitionKey, rowKey).toString();
    }

    /**
     * Helper method to get the partition key from the given string representation
     * of a {@link AzureTableKey}.
     *
     * @param key the {@link AzureTableKey}
     *
     * @return the partition key
     */
    public static String getPrartitionKey(String key) {
        return new AzureTableKey(key).getPartitionKey();
    }

    /**
     * Helper method to get the row key from the given string representation
     * of a {@link AzureTableKey}.
     *
     * @param key the {@link AzureTableKey}
     *
     * @return the row key
     */
    public static String getRowKey(String key) {
        return new AzureTableKey(key).getRowKey();
    }

    @Override
    public String toString() {
        return partitionKey + SEPARATOR + rowKey;
    }
}