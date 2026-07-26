package com.devforge.instance.contract;

/** Who may create an account on this instance. */
public enum RegistrationMode {

    /** Anyone with the address. Suits a public instance. */
    OPEN,

    /** Only addresses in the allowed domains. Suits a company instance. */
    RESTRICTED,

    /** Nobody self-registers; an instance admin creates accounts. */
    CLOSED
}
