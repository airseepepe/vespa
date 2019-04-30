/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.metric.model.json;

import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;

import static ai.vespa.metricsproxy.metric.model.ConsumerId.toConsumerId;
import static ai.vespa.metricsproxy.metric.model.MetricId.toMetricId;
import static ai.vespa.metricsproxy.metric.model.ServiceId.toServiceId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests for YamasJsonModel and YamasArrayJsonModel
 *
 * @author smorgrav
 * @author gjoranv
 */
public class YamasJsonModelTest {

    private static final String EXPECTED_JSON = "{\"metrics\":[{\"status_code\":0,\"timestamp\":1400047900,\"application\":\"vespa.searchnode\",\"metrics\":{\"cpu\":55.5555555555555,\"memory_virt\":22222222222,\"memory_rss\":5555555555},\"dimensions\":{\"applicationName\":\"app\",\"tenantName\":\"tenant\",\"metrictype\":\"system\",\"instance\":\"searchnode\",\"applicationInstance\":\"default\",\"clustername\":\"cluster\"},\"routing\":{\"yamas\":{\"namespaces\":[\"Vespa\"]}},\"status_msg\":\"Data collected successfully\"}]}";

    @Test
    public void array_definition_creates_correct_json() throws IOException {
        YamasJsonModel jsonModel = getYamasJsonModel("yamas-array.json");

        YamasArrayJsonModel yamasData = new YamasArrayJsonModel();
        yamasData.add(jsonModel);

        assertEquals(EXPECTED_JSON, yamasData.serialize());
    }

    @Test
    public void deserialize_serialize_roundtrip() throws IOException {
        YamasJsonModel jsonModel = getYamasJsonModel("yamas-array.json");

        // Do some sanity checking
        assertEquals("vespa.searchnode", jsonModel.application);
        assertEquals("Vespa", jsonModel.routing.get("yamas").namespaces.get(0));
        assertEquals(5.555555555E9, jsonModel.metrics.get("memory_rss"), 0.1d); //Not using custom double renderer

        // Serialize and verify
        YamasArrayJsonModel yamasArray = new YamasArrayJsonModel();
        yamasArray.add(jsonModel);
        String string = yamasArray.serialize();
        assertEquals(EXPECTED_JSON, string);
    }

    @Test
    public void deserialize_serialize_roundtrip_with_metrics_packet() throws IOException {
        YamasJsonModel jsonModel = getYamasJsonModel("yamas-array.json");
        MetricsPacket metricsPacket = JsonUtil.toMetricsPacketBuilder(jsonModel).build();

        // Do some sanity checking
        assertEquals(toServiceId("vespa.searchnode"), metricsPacket.service);
        assertEquals(toConsumerId("Vespa"), metricsPacket.consumers().get(0));
        assertEquals(5.555555555E9, metricsPacket.metrics().get(toMetricId("memory_rss")).doubleValue(), 0.1d); //Not using custom double rendrer

        // Serialize and verify
        YamasArrayJsonModel yamasArray = JsonUtil.toYamasArray(Collections.singleton(metricsPacket), true);
        String string = yamasArray.serialize();
        assertEquals(EXPECTED_JSON, string);
    }

    @Test
    public void missing_routing_object_makes_it_null() throws IOException {
        // Read file that was taken from production (real -life example that is)
        String filename = getClass().getClassLoader().getResource("yamas-array-no-routing.json").getFile();
        BufferedReader reader = Files.newBufferedReader(Paths.get(filename));
        ObjectMapper mapper = new ObjectMapper();
        YamasJsonModel jsonModel = mapper.readValue(reader, YamasJsonModel.class);

        // Do some sanity checking
        assertNull(jsonModel.routing);
    }

    private YamasJsonModel getYamasJsonModel(String testFile) throws IOException {
        String filename = getClass().getClassLoader().getResource(testFile).getFile();
        BufferedReader reader = Files.newBufferedReader(Paths.get(filename));
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(reader, YamasJsonModel.class);
    }

}
