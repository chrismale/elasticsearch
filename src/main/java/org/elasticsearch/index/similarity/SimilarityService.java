/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index.similarity;

import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.apache.lucene.search.similarities.PerFieldSimilarityWrapper;
import org.apache.lucene.search.similarities.Similarity;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.name.Named;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.AbstractIndexComponent;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.mapper.FieldMapper;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.settings.IndexSettings;

/**
 *
 */
public class SimilarityService extends AbstractIndexComponent {

    private final MapperService mapperService;

    private final Similarity defaultIndexSimilarity;
    private final Similarity defaultSearchSimilarity;

    private final Similarity indexSimilarity;
    private final Similarity searchSimilarity;

    public SimilarityService(Index index, MapperService mapperService) {
        this(index, ImmutableSettings.Builder.EMPTY_SETTINGS, new DefaultSimilarity(), new DefaultSimilarity(), mapperService);
    }

    @Inject
    public SimilarityService(Index index, @IndexSettings Settings indexSettings,
                             @Named("default_index") Similarity defaultIndexSimilarity,
                             @Named("default_search") Similarity defaultSearchSimilarity,
                             MapperService mapperService) {
        super(index, indexSettings);
        this.mapperService = mapperService;

        this.defaultIndexSimilarity = defaultIndexSimilarity;
        this.defaultSearchSimilarity = defaultSearchSimilarity;

        this.indexSimilarity = new IndexPerFieldSimilarity();
        this.searchSimilarity = new SearchPerFieldSimilarity();
    }

    public Similarity defaultIndexSimilarity() {
        return indexSimilarity;
    }

    public Similarity defaultSearchSimilarity() {
        return searchSimilarity;
    }

    private class IndexPerFieldSimilarity extends PerFieldSimilarityWrapper {

        @Override
        public Similarity get(String name) {
            FieldMapper mapper = mapperService.smartNameFieldMapper(name);
            return (mapper != null && mapper.indexSimilarity() != null) ? mapper.indexSimilarity() : defaultIndexSimilarity;
        }
    }

    private class SearchPerFieldSimilarity extends PerFieldSimilarityWrapper {

        @Override
        public Similarity get(String name) {
            FieldMapper mapper = mapperService.smartNameFieldMapper(name);
            return (mapper != null && mapper.searchSimilarity() != null) ? mapper.searchSimilarity() : defaultSearchSimilarity;
        }
    }
}
