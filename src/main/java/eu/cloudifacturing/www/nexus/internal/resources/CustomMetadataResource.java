package eu.cloudifacturing.www.nexus.internal.resources;

import eu.cloudifacturing.www.nexus.internal.api.CustomMetadataXO;
import eu.cloudifacturing.www.nexus.internal.resources.doc.CustomMetadataResourceDoc;
import jdk.nashorn.internal.objects.annotations.Getter;
import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.common.entity.DetachedEntityId;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.manager.RepositoryManager;
import org.sonatype.nexus.repository.rest.internal.resources.AssetsResource;
import org.sonatype.nexus.repository.rest.internal.resources.ComponentsResource;
import org.sonatype.nexus.repository.storage.Asset;
import org.sonatype.nexus.repository.storage.AssetEntityAdapter;
import org.sonatype.nexus.repository.storage.AssetStore;
import org.sonatype.nexus.rest.Resource;
import org.sonatype.nexus.repository.browse.BrowseService;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.xml.soap.Detail;

import java.util.Base64;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.sonatype.nexus.repository.http.HttpStatus.UNPROCESSABLE_ENTITY;

@Named
@Singleton
@Path(CustomMetadataResource.RESOURCE_URI)
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
public class CustomMetadataResource
    extends ComponentSupport
    implements Resource, CustomMetadataResourceDoc {

    public static final String RESOURCE_URI = "metadata";
    private final BrowseService browseService;
    private final RepositoryManager repositoryManager;
    private final AssetEntityAdapter assetEntityAdapter;
    private final AssetStore assetStore;

    @Inject
    public CustomMetadataResource(final BrowseService browseService,
                                  final RepositoryManager repositoryManager,
                                  final AssetEntityAdapter assetEntityAdapter,
                                  final AssetStore assetStore
                                  ){
        this.browseService = browseService;
        this.repositoryManager = repositoryManager;
        this.assetEntityAdapter = assetEntityAdapter;
        this.assetStore = assetStore;
    }

    @GET
    @Path("/{id}")
    @Override
    public CustomMetadataXO listCustomMetadataById(@PathParam("id") final String id) {
        String decoded = new String(Base64.getUrlDecoder().decode(id));
        String assetId = decoded.split(":")[1];
        String repositoryId = decoded.split(":")[0];
        Repository repository = getRepository(repositoryId);
        Asset asset = getAsset(id, repository, new DetachedEntityId(assetId));
        return CustomMetadataXO.fromAssetMetadata(asset,repository);
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Override
    public CustomMetadataXO updateCustomMetadataById(@PathParam("id") String id, final Map<String, String> metadata) {
        String decoded = new String(Base64.getUrlDecoder().decode(id));
        String assetId = decoded.split(":")[1];
        String repositoryId = decoded.split(":")[0];
        Repository repository = getRepository(repositoryId);
        Asset asset = getAsset(id, repository, new DetachedEntityId(assetId));
        metadata.forEach((k,v)->{
            asset.attributes().child("metadata").set(k,v);
        });
        assetStore.save(asset);
        return CustomMetadataXO.fromAssetMetadata(asset,repository);
    }

    private Asset getAsset(final String id, final Repository repository, final DetachedEntityId entityId){
        try {
            return ofNullable(browseService
                    .getAssetById(assetEntityAdapter.recordIdentity(entityId),repository))
                    .orElseThrow(()-> new NotFoundException("Unable to locate asset with id"));
        }
        catch (IllegalArgumentException e){
            throw new WebApplicationException(format("Unnable to process asset with id %s",entityId), UNPROCESSABLE_ENTITY);
        }
    }

    private Repository getRepository(final String id){
        if (id==null){
            throw new WebApplicationException("Repository ID is required.", UNPROCESSABLE_ENTITY);
        }
        Repository repository = ofNullable(repositoryManager.get(id))
                .orElseThrow(()->new NotFoundException("Unable to locate repository with id " + id));

        return repository;
    }
}
