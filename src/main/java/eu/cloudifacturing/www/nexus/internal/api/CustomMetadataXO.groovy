package eu.cloudifacturing.www.nexus.internal.api

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.transform.builder.Builder
import org.sonatype.nexus.common.collect.NestedAttributesMap
import org.sonatype.nexus.repository.Repository
import org.sonatype.nexus.repository.storage.Asset

import static org.sonatype.nexus.common.entity.EntityHelper.id

@CompileStatic
@Builder
@ToString(includePackage = false, includeNames = true)
@EqualsAndHashCode(includes = ['id'])
class CustomMetadataXO {
    String id

    Map metadatas

    static CustomMetadataXO fromAssetMetadata(final Asset asset, final Repository repository){
        String internalId = id(asset).getValue()

        return builder()
        .id(getID(repository,internalId))
        .metadatas(asset.attributes().child("metadata").backing())
        .build()
    }

    private static String getID(Repository repository, String id){
        String key = repository.name + id
        return new String(Base64.getUrlEncoder().withoutPadding().encode(key.bytes))
    }

}
