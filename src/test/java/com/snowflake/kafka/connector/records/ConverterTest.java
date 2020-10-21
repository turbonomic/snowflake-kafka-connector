/*
 * Copyright (c) 2019 Snowflake Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.snowflake.kafka.connector.records;

import com.snowflake.kafka.connector.mock.MockSchemaRegistryClient;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;

import io.confluent.connect.avro.AvroConverter;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import net.snowflake.client.jdbc.internal.fasterxml.jackson.core.JsonProcessingException;
import net.snowflake.client.jdbc.internal.fasterxml.jackson.databind.JsonNode;
import net.snowflake.client.jdbc.internal.fasterxml.jackson.databind
  .ObjectMapper;
import net.snowflake.client.jdbc.internal.fasterxml.jackson.databind.node
  .ObjectNode;
import org.apache.avro.LogicalTypes;
import org.apache.kafka.connect.data.Date;
import org.apache.kafka.connect.data.Decimal;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.data.Time;
import org.apache.kafka.connect.data.Timestamp;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.json.JsonConverter;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static com.snowflake.kafka.connector.records.RecordService.MAX_SNOWFLAKE_NUMBER_PRECISION;

public class ConverterTest
{

  private static final ObjectMapper mapper = new ObjectMapper();
  private static final String TEST_FILE_NAME = "test.avro";

  private static final String TEST_TOPIC = "test";

  @Test
  public void testJsonConverter()
  {
    SnowflakeConverter converter = new SnowflakeJsonConverter();

    ObjectNode node = mapper.createObjectNode();

    node.put("str", "test");
    node.put("num", 123);

    SchemaAndValue sv =
      converter.toConnectData("test", node.toString().getBytes(StandardCharsets.UTF_8));

    assert sv.schema().name().equals(SnowflakeJsonSchema.NAME);

    assert sv.value() instanceof SnowflakeRecordContent;

    SnowflakeRecordContent content = (SnowflakeRecordContent) sv.value();

    JsonNode[] jsonNodes = content.getData();

    assert jsonNodes.length == 1;
    assert node.toString().equals(jsonNodes[0].toString());

    //null value
    sv = converter.toConnectData("test",null);
    assert ((SnowflakeRecordContent) sv.value()).getData()[0].toString().equals("{}");
  }

  @Test
  public void testAvroConverter() throws IOException
  {
    //todo: test schema registry
    URL resource = ConverterTest.class.getResource(TEST_FILE_NAME);

    byte[] testFile = Files.readAllBytes(Paths.get(resource.getFile()));

    SnowflakeConverter converter =
      new SnowflakeAvroConverterWithoutSchemaRegistry();

    SchemaAndValue sv = converter.toConnectData("test", testFile);

    assert sv.schema().name().equals(SnowflakeJsonSchema.NAME);

    assert sv.value() instanceof SnowflakeRecordContent;

    SnowflakeRecordContent content = (SnowflakeRecordContent) sv.value();

    JsonNode[] jsonNodes = content.getData();

    assert jsonNodes.length == 2;

    assert jsonNodes[0].toString().equals("{\"name\":\"foo\",\"age\":30}");
    assert jsonNodes[1].toString().equals("{\"name\":\"bar\",\"age\":29}");

    //null value
    sv = converter.toConnectData("test",null);
    assert ((SnowflakeRecordContent) sv.value()).getData()[0].toString().equals("{}");
  }

  @Test
  public void testAvroWithSchemaRegistry() throws IOException
  {
    MockSchemaRegistryClient client = new MockSchemaRegistryClient();
    SnowflakeAvroConverter converter = new SnowflakeAvroConverter();
    converter.setSchemaRegistry(client);
    SchemaAndValue input = converter.toConnectData("test", client.getData());
    SnowflakeRecordContent content = (SnowflakeRecordContent) input.value();
    assert content.getData().length == 1;
    assert content.getData()[0].asText().equals(mapper.readTree("{\"int" +
      "\":1234}").asText());

    //null value
    input = converter.toConnectData("test",null);
    assert ((SnowflakeRecordContent) input.value()).getData()[0].toString().equals("{}");

  }

  @Test
  public void testAvroWithSchemaRegistryByteInput() throws IOException
  {
    // avro
    SchemaBuilder schemaBuilder = SchemaBuilder.struct()
      .field("bytes", Schema.BYTES_SCHEMA)
      .field("bytesReadOnly", Schema.BYTES_SCHEMA)
      .field("bytesHex", Schema.BYTES_SCHEMA)
      .field("bytesDecimal", Decimal.schema(MAX_SNOWFLAKE_NUMBER_PRECISION));
    Struct original = new Struct(schemaBuilder.build())
      .put("bytes", ByteBuffer.wrap("foo".getBytes()))
      .put("bytesReadOnly", ByteBuffer.wrap("foo".getBytes()).asReadOnlyBuffer())
      .put("bytesHex", new BigInteger("AC00A0BF", 16).toByteArray())
      .put("bytesDecimal", new BigDecimal("1111111111111111111111111111111111111")
                                    .setScale(MAX_SNOWFLAKE_NUMBER_PRECISION, BigDecimal.ROUND_HALF_UP));

    SchemaRegistryClient schemaRegistry = new io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient();

    SnowflakeAvroConverter converter = new SnowflakeAvroConverter();
    converter.setSchemaRegistry(schemaRegistry);

    AvroConverter avroConverter = new AvroConverter(schemaRegistry);
    avroConverter.configure(Collections.singletonMap("schema.registry.url", "http://fake-url"), false);
    byte[] converted = avroConverter.fromConnectData(TEST_TOPIC, original.schema(), original);
    SchemaAndValue avroInputValue = converter.toConnectData(TEST_TOPIC, converted);

    SnowflakeRecordContent content = (SnowflakeRecordContent) avroInputValue.value();
    assert content.getData()[0].toString()
      .equals("{\"bytes\":\"foo\",\"bytesReadOnly\":\"foo\",\"bytesHex\":\"\\u0000¬\\u0000 ¿\",\"bytesDecimal\":1.1111111111111112E36}");
  }

  @Test
  public void testBrokenRecord()
  {
    byte[] data = "fasfas".getBytes(StandardCharsets.UTF_8);
    SnowflakeConverter converter = new SnowflakeJsonConverter();
    SchemaAndValue result = converter.toConnectData("test", data);
    assert ((SnowflakeRecordContent) result.value()).isBroken();
    assert Arrays.equals(data,
      ((SnowflakeRecordContent) result.value()).getBrokenData());

    converter = new SnowflakeAvroConverter();
    result = converter.toConnectData("test", data);
    assert ((SnowflakeRecordContent) result.value()).isBroken();
    assert Arrays.equals(data,
      ((SnowflakeRecordContent) result.value()).getBrokenData());

    converter = new SnowflakeAvroConverterWithoutSchemaRegistry();
    result = converter.toConnectData("test", data);
    assert ((SnowflakeRecordContent) result.value()).isBroken();
    assert Arrays.equals(data,
      ((SnowflakeRecordContent) result.value()).getBrokenData());
  }

  @Test
  public void testConnectJsonConverter_MapInt64() throws JsonProcessingException {
    JsonConverter jsonConverter = new JsonConverter();
    Map<String, ?> config = Collections.singletonMap("schemas.enable", false);
    jsonConverter.configure(config, false);
    Map<String, Object> jsonMap = new HashMap<>();
    // Value will map to int64.
    jsonMap.put("test", Integer.MAX_VALUE);
    SchemaAndValue schemaAndValue =
        jsonConverter.toConnectData("test", mapper.writeValueAsBytes(jsonMap));
    JsonNode result = RecordService.convertToJson(schemaAndValue.schema(), schemaAndValue.value());

    ObjectNode expected = mapper.createObjectNode();
    expected.put("test", Integer.MAX_VALUE);
    assert expected.toString().equals(result.toString());
  }

  @Test
  public void testConnectJsonConverter_MapBigDecimalExceedsMaxPrecision() throws JsonProcessingException {
    JsonConverter jsonConverter = new JsonConverter();
    Map<String, ?> config = Collections.singletonMap("schemas.enable", false);
    jsonConverter.configure(config, false);
    Map<String, Object> jsonMap = new HashMap<>();
    jsonMap.put("test", new BigDecimal("999999999999999999999999999999999999999"));
    SchemaAndValue schemaAndValue =
        jsonConverter.toConnectData("test", mapper.writeValueAsBytes(jsonMap));
    JsonNode result = RecordService.convertToJson(schemaAndValue.schema(), schemaAndValue.value());

    ObjectNode expected = mapper.createObjectNode();
    expected.put("test", new BigDecimal("999999999999999999999999999999999999999"));
    //TODO: uncomment it once KAFKA-10457 is merged
    //assert expected.toString().equals(result.toString());
  }

  @Test
  public void testAvroConverterConfig() {
    SnowflakeAvroConverter converter = new SnowflakeAvroConverter();

    Map<String, ?> config = Collections.singletonMap("schema.registry.url", "mock://my-scope-name");
    converter.readBreakOnSchemaRegistryError(config);
    assert !converter.getBreakOnSchemaRegistryError();

    config = Collections.singletonMap(SnowflakeAvroConverter.BREAK_ON_SCHEMA_REGISTRY_ERROR, "true");
    converter.readBreakOnSchemaRegistryError(config);
    assert converter.getBreakOnSchemaRegistryError();

    config = Collections.singletonMap(SnowflakeAvroConverter.BREAK_ON_SCHEMA_REGISTRY_ERROR, "trueeee");
    converter.readBreakOnSchemaRegistryError(config);
    assert !converter.getBreakOnSchemaRegistryError();

    config = Collections.singletonMap(SnowflakeAvroConverter.BREAK_ON_SCHEMA_REGISTRY_ERROR, "True");
    converter.readBreakOnSchemaRegistryError(config);
    assert converter.getBreakOnSchemaRegistryError();
  }
}
