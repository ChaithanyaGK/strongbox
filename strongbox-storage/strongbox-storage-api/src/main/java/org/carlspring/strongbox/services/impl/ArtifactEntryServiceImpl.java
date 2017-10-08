package org.carlspring.strongbox.services.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.carlspring.strongbox.artifact.coordinates.ArtifactCoordinates;
import org.carlspring.strongbox.data.service.CommonCrudService;
import org.carlspring.strongbox.domain.ArtifactEntry;
import org.carlspring.strongbox.services.ArtifactEntryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;

/**
 * DAO implementation for {@link ArtifactEntry} entities.
 *
 * @author Alex Oreshkevich
 */
@Service
@Transactional
class ArtifactEntryServiceImpl extends CommonCrudService<ArtifactEntry>
        implements ArtifactEntryService
{

    private static final Logger logger = LoggerFactory.getLogger(ArtifactEntryService.class);

    @Override
    public <S extends ArtifactEntry> S save(S entity)
    {
        return super.save(entity);
    }

    @Override
    public List<ArtifactEntry> findByCoordinates(Map<String, String> coordinates)
    {
        return findByCoordinates(coordinates, null, false);
    }

    @Override
    @Transactional
    public List<ArtifactEntry> findByCoordinates(Map<String, String> coordinates, String orderBy, boolean strict)
    {
        if (coordinates == null || coordinates.keySet().isEmpty())
        {
            return findAll().orElse(Collections.EMPTY_LIST);
        }

        coordinates = coordinates.entrySet()
                                 .stream()
                                 .filter(e -> e.getValue() != null)
                                 .collect(Collectors.toMap(Map.Entry::getKey,
                                                           e -> e.getValue() == null ? null
                                                                   : e.getValue().toLowerCase()));
        
        // Prepare a custom query based on all non-null coordinates that were joined by logical AND.
        // Read more about fetching strategies here: http://orientdb.com/docs/2.2/Fetching-Strategies.html
        String sQuery = buildCoordinatesQuery(coordinates, orderBy, strict);
        OSQLSynchQuery<ArtifactEntry> oQuery = new OSQLSynchQuery<>(sQuery);

        List<ArtifactEntry> entries = getDelegate().command(oQuery).execute(coordinates);

        return entries;
    }

    @Override
    @SuppressWarnings("unchecked")
    // don't try to use second level cache here until you make all coordinates properly serializable
    public List<ArtifactEntry> findByCoordinates(ArtifactCoordinates coordinates)
    {
        if (coordinates == null)
        {
            return findByCoordinates((Map<String, String>)null);
        }
        return findByCoordinates(coordinates.getCoordinates());
    }

    @Override
    public Optional<ArtifactEntry> findOne(ArtifactCoordinates artifactCoordinates)
    {
        List<ArtifactEntry> artifactEntryList = findByCoordinates(artifactCoordinates);

        return Optional.ofNullable(artifactEntryList == null || artifactEntryList.isEmpty() ?
                                   null : artifactEntryList.iterator().next());
    }

    protected String buildCoordinatesQuery(Map<String, String> map, String orderBy, boolean strict)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM ").append(getEntityClass().getSimpleName());

        if (map == null || map.isEmpty())
        {
            return sb.toString();
        }

        sb.append(" WHERE ");

        // process only coordinates with non-null values
        map.entrySet()
           .stream()
           .filter(entry -> entry.getValue() != null)
           .forEach(entry -> sb.append("artifactCoordinates.coordinates.")
                               .append(entry.getKey()).append(".toLowerCase()")
                               .append(strict ? " = " : " like ")
                               .append(String.format(":%s", entry.getKey()))
                               .append(" AND "));

        
        // remove last 'and' statement (that doesn't relate to any value)
        String query = sb.toString();
        query = query.substring(0, query.length() - 5);

        if (orderBy != null && !orderBy.trim().isEmpty())
        {
            query += String.format(" ORDER BY artifactCoordinates.coordinates.%s", orderBy);
        }
        
        // now query should looks like
        // SELECT * FROM Foo WHERE blah = :blah AND moreBlah = :moreBlah

        logger.debug("Executing SQL query> " + query);

        return query;
    }


    @Override
    public Class<ArtifactEntry> getEntityClass()
    {
        return ArtifactEntry.class;
    }

    @Override
    public boolean exists(String storageId, String repositoryId, String path)
    {
        String sQuery = String.format("SELECT FROM INDEX:idx_artifact WHERE key = [:storageId, :repositoryId, :path]");

        OSQLSynchQuery<ODocument> oQuery = new OSQLSynchQuery<>(sQuery);
        oQuery.setLimit(1);

        HashMap<String, Object> params = new HashMap<>();
        params.put("storageId", storageId);
        params.put("repositoryId", repositoryId);
        params.put("path", path);

        List<ODocument> resultList = getDelegate().command(oQuery).execute(params);
        return !resultList.isEmpty();
    }

    
}
