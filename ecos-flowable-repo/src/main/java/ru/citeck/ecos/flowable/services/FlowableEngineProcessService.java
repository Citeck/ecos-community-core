package ru.citeck.ecos.flowable.services;

/**
 * Implement this interface to add service to flowable engine
 *
 * @author Roman Makarskiy
 */
public interface FlowableEngineProcessService {

    /**
     * @return The service key by which the service will be available from the flowable engine
     */
    String getKey();
}
