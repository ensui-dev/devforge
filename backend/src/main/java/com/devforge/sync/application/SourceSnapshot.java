package com.devforge.sync.application;

import java.util.List;

/**
 * The state of a repository's documentation at one ref.
 *
 * @param ref   what was fetched — a commit id where the source knows one, otherwise
 *              the branch name. Recorded so an operator can tell which version is
 *              live.
 * @param files every markdown file found beneath the configured path
 */
public record SourceSnapshot(String ref, List<SourceFile> files) {
}
