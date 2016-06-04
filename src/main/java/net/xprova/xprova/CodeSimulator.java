package net.xprova.xprova;

import java.util.ArrayList;
import java.util.HashSet;

import net.xprova.dot.GraphDotFormatter;
import net.xprova.dot.GraphDotPrinter;
import net.xprova.graph.Graph;

public class CodeSimulator {

	public void exploreSpace() throws Exception {

		// int {STATE_BIT} = 0;
		int count_0_ = 0;
		int count_1_ = 0;
		int count_2_ = 0;
		int count_3_ = 0;
		int n15 = 0;

		// int {NON_STATE_BIT};
		int n1;
		int n2;
		int n3;
		int n4;
		int n5;
		int n6;
		int n7;
		int n8;
		int n9, n10, n11, n12, n13, n14, valid;

		int[] vals = { 0, -1 };

		Graph<String> stateGraph = new Graph<String>();

		HashSet<Integer> toVisit = new HashSet<Integer>();

		HashSet<Integer> visited = new HashSet<Integer>();

		toVisit.add(0);

		while (!toVisit.isEmpty()) {

			HashSet<Integer> toVisitNext = new HashSet<Integer>();

			for (Integer state : toVisit) {

				// {STATE_BIT} = (state >> {STATE_BIT_INDEX}) & 1;
				count_0_ = (state >> 0) & 1;
				count_1_ = (state >> 1) & 1;
				count_2_ = (state >> 2) & 1;
				count_3_ = (state >> 3) & 1;
				n15 = (state >> 4) & 1;

				String s1 = String.format("%5s", Integer.toBinaryString(state)).replace(' ', '0');

				stateGraph.addVertex(s1);

				// for (int {INPUT_BIT} : vals) {
				for (int ena : vals) {

					// {NON_STATE_EQNS}
					n12 = count_1_ | count_0_;
					n13 = count_2_ & n12;
					n5 = ~ena;
					n6 = (count_1_ ^ count_0_);
					n9 = ~(count_1_ & count_0_);
					n7 = ~(n6 | n5);
					n10 = ~(n9 | n5);
					n11 = count_2_ & n10;
					n14 = count_3_ | n13;
					n8 = ~(count_1_ | ena);
					n1 = (count_0_ ^ ena);
					n3 = (count_2_ ^ n10);
					n2 = ~(n8 | n7);
					n4 = (count_3_ ^ n11);
					valid = ~(n15 & n14);

					// {STATE_EQNS}
					int next_count_3_ = n4;
					int next_count_1_ = n2;
					int next_n15 = ena;
					int next_count_2_ = n3;
					int next_count_0_ = n1;

					int next_state = 0;
					next_state += (next_count_0_ & 1) << 0;
					next_state += (next_count_1_ & 1) << 1;
					next_state += (next_count_2_ & 1) << 2;
					next_state += (next_count_3_ & 1) << 3;
					next_state += (next_n15 & 1) << 4;

					toVisitNext.add(next_state);

					String s2 = String.format("%5s", Integer.toBinaryString(next_state)).replace(' ', '0');

					System.out.printf("discovered %s (valid = %d) -> %s  by ena = %d\n", s1, valid == -1 ? 1 : 0, s2,
							ena == -1 ? 1 : 0);

					stateGraph.addVertex(s2);

					stateGraph.addConnection(s1, s2);

				}

			}

			visited.addAll(toVisit);

			toVisitNext.removeAll(visited);

			toVisit = toVisitNext;

		}

		GraphDotPrinter.printGraph("output/state.dot", stateGraph, new GraphDotFormatter<String>(),
				stateGraph.getVertices());

	}

	public void exploreSpace2() throws Exception {

		int count_0_ = 0;
		int count_1_ = 0;

		// int ena;

		int n1;
		int n2;
		int n3;
		int n4;
		int n5;
		int n6;
		int n7;
		int n8;
		int n9, n10, n11, n12, n13, n14, n15;

		int[] vals = { 0, -1 };

		Graph<Integer> stateGraph = new Graph<Integer>();

		HashSet<Integer> toVisit = new HashSet<Integer>();

		HashSet<Integer> visited = new HashSet<Integer>();

		toVisit.add(0);

		while (!toVisit.isEmpty()) {

			HashSet<Integer> toVisitNext = new HashSet<Integer>();

			for (Integer state : toVisit) {

				count_0_ = (state >> 0) & 1;
				count_1_ = (state >> 1) & 1;

				stateGraph.addVertex(state);

				for (int ena1 : vals) {
					for (int ena2 : vals) {
						n15 = ~ena1;
						n13 = ~ena2;
						n7 = ena2 & n15;
						n8 = n13 & ena1;
						n3 = ena2 | n15;
						n14 = n13 | ena1;
						n4 = n3 & n14;
						n6 = ~count_1_;
						n9 = n8 | n7;
						n11 = count_1_ & n4;
						n5 = count_0_ & n4;
						n10 = n9 & n6;
						n12 = n9 & count_0_;
						n2 = n12 | n11;
						n1 = n10 | n5;

						int next_count_0_ = n1;
						int next_count_1_ = n2;

						int next_state = 0;
						next_state += (next_count_0_ & 1) << 0;
						next_state += (next_count_1_ & 1) << 1;

						toVisitNext.add(next_state);

						System.out.printf("discovered %d -> %d by ena1 = %d, ena2 = %d\n", state, next_state,
								ena1 == -1 ? 1 : 0, ena2 == -1 ? 1 : 0);

						stateGraph.addVertex(next_state);

						stateGraph.addConnection(state, next_state);

					}
				}

			}

			visited.addAll(toVisit);

			toVisitNext.removeAll(visited);

			toVisit = toVisitNext;

		}

		GraphDotPrinter.printGraph("output/state.dot", stateGraph, new GraphDotFormatter<Integer>(),
				stateGraph.getVertices());

	}

	public void simulate() {

		AssertionTest gray1 = new AssertionTest();

		int[] initial = { 0, 0, 0, 0, 0 };

		ArrayList<String> sigNames = gray1.getSignalNames();

		int[] ena = { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 0, 0, 0, 0 };

		ArrayList<int[]> inputs = new ArrayList<int[]>();

		inputs.add(ena);

		int cycles = ena.length;

		ArrayList<int[]> results = gray1.simulate(initial, inputs, cycles);

		System.out.printf("%10s : ", "Cycle");

		for (int i = 0; i < cycles; i++)
			System.out.printf("%d", i % 10);

		System.out.println();

		System.out.println();

		for (int j = 0; j < results.size(); j++) {

			if (j == gray1.getStateBitCount())
				System.out.println();

			int[] sig = results.get(j);

			System.out.printf("%10s : ", sigNames.get(j));

			for (int i = 0; i < cycles; i++) {

				System.out.printf((sig[i] == -1) ? "1" : "0");

			}

			System.out.println();

		}

	}

}
