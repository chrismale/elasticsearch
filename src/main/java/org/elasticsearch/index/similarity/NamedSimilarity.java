package org.elasticsearch.index.similarity;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.index.Norm;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.similarities.Similarity;

import java.io.IOException;

public class NamedSimilarity extends Similarity {

    private final Similarity delegate;
    private final String name;

    public NamedSimilarity(Similarity delegate, String name) {
        this.delegate = delegate;
        this.name = name;
    }

    public String name() {
        return name;
    }

    @Override
    public float coord(int overlap, int maxOverlap) {
        return delegate.coord(overlap, maxOverlap);
    }

    @Override
    public float queryNorm(float valueForNormalization) {
        return delegate.queryNorm(valueForNormalization);
    }

    @Override
    public void computeNorm(FieldInvertState state, Norm norm) {
        delegate.computeNorm(state, norm);
    }

    @Override
    public SimWeight computeWeight(float queryBoost, CollectionStatistics collectionStats, TermStatistics... termStats) {
        return delegate.computeWeight(queryBoost, collectionStats, termStats);
    }

    @Override
    public ExactSimScorer exactSimScorer(SimWeight weight, AtomicReaderContext context) throws IOException {
        return delegate.exactSimScorer(weight, context);
    }

    @Override
    public SloppySimScorer sloppySimScorer(SimWeight weight, AtomicReaderContext context) throws IOException {
        return delegate.sloppySimScorer(weight, context);
    }
}
