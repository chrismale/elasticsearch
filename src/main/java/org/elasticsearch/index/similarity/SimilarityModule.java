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

import com.google.common.collect.Maps;
import org.apache.lucene.search.similarities.*;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.inject.Scopes;
import org.elasticsearch.common.inject.assistedinject.FactoryProvider;
import org.elasticsearch.common.inject.multibindings.MapBinder;
import org.elasticsearch.common.inject.name.Names;
import org.elasticsearch.common.settings.Settings;

import java.util.Map;

/**
 *
 */
public class SimilarityModule extends AbstractModule {

    private final Settings settings;

    private final Map<String, Class<? extends Similarity>> similarities = Maps.newHashMap();

    public SimilarityModule(Settings settings) {
        this.settings = settings;

        addSimilarity("tfidf", DefaultSimilarity.class);
        addSimilarity("bm25", BM25Similarity.class);
        addSimilarity("ib", IBSimilarity.class);
        addSimilarity("dfr", DFRSimilarity.class);
        addSimilarity("lmdirichlet", LMDirichletSimilarity.class);
        addSimilarity("lmjelinekmercer", LMJelinekMercerSimilarity.class);
    }

    public void addSimilarity(String name, Class<? extends Similarity> similarity) {
        similarities.put(name, similarity);
    }

    @Override
    protected void configure() {
        MapBinder<String, Similarity> similarityBinder = MapBinder.newMapBinder(binder(), String.class, Similarity.class);

        for (Map.Entry<String, Class<? extends Similarity>> entry : similarities.entrySet()) {
            String similarityName = entry.getKey();
            Class<? extends Similarity> similarityClass = entry.getValue();

            similarityBinder.addBinding(similarityName).to(similarityClass).in(Scopes.SINGLETON);
        }

        bind(SimilarityLookupService.class).asEagerSingleton();

        // TODO: Make defaults configurable
        bind(Similarity.class).annotatedWith(Names.named("default_index")).to(DefaultSimilarity.class);
        bind(Similarity.class).annotatedWith(Names.named("default_search")).to(DefaultSimilarity.class);
        bind(SimilarityService.class).asEagerSingleton();
    }
}
