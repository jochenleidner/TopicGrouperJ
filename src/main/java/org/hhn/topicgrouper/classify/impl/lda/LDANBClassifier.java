package org.hhn.topicgrouper.classify.impl.lda;

import gnu.trove.list.TDoubleList;

import org.hhn.topicgrouper.classify.impl.AbstractTopicBasedNBClassifier;
import org.hhn.topicgrouper.doc.Document;
import org.hhn.topicgrouper.lda.impl.LDAGibbsSampler;

public class LDANBClassifier<T, L> extends AbstractTopicBasedNBClassifier<T, L> {
	private final LDAGibbsSampler<T> ldaGibbsSampler;
	private final int foldInterations;
	private final int foldInSamples;

	public LDANBClassifier(double lambda, LDAGibbsSampler<T> ldaGibbsSampler,
			int foldIterations, int foldInSamples) {
		super(lambda);
		this.ldaGibbsSampler = ldaGibbsSampler;
		this.foldInterations = foldIterations;
		this.foldInSamples = foldInSamples;
	}

	@Override
	protected void computeTopicFrequency(Document<T> d, double[] v) {
		for (int i = 0; i < v.length; i++) {
			// We are just getting the current sample state from the Gibbs
			// sampler.
			// To be exact, we would have to average over several the examples.
			// TODO: Really wanna do this? --> No - we go over so many document
			// - that's enough averaging...
			int c = ldaGibbsSampler.getDocumentTopicAssignmentCount(
					d.getIndex(), i);
			// Alpha smoothing via Dirichlet
			v[i] += (c + ldaGibbsSampler.getAlpha(i))
					/ (d.getSize() + ldaGibbsSampler.getAlphaConc())
					* d.getSize();
		}
	}

	@Override
	protected double getLogPtc(int l, int topic) {
		TDoubleList pt = ptc.get(l);
		double total = pt.get(0);
		double sumt = pt.get(topic + 1);

		// No smoothing here any more - already done in computeTopicFrequency()
		return Math.log(sumt / total);
	}

	@Override
	protected double[] computeTopicFrequencyTest(Document<T> d) {
		double[] res = new double[getNTopics()];

		// We are just getting the current fold in sample from the Gibbs
		// sampler.
		// To be more exact, we have to average over the examples.
		LDAGibbsSampler<T>.FoldInStore store = ldaGibbsSampler.foldIn(
				foldInterations, d);
		for (int j = 0; j < foldInSamples; j++) {
			int[] h = store.getDTopicAssignmentCounts();
			for (int i = 0; i < res.length; i++) {
				// This is wrong: Dirichlet smoothing is already done inside nextFoldInPtdSample()
				// res[i] += (h[i] + ldaGibbsSampler.getAlpha(i))
				// / (d.getSize() + ldaGibbsSampler.getAlphaConc());
				res[i] += ((double) h[i]) / d.getSize();
			}
			store.nextFoldInPtdSample();
		}
		for (int i = 0; i < res.length; i++) {
			res[i] = d.getSize() * res[i] / foldInSamples;
		}

		return res;
	}

	@Override
	protected int getNTopics() {
		return ldaGibbsSampler.getNTopics();
	}

	@Override
	protected int[] getTopicIndices() {
		return new int[0];
	}

	@Override
	protected int getTopicIndex(int wordIndex) {
		throw new IllegalStateException("should not get called");
	}
}
