package ru.citeck.ecos.sysnotification.service;

/**
 * @author Pavel Tkachenko
 */
public class NoDaoException extends RuntimeException {
    public NoDaoException(String message) {
        super(message);
    }
}
