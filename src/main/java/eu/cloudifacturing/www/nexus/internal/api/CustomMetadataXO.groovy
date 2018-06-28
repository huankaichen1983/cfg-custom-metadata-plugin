package eu.cloudifacturing.www.nexus.internal.api

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.transform.builder.Builder
import org.sonatype.nexus.repository.Repository
import org.sonatype.nexus.repository.storage.Asset

import javax.annotation.Nullable

import static org.sonatype.nexus.common.entity.EntityHelper.id
import static org.sonatype.nexus.repository.search.DefaultComponentMetadataProducer.ID
import static org.sonatype.nexus.repository.search.DefaultComponentMetadataProducer.NAME
import static org.sonatype.nexus.repository.storage.Asset.CHECKSUM

@CompileStatic
@Builder
@ToString(includePackage = false, includeNames = true)
@EqualsAndHashCode(includes = ['id'])
class CustomMetadataXO {
    String id

    String downloadUrl

    String path

    String repository

    String format

    Map checksum

    Map metadata

    static CustomMetadataXO fromAssetMetadata(final Asset asset, @Nullable final Repository repository){
        String internalId = id(asset).getValue()

        return builder()
        .id(getID(repository,internalId))
        .downloadUrl(repository.url + '/' + asset.name())
        .repository(repository.name)
        .checksum(asset.attributes().child(CHECKSUM).backing())
        .format(repository.format.value)
        .metadata(asset.attributes().child("metadata").backing())
        .build()
    }

    static CustomMetadataXO fromElasticSearchMap(final Map map, final Repository repository){
        String internalId = (String) map.get(ID)

        return builder()
        .id(getID(repository,internalId))
        .path((String) map.get(NAME))
        .downloadUrl(repository.url + '/' + (String) map.get(NAME))
        .repository(repository.name)
        .checksum((Map) map.get("attributes",[:])[CHECKSUM])
        .format(repository.format.value)
        .metadata((Map) map.get("attributes",[:])["metadata"])
        .build()
    }

    private static String getID(Repository repository, String id){
        String key = repository.name + id
        return new String(Base64.getUrlEncoder().withoutPadding().encode(key.bytes))
    }

}
