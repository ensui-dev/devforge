package com.devforge.instance.application;

/**
 * The outcome of first-run setup.
 *
 * @param adminEmail echoed so the client can prefill the sign-in form it sends the
 *                   operator to next
 */
public record SetupResponse(InstanceResponse instance, String adminEmail) {
}
