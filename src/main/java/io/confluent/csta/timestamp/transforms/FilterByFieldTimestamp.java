package io.confluent.csta.timestamp.transforms;

import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.transforms.Transformation;
import org.apache.kafka.connect.transforms.util.SimpleConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.Locale;
import java.text.SimpleDateFormat;

public class FilterByFieldTimestamp<R extends ConnectRecord<R>> implements Transformation<R> {

    public static final String FIELD_NAME_CONFIG = "timestamp.field";
    public static final String START_DATETIME_CONFIG = "start.datetime";
    public static final String END_DATETIME_CONFIG = "end.datetime";

    private static final ConfigDef CONFIG_DEF = new ConfigDef()
            .define(FIELD_NAME_CONFIG, ConfigDef.Type.STRING, ConfigDef.NO_DEFAULT_VALUE, ConfigDef.Importance.HIGH,
                    "The name of the field containing the timestamp in epoch milliseconds.")
            .define(START_DATETIME_CONFIG, ConfigDef.Type.STRING, ConfigDef.NO_DEFAULT_VALUE, ConfigDef.Importance.HIGH,
                    "Start of the valid timestamp window (inclusive), in yyyyMMddHHmmss format.")
            .define(END_DATETIME_CONFIG, ConfigDef.Type.STRING, ConfigDef.NO_DEFAULT_VALUE, ConfigDef.Importance.HIGH,
                    "End of the valid timestamp window (inclusive), in yyyyMMddHHmmss format.");

    private String fieldName;
    private long startEpochMillis;
    private long endEpochMillis;

    private static final Logger logger = LoggerFactory.getLogger(FilterByFieldTimestamp.class);

    @Override
    public void configure(Map<String, ?> configs) {
        SimpleConfig config = new SimpleConfig(CONFIG_DEF, configs);
        this.fieldName = config.getString(FIELD_NAME_CONFIG);
        this.startEpochMillis = parseDateTimeToEpochMillis(config.getString(START_DATETIME_CONFIG));
        this.endEpochMillis = parseDateTimeToEpochMillis(config.getString(END_DATETIME_CONFIG));
    }

    private long parseDateTimeToEpochMillis(String yyyymmddhhmmss) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = sdf.parse(yyyymmddhhmmss);
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.setTime(date);
            return calendar.getTimeInMillis();
        } catch (Exception e) {
            throw new DataException("Failed to parse date/time '" + yyyymmddhhmmss + "': " + e.getMessage(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public R apply(R record) {
        Object value = record.value();
        if (!(value instanceof Struct)) {
            throw new DataException("Expected Struct value but found: " + (value == null ? "null" : value.getClass()));
        }

        Struct struct = (Struct) value;
        Schema schema = struct.schema();

        if (schema.field(fieldName) == null) {
            throw new DataException("Field '" + fieldName + "' not found in record schema.");
        }

        if (!schema.field(fieldName).schema().type().equals(Schema.Type.INT64)) {
            throw new DataException("Field '" + fieldName + "' must be of type INT64 (epoch millis).");
        }

        Long timestamp = ((Date)struct.get(fieldName)).getTime();
        if (timestamp == null) {
            return null; // drop if timestamp field is missing
        }

        if (timestamp >= startEpochMillis && timestamp <= endEpochMillis) {
            return record;
        } else {
            return null; // filtered out
        }
    }

    @Override
    public ConfigDef config() {
        return CONFIG_DEF;
    }

    @Override
    public void close() {
        // nothing to close
    }

    @Override
    public String toString() {
        return "FilterByFieldTimestamp";
    }
}


