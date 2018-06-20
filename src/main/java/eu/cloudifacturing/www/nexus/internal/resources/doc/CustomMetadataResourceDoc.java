package eu.cloudifacturing.www.nexus.internal.resources.doc;

import eu.cloudifacturing.www.nexus.internal.api.CustomMetadataXO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import java.util.Map;

@Api(value = "metadata", description = "Operations to add, get, list, update and delete custom metadata in artifacts")
public interface CustomMetadataResourceDoc {
    @ApiOperation("List artifact metadata by ID")
    CustomMetadataXO listCustomMetadataById(@ApiParam(value = "ID of the artifact") final String id);

    @ApiOperation("Add/Update artifact metadatas by ID")
    CustomMetadataXO updateCustomMetadataById(@ApiParam(value = "ID of the artifact") final String id, final Map<String, String> metadata);

    @ApiOperation("Delete artifact metadatas by ID & Key")
    CustomMetadataXO deleteCustomMetadataByIdKey(@ApiParam(value = "ID of the artifact") final String id, final String key);
}
