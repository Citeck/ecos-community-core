package ru.citeck.ecos.webapp.io;

import kotlin.jvm.functions.Function1;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import ru.citeck.ecos.webapp.lib.io.EcosResource;
import ru.citeck.ecos.webapp.lib.io.EcosResourceResolver;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class EcosSpringResourceResolverAdapter implements EcosResourceResolver {

    private final ResourcePatternResolver resolver;

    @Nullable
    @Override
    public EcosResource getResource(@NotNull String pattern) {
        Resource res = resolver.getResource(pattern);
        if (!res.exists()) {
            return null;
        }
        return new SpringResourceAdapter(res);
    }

    @NotNull
    @Override
    public List<EcosResource> getResources(@NotNull String pattern) {
        Resource[] resources;
        try {
            resources = resolver.getResources(pattern);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Arrays.stream(resources)
            .map(SpringResourceAdapter::new)
            .collect(Collectors.toList());
    }

    @Slf4j
    @RequiredArgsConstructor
    private static class SpringResourceAdapter implements EcosResource {

        private final Resource resource;

        @Nullable
        @Override
        public String getFilename() {
            return resource.getFilename();
        }

        @Nullable
        @Override
        public URI getUri() {
            try {
                return resource.getURI();
            } catch (Exception e) {
                log.error("Exception while resource URI calculation", e);
                return null;
            }
        }

        @Override
        public <T> T read(@NotNull Function1<? super InputStream, ? extends T> action) {
            try (InputStream input = resource.getInputStream()) {
                return action.invoke(input);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
