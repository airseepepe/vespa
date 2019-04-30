/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.metric;

import ai.vespa.metricsproxy.core.ConsumersConfig;
import ai.vespa.metricsproxy.core.MetricsConsumers;
import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

import java.util.List;

import static ai.vespa.metricsproxy.metric.ExternalMetrics.VESPA_NODE_SERVICE_ID;
import static ai.vespa.metricsproxy.metric.model.ConsumerId.toConsumerId;
import static ai.vespa.metricsproxy.metric.model.ServiceId.toServiceId;
import static org.junit.Assert.assertEquals;

/**
 * @author gjoranv
 */
public class ExternalMetricsTest {
    private static final ConsumerId CUSTOM_CONSUMER_1 = toConsumerId("consumer-1");
    private static final ConsumerId CUSTOM_CONSUMER_2 = toConsumerId("consumer-2");

    @Test
    public void extra_metrics_are_added() {
        MetricsConsumers noConsumers = new MetricsConsumers(new ConsumersConfig.Builder().build());
        ExternalMetrics externalMetrics = new ExternalMetrics(noConsumers);

        externalMetrics.setExtraMetrics(ImmutableList.of(
                new MetricsPacket.Builder(toServiceId("foo"))));

        List<MetricsPacket.Builder> packets = externalMetrics.getMetrics();
        assertEquals(1, packets.size());
    }

    @Test
    public void service_id_is_set_to_vespa_node_id() {
        MetricsConsumers noConsumers = new MetricsConsumers(new ConsumersConfig.Builder().build());
        ExternalMetrics externalMetrics = new ExternalMetrics(noConsumers);
        externalMetrics.setExtraMetrics(ImmutableList.of(
                new MetricsPacket.Builder(toServiceId("replace_with_vespa_node_id"))));

        List<MetricsPacket.Builder> packets = externalMetrics.getMetrics();
        assertEquals(1, packets.size());
        assertEquals(VESPA_NODE_SERVICE_ID, packets.get(0).build().service);
    }

    @Test
    public void custom_consumers_are_added() {
        ConsumersConfig consumersConfig = new ConsumersConfig.Builder()
                .consumer(new ConsumersConfig.Consumer.Builder().name(CUSTOM_CONSUMER_1.id))
                .consumer(new ConsumersConfig.Consumer.Builder().name(CUSTOM_CONSUMER_2.id))
                .build();
        MetricsConsumers consumers = new MetricsConsumers(consumersConfig);
        ExternalMetrics externalMetrics = new ExternalMetrics(consumers);

        externalMetrics.setExtraMetrics(ImmutableList.of(
                new MetricsPacket.Builder(toServiceId("foo"))));

        List<MetricsPacket.Builder> packets = externalMetrics.getMetrics();
        assertEquals(1, packets.size());

        List<ConsumerId> consumerIds = packets.get(0).build().consumers();
        assertEquals(2, consumerIds.size());
        assertEquals(CUSTOM_CONSUMER_1, consumerIds.get(0));
        assertEquals(CUSTOM_CONSUMER_2, consumerIds.get(1));
    }

}
