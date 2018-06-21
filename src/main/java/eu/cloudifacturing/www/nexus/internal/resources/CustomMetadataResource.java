package eu.cloudifacturing.www.nexus.internal.resources;

import eu.cloudifacturing.www.nexus.internal.api.CustomMetadataXO;
import eu.cloudifacturing.www.nexus.internal.resources.doc.CustomMetadataResourceDoc;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.common.entity.DetachedEntityId;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.browse.BrowseService;
import org.sonatype.nexus.repository.manager.RepositoryManager;
import org.sonatype.nexus.repository.rest.SearchUtils;
import org.sonatype.nexus.repository.search.SearchService;
import org.sonatype.nexus.repository.storage.Asset;
import org.sonatype.nexus.repository.storage.AssetEntityAdapter;
import org.sonatype.nexus.repository.storage.AssetStore;
import org.sonatype.nexus.rest.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.sonatype.nexus.repository.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static org.sonatype.nexus.repository.search.DefaultComponentMetadataProducer.REPOSITORY_NAME;

@Named
@Singleton
@Path(CustomMetadataResource.RESOURCE_URI)
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
public class CustomMetadataResource
    extends ComponentSupport
    implements Resource, CustomMetadataResourceDoc {

    public static final String RESOURCE_URI = "metadatas";

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

    @Override
    @DELETE
    @Path("/asset/{id}/all")
    public CustomMetadataXO deleteAllCustomMetadataById(@PathParam("id") final String id) {
        String decoded = new String(Base64.getUrlDecoder().decode(id));
        String assetId = decoded.split(":")[1];
        String repositoryId = decoded.split(":")[0];
        Repository repository = getRepository(repositoryId);
        Asset asset = getAsset(id, repository, new DetachedEntityId(assetId));
        asset.attributes().remove("metadata");
        assetStore.save(asset);
        return CustomMetadataXO.fromAssetMetadata(asset,repository);
    }

    @GET
    @Path("/search/{key}")
    @Override
    public List<CustomMetadataXO> searchAssetByKeyValue(@PathParam("key") final String key, @QueryParam("value") final String value) {
        BoolQueryBuilder query = boolQuery();
        query.must(termQuery("assets.attributes.metadata."+key, value));

        Iterable<SearchHit> hits = searchService.browseUnrestricted(query);
        System.out.println("Search by Key Value : " + key +"-" + value +"-" + hits.toString());
        hits.forEach(hit->{
            List<Map<String,Object>> assets = (List<Map<String,Object>>) hit.getSource().get("assets");
            assets.forEach(asset ->{
                System.out.println(asset.get("name").toString());
            });
        });

        return StreamSupport.stream(hits.spliterator(),false)
                .flatMap(hit -> extractAssets(hit,key,value))
                .collect(toList());
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

    private Stream<CustomMetadataXO> extractAssets(final SearchHit componentHit, final String key, final String value){
        Map<String, Object> componentMap = checkNotNull(componentHit.getSource());
        Repository repository = searchUtils.getRepository((String) componentMap.get(REPOSITORY_NAME));
        List<Map<String,Object>> assets = (List<Map<String,Object>>) componentMap.get("assets");
        if(assets == null){
            return Stream.empty();
        }
        return assets.stream()
                .filter(assetMap ->
                    filterAsset(assetMap,key,value)
                )
                .map(asset -> CustomMetadataXO.fromElasticSearchMap(asset,repository));
    }

    private boolean filterAsset(final Map<String, Object> assetMap, final String key, final String value){
        Map attributes = (Map) assetMap.get("attributes");
        if(attributes.containsKey("metadata")) {
            Map metadatas = (Map) attributes.get("metadata");
            if (metadatas.containsKey(key)) {
                if (metadatas.get(key).equals(value)) {
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
}
