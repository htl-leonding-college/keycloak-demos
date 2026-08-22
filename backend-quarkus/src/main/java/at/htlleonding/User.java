package at.htlleonding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Ein Konto, gekuerzt auf das, was eine Klassenliste braucht.
 *
 * Was hier NICHT steht, ist der Punkt: keine Attribute, keine
 * Verzeichnisherkunft, kein LDAP_ENTRY_DN, keine Anmeldezeitpunkte. Die
 * Verwaltungsschnittstelle liefert das alles mit; ein Backend, das es
 * ungefiltert an sein Frontend weiterreicht, gibt mehr preis als die
 * Anwendung braucht.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record User(
        String id,
        String username,
        String firstName,
        String lastName,
        String email,
        Boolean enabled) {
}
