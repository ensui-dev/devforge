package com.devforge.document.contract;

/**
 * What kind of knowledge a document holds.
 *
 * <p>Part of the published contract because other modules and the client render
 * type-specific affordances (icons, filters) from it.
 */
public enum DocumentType {

    /** Anything that does not fit a more specific type. */
    GENERAL,

    /** Explains a module, class, or algorithm in the codebase. */
    CODE,

    /** A repeatable process: release steps, onboarding, incident response. */
    PROCEDURE,

    /** A single technology or library and how this project uses it. */
    TECHNOLOGY,

    /** The overall set of technologies in use and why. */
    TECH_STACK,

    /** System structure, boundaries, and data flow. */
    ARCHITECTURE,

    /** An interface contract: endpoints, payloads, error semantics. */
    API,

    /** Operational guidance for running and recovering the system. */
    RUNBOOK,

    /** An architecture decision record: context, options, and the choice made. */
    DECISION
}
