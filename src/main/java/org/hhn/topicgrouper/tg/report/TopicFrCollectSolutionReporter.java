package org.hhn.topicgrouper.tg.report;

import gnu.trove.TIntCollection;
import gnu.trove.iterator.TIntIterator;

import java.util.Arrays;

import org.hhn.topicgrouper.tg.TGSolution;
import org.hhn.topicgrouper.tg.TGSolutionListener;

public class TopicFrCollectSolutionReporter<T> implements TGSolutionListener<T> {
	private int[][] frequenciesPerNTopics;

	public TopicFrCollectSolutionReporter() {
	}

	public int[][] getFrequenciesPerNTopics() {
		return frequenciesPerNTopics;
	}

	@Override
	public void beforeInitialization(int maxTopics, int documents) {
		frequenciesPerNTopics = new int[maxTopics][];
	}

	@Override
	public void initalizing(double percentage) {
	}

	@Override
	public void initialized(TGSolution<T> initialSolution) {
	}
	
	@Override
	public void done() {
	}

	@Override
	public void updatedSolution(int newTopicIndex, int oldTopicIndex,
			double improvement, int t1Size, int t2Size, TGSolution<T> solution) {
		int[] frs = new int[solution.getNumberOfTopics()];
		int i = 0;
		for (TIntCollection t : solution.getTopics()) {
			if (t != null) {
				int sum = 0;
				TIntIterator it = t.iterator();
				while (it.hasNext()) {
					sum += solution.getGlobalWordFrequency(it.next());
				}
				frs[i] = sum;
				i++;
			}
		}
		Arrays.sort(frs);
		frequenciesPerNTopics[frs.length - 1] = frs;
	}
}
