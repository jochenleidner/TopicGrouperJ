package org.hhn.topicgrouper.paper;

import gnu.trove.iterator.TIntIterator;

import java.io.IOException;

import org.hhn.topicgrouper.classify.SupervisedDocumentClassifier;
import org.hhn.topicgrouper.classify.impl.AbstractTopicBasedNBClassifier;
import org.hhn.topicgrouper.doc.Document;
import org.hhn.topicgrouper.tg.TGSolution;

public class ReutersTGNaiveBayesExperiment extends
		AbstractTGClassificationExperiment {
	public ReutersTGNaiveBayesExperiment() throws IOException {
		super();
	}

	@Override
	protected SupervisedDocumentClassifier<String, String> createClassifier(
			final TGSolution<String> solution) {
		int nt = solution.getNumberOfTopics();
		if (nt % 100 == 0 || nt < 300) {
			final int[] topicIds = solution.getTopicIds();
			return new AbstractTopicBasedNBClassifier<String, String>(1) {
				@Override
				protected double[] getFtd(Document<String> d) {
					double[] res = new double[topicIds.length];

					TIntIterator it = d.getWordIndices().iterator();
					while (it.hasNext()) {
						int wordIndex = it.next();
						int topicIndex = solution.getTopicForWord(wordIndex);
						int k = -1;
						for (int i = 0; i < topicIds.length; i++) {
							if (topicIds[i] == topicIndex) {
								k = i;
								break;
							}
						}

						res[k] += d.getWordFrequency(wordIndex);
					}
					return res;
				}

				@Override
				protected int getNTopics() {
					return solution.getNumberOfTopics();
				}
			};
		} else {
			return null;
		}
	}

	public static void main(String[] args) throws IOException {
		new ReutersTGNaiveBayesExperiment().run();
	}
}