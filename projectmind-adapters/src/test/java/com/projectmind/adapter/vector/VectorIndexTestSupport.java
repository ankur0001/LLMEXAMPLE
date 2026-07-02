package com.projectmind.adapter.vector;

import com.projectmind.core.port.ConfigurationPort;
import com.projectmind.core.port.MemoryManagerPort;
import com.projectmind.core.port.OllamaClientPort;

import java.util.Optional;

final class VectorIndexTestSupport {

    private VectorIndexTestSupport() {
    }

    static InMemoryVectorIndex createIndex(OllamaClientPort ollama) {
        MemoryManagerPort memory = org.mockito.Mockito.mock(MemoryManagerPort.class);
        org.mockito.Mockito.when(memory.getCacheEntry(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());
        ConfigurationPort configuration = org.mockito.Mockito.mock(ConfigurationPort.class);
        org.mockito.Mockito.when(configuration.isCacheEnabled()).thenReturn(false);
        return new InMemoryVectorIndex(ollama, memory, configuration);
    }
}
