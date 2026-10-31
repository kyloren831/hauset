package com.appdomotica.multimedia.service;

import org.springframework.core.io.Resource;

import java.io.InputStream;

public interface StorageService {

    String store(String subpath, String filename, byte[] content);

    InputStream load(String storageKey);

    Resource loadAsResource(String storageKey);

    void delete(String storageKey);

    boolean exists(String storageKey);

    long size(String storageKey);
}
