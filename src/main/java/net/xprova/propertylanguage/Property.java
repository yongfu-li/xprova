package net.xprova.propertylanguage;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Property {

	public String name;

	public int delay;

	public List<Property> children;

	public static Property build(String name) {

		return new Property(name);

	}

	public boolean isNumber() {

		return name.matches("\\d*\\d+");

	}

	public Property delay(int delay) {

		this.delay = delay;

		return this;

	}

	public Property setChild(Property child) {

		children = new ArrayList<Property>();

		return addChild(child);

	}

	public Property addChild(Property child) {

		children.add(child);

		return this;

	}

	public Property setChildren(List<Property> children) {

		this.children = new ArrayList<Property>();

		for (Property c : children)
			this.children.add(new Property(c));

		return this;

	}

	public Property(String name) {

		this.name = name;

		this.children = new ArrayList<Property>();

		this.delay = 0;

	}

	public Property(Property other) {

		this.name = other.name;

		this.delay = other.delay;

		setChildren(other.children);

	}

	public Property copyFrom(Property other) {

		this.name = other.name;

		this.delay = other.delay;

		this.children = other.children;

		return this;

	}

	public void print() {

		try {

			PrintStream out = new PrintStream(System.out, true, "UTF-8");

			print("", true, out);

		} catch (UnsupportedEncodingException e) {

			System.out.println("(internal error, could not print property)");

		}

	}

	public boolean isTerminal() {

		return children.isEmpty();

	}

	public void addDelayRecur(int extraDelay) {

		// adds to the delay of terminal nodes under root

		if (isTerminal()) {

			delay += extraDelay;

		} else {

			for (Property c : children)
				c.addDelayRecur(extraDelay);

		}

	}

	public int getMinDelay(int parentDelay) {

		if (isTerminal()) {

			return parentDelay + delay;

		} else {

			int minDelay = Integer.MAX_VALUE;

			for (Property n : children) {

				int d = n.getMinDelay(parentDelay + delay);

				minDelay = d < minDelay ? d : minDelay;

			}

			return minDelay;

		}

	}

	public int getMaxDelay(int parentDelay) {

		if (isTerminal()) {

			return parentDelay + delay;

		} else {

			int maxDelay = Integer.MIN_VALUE;

			for (Property n : children) {

				int d = n.getMaxDelay(parentDelay + delay);

				maxDelay = d > maxDelay ? d : maxDelay;

			}

			return maxDelay;

		}

	}

	public void flattenDelays(int parentDelay) {

		// this function propagates delays down a tree, effectively
		// mapping an expression like (@1 (a & b)) to (@1 a & @1 b)

		if (isTerminal()) {

			delay += parentDelay;

		} else {

			for (Property c : children)
				c.flattenDelays(delay + parentDelay);

			delay = 0;

		}

	}

	public void groupDelays(Map<String, Integer> identifiers) {

		// this is the reverse operation of flatten

		// it minimises the sum of delays across all nodes, effectively mapping
		// an expression like (@1 a & @1 b) to (@1 (a & b))

		if (!isTerminal()) {

			for (Property c : children)
				c.groupDelays(identifiers);

			int minChildDelay = Integer.MAX_VALUE;

			for (Property c : children)
				minChildDelay = c.delay < minChildDelay ? c.delay : minChildDelay;

			for (Property c : children)
				c.delay -= minChildDelay;

			delay += minChildDelay;

		}

	}

	public int recurCheck(Map<String, Integer> identifiers, boolean insideGroup) throws Exception {

		// this function checks for the unsupported property expression constructs:
		//
		// 1. delayed multi-bit identifiers
		// 2. comparison between identifiers of different bit widths

		int bits;

		if (isTerminal()) {

			bits = isNumber() ? -1 : identifiers.get(name);

		} else {

			ArrayList<Integer> childBits = new ArrayList<Integer>();

			for (Property c : children) {

				int b = c.recurCheck(identifiers, insideGroup || name.equals("{"));

				if (b != -1)
					childBits.add(b);

			}

			// check that all children have the same bit width

			for (int b : childBits)
				if (b != childBits.get(0))
					throw new Exception("bit width mismatch in property expression");

			if (name.equals("{")) {

				bits = children.size();

			} else if (Arrays.asList(PropertyBuilder.comparisonOps).contains(name)) {

				bits = 1;

			} else {

				bits = childBits.isEmpty() ? -1 : childBits.get(0);

			}

		}

		if (bits != 1 && delay != 0)
			throw new Exception("Found a delayed multi-bit net in property expression:\n" + toString());

		return bits;

	}

	@Override
	public String toString() {

		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		PrintStream ps = new PrintStream(baos);

		print("", true, ps);

		String content = new String(baos.toByteArray(), StandardCharsets.UTF_8);

		return content;

	}

	private void print(String prefix, boolean isTail, PrintStream out) {

		String n = name;

		n += (delay != 0) ? String.format(" (delay %d)", delay) : "";

		out.println(prefix + (isTail ? "\u2514\u2500\u2500 " : "\u251C\u2500\u2500 ") + n);

		if (children != null) {

			for (int i = 0; i < children.size() - 1; i++)
				children.get(i).print(prefix + (isTail ? "    " : "\u2502   "), false, out);

			if (children.size() > 0)
				children.get(children.size() - 1).print(prefix + (isTail ? "    " : "\u2502   "), true, out);

		}
	}

	public Property setName(String name) {

		this.name = name;

		return this;

	}
}