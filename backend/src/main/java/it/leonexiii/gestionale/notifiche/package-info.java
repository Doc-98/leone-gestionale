/**
 * Modulo notifiche: invio delle email (SMTP configurato con spring.mail.*) e registro di tutte le email, inviate o no.
 * Senza server di posta configurato le email si registrano come «non inviate»: si vedono, ma non partono.
 * Gli altri moduli usano {@link it.leonexiii.gestionale.notifiche.Posta}.
 */
package it.leonexiii.gestionale.notifiche;
