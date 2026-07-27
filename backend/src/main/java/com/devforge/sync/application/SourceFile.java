package com.devforge.sync.application;

/**
 * One file as fetched, before anything has been decided about it.
 *
 * @param path repository-relative, including the configured document path
 * @param text file contents, already decoded as UTF-8
 */
public record SourceFile(String path, String text) {
}
