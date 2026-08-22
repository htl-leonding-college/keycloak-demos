package at.htlleonding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Eine Gruppe, so wie Keycloak sie ausliefert - auf die Felder gekuerzt, die
 * diese Demo benutzt.
 *
 * {@code ignoreUnknown} ist hier kein Schludern, sondern Absicht: Die
 * Verwaltungsschnittstelle bekommt mit jeder Version Felder dazu. Ohne diese
 * Angabe bricht die Anwendung beim naechsten Serverwechsel an einer Stelle,
 * die mit der Aenderung nichts zu tun hat.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Group(
        String id,
        String name,
        String path,
        Integer subGroupCount,
        List<Group> subGroups) {
}
