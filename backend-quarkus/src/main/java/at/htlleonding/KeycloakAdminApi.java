package at.htlleonding;

import io.quarkus.oidc.client.filter.OidcClientFilter;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

/**
 * Die Verwaltungsschnittstelle des Keycloak, so weit diese Demo sie braucht.
 *
 * Bewusst als eigenes Interface statt ueber die Keycloak-Admin-Bibliothek:
 * Hier sind die drei Aufrufe zu sehen, die es wirklich sind. Wer sie kennt,
 * kann sie in jeder Sprache nachbauen - eine Bibliothek verdeckt genau das.
 *
 * {@code @OidcClientFilter} haengt an jeden Aufruf ein Token des Dienstkontos.
 * Es holt es selbst ueber client_credentials und erneuert es, wenn es
 * ablaeuft. Das Token der angemeldeten Person wird hier NICHT weitergereicht -
 * eine Lehrkraft hat im Realm keine Verwaltungsrechte, und das ist Absicht.
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@RegisterRestClient(configKey = "keycloak-admin")
@OidcClientFilter
public interface KeycloakAdminApi {

    /**
     * Die obersten Gruppen. Seit Keycloak 23 liefert dieser Aufruf die
     * Untergruppen NICHT mehr mit - nur ihre Anzahl. Der Baum entsteht
     * deshalb ueber wiederholte Aufrufe von {@link #children}.
     */
    @GET
    @Path("groups")
    List<Group> topLevelGroups(@QueryParam("first") int first,
                               @QueryParam("max") int max,
                               @QueryParam("briefRepresentation") boolean brief);

    /** Die direkten Untergruppen einer Gruppe. */
    @GET
    @Path("groups/{id}/children")
    List<Group> children(@PathParam("id") String id,
                         @QueryParam("first") int first,
                         @QueryParam("max") int max,
                         @QueryParam("briefRepresentation") boolean brief);

    /**
     * Die Mitglieder einer Gruppe - nur die direkten, nicht die der
     * Untergruppen. Fuer Klassengruppen ist das genau richtig: Sie sind
     * Blaetter im Baum.
     *
     * Der Aufruf ist SEITENWEISE. Ohne max liefert Keycloak 100 Eintraege und
     * sagt nicht dazu, dass es mehr gibt - eine Klasse mit 73 Mitgliedern
     * faellt dabei nicht auf, ein Jahrgang mit 130 schon.
     */
    @GET
    @Path("groups/{id}/members")
    List<User> members(@PathParam("id") String id,
                       @QueryParam("first") int first,
                       @QueryParam("max") int max,
                       @QueryParam("briefRepresentation") boolean brief);
}
