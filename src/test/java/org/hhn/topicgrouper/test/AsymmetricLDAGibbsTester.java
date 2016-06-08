package org.hhn.topicgrouper.test;

import java.util.Random;

import org.hhn.topicgrouper.base.DocumentProvider;
import org.hhn.topicgrouper.eval.TWCLDAPaperDocumentGenerator;
import org.hhn.topicgrouper.ldagibbs.BasicGibbsSolutionReporter;
import org.hhn.topicgrouper.ldagibbs.GibbsSamplingLDAAdapt;
import org.hhn.topicgrouper.ldagibbs.GibbsSamplingLDAWithPerplexity;
import org.hhn.topicgrouper.validation.InDocumentHoldOutSplitter;

public class AsymmetricLDAGibbsTester {
	public static void main(String[] args) throws Exception {
		DocumentProvider<String> documentProvider = new TWCLDAPaperDocumentGenerator();

		InDocumentHoldOutSplitter<String> holdoutSplitter = new InDocumentHoldOutSplitter<String>(
				new Random(42), documentProvider, 0.1, 0);

		GibbsSamplingLDAAdapt gibbsSampler = new GibbsSamplingLDAWithPerplexity(
				holdoutSplitter.getRest(), new double[] { 5, 0.5, 0.5, 0.5 },
				0.5, 200, 10, "demo", "", 0, holdoutSplitter.getHoldOut());
		gibbsSampler.inference();
	}
}