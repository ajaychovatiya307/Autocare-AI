package com.autocare.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loads src/main/resources/application.yml directly from disk (not the test-classpath override in
 * src/test/resources) to confirm the real, shipped autocare.* values bind correctly (SRS §4.1, NFR-008).
 */
class AutoCarePropertiesBindingTest {

    @Test
    void bindsAutocarePropertiesFromMainApplicationYml() throws IOException {
        FileSystemResource resource = new FileSystemResource("src/main/resources/application.yml");
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load("application", resource);

        MutablePropertySources propertySources = new MutablePropertySources();
        sources.forEach(propertySources::addLast);

        Binder binder = new Binder(ConfigurationPropertySources.from(propertySources));
        AutoCareProperties props = binder.bind("autocare", Bindable.of(AutoCareProperties.class)).get();

        assertThat(props.rag().similarityThreshold()).isEqualTo(0.50);
        assertThat(props.rag().allowEmptyContext()).isFalse();
        assertThat(props.embedding().dimension()).isEqualTo(1536);
        assertThat(props.chat().queryTransformDeployment()).isEqualTo("gpt-5-1");
        assertThat(props.chat().answerDeployment()).isEqualTo("gpt-5-1");
        assertThat(props.chat().memoryMaxMessages()).isEqualTo(20);
        assertThat(props.kb().defaultVersion()).isEqualTo("v1.0");
        assertThat(props.rateLimit().chatRequestsPerMinute()).isEqualTo(10);
        assertThat(props.rateLimit().kbRequestsPerMinute()).isEqualTo(10);
    }
}
