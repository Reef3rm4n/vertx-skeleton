package io.vertx.skeleton.config;

import io.smallrye.mutiny.tuples.Tuple2;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.sqlclient.templates.RowMapper;
import io.vertx.mutiny.sqlclient.templates.TupleMapper;
import io.vertx.skeleton.orm.RepositoryMapper;

import java.util.*;


public class BlobConfigurationRecordMapper implements RepositoryMapper<ConfigurationKey, BlobConfigurationRecord, ConfigurationQuery> {

  public static final BlobConfigurationRecordMapper INSTANCE = new BlobConfigurationRecordMapper();
  private BlobConfigurationRecordMapper(){}
  public final RowMapper<BlobConfigurationRecord> ROW_MAPPER = RowMapper.newInstance(
    row -> new BlobConfigurationRecord(
      row.getString("name"),
      row.getString("class"),
      Buffer.newInstance(row.getBuffer("data")),
      from(row)
    )
  );

  @Override
  public Class<BlobConfigurationRecord> valueClass() {
    return BlobConfigurationRecord.class;
  }

  public final TupleMapper<BlobConfigurationRecord> TUPLE_MAPPER = TupleMapper.mapper(
    config -> {
      Map<String, Object> parameters = config.persistedRecord().params();
      parameters.put("name", config.name());
      parameters.put("class", config.tClass());
      parameters.put("data", config.data().getDelegate());
      return parameters;
    }
  );

  public static final TupleMapper<ConfigurationKey> KEY_MAPPER = TupleMapper.mapper(
    savedConfigurationKey -> {
      Map<String, Object> parameters = new HashMap<>();
      parameters.put("name", savedConfigurationKey.name());
      parameters.put("class", savedConfigurationKey.tClass());
      parameters.put("tenant", savedConfigurationKey.tenant().generateString());
      return parameters;
    }
  );

  @Override
  public List<Tuple2<String, List<?>>> queryFieldsColumn(final ConfigurationQuery queryFilter) {
    final var tupleList = new ArrayList<Tuple2<String, List<?>>>();
    tupleList.add(Tuple2.of("name", queryFilter.name()));
    tupleList.add(Tuple2.of("class", queryFilter.tClasses()));
    return tupleList;
  }

  @Override
  public String table() {
    return "configuration";
  }

  @Override
  public Set<String> insertColumns() {
    return Set.of("name", "class", "data");
  }

  @Override
  public Set<String> updateColumns() {
    return Set.of("data");
  }

  @Override
  public Set<String> keyColumns() {
    return Set.of("name", "class", "tenant");
  }

  @Override
  public RowMapper<BlobConfigurationRecord> rowMapper() {
    return ROW_MAPPER;
  }

  @Override
  public TupleMapper<BlobConfigurationRecord> tupleMapper() {
    return TUPLE_MAPPER;
  }

  @Override
  public TupleMapper<ConfigurationKey> keyMapper() {
    return KEY_MAPPER;
  }

  @Override
  public Class<ConfigurationKey> keyClass() {
    return ConfigurationKey.class;
  }

}
