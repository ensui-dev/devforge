package com.devforge.shared.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds the authenticated user's id to a {@code UUID} controller parameter.
 *
 * <p>Keeps Spring Security types out of the application layer: controllers receive
 * a plain identifier and pass it on as the acting user, so services can be unit
 * tested without a security context.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
