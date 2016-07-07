package org.hhn.topicgrouper.tgimpl;

import gnu.trove.TIntCollection;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import org.hhn.topicgrouper.base.Document;
import org.hhn.topicgrouper.base.DocumentProvider;
import org.hhn.topicgrouper.base.Solution;
import org.hhn.topicgrouper.util.UnionFind;

public class OptimizedTopicGrouper<T> extends AbstractTopicGrouper<T> {
	protected final int nWords;
	protected final int maxTopics;

	protected final TIntList[] topics;
	protected final UnionFind topicUnionFind;
	protected final int[] topicSizes;
	protected final TreeSet<JoinCandidate> joinCandidates;
	protected final double[] topicLikelihoods;
	protected double totalLikelihood;
	protected final int[] nTopics;
	protected final Solution<T> solution;
	protected final int[][] topicFrequencyPerDocuments;
	protected final double[] sumWordFrTimesLogWordFrByTopic;
	protected final double[] onePlusLambdaDivDocSizes;

	// key is word index, value is list of documents with word, list entry
	// consists of document index and word frequency in respective document
	protected final TIntObjectMap<List<DocIndexAndWordFr>> invertedIndex;

	protected final int minTopics;
	protected final DocIndexAndWordFr searchDummy = new DocIndexAndWordFr();

	public OptimizedTopicGrouper(int minWordFrequency, double lambda,
			DocumentProvider<T> documentProvider, int minTopics) {
		super(minWordFrequency, lambda, documentProvider);

		if (minWordFrequency < 1) {
			throw new IllegalArgumentException("minWordFrequency must be >= 1");
		}

		this.minTopics = Math.max(1, minTopics);
		nWords = documentProvider.getNumberOfWords();

		int counter = 0;
		for (int i = 0; i < nWords; i++) {
			if (documentProvider.getWordFrequency(i) >= minWordFrequency) {
				counter++;
			}
		}
		maxTopics = counter;

		topicUnionFind = new UnionFind(maxTopics);
		topics = new TIntList[maxTopics];
		topicSizes = new int[maxTopics];
		joinCandidates = new TreeSet<JoinCandidate>();
		topicLikelihoods = new double[maxTopics];
		nTopics = new int[1];

		topicFrequencyPerDocuments = new int[maxTopics][];
		sumWordFrTimesLogWordFrByTopic = new double[maxTopics];

		onePlusLambdaDivDocSizes = new double[documents.size()];
		for (int i = 0; i < documents.size(); i++) {
			onePlusLambdaDivDocSizes[i] = 1 + lambda / documentSizes[i];
		}

		invertedIndex = createInvertedIndex();

		solution = createSolution();
	}

	protected TIntObjectMap<List<DocIndexAndWordFr>> createInvertedIndex() {
		TIntObjectMap<List<DocIndexAndWordFr>> invertedIndex = new TIntObjectHashMap<List<DocIndexAndWordFr>>();
		for (int i = 0; i < documents.size(); i++) {
			Document<T> d = documents.get(i);
			TIntIterator it = d.getWordIndices().iterator();
			while (it.hasNext()) {
				int wordIndex = it.next();
				List<DocIndexAndWordFr> value = invertedIndex.get(wordIndex);
				if (value == null) {
					value = new ArrayList<DocIndexAndWordFr>();
					invertedIndex.put(wordIndex, value);
				}
				DocIndexAndWordFr entry = new DocIndexAndWordFr();
				entry.docIndex = i;
				entry.wordFr = d.getWordFrequency(wordIndex);
				int position = Collections.binarySearch(value, entry);
				value.add(-position - 1, entry);
			}
		}
		return invertedIndex;
	}

	protected Solution<T> createSolution() {
		return new Solution<T>() {
			@Override
			public TIntCollection[] getTopicsAlt() {
				return topics;
			}

			@Override
			public int getTopicForWord(int wordIndex) {
				return topicUnionFind.find(wordIndex);
			}

			@Override
			public T getWord(int wordIndex) {
				return OptimizedTopicGrouper.this.documentProvider
						.getWord(wordIndex);
			}

			@Override
			public int getIndex(T word) {
				return OptimizedTopicGrouper.this.documentProvider
						.getIndex(word);
			}

			@Override
			public int getGlobalWordFrequency(int wordIndex) {
				return OptimizedTopicGrouper.this.documentProvider
						.getWordFrequency(wordIndex);
			}

			@Override
			public int getTopicFrequency(int topicIndex) {
				return topicSizes[topicIndex];
			}

			@Override
			public List<TIntCollection> getTopics() {
				return null;
			}

			@Override
			public double[] getTopicLikelihoods() {
				return topicLikelihoods;
			}

			@Override
			public double getTotalLikelhood() {
				return totalLikelihood;
			}

			@Override
			public TIntCollection getHomonymns() {
				return OptimizedTopicGrouper.this.getHomonyms();
			}

			@Override
			public int getNumberOfTopics() {
				return nTopics[0];
			}
		};
	}

	protected TIntCollection getHomonyms() {
		return null;
	}

	protected double computeOneWordTopicLogLikelihood(int wordIndex) {
		double sum = 0; // Coherence weight log(1).
		for (int i = 0; i < documents.size(); i++) {
			Document<T> d = documents.get(i);
			double wordFrPerDoc = d.getWordFrequency(wordIndex);
			if (wordFrPerDoc > 0 && documentSizes[i] > 0) {
				sum += onePlusLambdaDivDocSizes[i] * wordFrPerDoc
						* (Math.log(wordFrPerDoc) - logDocumentSizes[i]);
			}
		}
		return sum;
	}

	protected double computeTwoWordLogLikelihood(int i, int j, int word1,
			int word2) {
		double sum = computeTwoWordLogLikelihoodHelp(word1, word2);
		sum += sumWordFrTimesLogWordFrByTopic[i];
		sum += sumWordFrTimesLogWordFrByTopic[j];
		int sizeSum = topicSizes[i] + topicSizes[j];
		sum -= (sizeSum) * Math.log(sizeSum);

		return sum;
	}

	protected double computeTwoWordLogLikelihoodHelp(int word1, int word2) {
		double sum = 0;
		List<DocIndexAndWordFr> l1 = invertedIndex.get(word1);
		List<DocIndexAndWordFr> l2 = invertedIndex.get(word2);
		if (l1 != null && l2 != null) {
			for (DocIndexAndWordFr entry1 : l1) {
				searchDummy.docIndex = entry1.docIndex;
				int posEntry2 = Collections.binarySearch(l2, searchDummy);
				if (posEntry2 >= 0) {
					DocIndexAndWordFr entry2 = l2.get(posEntry2);
					if (documentSizes[entry1.docIndex] > 0) {
						int fr = entry1.wordFr + entry2.wordFr;
						sum += onePlusLambdaDivDocSizes[entry1.docIndex]
								* fr
								* (Math.log(fr) - logDocumentSizes[entry1.docIndex]);
					}
				} else {
					if (documentSizes[entry1.docIndex] > 0) {
						sum += onePlusLambdaDivDocSizes[entry1.docIndex]
								* entry1.wordFr
								* (Math.log(entry1.wordFr) - logDocumentSizes[entry1.docIndex]);
					}
				}
			}
			for (DocIndexAndWordFr entry2 : l2) {
				searchDummy.docIndex = entry2.docIndex;
				int posEntry1 = Collections.binarySearch(l1, searchDummy);
				if (posEntry1 < 0) {
					if (documentSizes[entry2.docIndex] > 0) {
						sum += onePlusLambdaDivDocSizes[entry2.docIndex]
								* entry2.wordFr
								* (Math.log(entry2.wordFr) - logDocumentSizes[entry2.docIndex]);
					}
				}
			}
		}
		return sum;
	}

	protected double computeTwoTopicLogLikelihood(int topic1, int topic2) {
		double sum = 0;
		int[] frTopicPerDocument1 = topicFrequencyPerDocuments[topic1];
		int[] frTopicPerDocument2 = topicFrequencyPerDocuments[topic2];
		for (int i = 0; i < documents.size(); i++) {
			if ((frTopicPerDocument1[i] > 0 || frTopicPerDocument2[i] > 0)
					&& documentSizes[i] > 0) {
				int fr = frTopicPerDocument1[i] + frTopicPerDocument2[i];
				sum += onePlusLambdaDivDocSizes[i] * fr
						* (Math.log(fr) - logDocumentSizes[i]);
			}
		}

		sum += sumWordFrTimesLogWordFrByTopic[topic1];
		sum += sumWordFrTimesLogWordFrByTopic[topic2];
		int sizeSum = topicSizes[topic1] + topicSizes[topic2];
		sum -= (sizeSum) * Math.log(sizeSum);

		return sum;
	}

	protected void createInitialTopics() {
		int counter = 0;
		for (int i = 0; i < nWords; i++) {
			// Only generate topics for elements occurring often in enough
			// across all entries
			int wordFr = documentProvider.getWordFrequency(i);
			if (wordFr >= minWordFrequency) {
				TIntList topic = new TIntArrayList();
				// Topic with that single element (for a start)
				topic.add(i);
				// at position i
				topics[counter] = topic;
				topicSizes[counter] = documentProvider.getWordFrequency(i);
				topicLikelihoods[counter] = computeOneWordTopicLogLikelihood(i);
				totalLikelihood += topicLikelihoods[counter];

				topicFrequencyPerDocuments[counter] = new int[documents.size()];
				for (int j = 0; j < documents.size(); j++) {
					topicFrequencyPerDocuments[counter][j] = documents.get(j)
							.getWordFrequency(i);
				}

				sumWordFrTimesLogWordFrByTopic[counter] = wordFr
						* Math.log(wordFr);

				counter++;
			}
		}
	}

	protected void createInitialJoinCandidates(
			SolutionListener<T> solutionListener) {
		int initMax = maxTopics * (maxTopics - 1) / 2;
		int initCounter = 0;
		double[] coherence = new double[1];

		for (int i = 0; i < maxTopics; i++) {
			// topics[i] may be null if the element's frequency is below
			// frThreshold
			double bestImprovement = Double.NEGATIVE_INFINITY;
			double bestLikelihood = 0;
			int bestJ = -1;

			for (int j = i + 1; j < maxTopics; j++) {
				double newLikelihood = computeTwoWordLogLikelihood(i, j,
						topics[i].get(0), topics[j].get(0));

				double newImprovement = newLikelihood - topicLikelihoods[i]
						- topicLikelihoods[j];
				if (newImprovement > bestImprovement) {
					bestImprovement = newImprovement;
					bestLikelihood = newLikelihood;
					bestJ = j;
				}
				initCounter++;
				if (initCounter % 100000 == 0) {
					solutionListener.initalizing(((double) initCounter)
							/ initMax);
				}
			}
			if (bestJ != -1) {
				JoinCandidate jc = new JoinCandidate();
				jc.improvement = bestImprovement;
				jc.likelihood = bestLikelihood;
				jc.i = i;
				jc.j = bestJ;
				addToJoinCandiates(i, jc);
			}
		}
	}

	protected void addToJoinCandiates(int i, JoinCandidate jc) {
		joinCandidates.add(jc);
	}

	@Override
	public void solve(SolutionListener<T> solutionListener) {
		// Initialization
		totalLikelihood = 0;
		solutionListener.beforeInitialization(maxTopics, documentSizes.length);
		createInitialTopics();

		createInitialJoinCandidates(solutionListener);

		solutionListener.initialized(solution);

		groupTopics(solutionListener);

		solutionListener.done();
	}

	protected JoinCandidate getBestJoinCandidate() {
		JoinCandidate jc = joinCandidates.last();
		joinCandidates.remove(jc);
		return jc;
	}

	private final List<JoinCandidate> addLaterCache = new ArrayList<JoinCandidate>();

	protected void updateJoinCandidates(JoinCandidate jc) {
		// Recompute the best join partner for joined topic
		if (updateJoinCandidateForTopic(jc)) {
			// Add the new best join partner for topic[jc.i]
			joinCandidates.add(jc);
			// What if there is a jc_2 with jc_2.j == jc.j?
			// Should'nt the best join partner k = jc_2.j be
			// recomputed for jc_2?
			// By construction the improvement with regard to k must
			// by less than jc_2.improvement as it is (other we
			// would have jc_2.j == k already).
			// So the lame jc_2 will found via treeSet.first() early
			// enough under case II below.
			//
			// How about the case jc_2.j should become jc.i?
			// Then jc_2.i < jc.i, but this case comes next...
		}

		// Check for all jc2 with jc2.i < jc.i if jc is a better join
		// partner than the old one
		Iterator<JoinCandidate> it = joinCandidates.iterator();
		addLaterCache.clear();
		while (it.hasNext()) {
			JoinCandidate jc2 = it.next();
			if (jc2.i < jc.i) {
				if (topics[jc2.i] != null) {
					// This refers to the old topic of jc.i, so the
					// new
					// join partner for jc2 must be computed.
					if (jc2.j == jc.i) {
						it.remove();
						if (updateJoinCandidateForTopic(jc2)) {
							addLaterCache.add(jc2);
						}
					} else {
						double newLikelihood = computeTwoTopicLogLikelihood(
								jc2.i, jc.i);

						double newImprovement = newLikelihood
								- topicLikelihoods[jc2.i]
								- topicLikelihoods[jc.i];
						if (newImprovement > jc2.improvement) {
							it.remove();
							jc2.improvement = newImprovement;
							jc2.likelihood = newLikelihood;
							jc2.j = jc.i;
							addLaterCache.add(jc2);
						}
					}
				}
			}
		}
		if (!addLaterCache.isEmpty()) {
			joinCandidates.addAll(addLaterCache);
		}
	}

	protected void handleInconsistentJoinCandidate(JoinCandidate jc) {
		if (topics[jc.i] != null && topics[jc.j] == null) {
			if (updateJoinCandidateForTopic(jc)) {
				joinCandidates.add(jc);
			}
		}
	}

	public void groupTopics(SolutionListener<T> solutionListener) {
		nTopics[0] = maxTopics;
		while (nTopics[0] > minTopics) {
			// Get the best join candidate
			JoinCandidate jc = getBestJoinCandidate();
			if (jc != null) {
				if (topics[jc.i] != null && topics[jc.j] != null) {
					int t1Size = 0, t2Size = 0;
					if (!handleHomonymicTopic(jc)) {
						t1Size = topicSizes[jc.i];
						t2Size = topicSizes[jc.j];
						// Join the topics at position jc.i
						topics[jc.i].addAll(topics[jc.j]);
						topicUnionFind.union(jc.j, jc.i);
						topicSizes[jc.i] += t2Size;
						sumWordFrTimesLogWordFrByTopic[jc.i] += sumWordFrTimesLogWordFrByTopic[jc.j];
						// Compute likelihood for joined topic
						totalLikelihood -= topicLikelihoods[jc.i];
						topicLikelihoods[jc.i] = jc.likelihood;
						totalLikelihood += topicLikelihoods[jc.i];
						int[] a = topicFrequencyPerDocuments[jc.i];
						int[] b = topicFrequencyPerDocuments[jc.j];
						for (int i = 0; i < a.length; i++) {
							a[i] += b[i];
						}
						// Topic at position jc.j is gone
						topics[jc.j] = null;
						totalLikelihood -= topicLikelihoods[jc.j];
						topicLikelihoods[jc.j] = 0;
						topicSizes[jc.j] = 0;

						nTopics[0]--;

						solutionListener.updatedSolution(jc.i, jc.j,
								jc.improvement, t1Size, t2Size, solution);
					}
					updateJoinCandidates(jc);
				}
				// Case II
				else {
					handleInconsistentJoinCandidate(jc);
				}
			}
		}
	}

	protected boolean handleHomonymicTopic(JoinCandidate jc) {
		return false;
	}

	protected boolean updateJoinCandidateForTopic(JoinCandidate jc) {
		// Recompute the best join partner for joined topic
		double bestImprovement = Double.NEGATIVE_INFINITY;
		double bestLikelihood = 0;
		int bestJ = -1;
		for (int j = jc.i + 1; j < maxTopics; j++) {
			if (topics[j] != null) {
				double newLikelihood = computeTwoTopicLogLikelihood(jc.i, j);
				double newImprovement = newLikelihood - topicLikelihoods[jc.i]
						- topicLikelihoods[j];
				if (newImprovement > bestImprovement) {
					bestImprovement = newImprovement;
					bestLikelihood = newLikelihood;
					bestJ = j;
				}
			}
		}
		if (bestJ != -1) {
			jc.improvement = bestImprovement;
			jc.likelihood = bestLikelihood;
			jc.j = bestJ;
			return true;
		}
		return false;
	}

	protected static class JoinCandidate implements Comparable<JoinCandidate> {
		public double likelihood;
		public double improvement;
		public int i;
		public int j;

		@Override
		public int compareTo(JoinCandidate o) {
			if (improvement != o.improvement) {
				return improvement > o.improvement ? 1 : -1;
			}
			if (i != o.i) {
				return (i - o.i);
			}
			return j - o.j;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof JoinCandidate) {
				JoinCandidate jc = (JoinCandidate) obj;
				return jc.likelihood == likelihood
						&& jc.improvement == improvement && jc.i == i
						&& jc.j == j;
			}
			return false;
		}

		@Override
		public String toString() {
			return "[l:" + likelihood + " imp:" + improvement + " i:" + i
					+ " j:" + j + "]";
		}
	}

	protected static class DocIndexAndWordFr implements
			Comparable<DocIndexAndWordFr> {
		public int docIndex;
		public int wordFr;

		public int compareTo(DocIndexAndWordFr o) {
			return docIndex - o.docIndex;
		}
	}
}
