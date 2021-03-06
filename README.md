# TopicGrouperJ
We offer a Java implementation and library for Topic Grouper, a complementary approach in the field of
probabilistic topic modeling.  Topic Grouper creates a disjunctive
partitioning of the training vocabulary in a stepwise manner such that
resulting partitions represent topics.  It is governed by a simple
generative model, where the likelihood to generate the training
documents via topics is optimized.  The algorithm starts with one-word
topics and joins two topics at every step. It therefore generates a
solution for every desired number of topics ranging between the size
of the training vocabulary and one. The process represents an
agglomerative clustering that corresponds to a binary tree of topics. A
resulting tree may act as a containment hierarchy, typically with more
general topics towards the root of tree and more specific topics
towards the leaves. Topic Grouper is not governed by a background
distribution such as the Dirichlet and avoids hyper parameter
optimizations.
