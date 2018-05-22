package com.sixt.service.framework.kafka;

import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.PartitionInfo;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.collect.ImmutableList.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Cluster.class)
public class SixtPartitionerTest {

    private Cluster cluster;
    private SixtPartitioner partitioner = new SixtPartitioner();

    @Before
    public void setup() {
        cluster = PowerMockito.mock(Cluster.class);
        List<PartitionInfo> partitions = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            partitions.add(new PartitionInfo(null, 1, null, null, null));
        }
        when(cluster.partitionsForTopic(anyString())).thenReturn(partitions);
    }

    @Test
    public void nullKeyRoundRobinNoAvailablePartitionsTest() {
        List<Integer> results = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            results.add(partitioner.partition("events", null, null,
                    null, null, cluster));
        }
        List<Integer> shouldBe = of(0, 1, 2, 3, 4, 0, 1, 2, 3, 4, 0, 1);
        assertThat(results).isEqualTo(shouldBe);
    }

    @Ignore // By incorporating available partitions instead of overall partition count,
            // we were getting non-deterministic partitions for known keys.  This is not
            // what we want for some applications, so this was changed.
    @Test
    public void nullKeyRoundRobinThreeAvailablePartitionsTest() {
        List<PartitionInfo> partitions = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            partitions.add(new PartitionInfo(null, i, null, null, null));
        }
        when(cluster.availablePartitionsForTopic(anyString())).thenReturn(partitions);

        List<Integer> results = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            results.add(partitioner.partition("events", null, null,
                    null, null, cluster));
        }
        List<Integer> shouldBe = of(0, 1, 2, 0, 1, 2, 0, 1, 2, 0, 1, 2);
        assertThat(results).isEqualTo(shouldBe);
    }

    @Test
    public void nonNullKeyTest() {
        String key = "311dd383-5430-412d-acd6-8b2c9ba3c226";
        byte[] keyBytes = key.getBytes();
        int partition = partitioner.partition("events", key, keyBytes,
                null, null, cluster);
        // hash value = -154506124
        assertThat(partition).isEqualTo(4);
    }
}
