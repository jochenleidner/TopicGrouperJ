package org.hhn.topicgrouper.lda.validation;

import org.hhn.topicgrouper.doc.Document;
import org.hhn.topicgrouper.doc.DocumentProvider;
import org.hhn.topicgrouper.doc.DocumentSplitter;
import org.hhn.topicgrouper.lda.impl.LDAGibbsSampler;

public class LDAPerplexityCalculatorLeftToRight<T> extends
		AbstractLDAPerplexityCalculator<T> {
	protected final int particles;

	public LDAPerplexityCalculatorLeftToRight(boolean bowFactor, int particles) {
		this(bowFactor, null, particles);
	}

	public LDAPerplexityCalculatorLeftToRight(boolean bowFactor,
			DocumentSplitter<T> documentSplitter, int particles) {
		super(bowFactor, documentSplitter, 1);
		this.particles = particles;
	}

	private LDAGibbsSampler<T>.LeftToRightParticleSampler lrpSampler;

	@Override
	public double computePerplexity(DocumentProvider<T> testDocumentProvider,
			LDAGibbsSampler<T> sampler) {
		lrpSampler = sampler.createLeftToRightParticleSampler(particles);
		return super.computePerplexity(testDocumentProvider, sampler);
	}

	int c;

	protected double computeLogProbability(Document<T> refD, Document<T> d,
			LDAGibbsSampler<T> sampler) {
//		System.out.println(c++);
		return lrpSampler.leftToRightDocCompletion(refD, d);
	}

	@Override
	protected void initPtd(Document<T> d, LDAGibbsSampler<T> sampler) {
	}

	@Override
	protected void updatePtd(Document<T> d, LDAGibbsSampler<T> sampler) {
	}
}