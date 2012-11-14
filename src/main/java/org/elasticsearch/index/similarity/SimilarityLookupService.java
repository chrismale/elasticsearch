package org.elasticsearch.index.similarity;

import com.google.common.collect.Maps;
import org.apache.lucene.search.similarities.Similarity;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.AbstractIndexComponent;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.settings.IndexSettings;

import java.util.Map;

public class SimilarityLookupService extends AbstractIndexComponent {

    private final Map<String, NamedSimilarity> similarities = Maps.newHashMap();

    @Inject
    public SimilarityLookupService(Index index, @IndexSettings Settings indexSettings, Map<String, Similarity> similarities) {
        super(index, indexSettings);
        for (Map.Entry<String, Similarity> entry : similarities.entrySet()) {
            this.similarities.put(entry.getKey(), new NamedSimilarity(entry.getValue(), entry.getKey()));
        }
    }

    public NamedSimilarity similarity(String name) {
        return similarities.get(name);
    }
}
