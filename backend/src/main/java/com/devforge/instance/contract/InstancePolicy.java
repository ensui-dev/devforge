package com.devforge.instance.contract;

/**
 * Deployment-level policy, published for the modules that must honour it.
 *
 * <p>The instance module owns these decisions; identity and workspace consult them
 * without reaching into its persistence.
 */
public interface InstancePolicy {

    /**
     * @return whether this address may create an account right now
     */
    boolean registrationAllowedFor(String email);

    /** Explains a refusal, for the message shown to the person refused. */
    String registrationRefusalReason();

    /** Whether workspaces on this instance may publish documentation publicly. */
    boolean publicDocumentationEnabled();

    /** Which published workspace {@code /docs} opens, as {@code handle/slug}. */
    String handbookPath();

    /** False until the setup wizard has run. */
    boolean isConfigured();
}
