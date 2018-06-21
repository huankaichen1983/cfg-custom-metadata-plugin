package eu.cloudifacturing.www.nexus.internal.resources;

import eu.cloudifacturing.www.nexus.internal.api.CustomMetadataXO;
import eu.cloudifacturing.www.nexus.internal.resources.doc.CustomMetadataResourceDoc;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.common.entity.DetachedEntityId;
import org.sonatype.nexus.common.io.Hex;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.browse.BrowseService;
import org.sonatype.nexus.repository.manager.RepositoryManager;
import org.sonatype.nexus.repository.rest.SearchUtils;
import org.sonatype.nexus.repository.search.SearchService;
import org.sonatype.nexus.repository.storage.Asset;
import org.sonatype.nexus.repository.storage.AssetEntityAdapter;
import org.sonatype.nexus.repository.storage.AssetStore;
import org.sonatype.nexus.rest.Page;
import org.sonatype.nexus.rest.Resource;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Base64;
import java.util.Map;

import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.ofNullable;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.sonatype.nexus.common.hash.HashAlgorithm.MD5;
import static org.sonatype.nexus.repository.http.HttpStatus.NOT_ACCEPTABLE;
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

    private final SearchUtils searchUtils;
    private final SearchService searchService;
    private final BrowseService browseService;
    private final RepositoryManager repositoryManager;
    private final AssetEntityAdapter assetEntityAdapter;
    private final AssetStore assetStore;

    @Inject
    public CustomMetadataResource(final SearchUtils searchUtils,
                                  final SearchService searchService,
                                  final BrowseService browseService,
                                  final RepositoryManager repositoryManager,
                                  final AssetEntityAdapter assetEntityAdapter,
                                  final AssetStore assetStore
                                  ){
        this.searchUtils = searchUtils;
        this.searchService = searchService;
        this.browseService = browseService;
        this.repositoryManager = repositoryManager;
        this.assetEntityAdapter = assetEntityAdapter;
        this.assetStore = assetStore;
    }

    @GET
    @Path("/asset/{id}")
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
    @Path("/asset/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Override
    public CustomMetadataXO updateCustomMetadataById(@PathParam("id") final String id, final Map<String, String> metadata) {
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

    @DELETE
    @Path("/asset/{id}")
    @Override
    public CustomMetadataXO deleteCustomMetadataByIdKey(@PathParam("id") final String id, @QueryParam("key") final String key) {
        String decoded = new String(Base64.getUrlDecoder().decode(id));
        String assetId = decoded.split(":")[1];
        String repositoryId = decoded.split(":")[0];
        Repository repository = getRepository(repositoryId);
        Asset asset = getAsset(id, repository, new DetachedEntityId(assetId));
        asset.attributes().child("metadata").remove(key);
        assetStore.save(asset);
        return CustomMetadataXO.fromAssetMetadata(asset,repository);
    }

    @GET
    @Path("/search/{key}")
    @Override
    public Page<CustomMetadataXO> searchAssetByKeyValue(@PathParam("key") final String key, @QueryParam("value") final String value) {
        BoolQueryBuilder query = boolQuery();
        query.filter(termQuery(key, value));
        return null;
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

    private int decode(@Nullable final String continuationToken, final QueryBuilder query) {
        if (continuationToken == null) {
            return 0;
        }
        else {
            String decoded = new String(Hex.decode(continuationToken), UTF_8);
            String[] decodedParts = decoded.split(":");
            if (decodedParts.length != 2) {
                throw new WebApplicationException(format("Unable to parse token %s", continuationToken), NOT_ACCEPTABLE);
            }
            if (!decodedParts[1].equals(getHashCode(query))) {
                throw new WebApplicationException(
                        format("Continuation token %s does not match this query", continuationToken), NOT_ACCEPTABLE);
            }
            return parseInt(decodedParts[0]);
        }
    }

    private String getHashCode(final QueryBuilder query) {
        return MD5.function().hashString(query.toString(), UTF_8).toString();
    }
}
